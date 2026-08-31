from __future__ import annotations

import importlib.util
import json
import sys
import unittest

from daedalon_test_support import (
    AUTHORED_UV_STATUE_FAMILIES,
    MESH_ROOT,
    REPO_ROOT,
    load_json,
)


UV_GENERATOR_PATH = REPO_ROOT / "tools/generate_statue_uvs.py"
SPEC = importlib.util.spec_from_file_location("generate_statue_uvs", UV_GENERATOR_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Could not load {UV_GENERATOR_PATH}")
UV_GENERATOR = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = UV_GENERATOR
SPEC.loader.exec_module(UV_GENERATOR)


class StatueUvRecipeSafetyTests(unittest.TestCase):
    def test_manifest_and_authored_meshes_are_locked_together(self) -> None:
        manifest = json.loads(
            UV_GENERATOR.RECIPE_MANIFEST_PATH.read_text(encoding="utf-8")
        )
        self.assertEqual(UV_GENERATOR.RECIPE_SCHEMA_VERSION, manifest["schema_version"])
        self.assertEqual(UV_GENERATOR.ALGORITHM_VERSION, manifest["algorithm_version"])
        self.assertEqual("5.2.0", manifest["blender_version"])
        self.assertEqual(UV_GENERATOR.REQUIRED_BLENDER_BUILD_HASH, manifest["blender_build_hash"])
        self.assertEqual(AUTHORED_UV_STATUE_FAMILIES, frozenset(manifest["models"]))

        recipes = UV_GENERATOR.load_recipe_manifest()
        for family in sorted(AUTHORED_UV_STATUE_FAMILIES):
            source = UV_GENERATOR.parse_source(MESH_ROOT / f"{family}.obj")
            self.assertEqual(
                recipes[family].topology_sha256,
                UV_GENERATOR.topology_sha256(source),
                family,
            )
            definition = load_json(MESH_ROOT / f"{family}_statue.json")
            self.assertFalse(definition["repair_degenerate_uvs"], family)
            self.assertFalse(definition["force_uv_projection"], family)

    def test_aesthetic_exceptions_are_model_scoped(self) -> None:
        self.assertEqual(
            {"dionysus": 3.05, "helios": 3.10, "phaeton": 2.65},
            UV_GENERATOR.MODEL_MAX_P95_STRETCH,
        )
        recipes = UV_GENERATOR.load_recipe_manifest()
        for family, recipe in recipes.items():
            expected_limit = UV_GENERATOR.MODEL_MAX_P95_STRETCH.get(
                family, UV_GENERATOR.MAX_P95_STRETCH
            )
            self.assertLessEqual(
                recipe.expected_p95_stretch_max,
                expected_limit,
                family,
            )

    def test_generated_uv_blocks_do_not_change_locked_source_line_identity(self) -> None:
        for family, exceptions in UV_GENERATOR.SINGLE_FACE_EXCEPTIONS.items():
            source = UV_GENERATOR.parse_source(MESH_ROOT / f"{family}.obj")
            for exception in exceptions:
                actual = tuple(
                    UV_GENERATOR.source_line_number_without_generated_uv_block(
                        source, source.faces[index]
                    )
                    for index in exception["source_faces"]
                )
                self.assertEqual(exception["source_lines"], actual, family)

    def test_discovery_is_explicit_and_dry_run_only(self) -> None:
        parser = UV_GENERATOR.build_parser()
        with self.assertRaisesRegex(ValueError, "requires --dry-run"):
            UV_GENERATOR.invocation_context(
                parser.parse_args(["apollo", "--discover", "--write"])
            )
        with self.assertRaisesRegex(ValueError, "explicit model names"):
            UV_GENERATOR.invocation_context(
                parser.parse_args(["all", "--discover", "--dry-run"])
            )

    def test_normal_replay_rejects_intentional_projection_fallbacks(self) -> None:
        parser = UV_GENERATOR.build_parser()
        for family in ("bellerophon", "lion_statant"):
            with self.subTest(family=family), self.assertRaisesRegex(
                ValueError, f"no locked UV recipe for: {family}"
            ):
                UV_GENERATOR.invocation_context(
                    parser.parse_args([family, "--check"])
                )

    def test_all_check_replays_only_authored_models(self) -> None:
        parser = UV_GENERATOR.build_parser()
        models, recipes = UV_GENERATOR.invocation_context(
            parser.parse_args(["all", "--check"])
        )
        expected = tuple(
            name
            for name in UV_GENERATOR.MODEL_NAMES
            if name in AUTHORED_UV_STATUE_FAMILIES
        )
        self.assertEqual(expected, models)
        self.assertEqual(AUTHORED_UV_STATUE_FAMILIES, frozenset(recipes))

    def test_all_write_refuses_incomplete_authored_coverage(self) -> None:
        parser = UV_GENERATOR.build_parser()
        with self.assertRaisesRegex(
            ValueError, "no locked UV recipe for: bellerophon, lion_statant"
        ):
            UV_GENERATOR.invocation_context(
                parser.parse_args(["all", "--write"])
            )


if __name__ == "__main__":
    unittest.main()
