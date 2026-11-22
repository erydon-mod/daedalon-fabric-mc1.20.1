"""Geometry acceptance for the independently authored Diota pilot.

The containment proof subtracts the actual collision-box union from every
triangle, including intersections between vertices. It does not sample only
vertices or assume that an enclosing rectangle covers an octagonal cross.
"""
from __future__ import annotations

from collections import Counter, defaultdict
import math
import re
import unittest

from daedalon_test_support import (
    GENERATOR, JAVA_ROOT, MESH_EXPECTATIONS, MESH_ROOT, REPO_ROOT,
    load_json, parse_obj, sha256,
)


EPSILON = 2e-8


def triangles(path):
    vertices, result = [], []
    material = ""
    for line in path.read_text(encoding="utf-8").splitlines():
        words = line.split()
        if not words:
            continue
        if words[0] == "v":
            vertices.append(tuple(map(float, words[1:])))
        elif words[0] == "usemtl":
            material = words[1]
        elif words[0] == "f":
            indices = tuple(int(word.split("/")[0]) - 1 for word in words[1:])
            if len(indices) != 3:
                raise AssertionError("The runtime Diota must contain triangles")
            result.append((material, indices, tuple(vertices[i] for i in indices)))
    return vertices, result


def cross(a, b):
    return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2],
            a[0]*b[1]-a[1]*b[0])


def minus(a, b):
    return tuple(a[i]-b[i] for i in range(3))


def area(poly):
    if len(poly) < 3:
        return 0.0
    return sum(math.sqrt(sum(v*v for v in cross(minus(poly[i], poly[0]),
                                               minus(poly[i+1], poly[0]))))/2
               for i in range(1, len(poly)-1))


def clip(poly, axis, plane, above):
    """Clip a real triangle/polygon, creating exact edge/plane intersections."""
    if not poly:
        return []
    result = []
    for a, b in zip(poly, (*poly[1:], poly[0])):
        inside_a = a[axis] >= plane if above else a[axis] <= plane
        inside_b = b[axis] >= plane if above else b[axis] <= plane
        if inside_a:
            result.append(a)
        if inside_a != inside_b:
            t = (plane-a[axis])/(b[axis]-a[axis])
            result.append(tuple(a[i]+t*(b[i]-a[i]) for i in range(3)))
    return result


def contains(box, point, epsilon=EPSILON):
    return all(box[i]-epsilon <= point[i] <= box[i+3]+epsilon for i in range(3))


def subtract_box(poly, box):
    """Partition polygon minus closed box into convex outside fragments."""
    pending, outside = poly, []
    for axis in range(3):
        for plane, above in ((box[axis]-EPSILON, True),
                             (box[axis+3]+EPSILON, False)):
            fragment = clip(pending, axis, plane, not above)
            if area(fragment) > 1e-16:
                outside.append(fragment)
            pending = clip(pending, axis, plane, above)
            if area(pending) <= 1e-16:
                return outside
    return outside


def shape_boxes(source):
    body = re.search(r"private static VoxelShape makeDiotaShape\(\) \{(.*?)\n    \}",
                     source, re.DOTALL).group(1)
    result, layers = [], []
    for match in re.finditer(
            r"(Block\.createCuboidShape|DecorShapeTransforms\.octagonalLayer)"
            r"\(([^)]+)\)", body):
        box = tuple(float(number.strip())/16 for number in match.group(2).split(","))
        if match.group(1).endswith("octagonalLayer"):
            layers.append(box)
            x0, y0, z0, x1, y1, z1 = box
            cut = min(x1-x0, z1-z0)/8
            result.extend(((x0+cut, y0, z0, x1-cut, y1, z1),
                           (x0, y0, z0+cut, x1, y1, z1-cut)))
        else:
            result.append(box)
    return result, layers


def transform_point(point, scale, offset, turns, world):
    # Independent scalar form of the renderer's centred quarter-turn transform.
    x, y, z = ((point[0]-.5)*scale, point[1]*scale,
               (point[2]-.5)*scale-offset)
    x, z = ((x, z), (-z, x), (-x, -z), (z, -x))[turns]
    return (x+.5+world[0], y+world[1], z+.5+world[2])


def transform_box(box, scale, offset, turns, world):
    # Match DecorShapeTransforms: scale/translate first, rotate the AABB next.
    x0, y0, z0, x1, y1, z1 = box
    result = (.5+(x0-.5)*scale, y0*scale, .5+(z0-.5)*scale-offset,
              .5+(x1-.5)*scale, y1*scale, .5+(z1-.5)*scale-offset)
    for _ in range(turns):
        x0, y0, z0, x1, y1, z1 = result
        result = (1-z1, y0, x0, 1-z0, y1, x1)
    return tuple(result[i]+world[i % 3] for i in range(6))


def vertex_components(tris):
    """Find separate indexed parts; touching palmette roots may share a point."""
    uses = defaultdict(set)
    for number, (_, indices, _) in enumerate(tris):
        for index in indices:
            uses[index].add(number)
    remaining, result = set(range(len(tris))), []
    while remaining:
        pending, component = [remaining.pop()], set()
        while pending:
            number = pending.pop()
            component.add(number)
            for index in tris[number][1]:
                for other in uses[index] & remaining:
                    remaining.remove(other)
                    pending.append(other)
        result.append(component)
    return result


class OriginalDiotaSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.vertices, cls.triangles = triangles(MESH_ROOT / "urn_diota.obj")
        cls.urn = (JAVA_ROOT / "block/UrnBlock.java").read_text(encoding="utf-8")
        cls.boxes, cls.layers = shape_boxes(cls.urn)
        cls.components = vertex_components(cls.triangles)

    def test_exact_compatibility_envelope_and_real_ground_contact(self):
        parsed = parse_obj(MESH_ROOT / "urn_diota.obj")
        expected = MESH_EXPECTATIONS["diota"]
        self.assertEqual(expected["obj_sha256"], sha256(MESH_ROOT / "urn_diota.obj"))
        low, high = parsed["bounds"]
        for actual, wanted in zip((high[i]-low[i] for i in range(3)),
                                  (.582233804, 1.0, .547980138)):
            # Blender stores authored coordinates as float32 before export.
            self.assertAlmostEqual(wanted, actual, delta=5e-8)
        self.assertEqual(0.0, low[1])
        self.assertAlmostEqual(0.0, low[0]+high[0], places=8)
        self.assertAlmostEqual(0.0, low[2]+high[2], places=8)
        floor = [tri for _, _, tri in self.triangles
                 if all(abs(point[1]) < 1e-10 for point in tri)]
        self.assertGreater(sum(map(area, floor)), .05)
        # The complete exported model, including both pierced lugs, hanging
        # rings and reliefs, must respect Oliver's runtime triangle cap.
        self.assertLessEqual(len(self.triangles), 6000)
        evidence = load_json(REPO_ROOT / "docs/evidence/original-vessels.json")
        self.assertEqual(6000, evidence["offline_validation"]["runtime_triangle_budget"])
        self.assertLessEqual(evidence["models"][0]["authored_polygons"], 6000)

    def test_square_plinth_has_flat_authored_normals_and_full_support(self):
        # The plinth owns the first eight rings of eight vertices. Confirm the
        # actual exported corner normals, since Blender's angle helper can
        # silently turn these deliberately flat planes back to smooth shading.
        plinth = self.vertices[:64]
        self.assertAlmostEqual(.44, max(p[0] for p in plinth)-min(p[0] for p in plinth), places=6)
        self.assertAlmostEqual(.44, max(p[2] for p in plinth)-min(p[2] for p in plinth), places=6)
        normals, checked = [], 0
        for line in (MESH_ROOT / "urn_diota.obj").read_text(encoding="utf-8").splitlines():
            fields = line.split()
            if not fields:
                continue
            if fields[0] == "vn":
                normals.append(tuple(map(float, fields[1:])))
            elif fields[0] == "f":
                corners = [field.split("/") for field in fields[1:]]
                indices = [int(corner[0])-1 for corner in corners]
                if not all(index < 64 for index in indices):
                    continue
                a, b, c = (self.vertices[index] for index in indices)
                normal = cross(minus(b, a), minus(c, a))
                length = math.sqrt(sum(v*v for v in normal))
                normal = tuple(v/length for v in normal)
                for corner in corners:
                    actual = normals[int(corner[2])-1]
                    self.assertGreater(sum(actual[i]*normal[i] for i in range(3)), .999999)
                    checked += 1
        self.assertEqual(372, checked)

    def test_closed_outward_components_without_degenerate_triangles(self):
        edges, neighbours = defaultdict(list), defaultdict(set)
        volumes = []
        for number, (_, indices, tri) in enumerate(self.triangles):
            self.assertGreater(area(tri), 1e-12, number)
            volumes.append(sum(tri[0][i]*cross(tri[1], tri[2])[i]
                               for i in range(3))/6)
            for a, b in zip(indices, (*indices[1:], indices[0])):
                edges[tuple(sorted((a, b)))].append((number, a, b))
        for edge, uses in edges.items():
            self.assertEqual(2, len(uses), edge)
            self.assertEqual(uses[0][1:], uses[1][1:][::-1], edge)
            a, b = uses[0][0], uses[1][0]
            neighbours[a].add(b)
            neighbours[b].add(a)
        remaining, component_volumes = set(range(len(self.triangles))), []
        while remaining:
            pending, members = [remaining.pop()], set()
            while pending:
                current = pending.pop()
                members.add(current)
                for other in neighbours[current] & remaining:
                    remaining.remove(other)
                    pending.append(other)
            component_volumes.append(sum(volumes[index] for index in members))
        # Base, vessel, fourteen small relief leaves, two pierced stone ears
        # and two bronze hoops. The leaves are intentionally smaller solids.
        self.assertEqual(20, len(component_volumes))
        self.assertTrue(all(volume > 1e-8 for volume in component_volumes),
                        component_volumes)

    def test_open_mouth_reaches_the_interior_floor_without_a_cap(self):
        # Project vertical rays into each actual triangle; this catches a cap
        # even if its vertices all lie around the rim rather than at the centre.
        for x in (-.05, 0.0, .05):
            for z in (-.05, 0.0, .05):
                heights = []
                for _, _, (a, b, c) in self.triangles:
                    determinant = (b[2]-c[2])*(a[0]-c[0])+(c[0]-b[0])*(a[2]-c[2])
                    if abs(determinant) < 1e-14:
                        continue
                    u = ((b[2]-c[2])*(x-c[0])+(c[0]-b[0])*(z-c[2]))/determinant
                    v = ((c[2]-a[2])*(x-c[0])+(a[0]-c[0])*(z-c[2]))/determinant
                    if min(u, v, 1-u-v) >= -1e-8:
                        heights.append(u*a[1]+v*b[1]+(1-u-v)*c[1])
                self.assertTrue(heights, (x, z))
                self.assertGreater(max(heights), .36)
                self.assertLess(max(heights), .45, (x, z, max(heights)))

    def test_small_hanging_hoops_keep_their_centres_clear_of_collision(self):
        hoops = [component for component in self.components
                 if self.triangles[next(iter(component))][0] == "bronze_handles"]
        self.assertEqual(2, len(hoops))
        for hoop in hoops:
            indices = {index for n in hoop for index in self.triangles[n][1]}
            edges = {tuple(sorted((a, b))) for n in hoop
                     for a, b in zip(self.triangles[n][1],
                                     (*self.triangles[n][1][1:], self.triangles[n][1][0]))}
            self.assertEqual(0, len(indices)-len(edges)+len(hoop))  # closed torus
            points = [self.vertices[index] for index in indices]
            height = max(p[1] for p in points)-min(p[1] for p in points)
            self.assertGreater(height, .14)
            self.assertLess(height, .17)
            centre = tuple(sum(p[i] for p in points)/len(points)
                           + (0 if i == 1 else .5) for i in range(3))
            self.assertLess(centre[1], .80)
            self.assertFalse(any(contains(box, centre) for box in self.boxes))
            for scale in (.6156, 1.0, 1.3844):
                for offset in (0.0, .8003375):
                    for turns in range(4):
                        world = (-17, -64, -33)
                        point = transform_point(centre, scale, offset, turns, world)
                        boxes = [transform_box(box, scale, offset, turns, world)
                                 for box in self.boxes]
                        self.assertFalse(any(contains(box, point) for box in boxes))

    def test_hanging_hoops_pass_through_actual_pierced_stone_ears(self):
        evidence = load_json(REPO_ROOT / "docs/evidence/original-vessels.json")
        aperture_vertices = evidence["models"][0]["handle_design"]["lug_segments"]
        self.assertEqual(24, aperture_vertices)
        ears, hoops = [], []
        for component in self.components:
            points = [self.vertices[index] for n in component
                      for index in self.triangles[n][1]]
            material = self.triangles[next(iter(component))][0]
            if material == "bronze_handles":
                hoops.append((component, points))
            elif min(p[1] for p in points) > .70 and min(abs(p[0]) for p in points) > .15:
                ears.append((component, points))
        self.assertEqual(2, len(ears))
        for _, points in ears:
            sign = math.copysign(1, points[0][0])
            hoop, ring_points = next((part, p) for part, p in hoops
                                     if math.copysign(1, p[0][0]) == sign)
            z0, z1 = min(p[2] for p in points), max(p[2] for p in points)
            min_y = min(p[1] for p in points)
            self.assertLess(min(p[1] for p in ring_points), min_y-.04)
            self.assertLess(min(p[2] for p in ring_points), z0)
            self.assertGreater(max(p[2] for p in ring_points), z1)
            # At the first positive Z plane, both the inner aperture and outer
            # ear outline exist. The nearest locked aperture vertices are the true
            # inner hole contour; use those actual edges, not an ellipse guess.
            plane = min(p[2] for p in points if p[2] > 0)
            candidates = {(p[0], p[1]) for p in points if abs(p[2]-plane) < 1e-8}
            centre = ((min(p[0] for p in points)+max(p[0] for p in points))/2, .850)
            hole = sorted(candidates, key=lambda p: sum((p[i]-centre[i])**2 for i in range(2)))[:aperture_vertices]
            self.assertEqual(aperture_vertices, len(hole))
            hole.sort(key=lambda p: math.atan2(p[1]-centre[1], p[0]-centre[0]))
            overlaps = 0
            for number in hoop:
                polygon = clip(clip(clip(self.triangles[number][2], 2, z0, True),
                                    2, z1, False), 1, min_y, True)
                if area(polygon) < 1e-15:
                    continue
                overlaps += 1
                for point in polygon:
                    for a, b in zip(hole, (*hole[1:], hole[0])):
                        side = (b[0]-a[0])*(point[1]-a[1])-(b[1]-a[1])*(point[0]-a[0])
                        self.assertGreaterEqual(side, -1e-9)
            self.assertGreater(overlaps, 0)

    def test_collision_union_contains_complete_triangles_between_height_bands(self):
        self.assertEqual(11, len(self.layers))
        self.assertEqual(38, len(self.boxes))
        self.assertLessEqual(max(box[4]-box[1] for box in self.boxes), .15)
        intersections = 0
        for number, (_, _, triangle) in enumerate(self.triangles):
            # Rendering's fitted base maps the centred source to owner-cell XZ.
            poly = tuple((x+.5, y, z+.5) for x, y, z in triangle)
            for point in poly:
                self.assertTrue(any(contains(box, point) for box in self.boxes),
                                (number, point))
            fragments = [poly]
            for box in self.boxes:
                if not fragments:
                    break
                if all(contains(box, point) for point in poly):
                    fragments = []
                    break
                fragments = [piece for fragment in fragments
                             for piece in subtract_box(fragment, box)]
            self.assertLess(sum(map(area, fragments)), 1e-13,
                            (number, fragments))
            for layer in self.layers:
                if min(p[1] for p in poly) < layer[1] < max(p[1] for p in poly):
                    clipped = clip(clip(poly, 1, layer[1], True), 1, layer[4], False)
                    intersections += area(clipped) > 1e-14
        self.assertGreater(intersections, 400)
        # Preserve the narrower waist and the air beside the pierced ears.
        self.assertFalse(any(contains(box, (.73, .875, .58)) for box in self.boxes))
        self.assertFalse(any(contains(box, (.73, .25, .5)) for box in self.boxes))

    def test_every_size_offset_facing_and_negative_world_position_agree(self):
        baked = (JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java").read_text(encoding="utf-8")
        shape = (JAVA_ROOT / "block/DecorShapeTransforms.java").read_text(encoding="utf-8")
        for source in (baked, shape):
            self.assertRegex(source, r"OFFSET_DISTANCE\s*=\s*0\.8003375F")
            self.assertRegex(source, r"OFFSET_BASE_Y\s*=\s*0\.0F")
        base = [(x+.5, y, z+.5) for x, y, z in self.vertices]
        # Pick an actual containing slab before applying scale. An epsilon-only
        # neighbour can stop containing a boundary vertex after scaling, even
        # while the adjacent slab still contains it exactly.
        witnesses = [next(index for index, box in enumerate(self.boxes)
                          if contains(box, point, epsilon=0.0)) for point in base]
        for scale in (.6156, 1.0, 1.3844):
            for offset in (0.0, .8003375):
                for turns in range(4):
                    for world in ((0, 0, 0), (-17, -64, -33)):
                        boxes = [transform_box(box, scale, offset, turns, world)
                                 for box in self.boxes]
                        for point, witness in zip(base, witnesses):
                            transformed = transform_point(point, scale, offset, turns, world)
                            self.assertTrue(contains(boxes[witness], transformed),
                                            (scale, offset, turns, world, transformed))
                        self.assertAlmostEqual(world[1], min(box[1] for box in boxes))

    def test_shared_rendering_policy_and_unchanged_other_vessels(self):
        definition = load_json(MESH_ROOT / "urn_diota.json")
        self.assertEqual("cylindrical", definition["uv_projection"])
        self.assertEqual(2, definition["cylindrical_u_repeats"])
        self.assertEqual(4, definition["texture_u_tiles"])
        self.assertEqual({"bronze_handles": "axis_stabilized_box"},
                         definition["material_uv_projections"])
        self.assertFalse(definition.get("double_sided", False))
        self.assertFalse(definition["smooth_normals"])
        self.assertTrue(definition["fit_to_block"])
        self.assertEqual([1, 1, 1], definition["scale"])
        self.assertEqual([0, 0, 0], definition["translate"])
        self.assertEqual(56, len(GENERATOR.family_block_ids("diota")))
        self.assertEqual(["urn_diota.obj"],
                         sorted(path.name for path in MESH_ROOT.glob("*diota*.obj")))
        for family, digest in {
            "kylix": "27c94f8102022124bd629847f286973f4c43565494b38b914287b18625c4ec92",
            "kalyx": "4de2bcf636bb142dd03907085b7558cc8f50811553170af1b477fab2148dff1f",
        }.items():
            self.assertEqual(digest, sha256(MESH_ROOT / f"urn_{family}.obj"))
        evidence = load_json(REPO_ROOT / "docs/evidence/original-vessels.json")
        self.assertEqual(["diota"], evidence["scope"])
        model = evidence["models"][0]
        self.assertFalse(model["source_geometry_loaded"])
        self.assertEqual(sha256(REPO_ROOT / model["tool"]), model["tool_sha256"])
        self.assertEqual(sha256(MESH_ROOT / "urn_diota.obj"), model["runtime_obj_sha256"])
        self.assertEqual(sha256(MESH_ROOT / "urn_diota.mtl"), model["mtl_sha256"])
        self.assertEqual(len(self.triangles), model["runtime_triangles"])
        self.assertEqual(Counter(model["material_triangles"]),
                         Counter(material for material, _, _ in self.triangles))
        for name, digest in model["design_source_sha256"].items():
            self.assertEqual(digest, sha256((REPO_ROOT / model["tool"]).with_name(name)))
        for name, digest in model["texture_sha256"].items():
            self.assertEqual(digest, sha256(MESH_ROOT.parents[1] / "textures/block" / name))
        self.assertIn("procedural", evidence["creation_provenance"])


if __name__ == "__main__":
    unittest.main()
