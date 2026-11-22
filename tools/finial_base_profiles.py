"""The Sphaira architectural pedestal, proportioned for the five finials.

Portable authoring geometry only: the caller supplies ``square`` and ``lathe``
builder methods. Original Meshy files and active runtime assets are untouched.
Sphaira's coordinates, profile simplification and segment counts remain exact.
"""
from __future__ import annotations

import math


SPHAIRA_FOOT = .605648
SPHAIRA_HEIGHT = .10
SPHAIRA_SQUARE = (
    (0., SPHAIRA_FOOT / 2 - .0015), (.0015, SPHAIRA_FOOT / 2),
    (.0985, SPHAIRA_FOOT / 2), (.10, SPHAIRA_FOOT / 2 - .0015),
    (.102, .282), (.115, .282), (.117, .266), (.161, .266),
    (.164, .264),
)
SPHAIRA_TURNED = (
    (.161,.262),(.17,.268),(.18,.273),(.195,.284),(.20,.285),
    (.21,.277),(.22,.265),(.24,.2495),(.26,.241),(.27,.241),
    (.28,.242),(.30,.255),(.31,.270),(.32,.270),(.33,.284),
    (.34,.303),(.36,.321),(.38,.329),(.40,.3307312405),
    (.42,.327),(.44,.315),(.45,.302),(.46,.280),(.466,.262),
)
# Keep each existing collar's final contact height/radius. The ornaments do not
# need to move or change shape to receive the common three-step pedestal.
CONTACTS = {
    "sphaira": (.466, .262), "balanos": (.304, .124),
    "kynara": (.275, .128), "phlox": (.23, .1815),
    "strobilos": (.282, .143),
}


def _smooth_profile(points, tolerance=.00035):
    """Preserve the established monotone Hermite and RDP construction exactly."""
    slopes=[(b[1]-a[1])/(b[0]-a[0]) for a,b in zip(points,points[1:])]
    derivatives=[slopes[0]]
    for left,right in zip(slopes,slopes[1:]):
        derivatives.append(0 if left*right<=0 else math.copysign(min(abs((left+right)/2),3*abs(left),3*abs(right)),left))
    derivatives.append(slopes[-1])
    dense=[]
    for i,((z0,r0),(z1,r1)) in enumerate(zip(points,points[1:])):
        for step in range(6):
            t=step/6; dz=z1-z0
            r=(2*t**3-3*t*t+1)*r0+(t**3-2*t*t+t)*dz*derivatives[i]+(-2*t**3+3*t*t)*r1+(t**3-t*t)*dz*derivatives[i+1]
            dense.append((z0+t*dz,r))
    dense.append(points[-1])
    def simplify(p):
        if len(p)<3:return p
        a,b=p[0],p[-1];dx=b[0]-a[0];dy=b[1]-a[1];length=math.hypot(dx,dy)
        distances=[abs(dx*(q[1]-a[1])-dy*(q[0]-a[0]))/length for q in p]
        index=max(range(len(p)),key=lambda j:distances[j])
        if distances[index]<=tolerance:return [a,b]
        return simplify(p[:index+1])[:-1]+simplify(p[index:])
    return simplify(dense)


def base_profile(style, width, depth, foot, height):
    """Return measured profiles without requiring Blender or constructing a mesh."""
    if style not in CONTACTS:
        raise ValueError("Unsupported architectural finial base: " + style)
    if min(width, depth, foot, height) <= 0 or foot > min(width, depth):
        raise ValueError("Finial and pedestal dimensions must be positive and fitted")
    if style == "sphaira":
        square = list(SPHAIRA_SQUARE)
        turned = list(SPHAIRA_TURNED)
        segments, ratio, corner = 40, depth / width, .0015
        neck_height, neck_radius = .27, .241
    else:
        radial_scale = foot / SPHAIRA_FOOT
        height_scale = height / SPHAIRA_HEIGHT
        square = [(z * height_scale, r * radial_scale) for z, r in SPHAIRA_SQUARE]
        start_height = SPHAIRA_TURNED[0][0] * height_scale
        end_height, end_radius = CONTACTS[style]
        if end_height <= square[-1][0]:
            raise ValueError("The ornament contact must be above all three steps")
        end_scale = end_radius / SPHAIRA_TURNED[-1][1]
        def transform(z, r):
            t = (z - SPHAIRA_TURNED[0][0]) / (SPHAIRA_TURNED[-1][0] - SPHAIRA_TURNED[0][0])
            # Smoothly reconcile each established ornament junction with its
            # exact support width, retaining the source's concave/beaded shape.
            blend = t * t * (3 - 2 * t)
            radius = r * (radial_scale + (end_scale - radial_scale) * blend)
            return start_height + (end_height - start_height) * t, radius
        turned = [transform(z, r) for z, r in SPHAIRA_TURNED]
        turned[-1] = end_height, end_radius
        neck_height, neck_radius = transform(.27, .241)
        segments, ratio, corner = 32, 1., .0015 * radial_scale
    profile = _smooth_profile(turned, .00035)
    return {
        "square_profile": square, "turned_profile": profile,
        "turned_control_profile": turned, "turned_segments": segments,
        "turned_ratio": ratio, "corner_radius": corner,
        "foot_width": foot, "square_foot_height": height,
        "base_family": "Sphaira three-step plinth and curved mouldings",
        "base_step_count": 3,
        "base_step_tops": [square[index][0] for index in (3, 5, 8)],
        "profile_landmarks": [
            {"label": "square_foot", "y": height / 2, "width": foot, "depth": foot},
            {"label": "turned_neck", "y": neck_height,
             "width": 2 * neck_radius, "depth": 2 * neck_radius * ratio},
        ],
    }


def build_base(builder, style, width, depth, foot, height):
    """Append the common pedestal and return its independently measurable record."""
    record = base_profile(style, width, depth, foot, height)
    first_face = len(builder.faces)
    builder.square(record["square_profile"], corner=record["corner_radius"])
    builder.lathe(record["turned_profile"], record["turned_segments"], record["turned_ratio"])
    record["base_authored_polygons"] = len(builder.faces) - first_face
    return record
