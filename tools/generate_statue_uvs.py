#!/usr/bin/env python3
"""Generate deterministic, topology-aware UVs for the classical statues.

Normal runs replay checked-in per-model recipes through Blender 5.2::

    python tools/generate_statue_uvs.py aphrodite demeter orpheus zeus --check
    python tools/generate_statue_uvs.py aphrodite demeter orpheus zeus --write

Expensive candidate search is an explicit, dry-run-only authoring operation for
a new or deliberately re-authored model::

    python tools/generate_statue_uvs.py new_model --discover --dry-run --json

The supplied OBJs intentionally keep their exact vertex stream and exact face
position stream/order/winding.  The generator only replaces generated ``vt``
records and adds the matching texture-coordinate reference to each face corner.

The unwrap is deliberately not Blender's Smart UV Project.  These statue
surfaces have many handles and Smart UV creates hundreds of tiny islands.  We
first open every handle with a deterministic tree/cotree cut graph.  Normal
``--write`` and ``--check`` runs then replay the locked base chart and measured
dual-geodesic arc ranks without searching alternatives.  ``--discover`` alone
tests the deterministic height/azimuth family and finds a new recipe.  Blender's
Angle Based unwrap parameterizes each chart; whole-chart Minimum Stretch (SLIM)
may replace folded or locally scale-distorted charts according to the recipe.
"""

from __future__ import annotations

import argparse
import hashlib
import heapq
import json
import math
import os
import struct
import subprocess
import sys
from collections import defaultdict, deque
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence

try:  # Available only after the launcher re-enters through Blender.
    import bpy  # type: ignore[import-not-found]
except ModuleNotFoundError:  # pragma: no cover - exercised by the launcher.
    bpy = None


MODEL_NAMES = (
    "zeus",
    "aphrodite",
    "apollo",
    "ares",
    "artemis",
    "athena",
    "bellerophon",
    "demeter",
    "dionysus",
    "helios",
    "hera",
    "heracles",
    "lion_couchant",
    "lion_statant",
    "orpheus",
    "perseus",
    "phaeton",
    "poseidon",
    "theseus",
)

CANDIDATE_DIMENSIONS = (
    (1, 1),
    (2, 3),
    (2, 4),
    (3, 1),
    (4, 1),
    (5, 1),
    (4, 3),
    (3, 4),
    (3, 3),
    (4, 4),
    (3, 5),
    (2, 5),
    (4, 5),
    (1, 5),
)

DEFAULT_BLENDER = Path(r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe")
REQUIRED_BLENDER_VERSION = (5, 2, 0)
REQUIRED_BLENDER_BUILD_HASH = "fbe6228777e7"
GENERATED_COMMENT_PREFIX = "# Daedalon topology-aware UV unwrap"
ALGORITHM_VERSION = 3
RECIPE_SCHEMA_VERSION = 1
RECIPE_MANIFEST_PATH = Path(__file__).with_name("statue_uv_recipes.json")

MAX_SEAM_PERCENT = 6.0
MAX_P95_STRETCH = 2.5
MAX_P99_STRETCH = 8.0
MODEL_MAX_P95_STRETCH = {
    "dionysus": 3.05,
    "helios": 3.10,
    "phaeton": 2.65,
}
MAX_DENSITY_RATIO_P50 = 1.25
MAX_DENSITY_RATIO_P90 = 1.5
MAX_DENSITY_RATIO_P95 = 2.0
MAX_DENSITY_RATIO_P99 = 4.0
UV_COMPARE_EPSILON = 1.0e-6
UV_COLLAPSE_EPSILON = 1.0e-20
SERIALIZED_DECIMALS = 12
TARGET_UV_PER_WORLD_BLOCK = 1.0 / 3.0
UV_GUTTER = 1.0e-6
MAX_ISLAND_DENSITY_ERROR = 1.0e-4
MAX_LOCKED_MICRO_ISLAND_DENSITY_ERROR = 5.0e-4
PRIMARY_ACTUAL_TRIAL_LIMIT = 4
EXPANDED_ACTUAL_TRIAL_LIMIT = 12
DISCOVERY_MAX_SPLITS_PER_BASE = 6
DISCOVERY_BASE_SPECS = (
    # Accepted bounded policy: rear spine first, then the two established
    # three-height baselines.  Do not silently expand this search family.
    (1, 1, False, False),
    (3, 1, False, False),
    (3, 1, True, False),
)
DISCOVERY_MODEL_BASE_SPECS = {
    "artemis": ((3, 1, False, False),),
    # Final bounded Bellerophon fallback: choose exactly one of these two
    # regional layouts by the established seam-visibility score.
    "bellerophon": ((3, 1, False, False), (3, 1, True, False)),
    "dionysus": ((3, 1, False, False),),
    "helios": ((4, 1, True, False),),
    "lion_statant": ((3, 1, False, False),),
    # Phaeton's high-genus chariot/human silhouette has one previously proven
    # disk-safe basin.  Keep discovery on that basin instead of widening the
    # ordinary three-base search.
    "phaeton": ((5, 1, True, False),),
}
DISCOVERY_EXTRA_REFINEMENT_SPLITS = {"phaeton": 2}
DISCOVERY_EXTRA_FIT_SPLITS: dict[str, int] = {}
DISCOVERY_EXPANDED_FIT_TRIAL_MODELS = frozenset({"apollo"})
DISCOVERY_EXPANDED_QUALITY_TRIAL_BUDGET = {"phaeton": 3}
DISCOVERY_REAR_VERTICAL_FIT_PATH_MODELS: frozenset[str] = frozenset()
DISCOVERY_VISIBILITY_CHOSEN_BASE_MODELS = frozenset({"bellerophon"})
DISCOVERY_SOLVER_POLICY = "abf_density_slim_v1"
DISCOVERY_REFINEMENT_POLICY = "conformal_density_v3"
SUPPORTED_SOLVER_POLICIES = frozenset(
    {"abf_fold_slim_v1", DISCOVERY_SOLVER_POLICY}
)
SUPPORTED_REFINEMENT_POLICIES = frozenset(
    {"conformal_v2", DISCOVERY_REFINEMENT_POLICY}
)
SUPPORTED_SINGLETON_REPAIRS = frozenset(
    {"generic_lowest_safe_disk_glue_v1"}
)

# Exact authored topology exceptions.  These are locked by the independent
# position/face-stream hash so a future mesh change cannot silently broaden the
# one-face-island allowance.
SINGLE_FACE_EXCEPTIONS = {
    "athena": ({
        "topology_sha256": "5b6789fb664f5740d783aa0247d3c2625ac52eb0c33e2010bc27be313f4e2d87",
        "solver_face": 8792,
        "source_faces": (8792,),
        "positions": ((4461, 4466, 4467),),
        "source_lines": (16275,),
    },),
    "dionysus": ({
        "topology_sha256": "938b398e29a5412f2e57d67d09f8b29a14e9af10b406b4c73fc67e29ff4e5d1f",
        "solver_face": 165,
        "source_faces": (165, 168),
        "positions": ((92, 104, 94), (94, 104, 92)),
        "source_lines": (7271, 7274),
    },),
    "phaeton": ({
        "topology_sha256": "17334c76818f12731a09a4a84d54403a2754608aecd6ec417816a4e0eece1935",
        "solver_face": 18017,
        "source_faces": (18017,),
        "positions": ((6241, 6257, 6243),),
        "source_lines": (27002,),
        # This source-authored flap is only ~1.43e-9 source units squared.
        # Twelve-decimal OBJ serialization followed by Java float32 parsing
        # cannot hold its aggregate scale to the regular 0.01% island gate.
        # Keep the narrowly measured 0.05% allowance locked to this exact
        # topology and exact one-face solver island.
        "float32_density_tolerance": MAX_LOCKED_MICRO_ISLAND_DENSITY_ERROR,
    },),
    "theseus": ({
        "topology_sha256": "c38c226c4abfcee24e2a903330f42324bd714cf33cde0cc6ab929caf8e3eb183",
        "solver_face": 11494,
        "source_faces": (11494,),
        "positions": ((5804, 5806, 5807),),
        "source_lines": (18668,),
    },),
    "zeus": (
        {
            "topology_sha256": "db90f6f3d59adbed321485b1332b9937a7c92baad956e54eab1e9d939fb38d02",
            "solver_face": 6165,
            "source_faces": (6165, 6173),
            "positions": ((3158, 3160, 3162), (3158, 3162, 3160)),
            "source_lines": (13470, 13478),
        },
        {
            "topology_sha256": "db90f6f3d59adbed321485b1332b9937a7c92baad956e54eab1e9d939fb38d02",
            "solver_face": 13976,
            "source_faces": (13977,),
            "positions": ((7015, 7017, 7016),),
            "source_lines": (21282,),
        },
    ),
}


@dataclass(frozen=True)
class FaceRef:
    position_token: str
    position_index: int
    normal_token: str | None


@dataclass(frozen=True)
class SourceFace:
    references: tuple[FaceRef, ...]
    source_line_index: int
    leading_whitespace: str
    trailing_comment: str

    @property
    def positions(self) -> tuple[int, ...]:
        return tuple(reference.position_index for reference in self.references)


@dataclass(frozen=True)
class SourceObj:
    path: Path
    lines: tuple[str, ...]
    newline: str
    trailing_newline: bool
    vertices: tuple[tuple[float, float, float], ...]
    vertex_lines: tuple[str, ...]
    faces: tuple[SourceFace, ...]


@dataclass(frozen=True)
class Candidate:
    height_bins: int
    azimuth_bins: int
    height_half_phase: bool
    azimuth_half_phase: bool
    cut_edges: frozenset[int]
    estimated_manifold_seams: int
    refinement_ranks: tuple[int, ...] = ()
    refinement_edge_additions: tuple[tuple[int, ...], ...] = ()

    def key(self) -> tuple[int, int, int, int, int, int, int]:
        dimensions = (self.height_bins, self.azimuth_bins)
        preferred_rank = CANDIDATE_DIMENSIONS.index(dimensions)
        return (
            preferred_rank,
            self.estimated_manifold_seams,
            self.height_bins * self.azimuth_bins,
            self.height_bins,
            self.azimuth_bins,
            int(self.height_half_phase),
            int(self.azimuth_half_phase),
        )

    def label(self) -> str:
        height_phase = "half" if self.height_half_phase else "zero"
        azimuth_phase = "half" if self.azimuth_half_phase else "zero"
        return (
            f"height={self.height_bins}@{height_phase},"
            f"azimuth={self.azimuth_bins}@{azimuth_phase}"
        )


@dataclass(frozen=True)
class UvRecipe:
    name: str
    topology_sha256: str
    height_bins: int
    azimuth_bins: int
    height_half_phase: bool
    azimuth_half_phase: bool
    singleton_repair: str
    solver_policy: str
    refinement_policy: str
    refinement_ranks: tuple[int, ...]
    refinement_edge_additions: tuple[tuple[int, ...], ...]
    expected_seam_edges: int
    expected_island_face_counts: tuple[int, ...]
    expected_single_face_indices: tuple[int, ...]
    expected_p95_stretch_max: float
    expected_p99_stretch_max: float
    expected_density_ratio_max: tuple[float, float, float, float]

    def base_label(self) -> str:
        height_phase = "half" if self.height_half_phase else "zero"
        azimuth_phase = "half" if self.azimuth_half_phase else "zero"
        return (
            f"height={self.height_bins}@{height_phase},"
            f"azimuth={self.azimuth_bins}@{azimuth_phase}"
        )


@dataclass(frozen=True)
class UvMetrics:
    manifold_edges: int
    seam_edges: int
    seam_percent: float
    islands: int
    island_face_counts: tuple[int, ...]
    single_face_islands: int
    allowed_single_face_islands: int
    single_face_indices: tuple[int, ...]
    allowed_single_face_indices: tuple[int, ...]
    collapsed_faces: int
    flipped_faces: int
    p95_stretch: float
    p99_stretch: float
    maximum_stretch: float
    minimum_abs_uv_area: float
    uv_minimum: tuple[float, float]
    uv_maximum: tuple[float, float]
    out_of_bounds_uvs: int
    oversized_islands: int
    maximum_fixed_density_span: float
    maximum_island_density_error: float
    density_ratio_p50: float
    density_ratio_p90: float
    density_ratio_p95: float
    density_ratio_p99: float

    def accepted(self, p95_stretch_limit: float = MAX_P95_STRETCH) -> bool:
        return (
            self.seam_percent <= MAX_SEAM_PERCENT + 1.0e-9
            and self.p95_stretch <= p95_stretch_limit + 1.0e-9
            and self.p99_stretch <= MAX_P99_STRETCH + 1.0e-9
            and self.density_ratio_p50 <= MAX_DENSITY_RATIO_P50 + 1.0e-9
            and self.density_ratio_p90 <= MAX_DENSITY_RATIO_P90 + 1.0e-9
            and self.density_ratio_p95 <= MAX_DENSITY_RATIO_P95 + 1.0e-9
            and self.density_ratio_p99 <= MAX_DENSITY_RATIO_P99 + 1.0e-9
            and self.collapsed_faces == 0
            and self.flipped_faces == 0
            and self.single_face_indices == self.allowed_single_face_indices
        )

    def density_accepted(self) -> bool:
        return (
            self.out_of_bounds_uvs == 0
            and self.oversized_islands == 0
            and self.maximum_island_density_error <= MAX_ISLAND_DENSITY_ERROR
        )


@dataclass(frozen=True)
class GeneratedModel:
    name: str
    text: str
    source: SourceObj
    candidate: Candidate
    metrics: UvMetrics
    uv_count: int
    topology_sha256: str
    non_disk_chart_seeds: tuple[int, ...]
    serialized_audit: "SerializedAudit"
    mode: str
    solver_policy: str
    refinement_policy: str

    def report(self) -> dict[str, object]:
        locked_singletons = [
            {
                "solver_face": int(exception["solver_face"]),
                "source_faces": list(exception["source_faces"]),
                "source_lines": list(exception["source_lines"]),
                "kind": (
                    "reversed_duplicate"
                    if len(exception["source_faces"]) > 1
                    else "source_isolated"
                ),
                **(
                    {
                        "float32_density_tolerance": exception[
                            "float32_density_tolerance"
                        ]
                    }
                    if "float32_density_tolerance" in exception
                    else {}
                ),
            }
            for exception in SINGLE_FACE_EXCEPTIONS.get(self.name, ())
        ]
        return {
            "model": self.name,
            "algorithm_version": ALGORITHM_VERSION,
            "blender_version": ".".join(str(value) for value in REQUIRED_BLENDER_VERSION),
            "blender_build_hash": REQUIRED_BLENDER_BUILD_HASH,
            "mode": self.mode,
            "singleton_repair": "generic_lowest_safe_disk_glue_v1",
            "solver_policy": self.solver_policy,
            "refinement_policy": self.refinement_policy,
            "candidate": self.candidate.label(),
            "refinement_candidate_ranks": list(self.candidate.refinement_ranks),
            "refinement_edge_additions": [
                list(edges) for edges in self.candidate.refinement_edge_additions
            ],
            "cut_edge_sha256": cut_edge_sha256(self.candidate.cut_edges),
            "vertices": len(self.source.vertices),
            "faces": len(self.source.faces),
            "uvs": self.uv_count,
            "manifold_edges": self.metrics.manifold_edges,
            "seam_edges": self.metrics.seam_edges,
            "seam_percent": round(self.metrics.seam_percent, 6),
            "islands": self.metrics.islands,
            "island_face_counts": list(self.metrics.island_face_counts),
            "smallest_island_faces": min(self.metrics.island_face_counts),
            "single_face_islands": self.metrics.single_face_islands,
            "allowed_single_face_islands": self.metrics.allowed_single_face_islands,
            "single_face_indices": list(self.metrics.single_face_indices),
            "allowed_single_face_indices": list(self.metrics.allowed_single_face_indices),
            "locked_single_face_exceptions": locked_singletons,
            "collapsed_faces": self.metrics.collapsed_faces,
            "flipped_faces": self.metrics.flipped_faces,
            "p95_stretch": round(self.metrics.p95_stretch, 6),
            "p99_stretch": round(self.metrics.p99_stretch, 6),
            "maximum_stretch": round(self.metrics.maximum_stretch, 6),
            "minimum_abs_uv_area": self.metrics.minimum_abs_uv_area,
            "uv_minimum": [round(value, 9) for value in self.metrics.uv_minimum],
            "uv_maximum": [round(value, 9) for value in self.metrics.uv_maximum],
            "out_of_bounds_uvs": self.metrics.out_of_bounds_uvs,
            "oversized_islands": self.metrics.oversized_islands,
            "maximum_fixed_density_span": round(
                self.metrics.maximum_fixed_density_span, 6
            ),
            "maximum_island_density_error": self.metrics.maximum_island_density_error,
            "density_ratio_p50": round(self.metrics.density_ratio_p50, 6),
            "density_ratio_p90": round(self.metrics.density_ratio_p90, 6),
            "density_ratio_p95": round(self.metrics.density_ratio_p95, 6),
            "density_ratio_p99": round(self.metrics.density_ratio_p99, 6),
            "position_face_sha256": self.topology_sha256,
            "non_disk_charts": len(self.non_disk_chart_seeds),
            "non_disk_chart_seeds": list(self.non_disk_chart_seeds),
            "obj_sha256": hashlib.sha256(self.text.encode("utf-8")).hexdigest(),
            **self.serialized_audit.report(),
        }


@dataclass(frozen=True)
class SerializedAudit:
    out_of_bounds_uvs: int
    nonfinite_uvs: int
    collapsed_faces: int
    flipped_faces: int
    geometric_degenerate_faces: int
    island_face_counts: tuple[int, ...]
    single_face_indices: tuple[int, ...]
    allowed_single_face_indices: tuple[int, ...]
    maximum_island_density_error: float
    maximum_regular_island_density_error: float
    maximum_locked_micro_island_density_error: float
    locked_micro_island_density_indices: tuple[int, ...]
    density_limit_violation_indices: tuple[int, ...]
    density_ratio_p50: float
    density_ratio_p90: float
    density_ratio_p95: float
    density_ratio_p99: float

    def accepted(self) -> bool:
        return (
            self.out_of_bounds_uvs == 0
            and self.nonfinite_uvs == 0
            and self.collapsed_faces == 0
            and self.flipped_faces == 0
            and self.single_face_indices == self.allowed_single_face_indices
            and not self.density_limit_violation_indices
            and self.density_ratio_p50 <= MAX_DENSITY_RATIO_P50 + 1.0e-9
            and self.density_ratio_p90 <= MAX_DENSITY_RATIO_P90 + 1.0e-9
            and self.density_ratio_p95 <= MAX_DENSITY_RATIO_P95 + 1.0e-9
            and self.density_ratio_p99 <= MAX_DENSITY_RATIO_P99 + 1.0e-9
        )

    def report(self) -> dict[str, object]:
        return {
            "float32_out_of_bounds_uvs": self.out_of_bounds_uvs,
            "float32_nonfinite_uvs": self.nonfinite_uvs,
            "float32_collapsed_faces": self.collapsed_faces,
            "float32_flipped_faces": self.flipped_faces,
            "geometric_degenerate_faces": self.geometric_degenerate_faces,
            "float32_island_face_counts": list(self.island_face_counts),
            "float32_single_face_indices": list(self.single_face_indices),
            "float32_allowed_single_face_indices": list(self.allowed_single_face_indices),
            "float32_maximum_island_density_error": self.maximum_island_density_error,
            "float32_maximum_regular_island_density_error": (
                self.maximum_regular_island_density_error
            ),
            "float32_maximum_locked_micro_island_density_error": (
                self.maximum_locked_micro_island_density_error
            ),
            "float32_locked_micro_island_density_indices": list(
                self.locked_micro_island_density_indices
            ),
            "float32_density_limit_violation_indices": list(
                self.density_limit_violation_indices
            ),
            "float32_density_ratio_p50": round(self.density_ratio_p50, 6),
            "float32_density_ratio_p90": round(self.density_ratio_p90, 6),
            "float32_density_ratio_p95": round(self.density_ratio_p95, 6),
            "float32_density_ratio_p99": round(self.density_ratio_p99, 6),
        }


def load_recipe_manifest(path: Path = RECIPE_MANIFEST_PATH) -> dict[str, UvRecipe]:
    """Load the checked-in replay contract without importing or launching Blender."""
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise ValueError(f"cannot load UV recipe manifest {path}: {exception}") from exception
    if not isinstance(document, dict):
        raise ValueError(f"{path}: recipe manifest root must be an object")
    required_header = {
        "schema_version": RECIPE_SCHEMA_VERSION,
        "algorithm_version": ALGORITHM_VERSION,
        "blender_version": ".".join(str(value) for value in REQUIRED_BLENDER_VERSION),
        "blender_build_hash": REQUIRED_BLENDER_BUILD_HASH,
    }
    for field, expected in required_header.items():
        if document.get(field) != expected:
            raise ValueError(
                f"{path}: {field} must be {expected!r}, found {document.get(field)!r}"
            )
    raw_models = document.get("models")
    if not isinstance(raw_models, dict):
        raise ValueError(f"{path}: models must be an object")
    unknown = sorted(set(raw_models) - set(MODEL_NAMES))
    if unknown:
        raise ValueError(f"{path}: unknown recipe model(s): {', '.join(unknown)}")

    def required_mapping(parent: dict[str, object], field: str, model: str) -> dict:
        value = parent.get(field)
        if not isinstance(value, dict):
            raise ValueError(f"{path}: {model}.{field} must be an object")
        return value

    def required_int(parent: dict, field: str, model: str, minimum: int = 0) -> int:
        value = parent.get(field)
        if type(value) is not int or value < minimum:
            raise ValueError(
                f"{path}: {model}.{field} must be an integer >= {minimum}"
            )
        return value

    def required_number(parent: dict, field: str, model: str) -> float:
        value = parent.get(field)
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise ValueError(f"{path}: {model}.{field} must be a number")
        result = float(value)
        if not math.isfinite(result) or result < 0.0:
            raise ValueError(f"{path}: {model}.{field} must be finite and non-negative")
        return result

    recipes: dict[str, UvRecipe] = {}
    for name in sorted(raw_models):
        raw = raw_models[name]
        if not isinstance(raw, dict):
            raise ValueError(f"{path}: recipe {name} must be an object")
        topology = raw.get("topology_sha256")
        if (
            not isinstance(topology, str)
            or len(topology) != 64
            or any(character not in "0123456789abcdef" for character in topology)
        ):
            raise ValueError(f"{path}: {name}.topology_sha256 must be lowercase SHA-256")
        base = required_mapping(raw, "base", name)
        height_bins = required_int(base, "height_bins", f"{name}.base", 1)
        azimuth_bins = required_int(base, "azimuth_bins", f"{name}.base", 1)
        if (height_bins, azimuth_bins) not in CANDIDATE_DIMENSIONS:
            raise ValueError(f"{path}: {name}.base dimensions are not discoverable")
        height_phase = base.get("height_phase")
        azimuth_phase = base.get("azimuth_phase")
        if height_phase not in {"zero", "half"}:
            raise ValueError(f"{path}: {name}.base.height_phase must be zero or half")
        if azimuth_phase not in {"zero", "half"}:
            raise ValueError(f"{path}: {name}.base.azimuth_phase must be zero or half")
        if height_bins == 1 and height_phase != "zero":
            raise ValueError(f"{path}: {name} cannot half-phase one height bin")
        if azimuth_bins == 1 and azimuth_phase != "zero":
            raise ValueError(f"{path}: {name} cannot half-phase one azimuth bin")

        singleton_repair = raw.get("singleton_repair")
        solver_policy = raw.get("solver_policy")
        refinement_policy = raw.get("refinement_policy")
        if singleton_repair not in SUPPORTED_SINGLETON_REPAIRS:
            raise ValueError(f"{path}: {name} has unsupported singleton repair")
        if solver_policy not in SUPPORTED_SOLVER_POLICIES:
            raise ValueError(f"{path}: {name} has unsupported solver policy")
        if refinement_policy not in SUPPORTED_REFINEMENT_POLICIES:
            raise ValueError(f"{path}: {name} has unsupported refinement policy")

        raw_ranks = raw.get("refinement_ranks")
        if not isinstance(raw_ranks, list) or any(
            type(rank) is not int or rank < 1 or rank > EXPANDED_ACTUAL_TRIAL_LIMIT
            for rank in raw_ranks
        ):
            raise ValueError(
                f"{path}: {name}.refinement_ranks must contain ranks 1.."
                f"{EXPANDED_ACTUAL_TRIAL_LIMIT}"
            )
        raw_edge_additions = raw.get("refinement_edge_additions", [])
        if not isinstance(raw_edge_additions, list) or any(
            not isinstance(edges, list)
            or not edges
            or any(type(edge) is not int or edge < 0 for edge in edges)
            or edges != sorted(set(edges))
            for edges in raw_edge_additions
        ):
            raise ValueError(
                f"{path}: {name}.refinement_edge_additions must contain "
                "sorted unique non-negative edge-index lists"
            )
        if raw_edge_additions and len(raw_edge_additions) != len(raw_ranks):
            raise ValueError(
                f"{path}: {name}.refinement_edge_additions must match "
                "refinement_ranks length"
            )
        expected = required_mapping(raw, "expected", name)
        raw_islands = expected.get("island_face_counts")
        raw_singletons = expected.get("single_face_indices")
        raw_density = expected.get("density_ratio_max")
        if (
            not isinstance(raw_islands, list)
            or not raw_islands
            or any(type(value) is not int or value < 1 for value in raw_islands)
            or raw_islands != sorted(raw_islands)
        ):
            raise ValueError(
                f"{path}: {name}.expected.island_face_counts must be a sorted integer list"
            )
        if (
            not isinstance(raw_singletons, list)
            or any(type(value) is not int or value < 0 for value in raw_singletons)
            or raw_singletons != sorted(raw_singletons)
        ):
            raise ValueError(
                f"{path}: {name}.expected.single_face_indices must be a sorted integer list"
            )
        if not isinstance(raw_density, list) or len(raw_density) != 4:
            raise ValueError(
                f"{path}: {name}.expected.density_ratio_max must contain p50/p90/p95/p99"
            )
        density_max = tuple(
            required_number({"value": value}, "value", f"{name}.expected.density_ratio_max")
            for value in raw_density
        )
        if tuple(sorted(density_max)) != density_max:
            raise ValueError(f"{path}: {name}.expected density maxima must be monotonic")
        hard_density_limits = (
            MAX_DENSITY_RATIO_P50,
            MAX_DENSITY_RATIO_P90,
            MAX_DENSITY_RATIO_P95,
            MAX_DENSITY_RATIO_P99,
        )
        if any(
            expected_max > hard_limit + 1.0e-12
            for expected_max, hard_limit in zip(
                density_max, hard_density_limits, strict=True
            )
        ):
            raise ValueError(f"{path}: {name}.expected density envelope weakens hard gates")
        expected_p95 = required_number(
            expected, "p95_stretch_max", f"{name}.expected"
        )
        expected_p99 = required_number(
            expected, "p99_stretch_max", f"{name}.expected"
        )
        p95_stretch_limit = MODEL_MAX_P95_STRETCH.get(name, MAX_P95_STRETCH)
        if expected_p95 > p95_stretch_limit or expected_p99 > MAX_P99_STRETCH:
            raise ValueError(f"{path}: {name}.expected stretch envelope weakens hard gates")
        locked_singletons = tuple(
            sorted(
                int(exception["solver_face"])
                for exception in SINGLE_FACE_EXCEPTIONS.get(name, ())
            )
        )
        if tuple(raw_singletons) != locked_singletons:
            raise ValueError(
                f"{path}: {name}.expected singleton identities must equal the "
                f"topology-locked set {locked_singletons}"
            )

        recipes[name] = UvRecipe(
            name=name,
            topology_sha256=topology,
            height_bins=height_bins,
            azimuth_bins=azimuth_bins,
            height_half_phase=height_phase == "half",
            azimuth_half_phase=azimuth_phase == "half",
            singleton_repair=str(singleton_repair),
            solver_policy=str(solver_policy),
            refinement_policy=str(refinement_policy),
            refinement_ranks=tuple(raw_ranks),
            refinement_edge_additions=tuple(
                tuple(edges) for edges in raw_edge_additions
            ),
            expected_seam_edges=required_int(expected, "seam_edges", f"{name}.expected"),
            expected_island_face_counts=tuple(raw_islands),
            expected_single_face_indices=tuple(raw_singletons),
            expected_p95_stretch_max=expected_p95,
            expected_p99_stretch_max=expected_p99,
            expected_density_ratio_max=density_max,  # type: ignore[arg-type]
        )
    return recipes


def parse_source(path: Path) -> SourceObj:
    raw = path.read_bytes()
    newline = "\r\n" if b"\r\n" in raw else "\n"
    text = raw.decode("utf-8")
    lines = text.splitlines()
    vertices: list[tuple[float, float, float]] = []
    vertex_lines: list[str] = []
    faces: list[SourceFace] = []

    for line_index, raw_line in enumerate(lines):
        content, separator, comment = raw_line.partition("#")
        stripped = content.strip()
        if stripped.startswith("v "):
            parts = stripped.split()
            if len(parts) < 4:
                raise ValueError(f"{path}:{line_index + 1}: malformed vertex")
            vertex = tuple(float(value) for value in parts[1:4])
            if not all(math.isfinite(value) for value in vertex):
                raise ValueError(f"{path}:{line_index + 1}: non-finite vertex")
            vertices.append(vertex)
            vertex_lines.append(raw_line)
        elif stripped.startswith("f "):
            references: list[FaceRef] = []
            for token in stripped.split()[1:]:
                fields = token.split("/")
                if not fields or not fields[0] or len(fields) > 3:
                    raise ValueError(f"{path}:{line_index + 1}: malformed face reference")
                raw_position = int(fields[0])
                resolved = raw_position - 1 if raw_position > 0 else len(vertices) + raw_position
                if resolved < 0 or resolved >= len(vertices):
                    raise ValueError(f"{path}:{line_index + 1}: position index out of range")
                normal = fields[2] if len(fields) == 3 and fields[2] else None
                references.append(FaceRef(fields[0], resolved, normal))
            if len(references) not in (3, 4):
                raise ValueError(f"{path}:{line_index + 1}: only triangles/quads are supported")
            leading = content[: len(content) - len(content.lstrip())]
            trailing = f"#{comment}" if separator else ""
            faces.append(SourceFace(tuple(references), line_index, leading, trailing))

    if not vertices or not faces:
        raise ValueError(f"{path}: expected vertices and faces")
    return SourceObj(
        path=path,
        lines=tuple(lines),
        newline=newline,
        trailing_newline=text.endswith(("\n", "\r")),
        vertices=tuple(vertices),
        vertex_lines=tuple(vertex_lines),
        faces=tuple(faces),
    )


def topology_sha256(source: SourceObj) -> str:
    digest = hashlib.sha256()
    for line in source.vertex_lines:
        digest.update(line.encode("utf-8"))
        digest.update(b"\n")
    for face in source.faces:
        digest.update("f ".encode("ascii"))
        digest.update(" ".join(reference.position_token for reference in face.references).encode("ascii"))
        digest.update(b"\n")
    return digest.hexdigest()


def source_line_number_without_generated_uv_block(
    source: SourceObj, face: SourceFace
) -> int:
    """Return the source line number before this tool inserted its UV block."""
    marker_indices = [
        index
        for index, line in enumerate(source.lines[: face.source_line_index])
        if line.strip().startswith(GENERATED_COMMENT_PREFIX)
    ]
    if not marker_indices:
        return face.source_line_index + 1
    marker_index = marker_indices[-1]
    generated_lines = sum(
        1
        for line in source.lines[marker_index : face.source_line_index]
        if line.strip().startswith(GENERATED_COMMENT_PREFIX)
        or line.strip().startswith("vt ")
    )
    return face.source_line_index + 1 - generated_lines


def cut_edge_sha256(cut_edges: Iterable[int]) -> str:
    digest = hashlib.sha256()
    for edge_index in sorted(cut_edges):
        digest.update(f"{edge_index}\n".encode("ascii"))
    return digest.hexdigest()


def create_blender_object(source: SourceObj):
    assert bpy is not None
    unique_face_indices: list[int] = []
    solver_face_by_key: dict[tuple[int, ...], int] = {}
    source_to_solver: list[int] = []
    solver_sources: list[list[int]] = []
    for source_face_index, face in enumerate(source.faces):
        key = tuple(sorted(face.positions))
        solver_face_index = solver_face_by_key.get(key)
        if solver_face_index is None:
            solver_face_index = len(unique_face_indices)
            solver_face_by_key[key] = solver_face_index
            unique_face_indices.append(source_face_index)
            solver_sources.append([])
        solver_sources[solver_face_index].append(source_face_index)
        source_to_solver.append(solver_face_index)

    mesh = bpy.data.meshes.new(f"daedalon_uv_{source.path.stem}")
    mesh.from_pydata(
        source.vertices,
        [],
        [source.faces[index].positions for index in unique_face_indices],
    )
    mesh.update(calc_edges=True)
    spans = [
        max(vertex[axis] for vertex in source.vertices)
        - min(vertex[axis] for vertex in source.vertices)
        for axis in range(3)
    ]
    largest_span = max(spans)
    if largest_span <= 0.0:
        raise ValueError(f"{source.path}: cannot fit a zero-span mesh")
    mesh["daedalon_world_scale"] = 3.0 / largest_span
    mesh["daedalon_model_name"] = source.path.stem
    if len(mesh.vertices) != len(source.vertices) or len(mesh.polygons) != len(unique_face_indices):
        raise ValueError(f"{source.path}: Blender changed vertex/face counts")
    for solver_face_index, polygon in enumerate(mesh.polygons):
        source_face_index = unique_face_indices[solver_face_index]
        if tuple(polygon.vertices) != source.faces[source_face_index].positions:
            raise ValueError(f"{source.path}: Blender changed face order or winding")
    allowed_singletons = 0
    allowed_singleton_faces: list[int] = []
    density_exception_faces: list[int] = []
    exceptions = SINGLE_FACE_EXCEPTIONS.get(source.path.stem, ())
    if exceptions:
        _edge_by_key, exception_edge_faces = edge_topology(mesh)
    for exception in exceptions:
        if topology_sha256(source) != exception["topology_sha256"]:
            raise ValueError(f"{source.path}: locked singleton topology hash changed")
        solver_face = int(exception["solver_face"])
        source_faces = tuple(int(index) for index in exception["source_faces"])
        if tuple(solver_sources[solver_face]) != source_faces:
            raise ValueError(f"{source.path}: locked singleton source mapping changed")
        if tuple(source.faces[index].positions for index in source_faces) != exception["positions"]:
            raise ValueError(f"{source.path}: locked singleton positions changed")
        if tuple(
            source_line_number_without_generated_uv_block(source, source.faces[index])
            for index in source_faces
        ) != exception["source_lines"]:
            raise ValueError(f"{source.path}: locked singleton source lines changed")
        polygon = mesh.polygons[solver_face]
        if not all(
            len(exception_edge_faces[_edge_by_key[tuple(sorted(key))]]) != 2
            for key in polygon.edge_keys
        ):
            raise ValueError(f"{source.path}: locked singleton is no longer isolated")
        allowed_singletons += 1
        allowed_singleton_faces.append(solver_face)
        if "float32_density_tolerance" in exception:
            density_exception_faces.append(solver_face)
    mesh["daedalon_allowed_single_face_islands"] = allowed_singletons
    if allowed_singleton_faces:
        mesh["daedalon_allowed_single_face_indices"] = allowed_singleton_faces
    if density_exception_faces:
        mesh["daedalon_density_exception_indices"] = density_exception_faces
    obj = bpy.data.objects.new(mesh.name, mesh)
    bpy.context.collection.objects.link(obj)
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    return obj, tuple(source_to_solver)


def edge_topology(mesh):
    edge_by_key = {tuple(sorted(edge.vertices)): edge.index for edge in mesh.edges}
    edge_faces: dict[int, list[int]] = {edge.index: [] for edge in mesh.edges}
    for polygon in mesh.polygons:
        for edge_key in polygon.edge_keys:
            edge_faces[edge_by_key[tuple(sorted(edge_key))]].append(polygon.index)
    return edge_by_key, edge_faces


def face_region_labels(mesh, candidate: Candidate) -> list[tuple[int, int]]:
    xs = [vertex.co.x for vertex in mesh.vertices]
    ys = [vertex.co.y for vertex in mesh.vertices]
    zs = [vertex.co.z for vertex in mesh.vertices]
    center_x = (min(xs) + max(xs)) * 0.5
    center_z = (min(zs) + max(zs)) * 0.5
    minimum_y = min(ys)
    span_y = max(ys) - minimum_y
    height_phase = 0.5 / candidate.height_bins if candidate.height_half_phase else 0.0
    azimuth_phase = 0.5 / candidate.azimuth_bins if candidate.azimuth_half_phase else 0.0
    labels: list[tuple[int, int]] = []
    for polygon in mesh.polygons:
        center = polygon.center
        normalized_y = 0.5 if span_y <= 0.0 else (center.y - minimum_y) / span_y
        height = math.floor((normalized_y - height_phase) * candidate.height_bins)
        height = max(0, min(candidate.height_bins - 1, height))
        angle = (math.atan2(center.x - center_x, center_z - center.z) + math.pi) / (
            2.0 * math.pi
        )
        azimuth = math.floor(((angle - azimuth_phase) % 1.0) * candidate.azimuth_bins)
        azimuth = max(0, min(candidate.azimuth_bins - 1, azimuth))
        labels.append((height, azimuth))
    return labels


def seam_visibility_cost(mesh, edge_indices: Iterable[int]) -> float:
    """Prefer vertical cuts on the +Z back over visible horizontal rings."""
    zs = [float(vertex.co.z) for vertex in mesh.vertices]
    minimum_z = min(zs)
    span_z = max(zs) - minimum_z
    total = 0.0
    for edge_index in edge_indices:
        first_index, second_index = mesh.edges[edge_index].vertices
        first = mesh.vertices[first_index].co
        second = mesh.vertices[second_index].co
        length = float((first - second).length)
        if length <= 1.0e-12:
            continue
        midpoint_z = (float(first.z) + float(second.z)) * 0.5
        backness = 1.0 if span_z <= 0.0 else (midpoint_z - minimum_z) / span_z
        backness = max(0.0, min(1.0, backness))
        horizontalness = 1.0 - abs(float(first.y) - float(second.y)) / length
        horizontalness = max(0.0, min(1.0, horizontalness))
        rear_penalty = (1.0 - backness) ** 2
        total += length * (
            rear_penalty * (1.0 + 3.0 * horizontalness)
            + 0.1 * horizontalness
        )
    return total


def rear_vertical_spine_cuts(
    mesh,
    edge_faces: dict[int, list[int]],
) -> frozenset[int]:
    """Find one deterministic bottom-to-top path along each closed rear surface."""
    adjacency: dict[int, list[tuple[int, int]]] = defaultdict(list)
    for edge_index, incident in edge_faces.items():
        if len(incident) != 2:
            continue
        first, second = mesh.edges[edge_index].vertices
        adjacency[first].append((second, edge_index))
        adjacency[second].append((first, edge_index))
    for neighbours in adjacency.values():
        neighbours.sort(key=lambda entry: (entry[0], entry[1]))

    remaining = set(adjacency)
    cuts: set[int] = set()
    while remaining:
        seed = min(remaining)
        remaining.remove(seed)
        component = {seed}
        queue = deque([seed])
        while queue:
            vertex = queue.popleft()
            for neighbour, _edge_index in adjacency[vertex]:
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    component.add(neighbour)
                    queue.append(neighbour)
        if len(component) < 2:
            continue

        xs = [float(mesh.vertices[index].co.x) for index in component]
        ys = [float(mesh.vertices[index].co.y) for index in component]
        zs = [float(mesh.vertices[index].co.z) for index in component]
        minimum_y = min(ys)
        maximum_y = max(ys)
        span_y = maximum_y - minimum_y
        span_x = max(max(xs) - min(xs), 1.0e-12)
        span_z = max(max(zs) - min(zs), 1.0e-12)
        center_x = (min(xs) + max(xs)) * 0.5
        if span_y <= 1.0e-12:
            continue

        anchor_band = max(span_y * 0.05, 1.0e-9)
        lower = [
            index
            for index in component
            if float(mesh.vertices[index].co.y) <= minimum_y + anchor_band
        ]
        upper = [
            index
            for index in component
            if float(mesh.vertices[index].co.y) >= maximum_y - anchor_band
        ]

        def anchor_key(index: int) -> tuple[float, float, int]:
            coordinate = mesh.vertices[index].co
            return (
                -float(coordinate.z),
                abs(float(coordinate.x) - center_x) / span_x,
                index,
            )

        start = min(lower, key=anchor_key)
        goal = min(upper, key=anchor_key)
        if start == goal:
            continue

        bin_count = 64
        back_by_bin = [-math.inf] * bin_count
        for index in component:
            coordinate = mesh.vertices[index].co
            normalized_y = (float(coordinate.y) - minimum_y) / span_y
            bin_index = min(bin_count - 1, int(normalized_y * bin_count))
            back_by_bin[bin_index] = max(back_by_bin[bin_index], float(coordinate.z))
        available = [index for index, value in enumerate(back_by_bin) if math.isfinite(value)]
        for bin_index, value in enumerate(back_by_bin):
            if math.isfinite(value):
                continue
            nearest = min(available, key=lambda candidate: (abs(candidate - bin_index), candidate))
            back_by_bin[bin_index] = back_by_bin[nearest]

        distance = {index: math.inf for index in component}
        parent: dict[int, tuple[int, int] | None] = {index: None for index in component}
        distance[start] = 0.0
        heap: list[tuple[float, int]] = [(0.0, start)]
        while heap:
            current_distance, vertex = heapq.heappop(heap)
            if current_distance != distance[vertex]:
                continue
            if vertex == goal:
                break
            first = mesh.vertices[vertex].co
            for neighbour, edge_index in adjacency[vertex]:
                if neighbour not in component:
                    continue
                second = mesh.vertices[neighbour].co
                length = float((first - second).length)
                if length <= 1.0e-12:
                    continue
                midpoint_y = (float(first.y) + float(second.y)) * 0.5
                midpoint_z = (float(first.z) + float(second.z)) * 0.5
                midpoint_x = (float(first.x) + float(second.x)) * 0.5
                normalized_y = (midpoint_y - minimum_y) / span_y
                bin_index = min(
                    bin_count - 1,
                    max(0, int(normalized_y * bin_count)),
                )
                back_depth = max(0.0, back_by_bin[bin_index] - midpoint_z) / span_z
                center_offset = abs(midpoint_x - center_x) / span_x
                horizontalness = 1.0 - abs(float(first.y) - float(second.y)) / length
                horizontalness = max(0.0, min(1.0, horizontalness))
                edge_cost = length * (
                    1.0
                    + 24.0 * back_depth * back_depth
                    + 2.0 * center_offset * center_offset
                    + 1.5 * horizontalness * horizontalness
                )
                candidate_distance = current_distance + edge_cost
                old_parent = parent[neighbour]
                tied = abs(candidate_distance - distance[neighbour]) <= 1.0e-12
                if candidate_distance < distance[neighbour] - 1.0e-12 or (
                    tied
                    and (old_parent is None or edge_index < old_parent[1])
                ):
                    distance[neighbour] = candidate_distance
                    parent[neighbour] = (vertex, edge_index)
                    heapq.heappush(heap, (candidate_distance, neighbour))

        if parent[goal] is None:
            continue
        vertex = goal
        while vertex != start:
            entry = parent[vertex]
            if entry is None:
                raise ValueError("rear seam path lost its deterministic parent")
            vertex, edge_index = entry
            cuts.add(edge_index)
    return frozenset(cuts)


def topology_cuts_for_regions(
    mesh,
    edge_faces: dict[int, list[int]],
    labels: Sequence[tuple[int, int]],
    preferred_cuts: Iterable[int] = (),
) -> frozenset[int]:
    """Open each coherent region's handles with a local tree/cotree cut graph.

    Region boundaries are treated as a virtual exterior face in the dual graph.
    This avoids retaining long global handle cuts after a height/azimuth boundary
    has already opened the same topology.
    """
    edge_by_key = {tuple(sorted(edge.vertices)): edge.index for edge in mesh.edges}
    face_edges: list[tuple[int, ...]] = []
    for polygon in mesh.polygons:
        face_edges.append(
            tuple(edge_by_key[tuple(sorted(key))] for key in polygon.edge_keys)
        )

    face_adjacency: list[list[int]] = [[] for _ in mesh.polygons]
    forced_cuts: set[int] = set(preferred_cuts)
    for edge_index, incident in edge_faces.items():
        if edge_index in forced_cuts or len(incident) != 2:
            forced_cuts.add(edge_index)
            continue
        first, second = incident
        if labels[first] != labels[second]:
            forced_cuts.add(edge_index)
        else:
            face_adjacency[first].append(second)
            face_adjacency[second].append(first)
    for neighbours in face_adjacency:
        neighbours.sort()

    remaining = set(range(len(mesh.polygons)))
    components: list[frozenset[int]] = []
    while remaining:
        seed = min(remaining)
        remaining.remove(seed)
        queue = deque([seed])
        faces: set[int] = {seed}
        while queue:
            face = queue.popleft()
            for neighbour in face_adjacency[face]:
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    faces.add(neighbour)
                    queue.append(neighbour)
        components.append(frozenset(faces))

    cuts = set(forced_cuts)
    for component in components:
        component_edges = {
            edge_index for face in component for edge_index in face_edges[face]
        }
        internal_edges = {
            edge_index
            for edge_index in component_edges
            if len(edge_faces[edge_index]) == 2
            and all(face in component for face in edge_faces[edge_index])
        }
        boundary_edges = component_edges - internal_edges
        component_vertices = {
            vertex
            for edge_index in component_edges
            for vertex in mesh.edges[edge_index].vertices
        }
        if not component_vertices:
            continue

        vertex_adjacency: dict[int, list[tuple[int, int, float]]] = {
            vertex: [] for vertex in component_vertices
        }
        for edge_index in internal_edges:
            first, second = mesh.edges[edge_index].vertices
            length = (mesh.vertices[first].co - mesh.vertices[second].co).length
            vertex_adjacency[first].append((second, edge_index, length))
            vertex_adjacency[second].append((first, edge_index, length))
        for neighbours in vertex_adjacency.values():
            neighbours.sort(key=lambda entry: (entry[0], entry[1]))

        boundary_vertices = {
            vertex
            for edge_index in boundary_edges
            for vertex in mesh.edges[edge_index].vertices
        }
        distance = {vertex: math.inf for vertex in component_vertices}
        parent_vertex: dict[int, int | None] = {
            vertex: None for vertex in component_vertices
        }
        parent_edge: dict[int, int | None] = {
            vertex: None for vertex in component_vertices
        }
        queue_vertices: list[tuple[float, int]] = []
        roots = boundary_vertices or {
            min(
                component_vertices,
                key=lambda index: (mesh.vertices[index].co.y, index),
            )
        }
        for root in sorted(roots):
            distance[root] = 0.0
            heapq.heappush(queue_vertices, (0.0, root))
        while queue_vertices:
            current_distance, vertex = heapq.heappop(queue_vertices)
            if current_distance != distance[vertex]:
                continue
            for neighbour, edge_index, edge_length in vertex_adjacency[vertex]:
                candidate_distance = current_distance + edge_length
                better = candidate_distance < distance[neighbour] - 1.0e-12
                tied = abs(candidate_distance - distance[neighbour]) <= 1.0e-12
                if better or (
                    tied
                    and (
                        parent_edge[neighbour] is None
                        or edge_index < parent_edge[neighbour]
                    )
                ):
                    distance[neighbour] = candidate_distance
                    parent_vertex[neighbour] = vertex
                    parent_edge[neighbour] = edge_index
                    heapq.heappush(queue_vertices, (candidate_distance, neighbour))

        primal_forest = {
            edge_index for edge_index in parent_edge.values() if edge_index is not None
        }
        dual_adjacency: dict[int, list[tuple[int, int]]] = {
            face: [] for face in component
        }
        for edge_index in internal_edges - primal_forest:
            first, second = edge_faces[edge_index]
            dual_adjacency[first].append((second, edge_index))
            dual_adjacency[second].append((first, edge_index))
        for neighbours in dual_adjacency.values():
            neighbours.sort()

        dual_forest: set[int] = set()
        seen_dual: set[int] = set()
        for seed in sorted(component):
            if seed in seen_dual:
                continue
            seen_dual.add(seed)
            stack = [seed]
            while stack:
                face = stack.pop()
                for neighbour, edge_index in dual_adjacency[face]:
                    if neighbour in seen_dual:
                        continue
                    seen_dual.add(neighbour)
                    dual_forest.add(edge_index)
                    stack.append(neighbour)

        generators = internal_edges - primal_forest - dual_forest
        for edge_index in sorted(generators):
            cuts.add(edge_index)
            for start in mesh.edges[edge_index].vertices:
                vertex = start
                guard = 0
                while parent_edge[vertex] is not None:
                    cuts.add(parent_edge[vertex])
                    parent = parent_vertex[vertex]
                    if parent is None:
                        break
                    vertex = parent
                    guard += 1
                    if guard > len(component_vertices):
                        raise ValueError("cycle in generated regional primal parent forest")
    return frozenset(cuts)


def build_candidates(mesh, edge_faces: dict[int, list[int]]) -> list[Candidate]:
    manifold_edges = {
        edge_index for edge_index, incident in edge_faces.items() if len(incident) == 2
    }
    maximum_seams = math.floor(len(manifold_edges) * MAX_SEAM_PERCENT / 100.0)
    unique: dict[frozenset[int], Candidate] = {}
    ordered: list[Candidate] = []
    model_name = str(mesh.get("daedalon_model_name", ""))
    base_specs = DISCOVERY_MODEL_BASE_SPECS.get(
        model_name, DISCOVERY_BASE_SPECS
    )
    for height_bins, azimuth_bins, height_half, azimuth_half in base_specs:
        placeholder = Candidate(
            height_bins,
            azimuth_bins,
            height_half,
            azimuth_half,
            frozenset(),
            0,
        )
        labels = face_region_labels(mesh, placeholder)
        preferred_cuts = (
            rear_vertical_spine_cuts(mesh, edge_faces)
            if (height_bins, azimuth_bins) == (1, 1)
            else frozenset()
        )
        frozen = merge_avoidable_singletons(
            mesh,
            edge_faces,
            topology_cuts_for_regions(
                mesh, edge_faces, labels, preferred_cuts
            ),
        )
        if non_disk_chart_seeds(mesh, edge_faces, frozen):
            continue
        seam_count = len(frozen & manifold_edges)
        if seam_count > maximum_seams:
            continue
        candidate = Candidate(
            height_bins,
            azimuth_bins,
            height_half,
            azimuth_half,
            frozen,
            seam_count,
        )
        if frozen not in unique:
            unique[frozen] = candidate
            ordered.append(candidate)
    if model_name in DISCOVERY_VISIBILITY_CHOSEN_BASE_MODELS and ordered:
        return [
            min(
                ordered,
                key=lambda candidate: (
                    seam_visibility_cost(mesh, sorted(candidate.cut_edges)),
                    candidate.key(),
                    tuple(sorted(candidate.cut_edges)),
                ),
            )
        ]
    return ordered


def merge_avoidable_singletons(
    mesh,
    edge_faces: dict[int, list[int]],
    cut_edges: frozenset[int],
) -> frozenset[int]:
    """Glue generated one-face charts to a neighbour across the lowest safe edge.

    A face with no manifold edge is an authored topology exception and remains
    isolated.  Gluing two disk charts along one complete edge preserves a disk.
    """
    cuts = set(cut_edges)
    edge_by_key = {tuple(sorted(edge.vertices)): edge.index for edge in mesh.edges}
    while True:
        charts = topological_charts(mesh, edge_faces, frozenset(cuts))
        singletons = sorted(next(iter(chart)) for chart in charts if len(chart) == 1)
        merged = False
        for face in singletons:
            safe = sorted(
                edge_by_key[tuple(sorted(key))]
                for key in mesh.polygons[face].edge_keys
                if edge_by_key[tuple(sorted(key))] in cuts
                and len(edge_faces[edge_by_key[tuple(sorted(key))]]) == 2
            )
            if not safe:
                continue
            cuts.remove(safe[0])
            merged = True
            break
        if not merged:
            return frozenset(cuts)


def build_recipe_candidate(
    mesh,
    edge_faces: dict[int, list[int]],
    recipe: UvRecipe,
) -> Candidate:
    """Rebuild exactly one locked base chart; never enumerate alternatives."""
    placeholder = Candidate(
        recipe.height_bins,
        recipe.azimuth_bins,
        recipe.height_half_phase,
        recipe.azimuth_half_phase,
        frozenset(),
        0,
    )
    labels = face_region_labels(mesh, placeholder)
    preferred_cuts = (
        rear_vertical_spine_cuts(mesh, edge_faces)
        if (recipe.height_bins, recipe.azimuth_bins) == (1, 1)
        else frozenset()
    )
    cuts = topology_cuts_for_regions(
        mesh, edge_faces, labels, preferred_cuts
    )
    if recipe.singleton_repair == "generic_lowest_safe_disk_glue_v1":
        cuts = merge_avoidable_singletons(mesh, edge_faces, cuts)
    else:  # Manifest validation should make this unreachable.
        raise ValueError(f"unsupported singleton repair {recipe.singleton_repair}")
    disk_failures = non_disk_chart_seeds(mesh, edge_faces, cuts)
    if disk_failures:
        raise ValueError(
            f"{recipe.name}: locked base recipe now has non-disk charts "
            f"{disk_failures}"
        )
    manifold_edges = {
        edge_index for edge_index, incident in edge_faces.items() if len(incident) == 2
    }
    seam_count = len(cuts & manifold_edges)
    maximum_seams = math.floor(len(manifold_edges) * MAX_SEAM_PERCENT / 100.0)
    if seam_count > maximum_seams:
        raise ValueError(
            f"{recipe.name}: locked base recipe exceeds the seam budget "
            f"({seam_count}/{len(manifold_edges)})"
        )
    return Candidate(
        recipe.height_bins,
        recipe.azimuth_bins,
        recipe.height_half_phase,
        recipe.azimuth_half_phase,
        cuts,
        seam_count,
    )


def apply_candidate(mesh, candidate: Candidate) -> None:
    assert bpy is not None
    if bpy.context.object is not None and bpy.context.object.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    for edge in mesh.edges:
        edge.use_seam = edge.index in candidate.cut_edges
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    result = bpy.ops.uv.unwrap(
        method="ANGLE_BASED",
        fill_holes=False,
        correct_aspect=False,
        margin_method="SCALED",
        margin=0.001,
        no_flip=True,
        iterations=80,
    )
    bpy.ops.object.mode_set(mode="OBJECT")
    if "FINISHED" not in result:
        raise ValueError(f"Blender UV unwrap failed: {result}")


def quantized_uv(value) -> tuple[float, float]:
    u = float(value.x)
    v = float(value.y)
    if not math.isfinite(u) or not math.isfinite(v):
        raise ValueError("Blender produced a non-finite UV coordinate")
    return (
        float(f"{u:.{SERIALIZED_DECIMALS}f}"),
        float(f"{v:.{SERIALIZED_DECIMALS}f}"),
    )


def triangle_stretch(positions, uvs) -> tuple[float | None, float]:
    first, second, third = positions
    edge_one = second - first
    edge_two = third - first
    length_one = edge_one.length
    if length_one <= 1.0e-15:
        return None, 0.0
    local_x = edge_one.dot(edge_two) / length_one
    local_y = math.sqrt(max(edge_two.length_squared - local_x * local_x, 0.0))
    if local_y <= 1.0e-15:
        return None, 0.0
    u0, u1, u2 = uvs
    a = (u1[0] - u0[0]) / length_one
    c = (u1[1] - u0[1]) / length_one
    b = ((u2[0] - u0[0]) - a * local_x) / local_y
    d = ((u2[1] - u0[1]) - c * local_x) / local_y
    determinant = abs(a * d - b * c)
    signed_area = (
        (u1[0] - u0[0]) * (u2[1] - u0[1])
        - (u1[1] - u0[1]) * (u2[0] - u0[0])
    )
    if determinant <= UV_COLLAPSE_EPSILON:
        return None, signed_area
    frobenius = a * a + b * b + c * c + d * d
    discriminant = math.sqrt(max(frobenius * frobenius - 4.0 * determinant * determinant, 0.0))
    maximum_squared = (frobenius + discriminant) * 0.5
    if maximum_squared <= 0.0:
        return None, signed_area
    # sigma_max * sigma_min == abs(det(J)); this avoids cancellation in sigma_min.
    condition = maximum_squared / determinant
    return condition, signed_area


def face_uvs(mesh) -> list[dict[int, tuple[float, float]]]:
    layer = mesh.uv_layers.active
    if layer is None:
        raise ValueError("Blender produced no UV layer")
    result: list[dict[int, tuple[float, float]]] = []
    for polygon in mesh.polygons:
        values: dict[int, tuple[float, float]] = {}
        for loop_index in polygon.loop_indices:
            vertex = mesh.loops[loop_index].vertex_index
            values[vertex] = quantized_uv(layer.data[loop_index].uv)
        result.append(values)
    return result


def continuity_graph(mesh, edge_faces, per_face_uv):
    continuous: list[list[int]] = [[] for _ in mesh.polygons]
    seam_edges = 0
    manifold_edges = 0
    for edge_index, incident in edge_faces.items():
        if len(incident) != 2:
            continue
        manifold_edges += 1
        first, second = incident
        vertices = mesh.edges[edge_index].vertices
        same = all(
            math.dist(per_face_uv[first][vertex], per_face_uv[second][vertex])
            <= UV_COMPARE_EPSILON
            for vertex in vertices
        )
        if same:
            continuous[first].append(second)
            continuous[second].append(first)
        else:
            seam_edges += 1
    return continuous, manifold_edges, seam_edges


def normalize_island_orientation(mesh, edge_faces) -> None:
    """Mirror whole negative islands; local flips still reject the candidate."""
    layer = mesh.uv_layers.active
    if layer is None:
        return
    per_face = face_uvs(mesh)
    continuous, _manifold, _seams = continuity_graph(mesh, edge_faces, per_face)
    remaining = set(range(len(mesh.polygons)))
    while remaining:
        seed = min(remaining)
        remaining.remove(seed)
        queue = deque([seed])
        island: list[int] = []
        signs: list[int] = []
        while queue:
            face_index = queue.popleft()
            island.append(face_index)
            polygon = mesh.polygons[face_index]
            loops = list(polygon.loop_indices)
            uv = [quantized_uv(layer.data[index].uv) for index in loops[:3]]
            signed = (
                (uv[1][0] - uv[0][0]) * (uv[2][1] - uv[0][1])
                - (uv[1][1] - uv[0][1]) * (uv[2][0] - uv[0][0])
            )
            if abs(signed) > UV_COLLAPSE_EPSILON:
                signs.append(1 if signed > 0.0 else -1)
            for neighbour in continuous[face_index]:
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    queue.append(neighbour)
        if signs and sum(signs) < 0:
            loop_indices = [
                loop_index
                for face_index in island
                for loop_index in mesh.polygons[face_index].loop_indices
            ]
            minimum = min(float(layer.data[index].uv.x) for index in loop_indices)
            maximum = max(float(layer.data[index].uv.x) for index in loop_indices)
            for loop_index in loop_indices:
                uv = layer.data[loop_index].uv
                uv.x = minimum + maximum - uv.x


def weighted_percentile(
    samples: Sequence[tuple[float, float]], fraction: float
) -> float:
    if not samples:
        return math.inf
    ordered = sorted(samples)
    target = sum(weight for _value, weight in ordered) * fraction
    cumulative = 0.0
    for value, weight in ordered:
        cumulative += weight
        if cumulative >= target:
            return value
    return ordered[-1][0]


def density_gates_pass(values: Sequence[float]) -> bool:
    p50, p90, p95, p99 = values
    return (
        p50 <= MAX_DENSITY_RATIO_P50 + 1.0e-9
        and p90 <= MAX_DENSITY_RATIO_P90 + 1.0e-9
        and p95 <= MAX_DENSITY_RATIO_P95 + 1.0e-9
        and p99 <= MAX_DENSITY_RATIO_P99 + 1.0e-9
    )


def density_quality(values: Sequence[float]) -> tuple[float, ...]:
    p50, p90, p95, p99 = values
    return (
        max(0.0, p95 - MAX_DENSITY_RATIO_P95),
        max(0.0, p99 - MAX_DENSITY_RATIO_P99),
        max(0.0, p90 - MAX_DENSITY_RATIO_P90),
        max(0.0, p50 - MAX_DENSITY_RATIO_P50),
        p95,
        p99,
        p90,
        p50,
    )


def virtual_density_analysis(
    mesh,
    edge_faces: dict[int, list[int]],
    per_face: Sequence[dict[int, tuple[float, float]]] | None = None,
) -> tuple[
    tuple[float, float, float, float],
    dict[int, float],
    tuple[frozenset[int], ...],
    dict[int, tuple[float, float, float, float]],
]:
    """Measure local scale after virtual exact-density normalization per chart."""
    if per_face is None:
        per_face = face_uvs(mesh)
    world_scale = float(mesh["daedalon_world_scale"])
    face_world_area: dict[int, float] = defaultdict(float)
    face_uv_area: dict[int, float] = defaultdict(float)
    face_samples: dict[int, list[tuple[float, float]]] = defaultdict(list)
    for polygon in mesh.polygons:
        vertices = list(polygon.vertices)
        triangles = (
            ((0, 1, 2),)
            if len(vertices) == 3
            else ((0, 1, 2), (0, 2, 3))
        )
        for indices in triangles:
            positions = [mesh.vertices[vertices[index]].co for index in indices]
            uvs = [per_face[polygon.index][vertices[index]] for index in indices]
            _stretch, signed_area = triangle_stretch(positions, uvs)
            world_area = (
                0.5
                * (positions[1] - positions[0])
                .cross(positions[2] - positions[0])
                .length
                * world_scale
                * world_scale
            )
            uv_area = 0.5 * abs(signed_area)
            face_world_area[polygon.index] += world_area
            face_uv_area[polygon.index] += uv_area
            if world_area > 0.0 and uv_area > 0.0:
                face_samples[polygon.index].append(
                    (math.sqrt(uv_area / world_area), world_area)
                )

    excluded = {
        int(index) for index in mesh.get("daedalon_density_exception_indices", [])
    }
    global_samples: list[tuple[float, float]] = []
    face_excess: dict[int, float] = {
        polygon.index: 0.0 for polygon in mesh.polygons
    }
    failing_islands: list[frozenset[int]] = []
    island_percentiles_by_seed: dict[int, tuple[float, float, float, float]] = {}
    for island_values in uv_islands(mesh, edge_faces):
        island = frozenset(island_values)
        world_area = sum(face_world_area[face] for face in island)
        uv_area = sum(face_uv_area[face] for face in island)
        if world_area <= 0.0 or uv_area <= 0.0:
            for face in island:
                face_excess[face] = math.inf
            failing_islands.append(island)
            continue
        if len(island) == 1 and next(iter(island)) in excluded:
            continue
        island_density = math.sqrt(uv_area / world_area)
        island_samples: list[tuple[float, float]] = []
        for face in island:
            for triangle_density, triangle_world_area in face_samples[face]:
                signed_error = math.log2(triangle_density / island_density)
                symmetric_ratio = math.pow(2.0, abs(signed_error))
                sample = (symmetric_ratio, triangle_world_area)
                global_samples.append(sample)
                island_samples.append(sample)
                face_excess[face] = max(
                    face_excess[face],
                    max(0.0, abs(signed_error) - 1.0),
                )
        island_percentiles = tuple(
            weighted_percentile(island_samples, fraction)
            for fraction in (0.50, 0.90, 0.95, 0.99)
        )
        island_percentiles_by_seed[min(island)] = island_percentiles
        if not density_gates_pass(island_percentiles):
            failing_islands.append(island)

    percentiles = tuple(
        weighted_percentile(global_samples, fraction)
        for fraction in (0.50, 0.90, 0.95, 0.99)
    )
    return (
        percentiles,
        face_excess,
        tuple(failing_islands),
        island_percentiles_by_seed,
    )


def measure_uv(mesh, edge_faces: dict[int, list[int]]) -> UvMetrics:
    per_face = face_uvs(mesh)
    continuous, manifold_edges, seam_edges = continuity_graph(mesh, edge_faces, per_face)
    remaining = set(range(len(mesh.polygons)))
    islands: list[list[int]] = []
    while remaining:
        seed = min(remaining)
        remaining.remove(seed)
        queue = deque([seed])
        island: list[int] = []
        while queue:
            face = queue.popleft()
            island.append(face)
            for neighbour in continuous[face]:
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    queue.append(neighbour)
        islands.append(island)

    stretches: list[float] = []
    collapsed = 0
    flipped = 0
    minimum_area = math.inf
    world_scale = float(mesh["daedalon_world_scale"])
    face_world_area: dict[int, float] = defaultdict(float)
    face_uv_area: dict[int, float] = defaultdict(float)
    for polygon in mesh.polygons:
        vertices = list(polygon.vertices)
        triangles = ((0, 1, 2),) if len(vertices) == 3 else ((0, 1, 2), (0, 2, 3))
        for indices in triangles:
            positions = [mesh.vertices[vertices[index]].co for index in indices]
            uvs = [per_face[polygon.index][vertices[index]] for index in indices]
            stretch, signed_area = triangle_stretch(positions, uvs)
            world_area = (
                0.5
                * (positions[1] - positions[0]).cross(positions[2] - positions[0]).length
                * world_scale
                * world_scale
            )
            uv_area = 0.5 * abs(signed_area)
            face_world_area[polygon.index] += world_area
            face_uv_area[polygon.index] += uv_area
            minimum_area = min(minimum_area, abs(signed_area))
            if stretch is None:
                collapsed += 1
            else:
                stretches.append(stretch)
            if signed_area < -UV_COLLAPSE_EPSILON:
                flipped += 1
    ordered = sorted(stretches)
    if not ordered:
        p95 = p99 = maximum = math.inf
    else:
        percentile = lambda fraction: ordered[
            min(len(ordered) - 1, math.ceil(len(ordered) * fraction) - 1)
        ]
        p95 = percentile(0.95)
        p99 = percentile(0.99)
        maximum = ordered[-1]

    layer = mesh.uv_layers.active
    assert layer is not None
    serialized_uvs = [quantized_uv(entry.uv) for entry in layer.data]
    minimum_uv = (
        min(value[0] for value in serialized_uvs),
        min(value[1] for value in serialized_uvs),
    )
    maximum_uv = (
        max(value[0] for value in serialized_uvs),
        max(value[1] for value in serialized_uvs),
    )
    out_of_bounds = sum(
        u < 0.0 or u > 1.0 or v < 0.0 or v > 1.0 for u, v in serialized_uvs
    )
    oversized = 0
    maximum_fixed_density_span = 0.0
    maximum_density_error = 0.0
    density_fit_limit = 1.0 - 2.0 * UV_GUTTER
    for island in islands:
        values = [
            per_face[face][vertex]
            for face in island
            for vertex in mesh.polygons[face].vertices
        ]
        width = max(value[0] for value in values) - min(value[0] for value in values)
        height = max(value[1] for value in values) - min(value[1] for value in values)
        world_area = sum(face_world_area[face] for face in island)
        uv_area = sum(face_uv_area[face] for face in island)
        if world_area <= 0.0 or uv_area <= 0.0:
            maximum_density_error = math.inf
            continue
        density_ratio = math.sqrt(uv_area / world_area) / TARGET_UV_PER_WORLD_BLOCK
        target_scale = 1.0 / density_ratio
        fixed_width = width * target_scale
        fixed_height = height * target_scale
        maximum_fixed_density_span = max(
            maximum_fixed_density_span,
            fixed_width / density_fit_limit,
            fixed_height / density_fit_limit,
        )
        if fixed_width > density_fit_limit or fixed_height > density_fit_limit:
            oversized += 1
        maximum_density_error = max(
            maximum_density_error,
            max(density_ratio, 1.0 / density_ratio) - 1.0,
        )

    density_percentiles, _density_excess, _density_failures, _island_density = (
        virtual_density_analysis(mesh, edge_faces, per_face)
    )
    density_p50, density_p90, density_p95, density_p99 = density_percentiles

    singleton_faces = tuple(
        sorted(island[0] for island in islands if len(island) == 1)
    )
    allowed_singleton_faces = tuple(
        sorted(int(index) for index in mesh.get("daedalon_allowed_single_face_indices", []))
    )
    return UvMetrics(
        manifold_edges=manifold_edges,
        seam_edges=seam_edges,
        seam_percent=100.0 * seam_edges / max(1, manifold_edges),
        islands=len(islands),
        island_face_counts=tuple(sorted(len(island) for island in islands)),
        single_face_islands=len(singleton_faces),
        allowed_single_face_islands=len(allowed_singleton_faces),
        single_face_indices=singleton_faces,
        allowed_single_face_indices=allowed_singleton_faces,
        collapsed_faces=collapsed,
        flipped_faces=flipped,
        p95_stretch=p95,
        p99_stretch=p99,
        maximum_stretch=maximum,
        minimum_abs_uv_area=minimum_area,
        uv_minimum=minimum_uv,
        uv_maximum=maximum_uv,
        out_of_bounds_uvs=out_of_bounds,
        oversized_islands=oversized,
        maximum_fixed_density_span=maximum_fixed_density_span,
        maximum_island_density_error=maximum_density_error,
        density_ratio_p50=density_p50,
        density_ratio_p90=density_p90,
        density_ratio_p95=density_p95,
        density_ratio_p99=density_p99,
    )


def normalize_uv_density(mesh, edge_faces: dict[int, list[int]]) -> None:
    """Give every coherent chart exactly 1/3 UV per final world block.

    Charts deliberately overlap because all statues use the same stone texture.
    No chart is packed or rescaled to fill the sprite independently.
    """
    layer = mesh.uv_layers.active
    if layer is None:
        raise ValueError("Blender produced no UV layer")
    per_face = face_uvs(mesh)
    world_scale = float(mesh["daedalon_world_scale"])
    for island_index, island in enumerate(uv_islands(mesh, edge_faces)):
        world_area = 0.0
        uv_area = 0.0
        loop_indices: list[int] = []
        for face in island:
            polygon = mesh.polygons[face]
            vertices = list(polygon.vertices)
            triangles = (
                ((0, 1, 2),)
                if len(vertices) == 3
                else ((0, 1, 2), (0, 2, 3))
            )
            for indices in triangles:
                positions = [mesh.vertices[vertices[index]].co for index in indices]
                uvs = [per_face[face][vertices[index]] for index in indices]
                _stretch, signed_area = triangle_stretch(positions, uvs)
                world_area += (
                    0.5
                    * (positions[1] - positions[0])
                    .cross(positions[2] - positions[0])
                    .length
                    * world_scale
                    * world_scale
                )
                uv_area += 0.5 * abs(signed_area)
            loop_indices.extend(polygon.loop_indices)
        if world_area <= 0.0 or uv_area <= 0.0:
            raise ValueError(f"UV island {island_index} has zero world or UV area")
        scale = math.sqrt(
            world_area * TARGET_UV_PER_WORLD_BLOCK * TARGET_UV_PER_WORLD_BLOCK / uv_area
        )
        scaled = [
            (
                float(layer.data[loop_index].uv.x) * scale,
                float(layer.data[loop_index].uv.y) * scale,
            )
            for loop_index in loop_indices
        ]
        minimum_u = min(value[0] for value in scaled)
        maximum_u = max(value[0] for value in scaled)
        minimum_v = min(value[1] for value in scaled)
        maximum_v = max(value[1] for value in scaled)
        width = maximum_u - minimum_u
        height = maximum_v - minimum_v
        limit = 1.0 - 2.0 * UV_GUTTER
        if width > limit + 1.0e-12 or height > limit + 1.0e-12:
            raise ValueError(
                f"fixed-density UV island {island_index} is oversized "
                f"({width:.6f} x {height:.6f})"
            )
        offset_u = 0.5 - 0.5 * (minimum_u + maximum_u)
        offset_v = 0.5 - 0.5 * (minimum_v + maximum_v)
        for loop_index, value in zip(loop_indices, scaled, strict=True):
            layer.data[loop_index].uv = (value[0] + offset_u, value[1] + offset_v)
        # Blender stores UVs as float32.  A few source-authored micro-flaps need
        # a deterministic correction after that conversion to retain density.
        target_area = world_area * TARGET_UV_PER_WORLD_BLOCK * TARGET_UV_PER_WORLD_BLOCK
        for _iteration in range(12):
            actual_area = 0.0
            for face in island:
                loops = list(mesh.polygons[face].loop_indices)
                triangles = (
                    ((0, 1, 2),)
                    if len(loops) == 3
                    else ((0, 1, 2), (0, 2, 3))
                )
                for indices in triangles:
                    uv = [
                        (
                            float(layer.data[loops[index]].uv.x),
                            float(layer.data[loops[index]].uv.y),
                        )
                        for index in indices
                    ]
                    actual_area += 0.5 * abs(
                        (uv[1][0] - uv[0][0]) * (uv[2][1] - uv[0][1])
                        - (uv[1][1] - uv[0][1]) * (uv[2][0] - uv[0][0])
                    )
            if actual_area <= 0.0:
                raise ValueError(f"UV island {island_index} collapsed at float32")
            correction = math.sqrt(target_area / actual_area)
            if abs(correction - 1.0) <= 1.0e-9:
                break
            for loop_index in loop_indices:
                uv = layer.data[loop_index].uv
                uv.x = 0.5 + (float(uv.x) - 0.5) * correction
                uv.y = 0.5 + (float(uv.y) - 0.5) * correction


def fixed_density_oversized_faces(
    mesh, edge_faces: dict[int, list[int]]
) -> frozenset[int]:
    """Return charts that cannot fit one sprite at the locked material scale."""
    per_face = face_uvs(mesh)
    world_scale = float(mesh["daedalon_world_scale"])
    problems: set[int] = set()
    for island in uv_islands(mesh, edge_faces):
        world_area = 0.0
        uv_area = 0.0
        values: list[tuple[float, float]] = []
        for face in island:
            polygon = mesh.polygons[face]
            vertices = list(polygon.vertices)
            triangles = (
                ((0, 1, 2),)
                if len(vertices) == 3
                else ((0, 1, 2), (0, 2, 3))
            )
            values.extend(per_face[face][vertex] for vertex in vertices)
            for indices in triangles:
                positions = [mesh.vertices[vertices[index]].co for index in indices]
                uvs = [per_face[face][vertices[index]] for index in indices]
                _stretch, signed_area = triangle_stretch(positions, uvs)
                world_area += (
                    0.5
                    * (positions[1] - positions[0])
                    .cross(positions[2] - positions[0])
                    .length
                    * world_scale
                    * world_scale
                )
                uv_area += 0.5 * abs(signed_area)
        if world_area <= 0.0 or uv_area <= 0.0:
            problems.update(island)
            continue
        scale = math.sqrt(
            world_area * TARGET_UV_PER_WORLD_BLOCK * TARGET_UV_PER_WORLD_BLOCK / uv_area
        )
        width = (max(value[0] for value in values) - min(value[0] for value in values)) * scale
        height = (max(value[1] for value in values) - min(value[1] for value in values)) * scale
        if width > 1.0 - 2.0 * UV_GUTTER or height > 1.0 - 2.0 * UV_GUTTER:
            problems.update(island)
    return frozenset(problems)


def fixed_density_span_extreme_faces(
    mesh, edge_faces: dict[int, list[int]]
) -> frozenset[int]:
    """Return dominant-axis endpoints of the worst oversized chart."""
    per_face = face_uvs(mesh)
    world_scale = float(mesh["daedalon_world_scale"])
    limit = 1.0 - 2.0 * UV_GUTTER
    candidates: list[tuple[float, int, frozenset[int]]] = []
    for island in uv_islands(mesh, edge_faces):
        if len(island) < 2:
            continue
        world_area = 0.0
        uv_area = 0.0
        for face in island:
            polygon = mesh.polygons[face]
            vertices = list(polygon.vertices)
            triangles = (
                ((0, 1, 2),)
                if len(vertices) == 3
                else ((0, 1, 2), (0, 2, 3))
            )
            for indices in triangles:
                positions = [mesh.vertices[vertices[index]].co for index in indices]
                uvs = [per_face[face][vertices[index]] for index in indices]
                _stretch, signed_area = triangle_stretch(positions, uvs)
                world_area += (
                    0.5
                    * (positions[1] - positions[0])
                    .cross(positions[2] - positions[0])
                    .length
                    * world_scale
                    * world_scale
                )
                uv_area += 0.5 * abs(signed_area)
        if world_area <= 0.0 or uv_area <= 0.0:
            continue
        scale = math.sqrt(
            world_area * TARGET_UV_PER_WORLD_BLOCK * TARGET_UV_PER_WORLD_BLOCK / uv_area
        )
        face_bounds = {
            face: (
                min(per_face[face][vertex][0] for vertex in mesh.polygons[face].vertices),
                max(per_face[face][vertex][0] for vertex in mesh.polygons[face].vertices),
                min(per_face[face][vertex][1] for vertex in mesh.polygons[face].vertices),
                max(per_face[face][vertex][1] for vertex in mesh.polygons[face].vertices),
            )
            for face in island
        }
        width = (max(bounds[1] for bounds in face_bounds.values()) - min(
            bounds[0] for bounds in face_bounds.values()
        )) * scale
        height = (max(bounds[3] for bounds in face_bounds.values()) - min(
            bounds[2] for bounds in face_bounds.values()
        )) * scale
        span = max(width, height) / limit
        if span <= 1.0 + 1.0e-12:
            continue
        lower_offset = 2 if height > width else 0
        upper_offset = lower_offset + 1
        lower_face = min(
            island,
            key=lambda face: (face_bounds[face][lower_offset], face),
        )
        upper_face = min(
            (face for face in island if face != lower_face),
            key=lambda face: (-face_bounds[face][upper_offset], face),
        )
        candidates.append(
            (span, min(island), frozenset((lower_face, upper_face)))
        )
    if not candidates:
        return frozenset()
    return max(candidates, key=lambda entry: (entry[0], -entry[1]))[2]


def rear_vertical_fit_path(
    mesh,
    edge_faces: dict[int, list[int]],
    current: Candidate,
    maximum_seams: int,
) -> frozenset[int] | None:
    """Return one rear-biased lengthwise split for an oversized disk chart.

    This is deliberately a single topology-only fallback.  It never rotates or
    rescales UVs and never sends more than its one best path to Blender.
    """
    per_face = face_uvs(mesh)
    world_scale = float(mesh["daedalon_world_scale"])
    fit_limit = 1.0 - 2.0 * UV_GUTTER
    manifold_edges = {
        edge_index for edge_index, incident in edge_faces.items() if len(incident) == 2
    }

    def face_stats(face: int) -> tuple[float, float, float, float, float, float]:
        polygon = mesh.polygons[face]
        vertices = list(polygon.vertices)
        triangles = (
            ((0, 1, 2),)
            if len(vertices) == 3
            else ((0, 1, 2), (0, 2, 3))
        )
        values = [per_face[face][vertex] for vertex in vertices]
        world_area = 0.0
        uv_area = 0.0
        for indices in triangles:
            positions = [mesh.vertices[vertices[index]].co for index in indices]
            uvs = [per_face[face][vertices[index]] for index in indices]
            _stretch, signed_area = triangle_stretch(positions, uvs)
            world_area += (
                0.5
                * (positions[1] - positions[0])
                .cross(positions[2] - positions[0])
                .length
                * world_scale
                * world_scale
            )
            uv_area += 0.5 * abs(signed_area)
        return (
            world_area,
            uv_area,
            min(value[0] for value in values),
            max(value[0] for value in values),
            min(value[1] for value in values),
            max(value[1] for value in values),
        )

    stats = {polygon.index: face_stats(polygon.index) for polygon in mesh.polygons}

    def chart_span(faces: Iterable[int]) -> float:
        selected = tuple(sorted(faces))
        world_area = sum(stats[face][0] for face in selected)
        uv_area = sum(stats[face][1] for face in selected)
        if world_area <= 0.0 or uv_area <= 0.0:
            return math.inf
        scale = math.sqrt(
            world_area
            * TARGET_UV_PER_WORLD_BLOCK
            * TARGET_UV_PER_WORLD_BLOCK
            / uv_area
        )
        width = (
            max(stats[face][3] for face in selected)
            - min(stats[face][2] for face in selected)
        ) * scale
        height = (
            max(stats[face][5] for face in selected)
            - min(stats[face][4] for face in selected)
        ) * scale
        return max(width, height) / fit_limit

    targets: list[tuple[float, int, int, int, frozenset[int]]] = []
    for raw_island in uv_islands(mesh, edge_faces):
        island = frozenset(raw_island)
        if len(island) < 32:
            continue
        span = chart_span(island)
        if span <= 1.0 + 1.0e-12:
            continue
        world_area = sum(stats[face][0] for face in island)
        uv_area = sum(stats[face][1] for face in island)
        if world_area <= 0.0 or uv_area <= 0.0:
            continue
        scale = math.sqrt(
            world_area
            * TARGET_UV_PER_WORLD_BLOCK
            * TARGET_UV_PER_WORLD_BLOCK
            / uv_area
        )
        width = (
            max(stats[face][3] for face in island)
            - min(stats[face][2] for face in island)
        ) * scale
        height = (
            max(stats[face][5] for face in island)
            - min(stats[face][4] for face in island)
        ) * scale
        lower_offset = 4 if height > width else 2
        upper_offset = lower_offset + 1
        lower_face = min(island, key=lambda face: (stats[face][lower_offset], face))
        upper_face = min(
            (face for face in island if face != lower_face),
            key=lambda face: (-stats[face][upper_offset], face),
        )
        targets.append((span, min(island), lower_face, upper_face, island))
    if not targets:
        return None
    target_span, _seed, lower_face, upper_face, chart = max(
        targets,
        key=lambda value: (value[0], -value[1], -value[2], -value[3]),
    )
    if chart not in topological_charts(mesh, edge_faces, current.cut_edges):
        return None
    if not chart_is_disk(mesh, edge_faces, current.cut_edges, chart):
        return None

    chart_vertices = {
        vertex for face in chart for vertex in mesh.polygons[face].vertices
    }
    parent: dict[tuple[int, int], tuple[int, int]] = {
        (face, vertex): (face, vertex)
        for face in chart
        for vertex in mesh.polygons[face].vertices
    }

    def find(corner: tuple[int, int]) -> tuple[int, int]:
        root = corner
        while parent[root] != root:
            root = parent[root]
        while parent[corner] != corner:
            next_corner = parent[corner]
            parent[corner] = root
            corner = next_corner
        return root

    def union(first: tuple[int, int], second: tuple[int, int]) -> None:
        first_root = find(first)
        second_root = find(second)
        if first_root != second_root:
            parent[max(first_root, second_root)] = min(first_root, second_root)

    adjacency: dict[int, list[tuple[int, int]]] = defaultdict(list)
    boundary_edges: set[int] = set()
    for edge_index, incident in edge_faces.items():
        incident_in_chart = [face for face in incident if face in chart]
        if not incident_in_chart:
            continue
        first, second = mesh.edges[edge_index].vertices
        internal = (
            len(incident) == 2
            and edge_index not in current.cut_edges
            and all(face in chart for face in incident)
        )
        if internal:
            adjacency[first].append((second, edge_index))
            adjacency[second].append((first, edge_index))
            first_face, second_face = incident
            union((first_face, first), (second_face, first))
            union((first_face, second), (second_face, second))
        else:
            boundary_edges.add(edge_index)
    for neighbours in adjacency.values():
        neighbours.sort(key=lambda entry: (entry[0], entry[1]))

    root_faces: dict[tuple[int, int], set[int]] = defaultdict(set)
    for face, vertex in parent:
        root_faces[find((face, vertex))].add(face)
    boundary_copies: set[tuple[int, int]] = set()
    for edge_index in sorted(boundary_edges):
        first, second = mesh.edges[edge_index].vertices
        for face in edge_faces[edge_index]:
            if face not in chart:
                continue
            boundary_copies.add(find((face, first)))
            boundary_copies.add(find((face, second)))
    if not boundary_copies:
        return None
    boundary_vertices = {copy[1] for copy in boundary_copies}

    xs = [float(mesh.vertices[index].co.x) for index in chart_vertices]
    ys = [float(mesh.vertices[index].co.y) for index in chart_vertices]
    zs = [float(mesh.vertices[index].co.z) for index in chart_vertices]
    minimum_y = min(ys)
    maximum_y = max(ys)
    span_y = maximum_y - minimum_y
    minimum_z = min(zs)
    span_z = max(max(zs) - minimum_z, 1.0e-12)
    center_x = (min(xs) + max(xs)) * 0.5
    span_x = max(max(xs) - min(xs), 1.0e-12)
    if span_y <= 1.0e-12:
        return None
    anchor_band = max(span_y * 0.05, 1.0e-9)
    lower_anchors = [
        copy
        for copy in boundary_copies
        if float(mesh.vertices[copy[1]].co.y) <= minimum_y + anchor_band
    ]
    upper_anchors = [
        copy
        for copy in boundary_copies
        if float(mesh.vertices[copy[1]].co.y) >= maximum_y - anchor_band
    ]

    def anchor_key(
        copy: tuple[int, int],
    ) -> tuple[float, float, int, tuple[int, int]]:
        vertex = copy[1]
        coordinate = mesh.vertices[vertex].co
        return (
            -float(coordinate.z),
            abs(float(coordinate.x) - center_x) / span_x,
            vertex,
            copy,
        )

    lower_anchors = sorted(lower_anchors, key=anchor_key)[:4]
    upper_anchors = sorted(upper_anchors, key=anchor_key)[:4]
    if not lower_anchors or not upper_anchors:
        return None

    bin_count = 64
    back_by_bin = [-math.inf] * bin_count
    for vertex in chart_vertices:
        coordinate = mesh.vertices[vertex].co
        normalized_y = (float(coordinate.y) - minimum_y) / span_y
        bin_index = min(bin_count - 1, max(0, int(normalized_y * bin_count)))
        back_by_bin[bin_index] = max(back_by_bin[bin_index], float(coordinate.z))
    available_bins = [
        index for index, value in enumerate(back_by_bin) if math.isfinite(value)
    ]
    for bin_index, value in enumerate(back_by_bin):
        if math.isfinite(value):
            continue
        nearest = min(
            available_bins,
            key=lambda candidate: (abs(candidate - bin_index), candidate),
        )
        back_by_bin[bin_index] = back_by_bin[nearest]

    base_chart_count = len(topological_charts(mesh, edge_faces, current.cut_edges))
    records: list[tuple[tuple[object, ...], frozenset[int]]] = []
    seen_paths: set[frozenset[int]] = set()
    for start_copy in lower_anchors:
        for goal_copy in upper_anchors:
            start = start_copy[1]
            goal = goal_copy[1]
            if start == goal:
                continue
            best: dict[int, tuple[float, tuple[int, ...]]] = {
                start: (0.0, ())
            }
            heap: list[tuple[float, tuple[int, ...], int]] = [(0.0, (), start)]
            while heap:
                distance, path, vertex = heapq.heappop(heap)
                if best.get(vertex) != (distance, path):
                    continue
                if vertex == goal:
                    break
                first = mesh.vertices[vertex].co
                for neighbour, edge_index in adjacency.get(vertex, ()):
                    incident = edge_faces[edge_index]
                    if not path and not any(
                        face in root_faces[start_copy] for face in incident
                    ):
                        continue
                    if neighbour == goal and not any(
                        face in root_faces[goal_copy] for face in incident
                    ):
                        continue
                    if neighbour in boundary_vertices and neighbour != goal:
                        continue
                    second = mesh.vertices[neighbour].co
                    length = float((first - second).length)
                    if length <= 1.0e-12:
                        continue
                    midpoint_y = (float(first.y) + float(second.y)) * 0.5
                    midpoint_z = (float(first.z) + float(second.z)) * 0.5
                    midpoint_x = (float(first.x) + float(second.x)) * 0.5
                    normalized_y = (midpoint_y - minimum_y) / span_y
                    backness = max(
                        0.0, min(1.0, (midpoint_z - minimum_z) / span_z)
                    )
                    horizontalness = 1.0 - abs(
                        float(first.y) - float(second.y)
                    ) / length
                    horizontalness = max(0.0, min(1.0, horizontalness))
                    if (
                        0.25 <= normalized_y <= 0.75
                        and backness < 0.5
                        and horizontalness > 0.75
                    ):
                        continue
                    bin_index = min(
                        bin_count - 1,
                        max(0, int(normalized_y * bin_count)),
                    )
                    back_depth = max(
                        0.0, back_by_bin[bin_index] - midpoint_z
                    ) / span_z
                    center_offset = abs(midpoint_x - center_x) / span_x
                    edge_cost = length * (
                        1.0
                        + 24.0 * back_depth * back_depth
                        + 2.0 * center_offset * center_offset
                        + 1.5 * horizontalness * horizontalness
                    )
                    candidate = (distance + edge_cost, path + (edge_index,))
                    if neighbour not in best or candidate < best[neighbour]:
                        best[neighbour] = candidate
                        heapq.heappush(heap, (*candidate, neighbour))
            if goal not in best:
                continue
            path_edges = frozenset(best[goal][1])
            if not path_edges or path_edges in seen_paths:
                continue
            seen_paths.add(path_edges)
            refined = frozenset(set(current.cut_edges) | set(path_edges))
            if len(refined & manifold_edges) > maximum_seams:
                continue
            new_charts = topological_charts(mesh, edge_faces, refined)
            if len(new_charts) != base_chart_count + 1:
                continue
            children = [child for child in new_charts if child & chart]
            if (
                len(children) != 2
                or any(not child <= chart or len(child) < 16 for child in children)
                or frozenset().union(*children) != chart
                or any(
                    lower_face in child and upper_face in child for child in children
                )
                or any(
                    not chart_is_disk(mesh, edge_faces, refined, child)
                    for child in children
                )
                or non_disk_chart_seeds(mesh, edge_faces, refined)
            ):
                continue
            predicted_span = max(chart_span(child) for child in children)
            if predicted_span >= target_span - 1.0e-12:
                continue
            key = (
                predicted_span,
                seam_visibility_cost(mesh, sorted(path_edges)),
                len(path_edges),
                tuple(sorted(path_edges)),
                start_copy,
                goal_copy,
            )
            records.append((key, refined))
    if not records:
        return None
    return min(records, key=lambda record: record[0])[1]


def uv_face_quality(
    mesh, edge_faces: dict[int, list[int]]
) -> tuple[dict[int, float], dict[int, float], frozenset[int]]:
    """Return conformal condition, local-density excess and strict faces."""
    per_face = face_uvs(mesh)
    worst: dict[int, float] = {}
    strict: set[int] = set()
    for polygon in mesh.polygons:
        vertices = list(polygon.vertices)
        triangles = ((0, 1, 2),) if len(vertices) == 3 else ((0, 1, 2), (0, 2, 3))
        maximum = 1.0
        for indices in triangles:
            positions = [mesh.vertices[vertices[index]].co for index in indices]
            uvs = [per_face[polygon.index][vertices[index]] for index in indices]
            stretch, signed_area = triangle_stretch(positions, uvs)
            if stretch is None or signed_area < -UV_COLLAPSE_EPSILON:
                strict.add(polygon.index)
                maximum = math.inf
            else:
                maximum = max(maximum, stretch)
        worst[polygon.index] = maximum
    _percentiles, density_excess, _failing_islands, _island_density = (
        virtual_density_analysis(mesh, edge_faces, per_face)
    )
    return worst, density_excess, frozenset(strict)


def uv_islands(mesh, edge_faces: dict[int, list[int]]) -> list[list[int]]:
    per_face = face_uvs(mesh)
    continuous, _manifold, _seams = continuity_graph(mesh, edge_faces, per_face)
    remaining = set(range(len(mesh.polygons)))
    islands: list[list[int]] = []
    while remaining:
        seed = min(remaining)
        remaining.remove(seed)
        queue = deque([seed])
        island: list[int] = []
        while queue:
            face = queue.popleft()
            island.append(face)
            for neighbour in continuous[face]:
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    queue.append(neighbour)
        islands.append(island)
    return islands




def topological_charts(
    mesh,
    edge_faces: dict[int, list[int]],
    cut_edges: frozenset[int],
) -> list[frozenset[int]]:
    adjacency: list[list[int]] = [[] for _ in mesh.polygons]
    for edge_index, incident in edge_faces.items():
        if len(incident) != 2 or edge_index in cut_edges:
            continue
        first, second = incident
        adjacency[first].append(second)
        adjacency[second].append(first)
    remaining = set(range(len(mesh.polygons)))
    charts: list[frozenset[int]] = []
    while remaining:
        seed = min(remaining)
        remaining.remove(seed)
        queue = deque([seed])
        chart = {seed}
        while queue:
            face = queue.popleft()
            for neighbour in adjacency[face]:
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    chart.add(neighbour)
                    queue.append(neighbour)
        charts.append(frozenset(chart))
    return charts


def chart_is_disk(
    mesh,
    edge_faces: dict[int, list[int]],
    cut_edges: frozenset[int],
    chart: frozenset[int],
) -> bool:
    """Prove a cut chart is one topological disk using corner copies."""
    edge_by_key = {tuple(sorted(edge.vertices)): edge.index for edge in mesh.edges}
    parent: dict[tuple[int, int], tuple[int, int]] = {
        (face, vertex): (face, vertex)
        for face in chart
        for vertex in mesh.polygons[face].vertices
    }

    def find(corner: tuple[int, int]) -> tuple[int, int]:
        root = corner
        while parent[root] != root:
            root = parent[root]
        while parent[corner] != corner:
            next_corner = parent[corner]
            parent[corner] = root
            corner = next_corner
        return root

    def union(first: tuple[int, int], second: tuple[int, int]) -> None:
        first_root = find(first)
        second_root = find(second)
        if first_root != second_root:
            parent[max(first_root, second_root)] = min(first_root, second_root)

    internal_edges: set[int] = set()
    boundary_sides: list[tuple[tuple[int, int], tuple[int, int]]] = []
    for face in sorted(chart):
        polygon = mesh.polygons[face]
        vertices = tuple(polygon.vertices)
        for offset, first in enumerate(vertices):
            second = vertices[(offset + 1) % len(vertices)]
            edge_index = edge_by_key[tuple(sorted((first, second)))]
            incident = edge_faces[edge_index]
            internal = (
                len(incident) == 2
                and edge_index not in cut_edges
                and all(neighbour in chart for neighbour in incident)
            )
            if internal:
                internal_edges.add(edge_index)
                neighbour = incident[0] if incident[1] == face else incident[1]
                union((face, first), (neighbour, first))
                union((face, second), (neighbour, second))
            else:
                boundary_sides.append(((face, first), (face, second)))

    vertex_copies = {find(corner) for corner in parent}
    edge_copies = len(internal_edges) + len(boundary_sides)
    euler_characteristic = len(vertex_copies) - edge_copies + len(chart)
    if euler_characteristic != 1 or not boundary_sides:
        return False

    boundary_adjacency: dict[tuple[int, int], list[tuple[int, int]]] = defaultdict(list)
    for first, second in boundary_sides:
        first_root = find(first)
        second_root = find(second)
        boundary_adjacency[first_root].append(second_root)
        boundary_adjacency[second_root].append(first_root)
    if any(len(neighbours) != 2 for neighbours in boundary_adjacency.values()):
        return False
    remaining = set(boundary_adjacency)
    boundary_components = 0
    while remaining:
        boundary_components += 1
        seed = min(remaining)
        remaining.remove(seed)
        queue = deque([seed])
        while queue:
            vertex = queue.popleft()
            for neighbour in boundary_adjacency[vertex]:
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    queue.append(neighbour)
    return boundary_components == 1


def non_disk_chart_seeds(
    mesh,
    edge_faces: dict[int, list[int]],
    cut_edges: frozenset[int],
) -> tuple[int, ...]:
    return tuple(
        min(chart)
        for chart in topological_charts(mesh, edge_faces, cut_edges)
        if not chart_is_disk(mesh, edge_faces, cut_edges, chart)
    )


def dual_geodesic_splits(
    mesh,
    edge_faces: dict[int, list[int]],
    current: Candidate,
    worst: dict[int, float],
    density_excess: dict[int, float],
    strict: frozenset[int],
    maximum_seams: int,
    trial_limit: int,
    fit_only: bool = False,
) -> list[frozenset[int]]:
    """Return the best disk-valid arcs for measured Blender trials."""
    per_face_uv = face_uvs(mesh)
    continuous, _manifold_count, _seam_count = continuity_graph(
        mesh, edge_faces, per_face_uv
    )
    islands = uv_islands(mesh, edge_faces)
    problem_faces = (
        {face for face, stretch in worst.items() if stretch > MAX_P95_STRETCH}
        | {face for face, excess in density_excess.items() if excess > 0.0}
        | set(strict)
    )
    eligible = [frozenset(island) for island in islands if set(island) & problem_faces]
    if not eligible:
        return []
    manifold_edges = {
        edge_index for edge_index, incident in edge_faces.items() if len(incident) == 2
    }
    face_area = {polygon.index: float(polygon.area) for polygon in mesh.polygons}

    def weighted_excess(face: int) -> float:
        stretch = worst[face]
        if not math.isfinite(stretch):
            stretch = MAX_P99_STRETCH * 1000.0
        conformal = max(0.0, stretch / MAX_P95_STRETCH - 1.0)
        local_density = density_excess.get(face, 0.0)
        return face_area[face] * (conformal + local_density)

    island = max(
        eligible,
        key=lambda faces: (
            len(faces & problem_faces),
            sum(weighted_excess(face) for face in faces),
            sum(face_area[face] for face in faces if face in problem_faces),
            len(faces),
            -min(faces),
        ),
    )
    tail = island & problem_faces
    island_set = set(island)
    world_scale = float(mesh["daedalon_world_scale"])
    density_fit_limit = 1.0 - 2.0 * UV_GUTTER

    def fixed_density_stats(
        face: int,
    ) -> tuple[float, float, float, float, float, float]:
        polygon = mesh.polygons[face]
        vertices = list(polygon.vertices)
        triangles = (
            ((0, 1, 2),)
            if len(vertices) == 3
            else ((0, 1, 2), (0, 2, 3))
        )
        values = [per_face_uv[face][vertex] for vertex in vertices]
        world_area = 0.0
        uv_area = 0.0
        for indices in triangles:
            positions = [mesh.vertices[vertices[index]].co for index in indices]
            uvs = [per_face_uv[face][vertices[index]] for index in indices]
            _stretch, signed_area = triangle_stretch(positions, uvs)
            world_area += (
                0.5
                * (positions[1] - positions[0])
                .cross(positions[2] - positions[0])
                .length
                * world_scale
                * world_scale
            )
            uv_area += 0.5 * abs(signed_area)
        return (
            world_area,
            uv_area,
            min(value[0] for value in values),
            max(value[0] for value in values),
            min(value[1] for value in values),
            max(value[1] for value in values),
        )

    fit_stats = {
        face: fixed_density_stats(face) for face in island
    } if fit_only else {}

    def predicted_fixed_density_span(
        world_area: float,
        uv_area: float,
        minimum_u: float,
        maximum_u: float,
        minimum_v: float,
        maximum_v: float,
    ) -> float:
        if world_area <= 0.0 or uv_area <= 0.0:
            return math.inf
        scale = math.sqrt(
            world_area
            * TARGET_UV_PER_WORLD_BLOCK
            * TARGET_UV_PER_WORLD_BLOCK
            / uv_area
        )
        return max(
            (maximum_u - minimum_u) * scale,
            (maximum_v - minimum_v) * scale,
        ) / density_fit_limit

    edge_between_faces = {
        tuple(sorted(incident)): edge_index
        for edge_index, incident in edge_faces.items()
        if len(incident) == 2
    }
    adjacency: dict[int, list[tuple[int, int]]] = {
        face: [
            (neighbour, edge_between_faces[tuple(sorted((face, neighbour)))])
            for neighbour in continuous[face]
            if neighbour in island_set
        ]
        for face in island
    }
    for neighbours in adjacency.values():
        neighbours.sort()

    edge_by_key = {tuple(sorted(edge.vertices)): edge.index for edge in mesh.edges}
    face_edges = [
        tuple(edge_by_key[tuple(sorted(key))] for key in polygon.edge_keys)
        for polygon in mesh.polygons
    ]
    boundary_faces = {
        face
        for face in island
        if any(
            len(edge_faces[edge_index]) != 2
            or any(neighbour not in island_set for neighbour in edge_faces[edge_index])
            for edge_index in face_edges[face]
        )
    }
    if not boundary_faces:
        return []

    total_bad = len(tail)
    base_chart_count = len(topological_charts(mesh, edge_faces, current.cut_edges))
    records: list[
        tuple[
            tuple[object, ...],
            tuple[object, ...],
            tuple[object, ...],
            frozenset[int],
            int,
            int,
        ]
    ] = []
    seen_crossings: set[frozenset[int]] = set()
    for seed in sorted(boundary_faces):
        distance = {face: -1 for face in island}
        distance[seed] = 0
        queue: deque[int] = deque([seed])
        while queue:
            face = queue.popleft()
            for neighbour, _edge_index in adjacency[face]:
                if distance[neighbour] < 0:
                    distance[neighbour] = distance[face] + 1
                    queue.append(neighbour)
        maximum_distance = max(distance.values())
        if maximum_distance < 2:
            continue
        size_at_distance = [0] * (maximum_distance + 1)
        bad_at_distance = [0] * (maximum_distance + 1)
        world_at_distance = [0.0] * (maximum_distance + 1)
        uv_at_distance = [0.0] * (maximum_distance + 1)
        minimum_u_at_distance = [math.inf] * (maximum_distance + 1)
        maximum_u_at_distance = [-math.inf] * (maximum_distance + 1)
        minimum_v_at_distance = [math.inf] * (maximum_distance + 1)
        maximum_v_at_distance = [-math.inf] * (maximum_distance + 1)
        for face, value in distance.items():
            size_at_distance[value] += 1
            if face in tail:
                bad_at_distance[value] += 1
            if fit_only:
                world_area, uv_area, min_u, max_u, min_v, max_v = fit_stats[face]
                world_at_distance[value] += world_area
                uv_at_distance[value] += uv_area
                minimum_u_at_distance[value] = min(
                    minimum_u_at_distance[value], min_u
                )
                maximum_u_at_distance[value] = max(
                    maximum_u_at_distance[value], max_u
                )
                minimum_v_at_distance[value] = min(
                    minimum_v_at_distance[value], min_v
                )
                maximum_v_at_distance[value] = max(
                    maximum_v_at_distance[value], max_v
                )
        suffix_world = [0.0] * (maximum_distance + 2)
        suffix_uv = [0.0] * (maximum_distance + 2)
        suffix_minimum_u = [math.inf] * (maximum_distance + 2)
        suffix_maximum_u = [-math.inf] * (maximum_distance + 2)
        suffix_minimum_v = [math.inf] * (maximum_distance + 2)
        suffix_maximum_v = [-math.inf] * (maximum_distance + 2)
        if fit_only:
            for value in range(maximum_distance, -1, -1):
                suffix_world[value] = (
                    suffix_world[value + 1] + world_at_distance[value]
                )
                suffix_uv[value] = suffix_uv[value + 1] + uv_at_distance[value]
                suffix_minimum_u[value] = min(
                    minimum_u_at_distance[value], suffix_minimum_u[value + 1]
                )
                suffix_maximum_u[value] = max(
                    maximum_u_at_distance[value], suffix_maximum_u[value + 1]
                )
                suffix_minimum_v[value] = min(
                    minimum_v_at_distance[value], suffix_minimum_v[value + 1]
                )
                suffix_maximum_v[value] = max(
                    maximum_v_at_distance[value], suffix_maximum_v[value + 1]
                )
        crossing_at_level: list[set[int]] = [set() for _ in range(maximum_distance)]
        for face in island:
            for neighbour, edge_index in adjacency[face]:
                if face < neighbour and distance[face] != distance[neighbour]:
                    level = min(distance[face], distance[neighbour])
                    crossing_at_level[level].add(edge_index)
        lower_size = 0
        lower_bad = 0
        lower_world = 0.0
        lower_uv = 0.0
        lower_minimum_u = math.inf
        lower_maximum_u = -math.inf
        lower_minimum_v = math.inf
        lower_maximum_v = -math.inf
        for level in range(maximum_distance):
            lower_size += size_at_distance[level]
            lower_bad += bad_at_distance[level]
            if fit_only:
                lower_world += world_at_distance[level]
                lower_uv += uv_at_distance[level]
                lower_minimum_u = min(
                    lower_minimum_u, minimum_u_at_distance[level]
                )
                lower_maximum_u = max(
                    lower_maximum_u, maximum_u_at_distance[level]
                )
                lower_minimum_v = min(
                    lower_minimum_v, minimum_v_at_distance[level]
                )
                lower_maximum_v = max(
                    lower_maximum_v, maximum_v_at_distance[level]
                )
            upper_size = len(island) - lower_size
            upper_bad = total_bad - lower_bad
            crossing = frozenset(crossing_at_level[level])
            if (
                level < 1
                or lower_size < 16
                or upper_size < 16
                or lower_bad == 0
                or upper_bad == 0
                or not crossing
                or crossing in seen_crossings
            ):
                continue
            seen_crossings.add(crossing)
            refined = frozenset(set(current.cut_edges) | set(crossing))
            if len(refined & manifold_edges) > maximum_seams:
                continue
            balance = max(lower_bad, upper_bad) / total_bad
            balance_rank = (
                balance,
                len(crossing),
                -min(lower_size, upper_size),
                seed,
                level,
            )
            visibility_rank = (
                seam_visibility_cost(mesh, crossing),
                *balance_rank,
            )
            if fit_only:
                lower_span = predicted_fixed_density_span(
                    lower_world,
                    lower_uv,
                    lower_minimum_u,
                    lower_maximum_u,
                    lower_minimum_v,
                    lower_maximum_v,
                )
                upper_span = predicted_fixed_density_span(
                    suffix_world[level + 1],
                    suffix_uv[level + 1],
                    suffix_minimum_u[level + 1],
                    suffix_maximum_u[level + 1],
                    suffix_minimum_v[level + 1],
                    suffix_maximum_v[level + 1],
                )
                fit_rank = (
                    max(lower_span, upper_span),
                    int(lower_span > 1.0 + 1.0e-12)
                    + int(upper_span > 1.0 + 1.0e-12),
                    seam_visibility_cost(mesh, crossing),
                    len(crossing),
                    -min(lower_size, upper_size),
                    seed,
                    level,
                )
            else:
                fit_rank = (0.0,)
            records.append(
                (fit_rank, balance_rank, visibility_rank, refined, seed, level)
            )

    fit_order = sorted(records, key=lambda entry: entry[0])
    balance_order = sorted(records, key=lambda entry: entry[1])
    visibility_order = sorted(records, key=lambda entry: entry[2])
    if fit_only:
        ordered_records = fit_order
    elif current.height_bins == 1 and current.azimuth_bins == 1:
        ordered_records = []
        ordered_cuts: set[frozenset[int]] = set()
        for index in range(max(len(balance_order), len(visibility_order))):
            for sequence in (visibility_order, balance_order):
                if index >= len(sequence):
                    continue
                record = sequence[index]
                if record[3] in ordered_cuts:
                    continue
                ordered_cuts.add(record[3])
                ordered_records.append(record)
    else:
        ordered_records = balance_order

    accepted: list[frozenset[int]] = []
    for (
        _fit_rank,
        _balance_rank,
        _visibility_rank,
        refined,
        seed,
        level,
    ) in ordered_records:
        if len(topological_charts(mesh, edge_faces, refined)) != base_chart_count + 1:
            continue
        distance = {face: -1 for face in island}
        distance[seed] = 0
        queue = deque([seed])
        while queue:
            face = queue.popleft()
            for neighbour, _edge_index in adjacency[face]:
                if distance[neighbour] < 0:
                    distance[neighbour] = distance[face] + 1
                    queue.append(neighbour)
        lower = {face for face, value in distance.items() if value <= level}
        upper = island_set - lower
        if min(len(lower), len(upper)) < 16:
            continue
        upper_seen = {min(upper)}
        queue = deque(upper_seen)
        while queue:
            face = queue.popleft()
            for neighbour, _edge_index in adjacency[face]:
                if neighbour in upper and neighbour not in upper_seen:
                    upper_seen.add(neighbour)
                    queue.append(neighbour)
        if upper_seen != upper:
            continue
        if non_disk_chart_seeds(mesh, edge_faces, refined):
            continue
        accepted.append(refined)
        if len(accepted) == trial_limit:
            break
    return accepted


def snapshot_uvs(mesh) -> tuple[tuple[float, float], ...]:
    layer = mesh.uv_layers.active
    if layer is None:
        raise ValueError("Blender produced no UV layer")
    return tuple((float(entry.uv.x), float(entry.uv.y)) for entry in layer.data)


def restore_uvs(mesh, values: Sequence[tuple[float, float]]) -> None:
    layer = mesh.uv_layers.active
    if layer is None or len(layer.data) != len(values):
        raise ValueError("cannot restore Blender UV snapshot")
    for entry, value in zip(layer.data, values, strict=True):
        entry.uv = value


def reunwrap_faces_minimum_stretch(mesh, face_indices: Iterable[int]) -> None:
    assert bpy is not None
    if bpy.context.object is not None and bpy.context.object.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    selected = set(face_indices)
    layer = mesh.uv_layers.active
    if layer is None:
        raise ValueError("Blender produced no UV layer")
    for entry in layer.data:
        entry.pin_uv = False
    for polygon in mesh.polygons:
        polygon.select = polygon.index in selected
    bpy.context.tool_settings.mesh_select_mode = (False, False, True)
    bpy.ops.object.mode_set(mode="EDIT")
    result = bpy.ops.uv.unwrap(
        method="MINIMUM_STRETCH",
        fill_holes=False,
        correct_aspect=False,
        margin_method="SCALED",
        margin=0.001,
        no_flip=True,
        iterations=80,
    )
    bpy.ops.object.mode_set(mode="OBJECT")
    if "FINISHED" not in result:
        raise ValueError(f"Blender Minimum Stretch UV fallback failed: {result}")


def hybrid_unwrap(
    mesh,
    edge_faces: dict[int, list[int]],
    candidate: Candidate,
    solver_policy: str,
) -> UvMetrics:
    """Use the recipe-locked ABF/SLIM solver policy."""
    if solver_policy not in SUPPORTED_SOLVER_POLICIES:
        raise ValueError(f"unsupported solver policy {solver_policy}")
    apply_candidate(mesh, candidate)
    normalize_island_orientation(mesh, edge_faces)
    _worst, _density_excess, strict = uv_face_quality(mesh, edge_faces)
    allowed_singletons = set(mesh.get("daedalon_allowed_single_face_indices", []))
    unapproved_singletons = {
        island[0]
        for island in uv_islands(mesh, edge_faces)
        if len(island) == 1 and island[0] not in allowed_singletons
    }
    fallback_faces = set(strict) | unapproved_singletons
    if fallback_faces:
        for chart in topological_charts(mesh, edge_faces, candidate.cut_edges):
            if not chart & fallback_faces:
                continue
            reunwrap_faces_minimum_stretch(mesh, chart)
        normalize_island_orientation(mesh, edge_faces)
    metrics = measure_uv(mesh, edge_faces)
    if solver_policy == "abf_fold_slim_v1":
        return metrics

    # Blender's SLIM implementation balances area and angle.  Evaluate it on
    # each ABF chart whose virtual fixed-density mapping misses the local-scale
    # gates, and keep it only when that chart improves without a fold,
    # singleton, topology or conformal regression.
    _global_density, _face_excess, failing_islands, island_density = (
        virtual_density_analysis(mesh, edge_faces)
    )
    for island in sorted(failing_islands, key=lambda faces: (min(faces), len(faces))):
        seed = min(island)
        baseline_density = island_density.get(seed)
        if baseline_density is None:
            continue
        baseline_values = snapshot_uvs(mesh)
        baseline_metrics = metrics
        reunwrap_faces_minimum_stretch(mesh, island)
        normalize_island_orientation(mesh, edge_faces)
        trial_metrics = measure_uv(mesh, edge_faces)
        _global, _excess, _failures, trial_island_density = (
            virtual_density_analysis(mesh, edge_faces)
        )
        trial_density = trial_island_density.get(seed)
        conformal_safe = (
            trial_metrics.p95_stretch
            <= max(MAX_P95_STRETCH, baseline_metrics.p95_stretch) + 1.0e-9
            and trial_metrics.p99_stretch
            <= max(MAX_P99_STRETCH, baseline_metrics.p99_stretch) + 1.0e-9
        )
        valid = (
            trial_density is not None
            and density_quality(trial_density) < density_quality(baseline_density)
            and conformal_safe
            and trial_metrics.collapsed_faces == 0
            and trial_metrics.flipped_faces == 0
            and trial_metrics.single_face_indices
            == trial_metrics.allowed_single_face_indices
            and trial_metrics.islands == baseline_metrics.islands
        )
        if valid:
            metrics = trial_metrics
        else:
            restore_uvs(mesh, baseline_values)
    return metrics


def refinement_quality_score(
    value: UvMetrics,
    refinement_policy: str,
    rear_spine: bool = False,
) -> tuple[float | int, ...]:
    fit_prefix: tuple[float | int, ...]
    if rear_spine:
        # A rear-spine chart may need several hidden splits before every
        # fixed-density chart fits.  Reward monotonic reduction of the worst
        # span even when an intermediate split temporarily leaves two charts
        # oversized instead of one.
        fit_prefix = (
            max(0.0, value.maximum_fixed_density_span - 1.0),
            value.oversized_islands,
        )
    else:
        fit_prefix = (value.oversized_islands,)
    if refinement_policy == "conformal_v2":
        return (
            *fit_prefix,
            max(0.0, value.p95_stretch - MAX_P95_STRETCH),
            max(0.0, value.p99_stretch - MAX_P99_STRETCH),
            value.p95_stretch,
            value.p99_stretch,
            value.seam_edges,
        )
    if refinement_policy != DISCOVERY_REFINEMENT_POLICY:
        raise ValueError(f"unsupported refinement policy {refinement_policy}")
    return (
        *fit_prefix,
        *density_quality(
            (
                value.density_ratio_p50,
                value.density_ratio_p90,
                value.density_ratio_p95,
                value.density_ratio_p99,
            )
        ),
        max(0.0, value.p95_stretch - MAX_P95_STRETCH),
        max(0.0, value.p99_stretch - MAX_P99_STRETCH),
        value.p95_stretch,
        value.p99_stretch,
        value.seam_edges,
    )


def unwrap_with_repairs(
    mesh,
    edge_faces: dict[int, list[int]],
    candidate: Candidate,
    solver_policy: str,
    refinement_policy: str,
) -> tuple[Candidate, UvMetrics]:
    """Refine high-distortion disk charts with coherent dual-geodesic arcs."""
    manifold_edges = {
        edge_index for edge_index, incident in edge_faces.items() if len(incident) == 2
    }
    maximum_seams = math.floor(len(manifold_edges) * MAX_SEAM_PERCENT / 100.0)
    current = candidate
    metrics = hybrid_unwrap(mesh, edge_faces, current, solver_policy)
    model_name = str(mesh.get("daedalon_model_name", ""))
    p95_stretch_limit = MODEL_MAX_P95_STRETCH.get(model_name, MAX_P95_STRETCH)
    if model_name in DISCOVERY_REAR_VERTICAL_FIT_PATH_MODELS:
        mesh["daedalon_rear_vertical_fit_diagnostic"] = "not_attempted"
    base_round_limit = (
        DISCOVERY_MAX_SPLITS_PER_BASE
        + DISCOVERY_EXTRA_REFINEMENT_SPLITS.get(model_name, 0)
    )
    maximum_rounds = (
        base_round_limit + DISCOVERY_EXTRA_FIT_SPLITS.get(model_name, 0)
    )
    expanded_fit_used = False
    expanded_quality_trials_used = 0
    rear_vertical_fit_used = False
    for _round in range(maximum_rounds):
        expanded_fit_exhausted_this_round = False
        if metrics.accepted(p95_stretch_limit) and metrics.oversized_islands == 0:
            return current, metrics
        rear_spine = current.height_bins == 1 and current.azimuth_bins == 1
        worst, density_excess, strict = uv_face_quality(mesh, edge_faces)
        if refinement_policy == "conformal_v2":
            density_excess = {face: 0.0 for face in density_excess}
        elif refinement_policy != DISCOVERY_REFINEMENT_POLICY:
            raise ValueError(f"unsupported refinement policy {refinement_policy}")
        oversized_faces = fixed_density_oversized_faces(mesh, edge_faces)
        fit_only = metrics.accepted(p95_stretch_limit) and bool(oversized_faces)
        if _round >= base_round_limit and not fit_only:
            break
        if fit_only:
            # This base already passes every appearance gate.  Target only the
            # dominant-axis endpoints of its worst oversized chart so the
            # bounded split trials cannot drift into polishing another island.
            span_extremes = fixed_density_span_extreme_faces(mesh, edge_faces)
            worst = {polygon.index: 1.0 for polygon in mesh.polygons}
            density_excess = {polygon.index: 0.0 for polygon in mesh.polygons}
            strict = frozenset()
            for face in span_extremes:
                worst[face] = math.inf
        else:
            for face in oversized_faces:
                worst[face] = math.inf
        best_candidate = current
        best_metrics = metrics
        best_values = snapshot_uvs(mesh)

        current_quality = refinement_quality_score(
            metrics, refinement_policy, rear_spine or fit_only
        )
        best_key = None

        def evaluate_options(
            values: Sequence[frozenset[int]],
            starting_rank: int,
        ) -> None:
            nonlocal best_key, best_candidate, best_metrics, best_values
            starting_values = snapshot_uvs(mesh)
            for option_rank, refined in enumerate(values, start=starting_rank):
                if non_disk_chart_seeds(mesh, edge_faces, refined):
                    continue
                restore_uvs(mesh, starting_values)
                trial_candidate = Candidate(
                    current.height_bins,
                    current.azimuth_bins,
                    current.height_half_phase,
                    current.azimuth_half_phase,
                    refined,
                    len(refined & manifold_edges),
                    current.refinement_ranks + (option_rank,),
                    current.refinement_edge_additions
                    + (tuple(sorted(refined - current.cut_edges)),),
                )
                trial_metrics = hybrid_unwrap(
                    mesh, edge_faces, trial_candidate, solver_policy
                )
                trial_quality = refinement_quality_score(
                    trial_metrics, refinement_policy, rear_spine or fit_only
                )
                if current.height_bins == 1 and current.azimuth_bins == 1:
                    trial_key = (
                        *trial_quality[:-1],
                        seam_visibility_cost(mesh, refined),
                        trial_quality[-1],
                        tuple(sorted(refined)),
                    )
                else:
                    trial_key = (*trial_quality, tuple(sorted(refined)))
                if (
                    trial_metrics.seam_percent <= MAX_SEAM_PERCENT + 1.0e-9
                    and trial_metrics.collapsed_faces == 0
                    and trial_metrics.flipped_faces == 0
                    and trial_metrics.single_face_indices
                    == trial_metrics.allowed_single_face_indices
                    and trial_metrics.islands == metrics.islands + 1
                    and trial_quality < current_quality
                    and (best_key is None or trial_key < best_key)
                ):
                    best_key = trial_key
                    best_candidate = trial_candidate
                    best_metrics = trial_metrics
                    best_values = snapshot_uvs(mesh)

        if fit_only:
            options = dual_geodesic_splits(
                mesh,
                edge_faces,
                current,
                worst,
                density_excess,
                strict,
                maximum_seams,
                PRIMARY_ACTUAL_TRIAL_LIMIT,
                fit_only=True,
            )
            evaluate_options(options, 1)
            if (
                best_candidate == current
                and not expanded_fit_used
                and model_name in DISCOVERY_EXPANDED_FIT_TRIAL_MODELS
                and rear_spine
            ):
                # These explicitly bounded rear-spine fit cases may inspect
                # ranks 5..12 once.  The same deterministic disk-safe ranking
                # supplies them, and the unchanged strict-improvement
                # predicate remains the only path that can commit one.
                restore_uvs(mesh, best_values)
                expanded = dual_geodesic_splits(
                    mesh,
                    edge_faces,
                    current,
                    worst,
                    density_excess,
                    strict,
                    maximum_seams,
                    EXPANDED_ACTUAL_TRIAL_LIMIT,
                    fit_only=True,
                )
                if expanded[: len(options)] != options:
                    raise ValueError(
                        "expanded fit trial ordering changed the primary prefix"
                    )
                evaluate_options(expanded[len(options) :], len(options) + 1)
                expanded_fit_exhausted_this_round = best_candidate == current
                expanded_fit_used = True
            if (
                best_candidate == current
                and (expanded_fit_exhausted_this_round or expanded_fit_used)
                and not rear_vertical_fit_used
                and model_name in DISCOVERY_REAR_VERTICAL_FIT_PATH_MODELS
                and rear_spine
            ):
                # The ranked face-level arcs are exhausted.  Try exactly one
                # rear-biased bottom-to-top manifold path, selected entirely
                # by topology and predicted fixed-density fit before Blender.
                restore_uvs(mesh, best_values)
                refined = rear_vertical_fit_path(
                    mesh, edge_faces, current, maximum_seams
                )
                if refined is not None:
                    addition = tuple(sorted(refined - current.cut_edges))
                    addition_hash = hashlib.sha256(
                        ",".join(str(edge) for edge in addition).encode("ascii")
                    ).hexdigest()
                    mesh["daedalon_rear_vertical_fit_diagnostic"] = (
                        f"candidate edges={len(addition)} sha256={addition_hash}"
                    )
                    evaluate_options((refined,), 1)
                    outcome = "committed" if best_candidate != current else "rejected"
                    outcome_metrics = (
                        best_metrics if best_candidate != current else metrics
                    )
                    mesh["daedalon_rear_vertical_fit_diagnostic"] = (
                        f"{outcome} edges={len(addition)} sha256={addition_hash} "
                        f"seams={outcome_metrics.seam_edges} "
                        f"islands={outcome_metrics.islands}"
                    )
                else:
                    mesh["daedalon_rear_vertical_fit_diagnostic"] = "no_candidate"
                rear_vertical_fit_used = True
        else:
            options = dual_geodesic_splits(
                mesh,
                edge_faces,
                current,
                worst,
                density_excess,
                strict,
                maximum_seams,
                PRIMARY_ACTUAL_TRIAL_LIMIT,
            )
            evaluate_options(options, 1)
            if (
                best_candidate == current
                and expanded_quality_trials_used
                < DISCOVERY_EXPANDED_QUALITY_TRIAL_BUDGET.get(model_name, 0)
                and current.height_bins == 5
                and current.azimuth_bins == 1
                and current.height_half_phase
                and not current.azimuth_half_phase
            ):
                # Phaeton has one previously proven high-genus basin.  Its
                # bounded continuation may inspect ranks 5..12 at three stuck
                # rounds in total (the established step plus two follow-ons),
                # committing at most one coherent arc each time.  All gates
                # and the strict-improvement predicate remain unchanged.
                restore_uvs(mesh, best_values)
                expanded = dual_geodesic_splits(
                    mesh,
                    edge_faces,
                    current,
                    worst,
                    density_excess,
                    strict,
                    maximum_seams,
                    EXPANDED_ACTUAL_TRIAL_LIMIT,
                )
                if expanded[: len(options)] != options:
                    raise ValueError(
                        "expanded quality trial ordering changed the primary prefix"
                )
                evaluate_options(expanded[len(options) :], len(options) + 1)
                expanded_quality_trials_used += 1
        restore_uvs(mesh, best_values)
        if best_candidate == current:
            break
        current = best_candidate
        metrics = best_metrics
    return current, metrics


def discover_unwrap(
    obj, edge_faces, seam_layout: str
) -> tuple[Candidate, UvMetrics, list[list[tuple[float, float]]]]:
    mesh = obj.data
    model_name = str(mesh.get("daedalon_model_name", ""))
    p95_stretch_limit = MODEL_MAX_P95_STRETCH.get(model_name, MAX_P95_STRETCH)
    candidates = build_candidates(mesh, edge_faces)
    if seam_layout == "rear-vertical":
        # A single rear spine opens each closed surface from bottom to top.
        # Keeping one height and one azimuth region forbids the horizontal
        # torso/neck bands produced by the earlier regional candidates.
        candidates = [
            candidate
            for candidate in candidates
            if candidate.height_bins == 1 and candidate.azimuth_bins == 1
        ]
    elif seam_layout != "any":
        raise ValueError(f"unsupported discovery seam layout {seam_layout}")
    if not candidates:
        raise ValueError(
            f"no {seam_layout} topology cut graph fits the seam budget"
        )
    best_rejection: tuple[float, Candidate, UvMetrics, str | None] | None = None
    for candidate in candidates:
        density_error: str | None = None
        candidate, metrics = unwrap_with_repairs(
            mesh,
            edge_faces,
            candidate,
            DISCOVERY_SOLVER_POLICY,
            DISCOVERY_REFINEMENT_POLICY,
        )
        if metrics.accepted(p95_stretch_limit):
            unnormalized = snapshot_uvs(mesh)
            try:
                normalize_uv_density(mesh, edge_faces)
            except ValueError as exception:
                restore_uvs(mesh, unnormalized)
                density_error = str(exception)
            else:
                metrics = measure_uv(mesh, edge_faces)
                if (
                    not metrics.accepted(p95_stretch_limit)
                    or not metrics.density_accepted()
                ):
                    restore_uvs(mesh, unnormalized)
                    density_error = (
                        "fixed-density audit failed: "
                        f"oob={metrics.out_of_bounds_uvs} "
                        f"oversized={metrics.oversized_islands} "
                        f"island_error={metrics.maximum_island_density_error:.9g}"
                    )
                else:
                    layer = mesh.uv_layers.active.data
                    selected = [
                        [
                            quantized_uv(layer[loop_index].uv)
                            for loop_index in polygon.loop_indices
                        ]
                        for polygon in mesh.polygons
                    ]
                    # Accepted practical visual bar: stop at the first fully
                    # serialized, fixed-density pass.  Discovery is not an
                    # aesthetic/minimum-seam optimizer.
                    return candidate, metrics, selected
        score = (
            max(0.0, metrics.seam_percent - MAX_SEAM_PERCENT) * 100.0
            + max(0.0, metrics.p95_stretch - MAX_P95_STRETCH) * 10.0
            + max(0.0, metrics.p99_stretch - MAX_P99_STRETCH)
            + max(0.0, metrics.density_ratio_p50 - MAX_DENSITY_RATIO_P50) * 10.0
            + max(0.0, metrics.density_ratio_p90 - MAX_DENSITY_RATIO_P90) * 10.0
            + max(0.0, metrics.density_ratio_p95 - MAX_DENSITY_RATIO_P95) * 10.0
            + max(0.0, metrics.density_ratio_p99 - MAX_DENSITY_RATIO_P99)
            + metrics.collapsed_faces * 1000.0
            + metrics.flipped_faces * 1000.0
        )
        if best_rejection is None or score < best_rejection[0]:
            best_rejection = (score, candidate, metrics, density_error)
    assert best_rejection is not None
    _score, candidate, metrics, density_error = best_rejection
    density_suffix = f" density={density_error}" if density_error else ""
    rear_fit = str(mesh.get("daedalon_rear_vertical_fit_diagnostic", ""))
    rear_fit_suffix = f" rear_fit={rear_fit}" if rear_fit else ""
    raise ValueError(
        "no candidate met UV acceptance; best "
        f"{candidate.label()} seams={metrics.seam_percent:.3f}% "
        f"p95={metrics.p95_stretch:.3f} p99={metrics.p99_stretch:.3f} "
        f"density={metrics.density_ratio_p50:.3f}/"
        f"{metrics.density_ratio_p90:.3f}/"
        f"{metrics.density_ratio_p95:.3f}/"
        f"{metrics.density_ratio_p99:.3f} "
        f"collapsed={metrics.collapsed_faces} flipped={metrics.flipped_faces}"
        f"{density_suffix}{rear_fit_suffix}"
    )


def replay_unwrap(
    obj,
    edge_faces: dict[int, list[int]],
    recipe: UvRecipe,
) -> tuple[Candidate, UvMetrics, list[list[tuple[float, float]]]]:
    """Replay one locked recipe without measuring any alternative unwrap."""
    mesh = obj.data
    manifold_edges = {
        edge_index for edge_index, incident in edge_faces.items() if len(incident) == 2
    }
    maximum_seams = math.floor(len(manifold_edges) * MAX_SEAM_PERCENT / 100.0)
    current = build_recipe_candidate(mesh, edge_faces, recipe)
    p95_stretch_limit = MODEL_MAX_P95_STRETCH.get(recipe.name, MAX_P95_STRETCH)
    metrics = hybrid_unwrap(mesh, edge_faces, current, recipe.solver_policy)

    for step, expected_rank in enumerate(recipe.refinement_ranks, start=1):
        rear_spine = current.height_bins == 1 and current.azimuth_bins == 1
        if recipe.refinement_edge_additions:
            addition = frozenset(recipe.refinement_edge_additions[step - 1])
            if addition & current.cut_edges or not addition <= manifold_edges:
                raise ValueError(
                    f"{recipe.name}: recipe refinement {step} has stale or "
                    "non-manifold locked seam edges"
                )
            refined = frozenset(set(current.cut_edges) | set(addition))
        else:
            worst, density_excess, strict = uv_face_quality(mesh, edge_faces)
            if recipe.refinement_policy == "conformal_v2":
                density_excess = {face: 0.0 for face in density_excess}
            elif recipe.refinement_policy != DISCOVERY_REFINEMENT_POLICY:
                raise ValueError(
                    f"{recipe.name}: unsupported refinement policy "
                    f"{recipe.refinement_policy}"
                )
            oversized_faces = fixed_density_oversized_faces(mesh, edge_faces)
            fit_only = (
                rear_spine
                and metrics.accepted(p95_stretch_limit)
                and bool(oversized_faces)
            )
            if fit_only:
                span_extremes = fixed_density_span_extreme_faces(mesh, edge_faces)
                worst = {polygon.index: 1.0 for polygon in mesh.polygons}
                density_excess = {polygon.index: 0.0 for polygon in mesh.polygons}
                strict = frozenset()
                for face in span_extremes:
                    worst[face] = math.inf
            else:
                for face in oversized_faces:
                    worst[face] = math.inf
            options = dual_geodesic_splits(
                mesh,
                edge_faces,
                current,
                worst,
                density_excess,
                strict,
                maximum_seams,
                expected_rank,
                fit_only=fit_only,
            )
            if len(options) < expected_rank:
                raise ValueError(
                    f"{recipe.name}: recipe refinement {step} requested disk-valid "
                    f"rank {expected_rank}, but only {len(options)} rank(s) remain"
                )
            refined = options[expected_rank - 1]
        if non_disk_chart_seeds(mesh, edge_faces, refined):
            raise ValueError(
                f"{recipe.name}: recipe refinement {step} no longer produces disks"
            )
        trial = Candidate(
            current.height_bins,
            current.azimuth_bins,
            current.height_half_phase,
            current.azimuth_half_phase,
            refined,
            len(refined & manifold_edges),
            current.refinement_ranks + (expected_rank,),
            current.refinement_edge_additions
            + (tuple(sorted(refined - current.cut_edges)),),
        )
        previous_quality = refinement_quality_score(
            metrics,
            recipe.refinement_policy,
            rear_spine,
        )
        previous_islands = metrics.islands
        trial_metrics = hybrid_unwrap(
            mesh, edge_faces, trial, recipe.solver_policy
        )
        trial_quality = refinement_quality_score(
            trial_metrics,
            recipe.refinement_policy,
            rear_spine,
        )
        if (
            trial_metrics.seam_percent > MAX_SEAM_PERCENT + 1.0e-9
            or trial_metrics.collapsed_faces != 0
            or trial_metrics.flipped_faces != 0
            or trial_metrics.single_face_indices
            != trial_metrics.allowed_single_face_indices
            or trial_metrics.islands != previous_islands + 1
            # Exact seam additions are the replay contract.  Their final
            # locked envelope is authoritative; do not re-run discovery's
            # intermediate aesthetic ranking during replay.
            or (
                not recipe.refinement_edge_additions
                and not rear_spine
                and trial_quality >= previous_quality
            )
        ):
            raise ValueError(
                f"{recipe.name}: recipe refinement {step} rank {expected_rank} "
                "no longer gives one improving, disk-safe chart split"
            )
        current = trial
        metrics = trial_metrics

    if not metrics.accepted(p95_stretch_limit) or metrics.oversized_islands != 0:
        raise ValueError(
            f"{recipe.name}: replayed recipe misses pre-normalization gates "
            f"seams={metrics.seam_percent:.6f}% "
            f"p95={metrics.p95_stretch:.6f} p99={metrics.p99_stretch:.6f} "
            f"density={metrics.density_ratio_p50:.6f}/"
            f"{metrics.density_ratio_p90:.6f}/"
            f"{metrics.density_ratio_p95:.6f}/"
            f"{metrics.density_ratio_p99:.6f} "
            f"oversized={metrics.oversized_islands}"
        )
    normalize_uv_density(mesh, edge_faces)
    metrics = measure_uv(mesh, edge_faces)
    if (
        not metrics.accepted(p95_stretch_limit)
        or not metrics.density_accepted()
    ):
        raise ValueError(
            f"{recipe.name}: replayed recipe misses fixed-density gates "
            f"oob={metrics.out_of_bounds_uvs} oversized={metrics.oversized_islands} "
            f"island_error={metrics.maximum_island_density_error:.9g}"
        )
    validate_recipe_metrics(recipe, current, metrics)
    layer = mesh.uv_layers.active
    if layer is None:
        raise ValueError(f"{recipe.name}: Blender produced no UV layer")
    selected = [
        [quantized_uv(layer.data[loop_index].uv) for loop_index in polygon.loop_indices]
        for polygon in mesh.polygons
    ]
    return current, metrics, selected


def validate_recipe_metrics(
    recipe: UvRecipe,
    candidate: Candidate,
    metrics: UvMetrics,
) -> None:
    failures: list[str] = []
    if candidate.label() != recipe.base_label():
        failures.append(f"base={candidate.label()} expected={recipe.base_label()}")
    if candidate.refinement_ranks != recipe.refinement_ranks:
        failures.append(
            f"ranks={candidate.refinement_ranks} expected={recipe.refinement_ranks}"
        )
    if metrics.seam_edges != recipe.expected_seam_edges:
        failures.append(
            f"seams={metrics.seam_edges} expected={recipe.expected_seam_edges}"
        )
    if metrics.island_face_counts != recipe.expected_island_face_counts:
        failures.append(
            f"islands={metrics.island_face_counts} "
            f"expected={recipe.expected_island_face_counts}"
        )
    if metrics.single_face_indices != recipe.expected_single_face_indices:
        failures.append(
            f"singletons={metrics.single_face_indices} "
            f"expected={recipe.expected_single_face_indices}"
        )
    if metrics.p95_stretch > recipe.expected_p95_stretch_max + 1.0e-9:
        failures.append(
            f"p95={metrics.p95_stretch:.9g} "
            f"max={recipe.expected_p95_stretch_max:.9g}"
        )
    if metrics.p99_stretch > recipe.expected_p99_stretch_max + 1.0e-9:
        failures.append(
            f"p99={metrics.p99_stretch:.9g} "
            f"max={recipe.expected_p99_stretch_max:.9g}"
        )
    actual_density = (
        metrics.density_ratio_p50,
        metrics.density_ratio_p90,
        metrics.density_ratio_p95,
        metrics.density_ratio_p99,
    )
    for label, actual, expected in zip(
        ("p50", "p90", "p95", "p99"),
        actual_density,
        recipe.expected_density_ratio_max,
        strict=True,
    ):
        if actual > expected + 1.0e-9:
            failures.append(f"density_{label}={actual:.9g} max={expected:.9g}")
    if failures:
        raise ValueError(
            f"{recipe.name}: replay result no longer matches its locked envelope: "
            + "; ".join(failures)
        )


def format_uv(value: float) -> str:
    if abs(value) < 0.5 * 10 ** (-SERIALIZED_DECIMALS):
        value = 0.0
    return f"{value:.{SERIALIZED_DECIMALS}f}"


def float32(value: float) -> float:
    return struct.unpack("!f", struct.pack("!f", value))[0]


def cyclic_orientation_parity(reference: Sequence[int], candidate: Sequence[int]) -> int:
    if len(reference) != len(candidate):
        raise ValueError("duplicate face corner count changed")
    size = len(reference)
    for offset in range(size):
        if all(candidate[index] == reference[(index + offset) % size] for index in range(size)):
            return 1
        if all(candidate[index] == reference[(offset - index) % size] for index in range(size)):
            return -1
    raise ValueError("duplicate face is neither cyclic nor reversed")


def audit_serialized_uvs(
    source: SourceObj,
    source_to_solver: Sequence[int],
    per_face_uvs: Sequence[Sequence[tuple[float, float]]],
    allowed_single_face_indices: Sequence[int],
) -> SerializedAudit:
    """Audit the exact Java float32 UV values for every original source face."""
    runtime_uvs: list[list[tuple[float, float]]] = []
    unique_runtime_uvs: set[tuple[float, float]] = set()
    for face_uv in per_face_uvs:
        values: list[tuple[float, float]] = []
        for u, v in face_uv:
            parsed_u = float32(float(format_uv(u)))
            parsed_v = float32(float(format_uv(1.0 - v)))
            runtime = (parsed_u, float32(1.0 - parsed_v))
            values.append(runtime)
            unique_runtime_uvs.add(runtime)
        runtime_uvs.append(values)
    nonfinite = sum(
        not math.isfinite(u) or not math.isfinite(v) for u, v in unique_runtime_uvs
    )
    out_of_bounds = sum(
        math.isfinite(u)
        and math.isfinite(v)
        and (u < 0.0 or u > 1.0 or v < 0.0 or v > 1.0)
        for u, v in unique_runtime_uvs
    )

    solver_count = max(source_to_solver, default=-1) + 1
    retained_source: list[int | None] = [None] * solver_count
    for source_face, solver_face in enumerate(source_to_solver):
        if retained_source[solver_face] is None:
            retained_source[solver_face] = source_face
    if any(index is None for index in retained_source):
        raise ValueError(f"{source.path}: incomplete solver/source face mapping")
    retained = [int(index) for index in retained_source]

    spans = [
        max(vertex[axis] for vertex in source.vertices)
        - min(vertex[axis] for vertex in source.vertices)
        for axis in range(3)
    ]
    world_scale = 3.0 / max(spans)

    def triangle_world_area(indices: Sequence[int]) -> float:
        first, second, third = (source.vertices[index] for index in indices)
        edge_one = tuple(second[axis] - first[axis] for axis in range(3))
        edge_two = tuple(third[axis] - first[axis] for axis in range(3))
        cross = (
            edge_one[1] * edge_two[2] - edge_one[2] * edge_two[1],
            edge_one[2] * edge_two[0] - edge_one[0] * edge_two[2],
            edge_one[0] * edge_two[1] - edge_one[1] * edge_two[0],
        )
        return (
            0.5
            * math.sqrt(sum(value * value for value in cross))
            * world_scale
            * world_scale
        )

    def signed_uv_area(values: Sequence[tuple[float, float]]) -> float:
        return 0.5 * (
            (values[1][0] - values[0][0]) * (values[2][1] - values[0][1])
            - (values[1][1] - values[0][1]) * (values[2][0] - values[0][0])
        )

    collapsed = 0
    flipped = 0
    geometric_degenerate = 0
    for source_index, face in enumerate(source.faces):
        retained_index = retained[source_to_solver[source_index]]
        parity = cyclic_orientation_parity(
            source.faces[retained_index].positions,
            face.positions,
        )
        triangles = ((0, 1, 2),) if len(face.positions) == 3 else ((0, 1, 2), (0, 2, 3))
        face_collapsed = False
        face_flipped = False
        face_degenerate = False
        for corners in triangles:
            position_indices = [face.positions[index] for index in corners]
            world_area = triangle_world_area(position_indices)
            if world_area <= UV_COLLAPSE_EPSILON:
                face_degenerate = True
                continue
            area = signed_uv_area([runtime_uvs[source_index][index] for index in corners])
            if abs(area) <= UV_COLLAPSE_EPSILON:
                face_collapsed = True
            elif area * parity < -UV_COLLAPSE_EPSILON:
                face_flipped = True
        collapsed += int(face_collapsed)
        flipped += int(face_flipped)
        geometric_degenerate += int(face_degenerate)

    solver_uv_by_vertex: list[dict[int, tuple[float, float]]] = []
    edge_faces: dict[tuple[int, int], list[int]] = defaultdict(list)
    face_world_area: dict[int, float] = defaultdict(float)
    face_uv_area: dict[int, float] = defaultdict(float)
    density_samples: list[tuple[int, float, float]] = []
    for solver_face, source_index in enumerate(retained):
        face = source.faces[source_index]
        values = runtime_uvs[source_index]
        solver_uv_by_vertex.append(dict(zip(face.positions, values, strict=True)))
        for index, first in enumerate(face.positions):
            second = face.positions[(index + 1) % len(face.positions)]
            edge_faces[tuple(sorted((first, second)))].append(solver_face)
        triangles = ((0, 1, 2),) if len(face.positions) == 3 else ((0, 1, 2), (0, 2, 3))
        for corners in triangles:
            world_area = triangle_world_area([face.positions[index] for index in corners])
            uv_area = abs(signed_uv_area([values[index] for index in corners]))
            face_world_area[solver_face] += world_area
            face_uv_area[solver_face] += uv_area
            if world_area > 0.0 and uv_area > 0.0:
                ratio = math.sqrt(uv_area / world_area) / TARGET_UV_PER_WORLD_BLOCK
                density_samples.append(
                    (solver_face, max(ratio, 1.0 / ratio), world_area)
                )

    adjacency: list[list[int]] = [[] for _ in range(solver_count)]
    for edge, incident in edge_faces.items():
        if len(incident) != 2:
            continue
        first, second = incident
        if all(
            math.dist(solver_uv_by_vertex[first][vertex], solver_uv_by_vertex[second][vertex])
            <= UV_COMPARE_EPSILON
            for vertex in edge
        ):
            adjacency[first].append(second)
            adjacency[second].append(first)
    remaining = set(range(solver_count))
    islands: list[list[int]] = []
    while remaining:
        seed = min(remaining)
        remaining.remove(seed)
        queue = deque([seed])
        island = [seed]
        while queue:
            face = queue.popleft()
            for neighbour in adjacency[face]:
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    island.append(neighbour)
                    queue.append(neighbour)
        islands.append(island)
    singleton_faces = tuple(
        sorted(island[0] for island in islands if len(island) == 1)
    )
    allowed_singletons = tuple(sorted(int(index) for index in allowed_single_face_indices))

    density_exception_tolerances: dict[int, float] = {}
    source_topology = topology_sha256(source)
    for exception in SINGLE_FACE_EXCEPTIONS.get(source.path.stem, ()):
        tolerance = exception.get("float32_density_tolerance")
        if tolerance is None:
            continue
        if source_topology != exception["topology_sha256"]:
            raise ValueError(f"{source.path}: locked density-exception topology changed")
        solver_face = int(exception["solver_face"])
        if solver_face not in allowed_singletons:
            raise ValueError(f"{source.path}: density exception is not an allowed singleton")
        density_exception_tolerances[solver_face] = float(tolerance)

    maximum_density_error = 0.0
    maximum_regular_density_error = 0.0
    maximum_locked_density_error = 0.0
    locked_density_indices: list[int] = []
    density_violations: list[int] = []
    for island in islands:
        world_area = sum(face_world_area[face] for face in island)
        uv_area = sum(face_uv_area[face] for face in island)
        if world_area <= 0.0 or uv_area <= 0.0:
            error = math.inf
        else:
            ratio = math.sqrt(uv_area / world_area) / TARGET_UV_PER_WORLD_BLOCK
            error = max(ratio, 1.0 / ratio) - 1.0
        maximum_density_error = max(maximum_density_error, error)
        locked_face = (
            island[0]
            if len(island) == 1 and island[0] in density_exception_tolerances
            else None
        )
        if locked_face is None:
            limit = MAX_ISLAND_DENSITY_ERROR
            maximum_regular_density_error = max(maximum_regular_density_error, error)
        else:
            limit = density_exception_tolerances[locked_face]
            locked_density_indices.append(locked_face)
            maximum_locked_density_error = max(maximum_locked_density_error, error)
        if error > limit + 1.0e-12:
            density_violations.extend(island)

    regular_density_samples = [
        (ratio, weight)
        for face, ratio, weight in density_samples
        if face not in density_exception_tolerances
    ]

    return SerializedAudit(
        out_of_bounds_uvs=out_of_bounds,
        nonfinite_uvs=nonfinite,
        collapsed_faces=collapsed,
        flipped_faces=flipped,
        geometric_degenerate_faces=geometric_degenerate,
        island_face_counts=tuple(sorted(len(island) for island in islands)),
        single_face_indices=singleton_faces,
        allowed_single_face_indices=allowed_singletons,
        maximum_island_density_error=maximum_density_error,
        maximum_regular_island_density_error=maximum_regular_density_error,
        maximum_locked_micro_island_density_error=maximum_locked_density_error,
        locked_micro_island_density_indices=tuple(sorted(locked_density_indices)),
        density_limit_violation_indices=tuple(sorted(density_violations)),
        density_ratio_p50=weighted_percentile(regular_density_samples, 0.50),
        density_ratio_p90=weighted_percentile(regular_density_samples, 0.90),
        density_ratio_p95=weighted_percentile(regular_density_samples, 0.95),
        density_ratio_p99=weighted_percentile(regular_density_samples, 0.99),
    )


def render_obj(source: SourceObj, per_face_uvs: Sequence[Sequence[tuple[float, float]]]) -> tuple[str, int]:
    uv_by_key: dict[tuple[int, str, str], int] = {}
    uv_values: list[tuple[str, str]] = []
    face_uv_indices: list[tuple[int, ...]] = []
    for face, face_uv in zip(source.faces, per_face_uvs, strict=True):
        if len(face.references) != len(face_uv):
            raise ValueError(f"{source.path}: Blender changed face corner count")
        indices: list[int] = []
        for reference, uv in zip(face.references, face_uv, strict=True):
            u = format_uv(uv[0])
            # Definitions retain flip_v=true; inverse here preserves Blender's final V at runtime.
            v = format_uv(1.0 - uv[1])
            key = (reference.position_index, u, v)
            uv_index = uv_by_key.get(key)
            if uv_index is None:
                uv_values.append((u, v))
                uv_index = len(uv_values)
                uv_by_key[key] = uv_index
            indices.append(uv_index)
        face_uv_indices.append(tuple(indices))

    face_by_line = {
        face.source_line_index: (face, uv_indices)
        for face, uv_indices in zip(source.faces, face_uv_indices, strict=True)
    }
    first_face_line = min(face.source_line_index for face in source.faces)
    output: list[str] = []
    inserted = False
    for line_index, raw_line in enumerate(source.lines):
        stripped = raw_line.strip()
        if stripped.startswith(GENERATED_COMMENT_PREFIX) or stripped.startswith("vt "):
            continue
        if line_index == first_face_line and not inserted:
            output.append(
                f"{GENERATED_COMMENT_PREFIX} v{ALGORITHM_VERSION}; "
                f"Blender {'.'.join(str(value) for value in REQUIRED_BLENDER_VERSION)} "
                f"build {REQUIRED_BLENDER_BUILD_HASH} "
                f"seam<={MAX_SEAM_PERCENT:.1f}% p95<={MAX_P95_STRETCH:.1f} "
                f"p99<={MAX_P99_STRETCH:.1f} density="
                f"{MAX_DENSITY_RATIO_P50:.2f}/"
                f"{MAX_DENSITY_RATIO_P90:.2f}/"
                f"{MAX_DENSITY_RATIO_P95:.2f}/"
                f"{MAX_DENSITY_RATIO_P99:.2f}"
            )
            output.extend(f"vt {u} {v}" for u, v in uv_values)
            inserted = True
        face_entry = face_by_line.get(line_index)
        if face_entry is None:
            output.append(raw_line)
            continue
        face, uv_indices = face_entry
        references: list[str] = []
        for reference, uv_index in zip(face.references, uv_indices, strict=True):
            if reference.normal_token is None:
                references.append(f"{reference.position_token}/{uv_index}")
            else:
                references.append(
                    f"{reference.position_token}/{uv_index}/{reference.normal_token}"
                )
        suffix = f" {face.trailing_comment}" if face.trailing_comment else ""
        output.append(f"{face.leading_whitespace}f {' '.join(references)}{suffix}")
    result = source.newline.join(output)
    if source.trailing_newline:
        result += source.newline
    return result, len(uv_values)


def generate_model(
    path: Path,
    recipe: UvRecipe | None,
    discover: bool,
    discovery_seam_layout: str,
) -> GeneratedModel:
    assert bpy is not None
    source = parse_source(path)
    source_topology = topology_sha256(source)
    if discover:
        if recipe is not None:
            raise ValueError(f"{path}: discovery must not replay a recipe")
        mode = "discover"
        solver_policy = DISCOVERY_SOLVER_POLICY
        refinement_policy = DISCOVERY_REFINEMENT_POLICY
    else:
        if recipe is None:
            raise ValueError(
                f"{path}: no locked UV recipe; use --discover {path.stem} "
                "--dry-run to author one"
            )
        if source_topology != recipe.topology_sha256:
            raise ValueError(
                f"{path}: topology hash changed; expected {recipe.topology_sha256}, "
                f"found {source_topology}. Use --discover only after reviewing the "
                "geometry change."
            )
        mode = "replay"
        solver_policy = recipe.solver_policy
        refinement_policy = recipe.refinement_policy
    obj, source_to_solver = create_blender_object(source)
    try:
        _edge_by_key, edge_faces = edge_topology(obj.data)
        if discover:
            candidate, metrics, solver_face_uvs = discover_unwrap(
                obj, edge_faces, discovery_seam_layout
            )
        else:
            assert recipe is not None
            candidate, metrics, solver_face_uvs = replay_unwrap(
                obj, edge_faces, recipe
            )
        disk_failures = non_disk_chart_seeds(obj.data, edge_faces, candidate.cut_edges)
        if disk_failures:
            raise ValueError(
                f"{path}: accepted UV candidate contains non-disk charts {disk_failures}"
            )
        per_face_uvs: list[list[tuple[float, float]]] = []
        for source_face, solver_face_index in zip(source.faces, source_to_solver, strict=True):
            solver_polygon = obj.data.polygons[solver_face_index]
            by_vertex = dict(
                zip(solver_polygon.vertices, solver_face_uvs[solver_face_index], strict=True)
            )
            per_face_uvs.append(
                [by_vertex[reference.position_index] for reference in source_face.references]
            )
        serialized_audit = audit_serialized_uvs(
            source,
            source_to_solver,
            per_face_uvs,
            tuple(
                int(index)
                for index in obj.data.get("daedalon_allowed_single_face_indices", [])
            ),
        )
        if serialized_audit.island_face_counts != metrics.island_face_counts:
            raise ValueError(
                f"{path}: float32 serialization changed UV island topology "
                f"{metrics.island_face_counts} -> "
                f"{serialized_audit.island_face_counts}"
            )
        if not serialized_audit.accepted():
            raise ValueError(
                f"{path}: serialized float32 UV audit failed "
                f"oob={serialized_audit.out_of_bounds_uvs} "
                f"nonfinite={serialized_audit.nonfinite_uvs} "
                f"collapsed={serialized_audit.collapsed_faces} "
                f"flipped={serialized_audit.flipped_faces} "
                f"singletons={serialized_audit.single_face_indices}/"
                f"{serialized_audit.allowed_single_face_indices} "
                f"density_error={serialized_audit.maximum_island_density_error:.9g} "
                f"density_violations={serialized_audit.density_limit_violation_indices}"
            )
        if recipe is not None:
            serialized_density = (
                serialized_audit.density_ratio_p50,
                serialized_audit.density_ratio_p90,
                serialized_audit.density_ratio_p95,
                serialized_audit.density_ratio_p99,
            )
            density_failures = [
                f"{label}={actual:.9g} max={expected:.9g}"
                for label, actual, expected in zip(
                    ("p50", "p90", "p95", "p99"),
                    serialized_density,
                    recipe.expected_density_ratio_max,
                    strict=True,
                )
                if actual > expected + 1.0e-9
            ]
            if density_failures:
                raise ValueError(
                    f"{path}: serialized UVs exceed the locked recipe envelope: "
                    + "; ".join(density_failures)
                )
        text, uv_count = render_obj(source, per_face_uvs)
        generated = GeneratedModel(
            name=path.stem,
            text=text,
            source=source,
            candidate=candidate,
            metrics=metrics,
            uv_count=uv_count,
            topology_sha256=source_topology,
            non_disk_chart_seeds=disk_failures,
            serialized_audit=serialized_audit,
            mode=mode,
            solver_policy=solver_policy,
            refinement_policy=refinement_policy,
        )
        # Prove that serializing/reparsing preserves the exact geometry stream.
        temporary_source = parse_source_text_for_validation(source.path, text)
        if temporary_source.vertex_lines != source.vertex_lines:
            raise ValueError(f"{path}: generated output changed the vertex stream")
        if [face.positions for face in temporary_source.faces] != [
            face.positions for face in source.faces
        ]:
            raise ValueError(f"{path}: generated output changed face positions/order/winding")
        return generated
    finally:
        bpy.data.objects.remove(obj, do_unlink=True)


def parse_source_text_for_validation(path: Path, text: str) -> SourceObj:
    # Reuse the parser without leaving a temporary file anywhere.
    newline = "\r\n" if "\r\n" in text else "\n"
    lines = text.splitlines()
    vertices: list[tuple[float, float, float]] = []
    vertex_lines: list[str] = []
    faces: list[SourceFace] = []
    for line_index, raw_line in enumerate(lines):
        content, separator, comment = raw_line.partition("#")
        stripped = content.strip()
        if stripped.startswith("v "):
            vertex = tuple(float(value) for value in stripped.split()[1:4])
            vertices.append(vertex)
            vertex_lines.append(raw_line)
        elif stripped.startswith("f "):
            references: list[FaceRef] = []
            for token in stripped.split()[1:]:
                fields = token.split("/")
                raw_position = int(fields[0])
                resolved = raw_position - 1 if raw_position > 0 else len(vertices) + raw_position
                normal = fields[2] if len(fields) == 3 and fields[2] else None
                references.append(FaceRef(fields[0], resolved, normal))
            leading = content[: len(content) - len(content.lstrip())]
            trailing = f"#{comment}" if separator else ""
            faces.append(SourceFace(tuple(references), line_index, leading, trailing))
    return SourceObj(
        path,
        tuple(lines),
        newline,
        text.endswith(("\n", "\r")),
        tuple(vertices),
        tuple(vertex_lines),
        tuple(faces),
    )


def selected_models(values: Sequence[str]) -> tuple[str, ...]:
    if not values or values == ["all"]:
        return MODEL_NAMES
    unknown = sorted(set(values) - set(MODEL_NAMES))
    if unknown:
        raise ValueError("unknown model(s): " + ", ".join(unknown))
    return tuple(dict.fromkeys(values))


def blender_arguments() -> list[str]:
    if "--" not in sys.argv:
        return []
    return sys.argv[sys.argv.index("--") + 1 :]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("models", nargs="*", default=["all"])
    parser.add_argument(
        "--mesh-dir",
        type=Path,
        default=Path(__file__).resolve().parents[1]
        / "src/main/resources/assets/daedalon/models/mesh",
    )
    parser.add_argument("--blender", type=Path, default=None)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--write", action="store_true")
    action.add_argument("--check", action="store_true")
    action.add_argument("--dry-run", action="store_true")
    parser.add_argument(
        "--discover",
        action="store_true",
        help="expensively search explicit model names; allowed only with --dry-run",
    )
    parser.add_argument(
        "--seam-layout",
        choices=("any", "rear-vertical"),
        default="any",
        help=(
            "discovery candidate family; rear-vertical forbids horizontal "
            "height bands and aligns an azimuth boundary with the model back"
        ),
    )
    parser.add_argument("--json", action="store_true")
    return parser


def invocation_context(
    args: argparse.Namespace,
) -> tuple[tuple[str, ...], dict[str, UvRecipe]]:
    models = selected_models(args.models)
    if args.discover:
        if not args.dry_run:
            raise ValueError("--discover is an authoring diagnostic and requires --dry-run")
        if not args.models or args.models == ["all"] or "all" in args.models:
            raise ValueError("--discover requires one or more explicit model names")
        return models, {}

    if args.seam_layout != "any":
        raise ValueError("--seam-layout is only valid with --discover")

    recipes = load_recipe_manifest()
    if args.check and (not args.models or args.models == ["all"]):
        # `all --check` means every authored replay recipe.  Models that
        # deliberately retained the runtime projection fallback remain
        # explicit discovery targets.  `all --write` continues to fail so it
        # cannot silently imply that every source mesh has authored UVs.
        models = tuple(name for name in MODEL_NAMES if name in recipes)
    missing = [name for name in models if name not in recipes]
    if missing:
        raise ValueError(
            "no locked UV recipe for: "
            + ", ".join(missing)
            + "; author each explicitly with MODEL --discover --dry-run"
        )
    # Fail before launching Blender if the geometry no longer matches the
    # checked-in recipe.  Blender repeats this check before any unwrap.
    for name in models:
        source = parse_source(args.mesh_dir / f"{name}.obj")
        actual = topology_sha256(source)
        expected = recipes[name].topology_sha256
        if actual != expected:
            raise ValueError(
                f"{name}: topology hash changed; expected {expected}, found {actual}"
            )
    return models, recipes


def launch_blender(args: argparse.Namespace, original_argv: Sequence[str]) -> int:
    blender = args.blender or Path(os.environ.get("DAEDALON_BLENDER", DEFAULT_BLENDER))
    if not blender.is_file():
        raise SystemExit(
            f"Blender 5.2 was not found at {blender}; pass --blender or set DAEDALON_BLENDER"
        )
    forwarded = [argument for argument in original_argv if argument != "--blender"]
    if "--blender" in original_argv:
        index = forwarded.index(str(args.blender)) if str(args.blender) in forwarded else -1
        if index >= 0:
            forwarded.pop(index)
    command = [
        str(blender),
        "--background",
        "--factory-startup",
        "--python-exit-code",
        "1",
        "--python",
        str(Path(__file__).resolve()),
        "--",
        *forwarded,
    ]
    return subprocess.run(command, check=False).returncode


def blender_main(argv: Sequence[str]) -> int:
    assert bpy is not None
    build_hash = bpy.app.build_hash.decode("ascii")
    if (
        tuple(bpy.app.version) != REQUIRED_BLENDER_VERSION
        or build_hash != REQUIRED_BLENDER_BUILD_HASH
    ):
        raise SystemExit(
            "UV generation is locked to Blender "
            f"{'.'.join(str(value) for value in REQUIRED_BLENDER_VERSION)} "
            f"build {REQUIRED_BLENDER_BUILD_HASH}; found "
            f"{bpy.app.version_string} build {build_hash}"
        )
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        models, recipes = invocation_context(args)
    except ValueError as exception:
        parser.error(str(exception))

    changed: list[str] = []
    failures: list[tuple[str, str]] = []
    reports: list[dict[str, object]] = []
    for name in models:
        path = args.mesh_dir / f"{name}.obj"
        try:
            generated = generate_model(
                path,
                recipes.get(name),
                args.discover,
                args.seam_layout,
            )
        except (OSError, ValueError) as exception:
            failures.append((name, str(exception)))
            if args.json:
                print(
                    "UV_ERROR "
                    + json.dumps({"model": name, "error": str(exception)}, sort_keys=True),
                    flush=True,
                )
            else:
                print(f"{name}: ERROR {exception}", file=sys.stderr, flush=True)
            continue
        report = generated.report()
        reports.append(report)
        if args.json:
            print("UV_METRICS " + json.dumps(report, sort_keys=True), flush=True)
        else:
            print(
                f"{name}: seams={report['seam_edges']}/{report['manifold_edges']} "
                f"({report['seam_percent']}%) p95={report['p95_stretch']} "
                f"p99={report['p99_stretch']} islands={report['islands']} "
                f"uvs={report['uvs']} {report['candidate']}",
                flush=True,
            )
        current = path.read_text(encoding="utf-8", newline="")
        if current != generated.text:
            changed.append(name)
            if args.write:
                path.write_text(generated.text, encoding="utf-8", newline="")
    if args.json:
        print(
            "UV_SUMMARY "
            + json.dumps(
                {
                    "reports": reports,
                    "failures": [
                        {"model": name, "error": error} for name, error in failures
                    ],
                },
                sort_keys=True,
            ),
            flush=True,
        )
    if args.check and changed:
        print("Generated statue UVs are stale: " + ", ".join(changed), file=sys.stderr)
        return 1
    if failures:
        print(
            "Statue UV generation failed: " + ", ".join(name for name, _error in failures),
            file=sys.stderr,
        )
        return 1
    if args.write:
        print(f"Generated statue UVs: changed={len(changed)}", flush=True)
    return 0


def main() -> int:
    if bpy is None:
        parser = build_parser()
        args = parser.parse_args(sys.argv[1:])
        try:
            invocation_context(args)
        except (OSError, ValueError) as exception:
            parser.error(str(exception))
        return launch_blender(args, sys.argv[1:])
    return blender_main(blender_arguments())


if __name__ == "__main__":
    raise SystemExit(main())
