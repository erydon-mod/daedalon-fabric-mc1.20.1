from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
TEST_ROOT = REPO_ROOT / "tools/tests"
sys.path.insert(0, str(TEST_ROOT))

from daedalon_test_support import (  # noqa: E402
    GENERATOR,
    MESH_EXPECTATIONS,
    all_block_ids,
)

ALIAS_NAMESPACE = "daedalon"
ALIAS_MANIFEST_ENTRY = (
    f"assets/{ALIAS_NAMESPACE}/texture_aliases/v1.json"
)
ALIAS_BLOB_PREFIX = f"assets/{ALIAS_NAMESPACE}/texture_blobs/"


def project_property(name: str) -> str:
    for raw_line in (REPO_ROOT / "gradle.properties").read_text(
        encoding="utf-8"
    ).splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() == name:
            return value.strip()
    raise AssertionError(f"Missing Gradle property: {name}")


EXPECTED_MOD_VERSION = project_property("mod_version")
EXPECTED_ARCHIVE_NAME = project_property("archives_base_name")
EXPECTED_FAMILY_COMPATIBILITY_GENERATION = int(
    project_property("family_compatibility_generation")
)
RESOLVER_CLASS_ENTRIES = {
    "com/oliver/daedalon/client/texturealias/"
    "FamilyTextureAliasCoordinator.class",
    "com/oliver/daedalon/client/texturealias/"
    "TextureAliasManifest.class",
    "com/oliver/daedalon/client/texturealias/"
    "TextureAliasResourcePack.class",
    "com/oliver/daedalon/client/texturealias/"
    "TextureAliasResourcePack$ModAliasResourcePack.class",
    "com/oliver/daedalon/mixin/client/texturealias/"
    "ModResourcePackCreatorMixin.class",
    "com/oliver/daedalon/mixin/client/texturealias/"
    "ResourcePackProfileMixin.class",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def sha256(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def texture_alias_details(
    archive: zipfile.ZipFile, entries: set[str]
) -> dict[str, object]:
    blob_entries = {
        entry
        for entry in entries
        if entry.startswith(ALIAS_BLOB_PREFIX) and not entry.endswith("/")
    }
    if ALIAS_MANIFEST_ENTRY not in entries:
        require(
            not blob_entries,
            "Physical fallback JAR must not contain orphan texture blobs",
        )
        return {
            "enabled": False,
            "aliases": 0,
            "blobs": 0,
            "logicalBytes": 0,
        }

    manifest = json.loads(archive.read(ALIAS_MANIFEST_ENTRY))
    require(
        manifest.get("schema_version") == 1,
        "Texture alias manifest schema must be 1",
    )
    require(
        manifest.get("format") == "erydon-texture-aliases",
        "Texture alias manifest format is invalid",
    )
    require(
        manifest.get("namespace") == ALIAS_NAMESPACE,
        "Texture alias manifest namespace must be daedalon",
    )
    aliases = manifest.get("aliases")
    blobs = manifest.get("blobs")
    require(isinstance(aliases, list), "Texture alias aliases must be an array")
    require(isinstance(blobs, list), "Texture alias blobs must be an array")

    declared_targets = {
        blob.get("target")
        for blob in blobs
        if isinstance(blob, dict)
    }
    actual_targets: set[str] = set()
    logical_bytes = 0
    logical_paths: set[str] = set()
    for alias in aliases:
        require(isinstance(alias, dict), "Texture alias entry must be an object")
        alias_path = alias.get("path")
        target = alias.get("target")
        file_hash = alias.get("file_sha256")
        require(
            isinstance(alias_path, str)
            and isinstance(target, str)
            and isinstance(file_hash, str),
            "Texture alias path, target and hash must be strings",
        )
        logical_entry = f"assets/{ALIAS_NAMESPACE}/{alias_path}"
        require(
            logical_entry not in entries,
            f"Deduplicated JAR retained physical alias {logical_entry}",
        )
        require(
            target.startswith(ALIAS_BLOB_PREFIX),
            f"Texture alias target escaped daedalon blobs: {target}",
        )
        require(target in entries, f"Texture alias target is missing: {target}")
        payload = archive.read(target)
        require(
            sha256(payload) == file_hash,
            f"Texture alias target hash differs: {target}",
        )
        require(
            target == f"{ALIAS_BLOB_PREFIX}{file_hash}.png",
            f"Texture alias target is not content addressed: {target}",
        )
        require(
            alias.get("mcmeta_present") is False,
            f"Metadata-backed alias is not permitted: {logical_entry}",
        )
        require(
            logical_entry not in logical_paths,
            f"Duplicate logical texture alias: {logical_entry}",
        )
        logical_paths.add(logical_entry)
        actual_targets.add(target)
        logical_bytes += len(payload)

    require(
        actual_targets == declared_targets == blob_entries,
        "Manifest blobs, alias targets and packaged blobs must match exactly",
    )
    return {
        "enabled": True,
        "aliases": len(aliases),
        "blobs": len(blob_entries),
        "logicalBytes": logical_bytes,
    }


def expanded_resources(archive: zipfile.ZipFile) -> dict[str, str]:
    entries = set(archive.namelist())
    expanded = {
        entry: sha256(archive.read(entry))
        for entry in entries
        if not entry.endswith("/")
        and (entry.startswith("assets/") or entry.startswith("data/"))
        and entry != ALIAS_MANIFEST_ENTRY
        and not entry.startswith(ALIAS_BLOB_PREFIX)
    }
    if ALIAS_MANIFEST_ENTRY in entries:
        manifest = json.loads(archive.read(ALIAS_MANIFEST_ENTRY))
        for alias in manifest["aliases"]:
            logical_entry = (
                f"assets/{manifest['namespace']}/{alias['path']}"
            )
            expanded[logical_entry] = sha256(archive.read(alias["target"]))
    return expanded


def exact_entry_hashes(
    archive: zipfile.ZipFile, predicate
) -> dict[str, str]:
    return {
        entry: sha256(archive.read(entry))
        for entry in archive.namelist()
        if not entry.endswith("/") and predicate(entry)
    }


def compare_with_baseline(
    jar_path: Path, baseline_path: Path
) -> dict[str, object]:
    require(
        baseline_path.is_file(),
        f"Baseline JAR does not exist: {baseline_path}",
    )
    with (
        zipfile.ZipFile(jar_path) as candidate,
        zipfile.ZipFile(baseline_path) as baseline,
    ):
        candidate_resources = expanded_resources(candidate)
        baseline_resources = expanded_resources(baseline)
        require(
            candidate_resources == baseline_resources,
            "Deduplicated and physical JAR logical resources differ",
        )
        candidate_properties = exact_entry_hashes(
            candidate, lambda entry: entry.endswith(".properties")
        )
        baseline_properties = exact_entry_hashes(
            baseline, lambda entry: entry.endswith(".properties")
        )
        require(
            candidate_properties == baseline_properties,
            "Deduplication changed CTM .properties files",
        )
        candidate_classes = exact_entry_hashes(
            candidate, lambda entry: entry.endswith(".class")
        )
        baseline_classes = exact_entry_hashes(
            baseline, lambda entry: entry.endswith(".class")
        )
        require(
            candidate_classes == baseline_classes,
            "Deduplicated and physical JAR classes differ",
        )
        require(
            candidate.read("fabric.mod.json")
            == baseline.read("fabric.mod.json"),
            "Deduplicated and physical fabric.mod.json differ",
        )
    require(
        jar_path.stat().st_size < baseline_path.stat().st_size,
        "Deduplicated JAR must be strictly smaller than the physical fallback",
    )
    return {
        "baseline": str(baseline_path.resolve()),
        "baselineBytes": baseline_path.stat().st_size,
        "candidateBytes": jar_path.stat().st_size,
        "strictlySmaller": jar_path.stat().st_size < baseline_path.stat().st_size,
        "logicalResourcesEqual": True,
        "propertiesEqual": True,
        "classesEqual": True,
        "modMetadataEqual": True,
    }


def audit(
    jar_path: Path, baseline_path: Path | None = None
) -> dict[str, object]:
    require(jar_path.is_file(), f"JAR does not exist: {jar_path}")
    expected_jar_name = (
        f"{EXPECTED_ARCHIVE_NAME}-"
        f"compat{EXPECTED_FAMILY_COMPATIBILITY_GENERATION}-"
        f"{EXPECTED_MOD_VERSION}.jar"
    )
    require(
        jar_path.name == expected_jar_name,
        f"Daedalon JAR filename must be {expected_jar_name}",
    )
    expected_ids = set(all_block_ids())
    expected_block_count = len(expected_ids)
    expected_json_names = {f"{block_id}.json" for block_id in expected_ids}

    with zipfile.ZipFile(jar_path) as archive:
        entries = set(archive.namelist())

        def direct_json_names(prefix: str) -> set[str]:
            return {
                entry[len(prefix) :]
                for entry in entries
                if entry.startswith(prefix)
                and entry.endswith(".json")
                and "/" not in entry[len(prefix) :]
            }

        blockstates = direct_json_names("assets/daedalon/blockstates/")
        items = direct_json_names("assets/daedalon/models/item/")
        loot = direct_json_names("data/daedalon/loot_tables/blocks/")
        require(
            blockstates == expected_json_names,
            f"JAR must contain exactly {expected_block_count} Daedalon blockstates",
        )
        require(
            items == expected_json_names | {"emblem.json"},
            f"JAR must contain exactly {expected_block_count} Daedalon block item models and the emblem",
        )
        require(
            loot == expected_json_names,
            f"JAR must contain exactly {expected_block_count} Daedalon self-drop loot tables",
        )

        expected_mesh_entries: set[str] = set()
        expected_obj_entries: set[str] = set()
        expected_mtl_entries: set[str] = set()
        expected_display_entries: set[str] = set()
        for expected in MESH_EXPECTATIONS.values():
            definition_entry = (
                f"assets/daedalon/models/mesh/{expected['definition']}"
            )
            obj_entry = f"assets/daedalon/models/mesh/{expected['obj']}"
            expected_mesh_entries.update({definition_entry, obj_entry})
            expected_obj_entries.add(obj_entry)
            if expected["mtl"] is not None:
                mtl_entry = f"assets/daedalon/models/mesh/{expected['mtl']}"
                expected_mesh_entries.add(mtl_entry)
                expected_mtl_entries.add(mtl_entry)
            expected_display_entries.add(
                f"assets/daedalon/models/block/mesh/{expected['display']}"
            )
        require(
            expected_mesh_entries <= entries,
            "JAR is missing one or more OBJ/MTL/definition manifests",
        )
        require(
            expected_display_entries <= entries,
            "JAR is missing one or more OBJ display models",
        )

        actual_obj_entries = {
            entry
            for entry in entries
            if entry.startswith("assets/daedalon/models/mesh/")
            and entry.endswith(".obj")
        }
        actual_mtl_entries = {
            entry
            for entry in entries
            if entry.startswith("assets/daedalon/models/mesh/")
            and entry.endswith(".mtl")
        }
        require(
            actual_obj_entries == expected_obj_entries,
            f"JAR must contain exactly {len(expected_obj_entries)} approved OBJs",
        )
        require(
            actual_mtl_entries == expected_mtl_entries,
            f"JAR must contain exactly {len(expected_mtl_entries)} approved MTLs",
        )

        for family, expected in MESH_EXPECTATIONS.items():
            obj_entry = f"assets/daedalon/models/mesh/{expected['obj']}"
            require(
                sha256(archive.read(obj_entry)) == expected["obj_sha256"],
                f"{family} OBJ hash changed",
            )
            if expected["mtl"] is not None:
                mtl_entry = f"assets/daedalon/models/mesh/{expected['mtl']}"
                require(
                    sha256(archive.read(mtl_entry)) == expected["mtl_sha256"],
                    f"{family} MTL hash changed",
                )

        forbidden_prefixes = (
            "assets/erydon/",
            "data/erydon/",
            "assets/erydon_themelios/",
            "data/erydon_themelios/",
            "assets/themelios/",
            "data/themelios/",
            "com/oliver/erydon/",
            "com/oliver/erydonthemelios/",
        )
        forbidden_entries = sorted(
            entry
            for entry in entries
            if entry.startswith(forbidden_prefixes)
        )
        require(
            not forbidden_entries,
            "JAR contains foreign ERYDON/Themelios namespaces: "
            + ", ".join(forbidden_entries[:10]),
        )

        metadata = json.loads(archive.read("fabric.mod.json"))
        require(metadata["id"] == "daedalon", "fabric.mod.json id must be daedalon")
        require(
            metadata["name"] == "ERYDON DEADALON",
            "fabric.mod.json name must be ERYDON DEADALON",
        )
        require(
            metadata["version"] == EXPECTED_MOD_VERSION,
            f"Daedalon version must be {EXPECTED_MOD_VERSION}",
        )
        require(
            metadata["license"] == "LicenseRef-Oliver-Restricted-1.0",
            "fabric.mod.json must name Oliver's restricted license",
        )
        require(
            metadata["depends"].get("minecraft") == "1.20.1",
            "Daedalon must target Minecraft 1.20.1",
        )
        require(
            metadata["depends"].get("java") == ">=17",
            "Daedalon must target Java 17",
        )
        require(
            metadata.get("custom", {}).get(
                "erydon:texture_alias_resolver"
            )
            == 1,
            "Daedalon must advertise texture alias resolver capability 1",
        )
        require(
            metadata.get("custom", {}).get(
                "erydon:family_compatibility_generation"
            )
            == EXPECTED_FAMILY_COMPATIBILITY_GENERATION,
            "Daedalon family compatibility generation metadata differs",
        )
        require(
            "daedalon-texture-alias.mixins.json" in entries,
            "Dedicated texture alias mixin config is missing",
        )
        require(
            RESOLVER_CLASS_ENTRIES <= entries,
            "JAR is missing one or more texture alias resolver classes",
        )
        alias_mixin = json.loads(
            archive.read("daedalon-texture-alias.mixins.json")
        )
        require(
            alias_mixin.get("required") is True,
            "Texture alias mixin config must be required",
        )
        require(
            set(alias_mixin.get("client", []))
            == {
                "ModResourcePackCreatorMixin",
                "ResourcePackProfileMixin",
            },
            "Texture alias mixin config has unexpected client hooks",
        )
        dependency_keys = {
            key
            for section in ("depends", "recommends", "suggests", "breaks", "conflicts")
            for key in metadata.get(section, {})
        }
        require(
            dependency_keys.isdisjoint(
                {"erydon", "erydon_themelios", "themelios"}
            ),
            "Daedalon metadata must not depend on ERYDON or Themelios",
        )

        license_entries = sorted(
            entry
            for entry in entries
            if entry.startswith("LICENSE_daedalon-fabric-mc1.20.1")
        )
        require(len(license_entries) == 1, "Restricted license is not packaged once")
        license_text = archive.read(license_entries[0]).decode("utf-8")
        require(
            "sell, rent, sublicense, or charge" in license_text,
            "Packaged license must prohibit sale",
        )
        require(
            "modify, adapt, translate" in license_text,
            "Packaged license must prohibit modification",
        )
        require(
            "<Product name> by Oliver" in license_text,
            "Packaged license must require public attribution",
        )

        notice_entry = "META-INF/THIRD_PARTY_NOTICES.md"
        require(notice_entry in entries, "Third-party notice is not packaged")
        notice = archive.read(notice_entry).decode("utf-8")
        for required_notice in (
            "Neoclassical Urn on Pedestal | Lowpoly Asset",
            "tina.hill",
            "https://skfb.ly/pGAGK",
            "https://creativecommons.org/licenses/by/4.0/",
        ):
            require(
                required_notice in notice,
                f"Third-party notice is missing {required_notice}",
            )

        nested_jars = sorted(
            entry for entry in entries if entry.endswith(".jar") and entry != jar_path.name
        )
        require(
            not nested_jars,
            "Standalone Daedalon JAR must not bundle dependency JARs",
        )
        alias_details = texture_alias_details(archive, entries)

    report = {
        "status": "passed",
        "jar": str(jar_path.resolve()),
        "jarBytes": jar_path.stat().st_size,
        "jarSha256": hashlib.sha256(jar_path.read_bytes()).hexdigest(),
        "metadata": {
            "id": "daedalon",
            "name": "Daedalon",
            "version": EXPECTED_MOD_VERSION,
            "familyCompatibilityGeneration": (
                EXPECTED_FAMILY_COMPATIBILITY_GENERATION
            ),
            "minecraft": "1.20.1",
            "java": 17,
        },
        "resources": {
            "blockstates": len(blockstates),
            "itemModels": len(items),
            "lootTables": len(loot),
            "objFiles": len(actual_obj_entries),
            "mtlFiles": len(actual_mtl_entries),
            "meshDefinitions": len(MESH_EXPECTATIONS),
            "displayModels": len(expected_display_entries),
        },
        "independence": {
            "forbiddenForeignEntries": 0,
            "foreignModDependencies": 0,
            "nestedJars": 0,
        },
        "textureAliases": alias_details,
    }
    if baseline_path is not None:
        report["baselineComparison"] = compare_with_baseline(
            jar_path, baseline_path
        )
    return report


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Audit the standalone Daedalon Fabric JAR."
    )
    parser.add_argument("jar", type=Path)
    parser.add_argument(
        "--baseline",
        type=Path,
        help="Physical fallback JAR used for logical/class/property gates.",
    )
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    report = audit(args.jar, args.baseline)
    if args.report is not None:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(
            json.dumps(report, indent=2) + "\n", encoding="utf-8"
        )
    print(
        "Daedalon JAR audit passed: "
        f"blocks={report['resources']['blockstates']} "
        f"objs={report['resources']['objFiles']} "
        f"aliases={report['textureAliases']['aliases']} "
        f"bytes={report['jarBytes']} "
        f"sha256={report['jarSha256']}"
    )


if __name__ == "__main__":
    main()
