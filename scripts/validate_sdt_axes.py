"""Regression test for source animation / weighted-mesh coordinate conversion.

This checks mathematical equivalence in source Z-up and Minecraft Y-up coordinates.
It is not a visual/rendering test and cannot reconstruct missing inverse bind poses.
"""
import json
import math
from pathlib import Path

BASE = Path("src/main/resources/assets/vergilsdt/sdt_data/animmodels/animations/biped/yamato/living")

def remap(v):
    x, y, z = v
    return (x, z, -y)

def normalize(q):
    norm = math.sqrt(sum(v * v for v in q))
    assert math.isfinite(norm) and norm > 0.00001, "Invalid quaternion"
    return tuple(x / norm for x in q)

def rotate(q, v):
    # Quaternion in (w,x,y,z) order.
    w, x, y, z = normalize(q)
    vx, vy, vz = v
    tx = 2 * (y * vz - z * vy)
    ty = 2 * (z * vx - x * vz)
    tz = 2 * (x * vy - y * vx)
    return (vx + w * tx + (y * tz - z * ty),
            vy + w * ty + (z * tx - x * tz),
            vz + w * tz + (x * ty - y * tx))

def test_axis_equivalence():
    sample_vectors = ((1.0, 0.0, 0.0), (0.0, 1.0, 0.0),
                      (0.0, 0.0, 1.0), (0.17, -0.83, 0.52))
    tested = 0
    for filename in ("yamato_sin_devil_trigger.json",
                     "yamato_sin_devil_trigger_back.json"):
        document = json.loads((BASE / filename).read_text(encoding="utf-8"))
        for bone in document["animation"]:
            for transform in bone["transform"]:
                loc = tuple(transform["loc"])
                q = tuple(transform["rot"])
                assert len(loc) == 3 and len(q) == 4
                assert all(math.isfinite(v) for v in loc + q)
                # Java: new Quaternionf(qx, qz, -qy, qw).normalize()
                w, x, y, z = q
                mapped_q = (w, x, z, -y)
                for vector in sample_vectors:
                    expected = remap(rotate(q, vector))
                    actual = rotate(mapped_q, remap(vector))
                    assert max(abs(a-b) for a, b in zip(expected, actual)) < 2e-5, (
                        filename, bone["name"], q, vector)
                tested += 1
    assert tested > 100, "Animation clip resources missing"
    print("SDT coordinate validation PASS:", tested, "keyframes across both clips")

if __name__ == "__main__":
    test_axis_equivalence()
