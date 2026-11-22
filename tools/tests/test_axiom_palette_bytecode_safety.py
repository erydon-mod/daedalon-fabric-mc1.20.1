"""Optional, read-only contract checks against locally installed Axiom JARs.

No Axiom binary is needed by the build or distributed with Daedalon. These
checks skip when neither local test installation nor javap is available.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import shutil
import subprocess
import unittest
import zipfile


REPO_ROOT = Path(__file__).resolve().parents[2]
AXIOM_CACHE = "com.moulberry.axiom.render.BlockRenderCache"
CAPTURE_DESCRIPTOR = (
    "(Lcom/moulberry/axiom/render/BlockRenderCache$SizedBlock;"
    "Lnet/minecraft/class_332;)V"
)


def installed_axiom_jars() -> list[Path]:
    directories = (
        REPO_ROOT / "run-dev/mods",
        Path.home() / "curseforge/minecraft/Instances/1.20.1 v3/mods",
    )
    jars = set()
    for directory in directories:
        for jar in directory.glob("*.jar"):
            # Read metadata rather than assuming a particular file name.
            if "axiom" not in jar.name.lower():
                continue
            with zipfile.ZipFile(jar) as archive:
                metadata = json.loads(archive.read("fabric.mod.json"))
                if metadata.get("id") == "axiom":
                    jars.add(jar.resolve())
    return sorted(jars)


def method_section(disassembly: str, name: str) -> str:
    headers = list(re.finditer(r"^  (?:public|private|protected).*;$", disassembly, re.M))
    matches = [
        (index, header)
        for index, header in enumerate(headers)
        if re.search(rf"\b{re.escape(name)}\(", header.group())
    ]
    if len(matches) != 1:
        raise AssertionError(f"Expected one {name} method, found {len(matches)}")
    index, header = matches[0]
    end = headers[index + 1].start() if index + 1 < len(headers) else len(disassembly)
    return disassembly[header.start():end]


class AxiomPaletteBytecodeSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        javap = shutil.which("javap")
        if javap is None:
            raise unittest.SkipTest("javap is not available")
        jars = installed_axiom_jars()
        if not jars:
            raise unittest.SkipTest("No Axiom JAR in either local test installation")
        cls.bytecode = {}
        for jar in jars:
            cls.bytecode[jar.name] = {}
            for name in (AXIOM_CACHE, AXIOM_CACHE + "$SizedBlock",
                         "com.moulberry.axiom.editor.ImGuiHelper"):
                result = subprocess.run(
                    [javap, "-p", "-c", "-s", "-classpath", str(jar), name],
                    check=True, capture_output=True, text=True, timeout=30,
                )
                cls.bytecode[jar.name][name] = result.stdout

    def test_capture_hook_and_accessors_match_installed_versions(self) -> None:
        for jar, classes in self.bytecode.items():
            with self.subTest(jar=jar):
                sized = classes[AXIOM_CACHE + "$SizedBlock"]
                self.assertIn("private final net.minecraft.class_2680 state;", sized)
                self.assertIn("private final boolean forceAsBlock;", sized)
                capture = method_section(classes[AXIOM_CACHE], "renderSizedBlock")
                self.assertIn("private static void renderSizedBlock(", capture)
                self.assertIn("descriptor: " + CAPTURE_DESCRIPTOR, capture)
                self.assertIn("SizedBlock.forceAsBlock:Z", capture)
                self.assertIn(
                    "shouldRenderAsItem:(Lnet/minecraft/class_2680;"
                    "Lnet/minecraft/class_1799;)Z", capture,
                )
                # HEAD cancellation precedes both FBO creation and slot insertion.
                self.assertRegex(
                    capture,
                    r"VersionUtilsClient\.(?:helper|legacy)CreateNewTextureTarget:",
                )
                self.assertIn("java/util/Map.put:", capture)

    def test_missing_capture_is_retried_by_the_main_ui(self) -> None:
        for jar, classes in self.bytecode.items():
            with self.subTest(jar=jar):
                cache = classes[AXIOM_CACHE]
                request = method_section(cache, "requestTexture")
                self.assertIn("instanceof", request)
                self.assertIn("class net/minecraft/class_2680", request)
                self.assertIn("java/util/Map.get:", request)
                self.assertIn("Field desired:", request)
                self.assertIn("java/util/Set.add:", request)
                self.assertIn("aconst_null", request)
                self.assertNotIn("java/util/Map.put:", request)

                tick = method_section(cache, "renderTick")
                self.assertEqual(2, tick.count("java/util/Set.forEach:"))
                self.assertEqual(2, tick.count("java/util/Set.clear:"))
                for suffix in ("0", "1"):
                    worker = method_section(cache, "lambda$renderTick$" + suffix)
                    self.assertIn("renderSizedBlock:" + CAPTURE_DESCRIPTOR, worker)

                draw = method_section(
                    classes["com.moulberry.axiom.editor.ImGuiHelper"], "drawBlockState"
                )
                # Axiom 5 returns int and 6 returns long. Neither API is hooked.
                self.assertRegex(
                    draw,
                    r"BlockRenderCache\.request:"
                    r"\(Lcom/moulberry/axiom/custom_blocks/CustomBlockState;IIZ\)[IJ]",
                )


if __name__ == "__main__":
    unittest.main()
