"""Physical regressions for the dressed Strobilos diamond crown.

These inspect Builder vertices and faces, independently of the helper's claimed
row dimensions. No Blender install, original source or generated preview is needed.
"""
from __future__ import annotations

from collections import Counter, defaultdict
import bisect
import importlib.util
import math
import unittest

from daedalon_test_support import REPO_ROOT


def _load(name):
    spec = importlib.util.spec_from_file_location(name, REPO_ROOT / "tools" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


AUTHOR = _load("rebuild_finial_meshes")
STROBILOS = _load("finial_strobilos_profile")


def _sub(a, b):
    return tuple(x-y for x, y in zip(a, b))


def _dot(a, b):
    return sum(x*y for x, y in zip(a, b))


def _cross(a, b):
    return a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]


def _triangles(face):
    return [(face[0], face[j], face[j+1]) for j in range(1, len(face)-1)]


def _samples(triangle):
    for first in range(5):
        for second in range(5-first):
            weights = first/4, second/4, 1-(first+second)/4
            yield tuple(sum(weight*point[axis] for weight, point in zip(weights, triangle))
                        for axis in range(3))


class StrobilosCrownSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = AUTHOR.Builder()
        STROBILOS.build_strobilos(cls.builder, .272, 1., AUTHOR.DIMENSIONS["strobilos"][1]/2)
        cls.neighbours = defaultdict(set)
        for face in cls.builder.faces:
            for a, b in zip(face, face[1:]+face[:1]):
                cls.neighbours[a].add(b)
                cls.neighbours[b].add(a)
        remaining = set(cls.neighbours)
        components = []
        while remaining:
            pending = [remaining.pop()]
            indices = set(pending)
            while pending:
                for following in cls.neighbours[pending.pop()] & remaining:
                    remaining.remove(following)
                    indices.add(following)
                    pending.append(following)
            components.append(indices)
        cls.core = max(components, key=len)
        cls.scales = sorted((item for item in components if item is not cls.core), key=min)
        cls.rings = defaultdict(list)
        for index in cls.core:
            point = cls.builder.vertices[index]
            cls.rings[point[2]].append(point)
        for ring in cls.rings.values():
            ring.sort(key=lambda point: math.atan2(point[1], point[0]))
        cls.levels = sorted(cls.rings)
        cls.radii = {height: math.hypot(*points[0][:2]) for height, points in cls.rings.items()}
        cls.scale_faces = [[face for face in cls.builder.faces if face[0] in component]
                           for component in cls.scales]

    @classmethod
    def _band(cls, height):
        index = max(0, min(len(cls.levels)-2, bisect.bisect_right(cls.levels, height)-1))
        return cls.levels[index], cls.levels[index+1]

    @classmethod
    def _surface(cls, height, angle):
        """Actual polygon-section ray intersection, not the authored radius formula."""
        low, high = cls._band(height)
        t = max(0., min(1., (height-low)/(high-low)))
        points = [tuple(a+(b-a)*t for a, b in zip(first[:2], second[:2]))
                  for first, second in zip(cls.rings[low], cls.rings[high])]
        direction = math.cos(angle), math.sin(angle)
        cross = lambda a, b: a[0]*b[1]-a[1]*b[0]
        hits = []
        for first, second in zip(points, points[1:]+points[:1]):
            edge = _sub(second, first)
            denominator = cross(direction, edge)
            if abs(denominator) < 1e-12:
                continue
            distance = cross(first, edge)/denominator
            along = cross(first, direction)/denominator
            if distance > 0 and -1e-9 <= along <= 1+1e-9:
                hits.append(distance)
        if not hits:
            raise AssertionError("No core surface along radial sample")
        return min(hits)

    @classmethod
    def _penetration(cls, point):
        x, y, z = point
        if z < cls.levels[0]-1e-10 or z > cls.levels[-1]+1e-10:
            return -math.inf
        return cls._surface(z, math.atan2(y, x))-math.hypot(x, y)

    @classmethod
    def _arc(cls, bottom, top):
        cuts = [bottom]+[level for level in cls.levels if bottom < level < top]+[top]
        def radius(z):
            low, high = cls._band(z)
            t = (z-low)/(high-low)
            return cls.radii[low]+(cls.radii[high]-cls.radii[low])*t
        return sum(math.hypot(b-a, radius(b)-radius(a)) for a, b in zip(cuts, cuts[1:]))

    @classmethod
    def _rear(cls, faces):
        # Rear corners each join two rear edges and one side edge. Front bevel
        # corners and the dressed centre have degree four. This identifies the
        # rear independently of whether it is actually embedded or floating.
        return [face for face in faces if all(len(cls.neighbours[i]) == 3 for i in face)]

    def test_every_scale_is_a_sealed_positive_solid_with_an_embedded_rear_patch(self):
        self.assertGreater(len(self.scales), 100)
        for number, (component, faces) in enumerate(zip(self.scales, self.scale_faces)):
            edges = Counter(tuple(sorted((a, b))) for face in faces
                            for a, b in zip(face, face[1:]+face[:1]))
            self.assertTrue(all(count == 2 for count in edges.values()), number)
            volume = 0
            for face in faces:
                for triangle in _triangles(face):
                    a, b, c = [self.builder.vertices[i] for i in triangle]
                    normal = _cross(_sub(b, a), _sub(c, a))
                    self.assertGreater(_dot(normal, normal), 1e-22, number)
                    volume += _dot(a, _cross(b, c))/6
            self.assertGreater(volume, 1e-12, number)
            rear = self._rear(faces)
            self.assertEqual(1, len(rear), number)
            for triangle in _triangles(rear[0]):
                for point in _samples([self.builder.vertices[i] for i in triangle]):
                    self.assertGreater(self._penetration(point), .0005, (number, point))

    def test_crown_diamonds_keep_both_halves_and_regular_proportions_without_a_phase_band(self):
        rows = defaultdict(list)
        upper_aspects = []
        for number, (component, faces) in enumerate(zip(self.scales, self.scale_faces)):
            rear = self._rear(faces)
            self.assertEqual(1, len(rear), number)
            rear_indices = set(rear[0])
            outer_indices = set().union(*(self.neighbours[i]-rear_indices for i in rear_indices))
            self.assertEqual(4, len(outer_indices), number)
            corners = sorted((self.builder.vertices[i] for i in outer_indices), key=lambda point: point[2])
            bottom, left, right, top = corners
            centre = (left[2]+right[2])/2
            rows[round(centre, 8)].append((number, math.atan2(left[1]+right[1], left[0]+right[0])))
            if centre < .85:
                continue
            lower, upper = self._arc(bottom[2], centre), self._arc(centre, top[2])
            self.assertGreater(upper/lower, .85, number)
            self.assertLess(upper/lower, 1.15, number)
            aspect = (lower+upper)/math.dist(left, right)
            self.assertGreater(aspect, .95, number)
            self.assertLess(aspect, 1.2, number)
            upper_aspects.append(aspect)
        self.assertGreater(len(upper_aspects), 30)
        self.assertLess(max(upper_aspects)/min(upper_aspects), 1.1)
        self.assertEqual(1, len({len(scales) for scales in rows.values()}),
                         "Changing column counts creates an abrupt star-shaped crown band")
        phases = []
        for _, scales in sorted(rows.items()):
            step = math.tau/len(scales)
            phases.append((scales[0][1] % step)/step)
        for previous, following in zip(phases, phases[1:]):
            self.assertAlmostEqual(.5, (following-previous) % 1, places=6,
                                   msg="Adjacent rows must retain their half-column stagger")

    def test_dressed_faces_point_out_of_the_actual_body_without_folded_crown_bevels(self):
        checked = 0
        for number, faces in enumerate(self.scale_faces):
            rear = self._rear(faces)
            self.assertEqual(1, len(rear), number)
            rear_indices = set(rear[0])
            front = [face for face in faces if not rear_indices.intersection(face)]
            self.assertEqual(8, len(front), number)
            for face in front:
                for triangle in _triangles(face):
                    a, b, c = [self.builder.vertices[i] for i in triangle]
                    centre = tuple((a[i]+b[i]+c[i])/3 for i in range(3))
                    low, high = self._band(centre[2])
                    angle = math.atan2(centre[1], centre[0])
                    slope = (self._surface(high, angle)-self._surface(low, angle))/(high-low)
                    expected = math.cos(angle), math.sin(angle), -slope
                    normal = _cross(_sub(b, a), _sub(c, a))
                    cosine = _dot(normal, expected)/math.sqrt(_dot(normal, normal)*_dot(expected, expected))
                    self.assertGreater(cosine, .1, (number, centre, cosine))
                    checked += 1
        self.assertGreater(checked, 1000)


if __name__ == "__main__":
    unittest.main()
