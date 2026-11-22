"""Retain Balanos's actual acanthus cup beneath a clean measured acorn.

The dense Oliver-authored Blender source is read without changing it. Only the
leaf-cup band receives a bounded reduction; the acorn and caller's pedestal are
regular measured geometry. Cut caps remain inside those clean supporting forms.
"""

from __future__ import annotations

import hashlib
import math
from pathlib import Path


SOURCE_SHA256 = "4819be08b3ca13a0f6cc418ecdf6dc7bb45946fdc1186cf879cf441321a72a80"
CUP_LOW, CUP_HIGH = .300, .665
CUP_TARGET_TRIANGLES = 3300


def acorn_radius(z):
    """Measured source radii above the cup agree to about a millimetre."""
    if z < .55:
        t = min(1, max(0, (z - .298) / (.55 - .298)))
        return .1234 + (.208 - .1234) * math.sin(t * math.pi / 2)
    u = min(1, max(0, (z - .55) / .45))
    return max(.0015, .208 * (1 - u ** 2.2) ** .70)


def _append_acorn(builder, z0, z1):
    # The sealed lower end is embedded in the caller's turned collar.
    heights = [z0 + .0004 + (z1 - z0 - .0004) * i / 36 for i in range(37)]
    rings = []
    for z in heights:
        r = acorn_radius(z)
        rings.append([builder.vertex((r * math.cos(a * math.tau / 32),
                                      r * math.sin(a * math.tau / 32), z)) for a in range(32)])
    for lower, upper in zip(rings, rings[1:]):
        for a in range(32):
            b = (a + 1) % 32
            builder.face((lower[a], lower[b], upper[b], upper[a]))
    builder.face(tuple(reversed(rings[0])))
    builder.face(tuple(rings[-1]))


def _retain_cup(source_path):
    import bpy
    import bmesh
    import numpy as np
    from mathutils import Vector
    from mathutils.bvhtree import BVHTree

    source_path = Path(source_path)
    assert hashlib.sha256(source_path.read_bytes()).hexdigest() == SOURCE_SHA256, "Balanos source identity changed"
    # Loading only source objects leaves the caller's clean scene and builder intact.
    with bpy.data.libraries.load(str(source_path), link=False) as (available, loaded):
        loaded.objects = available.objects
    imported = [obj for obj in loaded.objects if obj is not None]
    source = next(obj for obj in imported if obj.type == "MESH")
    mesh = source.data
    coordinates = np.empty(len(mesh.vertices) * 3, dtype=np.float32)
    mesh.vertices.foreach_get("co", coordinates)
    coordinates = coordinates.reshape((-1, 3)).astype(np.float64)
    matrix = np.asarray(source.matrix_world)
    coordinates = coordinates @ matrix[:3, :3].T + matrix[:3, 3]
    minimum, maximum = coordinates.min(axis=0), coordinates.max(axis=0)
    coordinates[:, :2] -= (minimum[:2] + maximum[:2]) / 2
    coordinates[:, 2] -= minimum[2]
    coordinates /= maximum[2] - minimum[2]
    mesh.calc_loop_triangles()
    triangles = np.empty(len(mesh.loop_triangles) * 3, dtype=np.int32)
    mesh.loop_triangles.foreach_get("vertices", triangles)
    triangles = triangles.reshape((-1, 3))
    heights = coordinates[:, 2]
    selected = triangles[(heights[triangles].min(axis=1) <= CUP_HIGH)
                         & (heights[triangles].max(axis=1) >= CUP_LOW)]
    used, inverse = np.unique(selected, return_inverse=True)
    positions = coordinates[used].tolist()
    faces = inverse.reshape((-1, 3)).tolist()
    reference = BVHTree.FromPolygons([Vector(p) for p in positions], faces, all_triangles=True)
    dense = bpy.data.meshes.new("Balanos actual native leaf cup")
    dense.from_pydata(positions, [], faces)
    dense.update()
    original_count = len(mesh.polygons)
    for obj in imported:
        data = obj.data if obj.type == "MESH" else None
        bpy.data.objects.remove(obj, do_unlink=True)
        if data is not None and data.users == 0:
            bpy.data.meshes.remove(data)
    bm = bmesh.new()
    bm.from_mesh(dense)
    for height, keep_above in [(CUP_LOW, True), (CUP_HIGH, False)]:
        bmesh.ops.bisect_plane(bm, geom=list(bm.verts) + list(bm.edges) + list(bm.faces),
                              plane_co=(0, 0, height), plane_no=(0, 0, 1),
                              clear_inner=keep_above, clear_outer=not keep_above, dist=1e-8)
    border = [edge for edge in bm.edges if edge.is_boundary]
    caps = bmesh.ops.holes_fill(bm, edges=border, sides=0)["faces"]
    cap_count = len(caps)
    bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
    bm.to_mesh(dense)
    bm.free()
    dense.update()
    dense.calc_loop_triangles()
    source_cup_triangles = len(dense.loop_triangles)
    obj = bpy.data.objects.new("Balanos bounded native acanthus retention", dense)
    bpy.context.scene.collection.objects.link(obj)
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    # Two light passes remove submillimetre scan ripples, retaining carved crests.
    smoothing = obj.modifiers.new("Light native surface cleanup", "SMOOTH")
    smoothing.factor = .12
    smoothing.iterations = 2
    bpy.ops.object.modifier_apply(modifier=smoothing.name)
    decimate = obj.modifiers.new("Acanthus cup only - bounded face budget", "DECIMATE")
    decimate.decimate_type = "COLLAPSE"
    decimate.ratio = CUP_TARGET_TRIANGLES / source_cup_triangles
    decimate.use_collapse_triangulate = True
    bpy.ops.object.modifier_apply(modifier=decimate.name)
    reduced = obj.data
    hidden_skin_vertices = 0
    hidden_skin_max_displacement = 0.0
    # Dress only the hidden joining bands; radial scaling also preserves cap interiors.
    for vertex in reduced.vertices:
        z = vertex.co.z
        if z < .315:
            weight = max(0, min(1, (.315 - z) / (.315 - CUP_LOW)))
            ratio = .1234 / .125
            vertex.co.x *= 1 + weight * (ratio - 1)
            vertex.co.y *= 1 + weight * (ratio - 1)
        elif z > .645:
            weight = max(0, min(1, (z - .645) / (CUP_HIGH - .645)))
            # The top cap sits a millimetre inside the clean acorn surface.
            target = max(.001, acorn_radius(z) - .001)
            source_radius = acorn_radius(z) + .0006
            ratio = target / source_radius
            vertex.co.x *= 1 + weight * (ratio - 1)
            vertex.co.y *= 1 + weight * (ratio - 1)
        # The retained band also contains uncarved acorn skin between the leaves.
        # Bury that duplicate skin behind the clean acorn rather than leaving
        # nearly coincident triangles on its exposed smooth surface. Raised
        # acanthus lobes remain unchanged beyond the 5mm relief transition.
        if z > .52:
            radius = math.hypot(vertex.co.x, vertex.co.y)
            core_radius = acorn_radius(z)
            gap = radius - core_radius
            if radius > core_radius - .002 and gap < .005:
                z_blend = max(0, min(1, (z - .52) / .02))
                z_blend = z_blend * z_blend * (3 - 2 * z_blend)
                relief_blend = max(0, min(1, (gap - .0025) / .0025))
                relief_blend = 1 - relief_blend * relief_blend * (3 - 2 * relief_blend)
                target_radius = core_radius - .002
                amount = z_blend * relief_blend
                new_radius = radius + amount * (target_radius - radius)
                vertex.co.x *= new_radius / radius
                vertex.co.y *= new_radius / radius
                hidden_skin_vertices += 1
                hidden_skin_max_displacement = max(hidden_skin_max_displacement, radius - new_radius)
    reduced.update()
    reduced.calc_loop_triangles()
    bm = bmesh.new()
    bm.from_mesh(reduced)
    bmesh.ops.remove_doubles(bm, verts=list(bm.verts), dist=1e-7)
    bmesh.ops.dissolve_degenerate(bm, edges=list(bm.edges), dist=1e-7)
    faults = [edge for edge in bm.edges if len(edge.link_faces) != 2]
    fault_vertices = list({vertex for edge in faults for vertex in edge.verts})
    # Three submillimetre source pinches survive Collapse. Merge only their
    # incident vertices, without smoothing or welding the surrounding leafwork.
    if faults:
        assert max(edge.calc_length() for edge in faults) < .0008, "Native fault exceeds the bounded cleanup"
        bmesh.ops.remove_doubles(bm, verts=fault_vertices, dist=.0008)
        bmesh.ops.dissolve_degenerate(bm, edges=list(bm.edges), dist=1e-7)
        bmesh.ops.holes_fill(bm, edges=[edge for edge in bm.edges if edge.is_boundary], sides=0)
    bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
    edge_counts = {"boundary": sum(edge.is_boundary for edge in bm.edges),
                   "nonmanifold": sum(len(edge.link_faces) > 2 for edge in bm.edges)}
    volume = bm.calc_volume(signed=True)
    bm.to_mesh(reduced)
    bm.free()
    reduced.update()
    reduced.calc_loop_triangles()
    assert edge_counts == {"boundary": 0, "nonmanifold": 0}, edge_counts
    assert volume > 0 and all(triangle.area > 1e-14 for triangle in reduced.loop_triangles)
    result_positions = [tuple(vertex.co) for vertex in reduced.vertices]
    result_faces = [tuple(triangle.vertices) for triangle in reduced.loop_triangles]
    candidate = BVHTree.FromPolygons([Vector(p) for p in result_positions], result_faces, all_triangles=True)
    # Hidden caps and intentionally buried duplicate acorn skin are construction,
    # so surface-error measurements cover the exposed native leaf relief only.
    def exposed_leaf(point):
        return (.32 < point[2] < .64
                and (point[2] <= .52 or math.hypot(point[0], point[1]) - acorn_radius(point[2]) >= .005))
    source_samples = [p for p in positions[::max(1, len(positions) // 3000)] if exposed_leaf(p)]
    candidate_samples = [p for p in result_positions if exposed_leaf(p)]
    errors_source = [candidate.find_nearest(Vector(p))[3] for p in source_samples]
    errors_candidate = [reference.find_nearest(Vector(p))[3] for p in candidate_samples]
    report = {"source_total_polygons": original_count, "source_cup_triangles": source_cup_triangles,
              "cup_target_triangles": CUP_TARGET_TRIANGLES, "retained_cup_triangles": len(result_faces),
              "cut_band_z": [CUP_LOW, CUP_HIGH], "hidden_cut_caps": cap_count,
              "native_smoothing_factor": .12, "native_smoothing_iterations": 2,
              "bounded_native_fault_edges": len(faults), "fault_weld_limit_m": .0008,
              "buried_duplicate_acorn_skin_vertices": hidden_skin_vertices,
              "hidden_skin_inset_m": .002, "hidden_skin_full_blend_gap_m": .0025,
              "hidden_skin_relief_transition_limit_m": .005,
              "hidden_skin_max_intentional_radial_displacement_m": hidden_skin_max_displacement,
              "surface_error_scope": "exposed native leaves; hidden caps and deliberately buried uncarved acorn skin excluded",
              "edge_counts": edge_counts, "signed_volume": volume,
              "sampled_exposed_source_to_candidate_max_m": max(errors_source),
              "sampled_exposed_candidate_to_source_max_m": max(errors_candidate),
              "source_sha256": SOURCE_SHA256, "original_unmodified": True}
    bpy.data.objects.remove(obj, do_unlink=True)
    if reduced.users == 0:
        bpy.data.meshes.remove(reduced)
    assert hashlib.sha256(source_path.read_bytes()).hexdigest() == SOURCE_SHA256
    return result_positions, result_faces, report


def build_acorn_and_cup(builder, source_path, z0, z1, max_radius):
    """Append a clean acorn and the actual reduced native acanthus cup."""
    if abs(z0 - .298) > .003 or abs(z1 - 1) > 1e-6:
        raise ValueError("The measured Balanos retention contract is Z .298..1")
    _append_acorn(builder, z0, z1)
    positions, faces, retention = _retain_cup(source_path)
    first_face = len(builder.faces)
    indices = [builder.vertex(position) for position in positions]
    for face in faces:
        builder.face(tuple(indices[index] for index in face))
    return {"ornament": "measured smooth acorn and retained native acanthus leaf cup",
            "acanthus_leaves": 8, "native_cup_first_face": first_face,
            "native_cup_retention": retention, "tip_dressing_metres": .0015,
            "flat_face_indices": []}
