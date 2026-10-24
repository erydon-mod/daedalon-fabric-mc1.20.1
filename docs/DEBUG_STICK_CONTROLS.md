# Debug-stick controls

Daedalon orders model settings by their purpose: Size, Width or Diameter first,
then Raised Base, Offset or Ceiling, Orientation/Facing, Pattern, Manual Corner,
Join and finally Waterlogged, where each applies. Automatic support and helper
offset states are not player controls. Frieze Manual Corner remains available;
Join appears when it is enabled, and single-section friezes omit Pattern.

A fresh stick's first left-click selects the first useful setting without
changing the block. Later left-clicks cycle settings; right-clicks change the
selected value. Sizes and numeric settings increase before wrapping; booleans
cycle false then true. The established eight-direction rotation control keeps
its 45-degree steps. Valid selections stored by older vanilla or ERYDON
companion controls remain readable, including the former Orientation name.

Fountain controls start with Basin Size and Raised Base. Assembled fountains
then expose Plinth Size, Tiers and Facing; Waterlogged remains last. Changing
Size or Raised Base validates the complete assembly before changing any state.
Clicks on a helper cell edit its owner rather than the helper's internal data.

Validation covers the ordering/value helpers, every registered model family's
actual controls, and real debug-stick calls with fresh and legacy selection
NBT. Capital tests also cover helper routing and ownership when ERYDON's older
companion hook is installed.
