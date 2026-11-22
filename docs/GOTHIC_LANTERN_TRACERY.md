# Gothic lantern tracery

The Gothic lantern windows use two pointed lancets, a slender central mullion
and a four-lobed quatrefoil at the head. The head ornament connects to the
mullion and outer pointed frame. All of this detailing remains black lead,
standing in front of the inset glazing.

`tools/rebuild_gothic_lantern_tracery.py` authors the same pattern for the pier,
wall and hanging variants using the locked Blender 5.2 build. Run it with
`--source-root <approved pre-tracery Blender aliases> --baseline-obj <approved OBJ>
--output-dir <staging>`. Review its staged front, detail and three-mount previews
before delivering the files. The editable Blender models retain their separate
gas-flame visibility control.

Only the old interior lead motif is replaced. The existing pointed perimeter
frames and every stone, bronze, glass and flame face retain their original
positions, normals, UVs and face references. The exporter matches the old
interior triangles, removes those faces, and appends the replacement inserts.
The surrounding attribute arrays remain intact. Georgian and Byzantine geometry,
the shared suspension, burner proportions and yellow flame are unchanged.

Portable source hashes, retained-geometry fingerprints, new component counts,
pane clearance and Blender delivery hashes are recorded in
[the tracery evidence](evidence/gothic-lantern-tracery.json).
The lantern physical tests independently lock every non-lead Gothic component,
so later window edits cannot silently change the accepted body or glazing.
The existing glazing, on/off and brightness checks continue to apply.
