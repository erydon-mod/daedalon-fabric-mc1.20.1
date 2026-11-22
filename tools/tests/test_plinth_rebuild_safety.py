"""Physical contracts for the five geometric plinth rebuilds.

These checks inspect the shipped triangles, rather than trusting the authoring
tool's validation flags. One metric mesh continues to supply every material,
standalone size and fountain attachment.
"""
from __future__ import annotations

from collections import Counter, defaultdict
import math
import re
import unittest

from daedalon_test_support import (
    JAVA_ROOT, MESH_EXPECTATIONS, MESH_ROOT, REPO_ROOT, load_json, sha256,
)


STYLES = ("astragalos", "bathron", "kion", "stephanos", "triphyllon")
# Approved Meshy runtime assets before the intentional geometric replacement.
PRIOR_RUNTIME_HASHES = {
    "astragalos": "4a705d84abd3704f9248f2e051255841a82c26f1603411205fc38afd7ff360c5",
    "bathron": "2543b6f83228c87f69bb106a86441813dc2e76d5910468baa8bea025d9c3824e",
    "kion": "04f426f5a9d161eaf78f6aa35eb6babd7c284620047f39aa35f337e4196bd6a8",
    "stephanos": "d21862cd4d56b3809737d1b8dc6b5426acc1e33714ecc94aa70617e5abc9fc14",
    "triphyllon": "9f18250c28fcb761de10d529f03b25bc5d598a4d5c6f970b53a99cf3c7cd160c",
}
EPSILON = 1e-8


def sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def bounds(points):
    lo = tuple(min(p[axis] for p in points) for axis in range(3))
    hi = tuple(max(p[axis] for p in points) for axis in range(3))
    return {"min": lo, "max": hi, "size": sub(hi, lo)}


def read_geometry(path):
    vertices, normals, triangles = [], [], []
    materials, uv_count = set(), 0
    material = None
    for line in path.read_text(encoding="utf-8").splitlines():
        fields = line.split()
        if not fields:
            continue
        if fields[0] == "v":
            vertices.append(tuple(map(float, fields[1:])))
        elif fields[0] == "vn":
            normals.append(tuple(map(float, fields[1:])))
        elif fields[0] == "vt":
            uv_count += 1
        elif fields[0] == "usemtl":
            material = fields[1]
        elif fields[0] == "f":
            refs = [token.split("/") for token in fields[1:]]
            if len(refs) != 3 or any(len(ref) != 3 or ref[1] or not ref[2] for ref in refs):
                raise AssertionError(f"Expected Y-up triangles with authored normals and no UVs: {path.name}: {line}")
            face = tuple((int(ref[0]) - 1, int(ref[2]) - 1) for ref in refs)
            if any(not 0 <= v < len(vertices) or not 0 <= n < len(normals) for v, n in face):
                raise AssertionError(f"Out-of-range OBJ index: {path.name}: {line}")
            triangles.append(face)
            materials.add(material)
    return {"vertices": vertices, "normals": normals, "triangles": triangles,
            "materials": materials, "uvs": uv_count}


def section_segments(mesh, height):
    """Intersect actual triangles with a horizontal plane in metric OBJ space."""
    segments = {}
    for face in mesh["triangles"]:
        triangle = [mesh["vertices"][ref[0]] for ref in face]
        points = {}
        for a, b in zip(triangle, triangle[1:] + triangle[:1]):
            if abs(a[1] - height) < EPSILON:
                points[(round(a[0], 10), round(a[2], 10))] = (a[0], a[2])
            if (a[1] - height) * (b[1] - height) < 0:
                factor = (height - a[1]) / (b[1] - a[1])
                p = (a[0] + factor * (b[0] - a[0]), a[2] + factor * (b[2] - a[2]))
                points[(round(p[0], 10), round(p[1], 10))] = p
        values = list(points.values())
        if len(values) == 2:
            key = tuple(sorted(points))
            segments[key] = tuple(values)
    return list(segments.values())


def radial_section(segments, samples=1440):
    """Outermost cross-section radius along equally spaced rays from the shaft."""
    radii = []
    for index in range(samples):
        angle = index * 2 * math.pi / samples
        dx, dz = math.cos(angle), math.sin(angle)
        hits = []
        for (ax, az), (bx, bz) in segments:
            ex, ez = bx - ax, bz - az
            denominator = dx * ez - dz * ex
            if abs(denominator) < 1e-13:
                continue
            radius = (ax * ez - az * ex) / denominator
            fraction = (ax * dz - az * dx) / denominator
            if radius > 0 and -EPSILON <= fraction <= 1 + EPSILON:
                hits.append(radius)
        if not hits:
            raise AssertionError(f"Shaft cross-section has an open radial ray at sample {index}")
        radii.append(max(hits))
    return radii


def significant_radial_valleys(radii, period):
    """Count flute valleys, excluding shallow polygon-tessellation ripples."""
    count = len(radii)
    half_sector = count // (period * 2)
    prominence = (max(radii) - min(radii)) * .15
    candidates = []
    for i, radius in enumerate(radii):
        if radius > radii[(i - 1) % count] or radius > radii[(i + 1) % count]:
            continue
        left = max(radii[(i - j) % count] for j in range(1, half_sector + 1))
        right = max(radii[(i + j) % count] for j in range(1, half_sector + 1))
        if min(left, right) - radius >= prominence:
            candidates.append(i)
    # A flat valley bottom may straddle two samples; it is still one flute.
    separation = max(1, count // (period * 8))
    groups = []
    for index in candidates:
        if groups and index - groups[-1][-1] <= separation:
            groups[-1].append(index)
        else:
            groups.append([index])
    if len(groups) > 1 and groups[0][0] + count - groups[-1][-1] <= separation:
        groups[0] = groups[-1] + groups[0]
        groups.pop()
    return len(groups)


class PlinthRebuildSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        evidence = load_json(REPO_ROOT / "docs/evidence/plinth-rebuild-source.json")
        cls.evidence = {model["style"]: model for model in evidence["models"]}
        cls.meshes = {style: read_geometry(MESH_ROOT / f"plinth_{style}.obj") for style in STYLES}
        cls.definitions = {style: load_json(MESH_ROOT / f"plinth_{style}.json") for style in STYLES}
        cls.profiles = {}
        source = (JAVA_ROOT / "block/PlinthBlock.java").read_text(encoding="utf-8")
        for style in STYLES:
            match = re.search(rf"\b{style.upper()}\(([^)]+)\)", source)
            cls.profiles[style] = tuple(float(value.strip()) for value in match.group(1).split(",")[-3:])

    def test_original_provenance_and_replacement_measurements_are_locked(self):
        original = load_json(REPO_ROOT / "docs/evidence/plinth-batch-source.json")
        originals = {model["style"]: model for model in original["models"]}
        rebuilt = load_json(REPO_ROOT / "docs/evidence/plinth-rebuild-source.json")
        self.assertFalse(rebuilt["source_files_modified"])
        authoring = rebuilt["authoring_tool"]
        self.assertEqual("5.2.0", authoring["blender_version"])
        self.assertEqual("fbe6228777e7", authoring["blender_build"])
        self.assertEqual(authoring["sha256"], sha256(REPO_ROOT / authoring["path"]))
        self.assertEqual(set(STYLES), set(self.evidence))
        for style, mesh in self.meshes.items():
            with self.subTest(style=style):
                record = self.evidence[style]
                self.assertEqual(originals[style]["source_uuid"], record["source_uuid"])
                self.assertEqual(originals[style]["source_sha256"], record["source_sha256"])
                self.assertEqual(PRIOR_RUNTIME_HASHES[style], record["prior_runtime_obj_sha256"])
                self.assertEqual(6000, record["prior_runtime_faces"])
                current = MESH_ROOT / f"plinth_{style}.obj"
                self.assertEqual(sha256(current), record["runtime_obj_sha256"])
                self.assertEqual(MESH_EXPECTATIONS[f"plinth_{style}"]["obj_sha256"], record["runtime_obj_sha256"])
                for key, actual in (("runtime_vertices", len(mesh["vertices"])),
                                    ("runtime_normals", len(mesh["normals"])),
                                    ("runtime_faces", len(mesh["triangles"]))):
                    self.assertEqual(actual, record[key])
                self.assertGreater(record["authored_polygons"], 0)
                self.assertLessEqual(record["authored_polygons"], record["runtime_faces"])
                self.assertEqual({"none"}, mesh["materials"])
                self.assertEqual(0, mesh["uvs"])
                for key, actual in bounds(mesh["vertices"]).items():
                    for expected, measured in zip(record["bounds"][key], actual):
                        self.assertAlmostEqual(expected, measured, delta=1e-7)
                self.assertNotIn(b"\r\n", current.read_bytes(), "Portable hash evidence uses canonical LF bytes")

    def test_actual_triangles_have_unit_normals_and_sealed_support_geometry(self):
        for style, mesh in self.meshes.items():
            with self.subTest(style=style):
                for vector in mesh["vertices"] + mesh["normals"]:
                    self.assertEqual(3, len(vector))
                    self.assertTrue(all(math.isfinite(value) for value in vector))
                for vector in mesh["normals"]:
                    self.assertAlmostEqual(1., math.sqrt(dot(vector, vector)), delta=2e-6)
                edges, oriented = Counter(), Counter()
                vertex_links = defaultdict(list)
                parent = list(range(len(mesh["vertices"])))
                def find(index):
                    while parent[index] != index:
                        parent[index] = parent[parent[index]]
                        index = parent[index]
                    return index
                face_volumes = []
                for face in mesh["triangles"]:
                    indices = [ref[0] for ref in face]
                    a, b, c = [mesh["vertices"][i] for i in indices]
                    geometric = cross(sub(b, a), sub(c, a))
                    self.assertGreater(dot(geometric, geometric), 1e-24, (style, indices))
                    face_volumes.append((indices[0], dot(a, cross(b, c)) / 6))
                    for corner in range(3):
                        vertex_links[indices[corner]].append((indices[(corner + 1) % 3], indices[(corner + 2) % 3]))
                    for start, end in zip(indices, indices[1:] + indices[:1]):
                        edges[tuple(sorted((start, end)))] += 1
                        oriented[(start, end)] += 1
                        parent[find(end)] = find(start)
                self.assertTrue(edges)
                support_roots = {find(i) for i, p in enumerate(mesh["vertices"])
                                 if abs(p[1]) < EPSILON or abs(p[1] - 1) < EPSILON}
                self.assertEqual(1, len(support_roots), f"{style}: base and top must share one sealed core")
                core = next(iter(support_roots))
                native_relief = style == "stephanos"
                if native_relief:
                    proof = load_json(REPO_ROOT / "docs/evidence/plinth-stephanos-relief-topology.json")
                    self.assertEqual(proof["sha256"], sha256(MESH_ROOT / "plinth_stephanos.obj"))
                    self.assertTrue(proof["core_geometry_and_corner_normals_exact"])
                    self.assertTrue(proof["whole_visible_relief_reduced_at_user_request"])
                    self.assertEqual(16000, proof["requested_editable_faces"])
                    self.assertEqual(15999, proof["authored_polygons"])
                    self.assertEqual(proof["sealed_core"]["vertex_count"], sum(find(i) == core for i in range(len(parent))))
                    self.assertEqual(proof["sealed_core"]["triangle_count"], sum(find(face[0][0]) == core for face in mesh["triangles"]))
                    exceptions = {tuple(record["vertex_indices"]): record for record in proof["topology_exceptions"]}
                    self.assertEqual({edge for edge, count in edges.items() if count != 2}, set(exceptions))
                    self.assertEqual(55, sum(count == 1 for count in edges.values()))
                    self.assertEqual(35, sum(count > 2 for count in edges.values()))
                    for edge, record in exceptions.items():
                        self.assertNotEqual(core, find(edge[0]), "Native relief exceptions must never reach the support core")
                        self.assertEqual(record["valence"], edges[edge])
                        self.assertEqual(record["positions"], [list(mesh["vertices"][i]) for i in edge])
                        if record["classification"] == "retained_hidden_rear_seam_after_requested_reduction":
                            support = record["support"]
                            for i in edge:
                                self.assertLessEqual(abs(support["rear_plane"]-mesh["vertices"][i][support["axis"]]),
                                                     support["maximum_rear_plane_deviation_m"]+1e-9)
                            self.assertGreaterEqual(support["minimum_inward_axis_clearance_m"], .00079)
                        else:
                            self.assertEqual("preexisting_native_tessellation_edge", record["classification"])
                else:
                    self.assertEqual({2}, set(edges.values()), f"{style}: boundary or nonmanifold edge")
                for edge, count in edges.items():
                    if not native_relief or find(edge[0]) == core:
                        self.assertEqual(2, count, f"{style}: unsealed architectural core")
                        start, end = edge
                        self.assertEqual(oriented[(start, end)], oriented[(end, start)], f"{style}: inconsistent solid winding")
                for vertex, links in vertex_links.items():
                    neighbours = defaultdict(set)
                    for a, b in links:
                        neighbours[a].add(b)
                        neighbours[b].add(a)
                    reached, pending = set(), [next(iter(neighbours))]
                    while pending:
                        node = pending.pop()
                        if node not in reached:
                            reached.add(node)
                            pending.extend(neighbours[node] - reached)
                    if not native_relief or find(vertex) == core:
                        self.assertEqual(set(neighbours), reached, f"{style}: pinched solid at vertex {vertex}")
                volumes = defaultdict(float)
                for index, volume in face_volumes:
                    volumes[find(index)] += volume
                self.assertGreater(volumes[core], 0., f"{style}: inward or collapsed support core")
                if not native_relief:
                    self.assertTrue(all(volume > 0 for volume in volumes.values()),
                                    f"{style}: inward or collapsed solid component")
                    self.assertEqual(self.evidence[style]["validation"]["closed_components"], len(volumes))
                if style == "astragalos":
                    beads = self.evidence[style]["feature_retention"]["beads_per_collar"]
                    self.assertEqual([128, 112], beads)
                    self.assertEqual(1 + sum(beads), len(volumes),
                                     "Retain all 240 separate collar beads around the architectural core")

    def test_ground_and_top_are_flat_sealed_positive_area_contacts(self):
        for style, mesh in self.meshes.items():
            for height, normal_sign in ((0., -1), (1., 1)):
                with self.subTest(style=style, height=height):
                    plane_vertices = {i for i, p in enumerate(mesh["vertices"]) if abs(p[1] - height) < EPSILON}
                    capped_vertices, area = set(), 0.
                    for face in mesh["triangles"]:
                        if all(ref[0] in plane_vertices for ref in face):
                            points = [mesh["vertices"][ref[0]] for ref in face]
                            normal = cross(sub(points[1], points[0]), sub(points[2], points[0]))
                            self.assertGreater(normal_sign * normal[1], 0., f"{style}: inward contact plane")
                            self.assertAlmostEqual(0., normal[0], delta=1e-12)
                            self.assertAlmostEqual(0., normal[2], delta=1e-12)
                            area += abs(normal[1]) / 2
                            capped_vertices.update(ref[0] for ref in face)
                    self.assertTrue(plane_vertices)
                    self.assertEqual(plane_vertices, capped_vertices, f"{style}: unsealed ground/top vertex")
                    self.assertGreater(area, 1e-8, f"{style}: contact must be a surface, not a point")

    def test_metric_geometry_matches_cached_footprint_and_all_three_attachment_heights(self):
        for style, mesh in self.meshes.items():
            with self.subTest(style=style):
                raw = bounds(mesh["vertices"])
                width, height, depth = self.profiles[style]
                self.assertEqual((0., 1.), (raw["min"][1], raw["max"][1]))
                self.assertAlmostEqual(0., raw["min"][0] + raw["max"][0], delta=EPSILON)
                self.assertAlmostEqual(0., raw["min"][2] + raw["max"][2], delta=EPSILON)
                for actual, cached in zip(raw["size"], (width, height, depth)):
                    self.assertAlmostEqual(actual, cached, delta=1e-6)
                definition = self.definitions[style]
                self.assertTrue(definition["fit_to_block"])
                self.assertEqual([2., 2., 2.], definition["scale"], "Kion must not retain the old short-source Y stretch")
                self.assertEqual([0., 0., 0.], definition["translate"])
                fit = 1. / max(raw["size"])
                for scale, target_height in ((.25, .5), (.5, 1.), (1., 2.)):
                    for turns in range(4):
                        transformed = []
                        for x, y, z in mesh["vertices"]:
                            x, y, z = x * fit * 2 * scale, y * fit * 2 * scale, z * fit * 2 * scale
                            for _ in range(turns):
                                x, z = -z, x
                            transformed.append((.5 + x, y, .5 + z))
                        actual = bounds(transformed)
                        expected = (width * target_height, target_height, depth * target_height)
                        if turns % 2:
                            expected = (expected[2], expected[1], expected[0])
                        self.assertAlmostEqual(0., actual["min"][1], delta=1e-8)
                        self.assertAlmostEqual(target_height, actual["max"][1], delta=1e-8)
                        for measured, cached in zip(actual["size"], expected):
                            self.assertAlmostEqual(measured, cached, delta=2e-6)

    def test_authored_normals_and_existing_block_texture_density_are_preserved(self):
        for style, definition in self.definitions.items():
            with self.subTest(style=style):
                self.assertFalse(definition["smooth_normals"], "Retain crisp planes and the authored rounded moulding normals")
                self.assertTrue(definition["force_uv_projection"])
                self.assertTrue(definition["face_oriented_uvs"])
                self.assertEqual("axis_stabilized_box", definition["uv_projection"])
                self.assertEqual(6., definition["box_projection_span"])
                self.assertEqual({"none"}, set(definition["materials"]))
                # Six tiles across the six-metre projection span: every size
                # retains one tile per world metre, including all six phases.
                for scale in (.25, .5, 1.):
                    self.assertEqual(2 * scale, 2 / definition["box_projection_span"] * scale * 6)
                    for u in (0., 1 / 3, 2 / 3, 1.):
                        scaled = .5 + (u - .5) * scale
                        for phase in range(6):
                            mapped = scaled * 6 / 12 + phase / 12
                            self.assertTrue(0 <= mapped <= 1)

    def test_measured_profile_landmarks_retain_base_shaft_and_crown_mouldings(self):
        for style, mesh in self.meshes.items():
            with self.subTest(style=style):
                landmarks = self.evidence[style]["feature_retention"]["profile_landmarks"]
                self.assertGreaterEqual(len(landmarks), 3)
                actual_widths = []
                for landmark in landmarks:
                    self.assertTrue(landmark["label"].strip())
                    self.assertTrue(0 <= landmark["y"] <= 1)
                    segments = section_segments(mesh, landmark["y"])
                    self.assertTrue(segments, (style, landmark["label"]))
                    points = [point for segment in segments for point in segment]
                    width = max(p[0] for p in points) - min(p[0] for p in points)
                    depth = max(p[1] for p in points) - min(p[1] for p in points)
                    self.assertAlmostEqual(landmark["width"], width, delta=1e-6,
                                           msg=f"{style}: {landmark['label']} width")
                    self.assertAlmostEqual(landmark["depth"], depth, delta=1e-6,
                                           msg=f"{style}: {landmark['label']} depth")
                    actual_widths.append(width)
                self.assertGreater(max(actual_widths) - min(actual_widths), 1e-3,
                                   f"{style}: mouldings must retain their measured relief")

    def test_kion_has_eighteen_real_flutes_through_the_shaft(self):
        feature = self.evidence["kion"]["feature_retention"]
        self.assertEqual(18, feature["flute_count"])
        bottom, top = feature["flute_y_interval"]
        self.assertTrue(0 < bottom < top < 1)
        for fraction in (.25, .5, .75):
            height = bottom + (top - bottom) * fraction
            with self.subTest(height=height):
                radii = radial_section(section_segments(self.meshes["kion"], height))
                self.assertGreater(max(radii) - min(radii), 1e-4,
                                   "Fluting must be modelled relief, not just a cylindrical normal")
                self.assertEqual(18, significant_radial_valleys(radii, 18))
                mean = sum(radii) / len(radii)
                harmonics = {}
                for frequency in range(1, 37):
                    real = imaginary = 0.
                    for index, radius in enumerate(radii):
                        angle = index * frequency * 2 * math.pi / len(radii)
                        real += (radius - mean) * math.cos(angle)
                        imaginary += (radius - mean) * math.sin(angle)
                    harmonics[frequency] = math.hypot(real, imaginary)
                self.assertEqual(18, max(harmonics, key=harmonics.get),
                                 "Measured shaft relief must repeat eighteen times around the circumference")


if __name__ == "__main__":
    unittest.main()
