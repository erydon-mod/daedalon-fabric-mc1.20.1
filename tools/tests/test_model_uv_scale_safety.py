from __future__ import annotations

import json
import math
import unittest
from pathlib import Path

from daedalon_test_support import JAVA_ROOT, MESH_ROOT


class ModelUvScaleSafetyTests(unittest.TestCase):
    def test_every_resizable_mesh_bakes_at_its_declared_largest_size(self) -> None:
        expected_spans = {
            "urn_": 1.0,
            "finial_": 2.0,
            "plinth_": 2.0,
            "bust_": 3.0,
            "corbel_": 3.0,
        }
        checked: set[str] = set()

        for definition_path in sorted(MESH_ROOT.glob("*.json")):
            stem = definition_path.stem
            expected = next(
                (span for prefix, span in expected_spans.items() if stem.startswith(prefix)),
                None,
            )
            if stem == "monument_obeliskos" or stem.endswith("_statue"):
                expected = 3.0
            if expected is None:
                continue

            definition = json.loads(definition_path.read_text(encoding="utf-8"))
            span = transformed_largest_span(definition, definition_path)
            self.assertTrue(math.isclose(span, expected, rel_tol=0.0, abs_tol=2.0e-5), (
                f"{stem} bakes with projection span {span:.8f}; expected {expected:.1f}. "
                "That changes its material scale or risks an atlas overrun."
            ))
            checked.add(stem)

        self.assertEqual(58, len(checked))
        self.assertEqual(
            {f"plinth_{style}" for style in (
                "astragalos", "bathron", "kion", "stephanos", "triphyllon"
            )},
            {stem for stem in checked if stem.startswith("plinth_")},
        )

    def test_every_runtime_size_transform_scales_uvs_with_geometry(self) -> None:
        source = (
            JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java"
        ).read_text(encoding="utf-8")

        for transform in (
            "StatueTransform",
            "UrnTransform",
            "CorbelTransform",
            "SizedDecorTransform",
            "GroundScaleTransform",
        ):
            body = class_body(source, transform)
            self.assertIn("quad.pos", body, transform)
            self.assertIn("quad.uv", body, transform)

        urn = class_body(source, "UrnTransform")
        self.assertIn("quad.u(vertex) * scale", urn)
        self.assertIn("quad.v(vertex) * scale", urn)

    def test_urn_large_uvs_stay_inside_the_repeat_sheet(self) -> None:
        large_scale = 1.3844
        source_columns = 4
        sheet_columns = 12
        source_rows = 1
        sheet_rows = 6
        maximum_phase = 5 / sheet_columns

        for definition_path in sorted(MESH_ROOT.glob("urn_*.json")):
            definition = json.loads(definition_path.read_text(encoding="utf-8"))
            repeats = float(definition["cylindrical_u_repeats"])
            texture_tiles = int(definition["texture_u_tiles"])
            # A rear-seam triangle can unwrap the angle as far as 1.5 turns.
            maximum_source_u = 1.5 * repeats / texture_tiles
            maximum_sheet_u = (
                maximum_source_u * large_scale * source_columns / sheet_columns
                + maximum_phase
            )
            maximum_sheet_v = large_scale * source_rows / sheet_rows
            self.assertGreaterEqual(maximum_sheet_u, 0.0, definition_path.name)
            self.assertLessEqual(maximum_sheet_u, 1.0, definition_path.name)
            self.assertGreaterEqual(maximum_sheet_v, 0.0, definition_path.name)
            self.assertLessEqual(maximum_sheet_v, 1.0, definition_path.name)


def transformed_largest_span(definition: dict[str, object], definition_path: Path) -> float:
    obj_id = str(definition["obj"])
    obj_path = MESH_ROOT / obj_id.rsplit("/", 1)[-1]
    minima = [math.inf, math.inf, math.inf]
    maxima = [-math.inf, -math.inf, -math.inf]
    for line in obj_path.read_text(encoding="utf-8").splitlines():
        if not line.startswith("v "):
            continue
        coordinates = [float(value) for value in line.split()[1:4]]
        for axis, coordinate in enumerate(coordinates):
            minima[axis] = min(minima[axis], coordinate)
            maxima[axis] = max(maxima[axis], coordinate)

    raw_spans = [maximum - minimum for minimum, maximum in zip(minima, maxima)]
    self_fit = 1.0 / max(raw_spans) if definition.get("fit_to_block", False) else 1.0
    scale = [float(value) for value in definition.get("scale", [1.0, 1.0, 1.0])]
    transformed = [
        raw_span * self_fit * axis_scale
        for raw_span, axis_scale in zip(raw_spans, scale)
    ]
    if not all(math.isfinite(value) and value > 0.0 for value in transformed):
        raise AssertionError(f"{definition_path.name} has invalid transformed bounds")
    return max(transformed)


def class_body(source: str, class_name: str) -> str:
    markers = (f"class {class_name}", f"record {class_name}")
    starts = [source.find(marker) for marker in markers]
    start = min(index for index in starts if index >= 0)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening:index + 1]
    raise AssertionError(f"Could not isolate {class_name}")


if __name__ == "__main__":
    unittest.main()
