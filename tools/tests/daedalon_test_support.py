from __future__ import annotations

import hashlib
import importlib.util
import json
import math
import struct
from collections import Counter
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
RESOURCES = REPO_ROOT / "src/main/resources"
DAEDALON_ASSETS = RESOURCES / "assets/daedalon"
DAEDALON_DATA = RESOURCES / "data/daedalon"
MESH_ROOT = DAEDALON_ASSETS / "models/mesh"
JAVA_ROOT = REPO_ROOT / "src/main/java/com/oliver/daedalon"

GENERATOR_PATH = REPO_ROOT / "tools/generate_daedalon_assets.py"
SPEC = importlib.util.spec_from_file_location(
    "generate_daedalon_assets", GENERATOR_PATH
)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Could not load {GENERATOR_PATH}")
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


AUTHORED_UV_STATUE_FAMILIES = frozenset(
    {
        "aphrodite",
        "apollo",
        "ares",
        "artemis",
        "athena",
        "demeter",
        "dionysus",
        "helios",
        "hera",
        "heracles",
        "lion_couchant",
        "orpheus",
        "perseus",
        "phaeton",
        "poseidon",
        "theseus",
        "zeus",
    }
)


MESH_EXPECTATIONS = {
    "spartan": {
        "definition": "statue_spartan_promachos.json",
        "obj": "spartan_siplified.obj",
        "mtl": "spartan_siplified.mtl",
        "display": "statue_spartan_promachos_display.json",
        "vertices": 5173,
        "uvs": 40815,
        "normals": 13605,
        "faces": 13605,
        "face_sizes": {3},
        "face_index_styles": {"v/vt/vn"},
        "obj_sha256": "e2f4a7486a8a9f438cd3607b172ed27afd7bf1c54c50efd054f4ae935cd189d9",
        "mtl_sha256": "63927167d1de1932c470adef139a23ef333afafcb0b188aef5052f76b4d0eeec",
    },
    "zeus": {
        "definition": "zeus_statue.json",
        "obj": "zeus.obj",
        "mtl": None,
        "display": "zeus_statue_display.json",
        "vertices": 7300,
        "uvs": 8040,
        "normals": 0,
        "faces": 14601,
        "face_sizes": {3},
        "face_index_styles": {"v/vt"},
        "bounds": (
            (-0.373892, -0.950682, -0.280535),
            (0.371331, 0.948878, 0.280345),
        ),
        "obj_sha256": "65c89a0567c73d38641965b9b1b17bf1f5387ed27e9f1f2e9dfa0bfed1571064",
        "mtl_sha256": None,
    },
    "amphora": {
        "definition": "urn_amphora.json",
        "obj": "urn_amphora.obj",
        "mtl": "urn_amphora.mtl",
        "display": "urn_amphora_display.json",
        "vertices": 1598,
        "uvs": 0,
        "normals": 1598,
        "faces": 3200,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": (
            (-0.366493, -0.951788, -0.367204),
            (0.365157, 0.95099, 0.364192),
        ),
        "obj_sha256": "bcddf999f98c153eb62592ea27dff23391f34244e5d3cc417d225b41dca8819a",
        "mtl_sha256": "bbaff126401a4c2f7497d513fedc67f945f37656d150536a869de512351b0378",
    },
    "konche": {
        "definition": "urn_konche.json",
        "obj": "urn_konche.obj",
        "mtl": "urn_konche.mtl",
        "display": "urn_konche_display.json",
        "vertices": 1602,
        "uvs": 0,
        "normals": 1602,
        "faces": 3200,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": (
            (-0.945006, -0.949646, -0.949212),
            (0.951881, 0.948897, 0.948956),
        ),
        "obj_sha256": "7b8188c31c160981f251898e43e56a22a5747191ecbe714b429517e99e298de7",
        "mtl_sha256": "db660e6f5bf889e06e344feb23b97debbf55d63e42090fa4f93d712fa84c3b45",
    },
    "diota": {
        "definition": "urn_diota.json",
        "obj": "urn_diota.obj",
        "mtl": "urn_diota.mtl",
        "display": "urn_diota_display.json",
        "vertices": 1983,
        "uvs": 7912,
        "normals": 1991,
        "faces": 1991,
        "face_sizes": {3, 4},
        "face_index_styles": {"v/vt/vn"},
        "obj_sha256": "056f640c64745f69ab144c77cf196234016df40bde1c44e0afb311b48eb45c01",
        "mtl_sha256": "a81950d4f4e5e154e0d361ad7a01c75dfa25dab38e2de43657df0bad6672ad28",
    },
    "kylix": {
        "definition": "urn_kylix.json",
        "obj": "urn_kylix.obj",
        "mtl": "urn_kylix.mtl",
        "display": "urn_kylix_display.json",
        "vertices": 1600,
        "uvs": 0,
        "normals": 1600,
        "faces": 3200,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": (
            (-0.949432, -0.632105, -0.954777),
            (0.946683, 0.630614, 0.951435),
        ),
        "obj_sha256": "27c94f8102022124bd629847f286973f4c43565494b38b914287b18625c4ec92",
        "mtl_sha256": "ced0dc2953fe351dd6b23baade320441cb68983a54b51417174385ecfc92899d",
    },
    "kalyx": {
        "definition": "urn_kalyx.json",
        "obj": "urn_kalyx.obj",
        "mtl": "urn_kalyx.mtl",
        "display": "urn_kalyx_display.json",
        "vertices": 1602,
        "uvs": 0,
        "normals": 1602,
        "faces": 3200,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": (
            (-0.698057, -0.951528, -0.697721),
            (0.6968, 0.948126, 0.697794),
        ),
        "obj_sha256": "4de2bcf636bb142dd03907085b7558cc8f50811553170af1b477fab2148dff1f",
        "mtl_sha256": "ced0dc2953fe351dd6b23baade320441cb68983a54b51417174385ecfc92899d",
    },
    "lekythos": {
        "definition": "urn_lekythos.json",
        "obj": "urn_lekythos.obj",
        "mtl": "urn_lekythos.mtl",
        "display": "urn_lekythos_display.json",
        "vertices": 1600,
        "uvs": 0,
        "normals": 1600,
        "faces": 3200,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": (
            (-0.317141, -0.952276, -0.277491),
            (0.316886, 0.951266, 0.276082),
        ),
        "obj_sha256": "f2144cd41250da57f76fab036b2219de30c79b45ff2f2454c4db9cbaa2a2dab9",
        "mtl_sha256": "bbaff126401a4c2f7497d513fedc67f945f37656d150536a869de512351b0378",
    },
    "pelike": {
        "definition": "urn_pelike.json",
        "obj": "urn_pelike.obj",
        "mtl": "urn_pelike.mtl",
        "display": "urn_pelike_display.json",
        "vertices": 1598,
        "uvs": 0,
        "normals": 1598,
        "faces": 3200,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": (
            (-0.778876, -0.952037, -0.777004),
            (0.775397, 0.951986, 0.776125),
        ),
        "obj_sha256": "db65061de3ae1729c2b308197bcd2894f93fe3c66f1af071ce7eee762b9d8743",
        "mtl_sha256": "bbaff126401a4c2f7497d513fedc67f945f37656d150536a869de512351b0378",
    },
    "pithos": {
        "definition": "urn_pithos.json",
        "obj": "urn_pithos.obj",
        "mtl": "urn_pithos.mtl",
        "display": "urn_pithos_display.json",
        "vertices": 1602,
        "uvs": 0,
        "normals": 1602,
        "faces": 3200,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": (
            (-0.664531, -0.95139, -0.663976),
            (0.664995, 0.951042, 0.661661),
        ),
        "obj_sha256": "6cbd8067609ae1a7ed93498d810e7072aa647db46638028e0f4eed427a98e627",
        "mtl_sha256": "bbaff126401a4c2f7497d513fedc67f945f37656d150536a869de512351b0378",
    },
    "rhabdos": {
        "definition": "urn_rhabdos.json",
        "obj": "urn_rhabdos.obj",
        "mtl": "urn_rhabdos.mtl",
        "display": "urn_rhabdos_display.json",
        "vertices": 1556,
        "uvs": 0,
        "normals": 1556,
        "faces": 3200,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": (
            (-0.564418, -0.950307, -0.563154),
            (0.561274, 0.950447, 0.563732),
        ),
        "obj_sha256": "f8261a5e36cded729609b3fa500c995e9f1d5f35a1983e4c4e9b89beb95797bc",
        "mtl_sha256": "bbaff126401a4c2f7497d513fedc67f945f37656d150536a869de512351b0378",
    },
    "salpinx": {
        "definition": "urn_salpinx.json",
        "obj": "urn_salpinx.obj",
        "mtl": "urn_salpinx.mtl",
        "display": "urn_salpinx_display.json",
        "vertices": 1602,
        "uvs": 0,
        "normals": 1602,
        "faces": 3200,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": (
            (-0.74165, -0.95116, -0.742804),
            (0.741099, 0.951052, 0.747363),
        ),
        "obj_sha256": "ed83f60d7b1a8c99fd3de3e4f1f6e2f66fcb77a531f5fb80bd27d19d0845d559",
        "mtl_sha256": "bbaff126401a4c2f7497d513fedc67f945f37656d150536a869de512351b0378",
    },
    "stamnos": {
        "definition": "urn_stamnos.json",
        "obj": "urn_stamnos.obj",
        "mtl": "urn_stamnos.mtl",
        "display": "urn_stamnos_display.json",
        "vertices": 1598,
        "uvs": 0,
        "normals": 1598,
        "faces": 3200,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": (
            (-0.952926, -0.765532, -0.829152),
            (0.949709, 0.764707, 0.831972),
        ),
        "obj_sha256": "59653359311e684c11c378defedcf9194e240e7cbb0f896202b83c56822c6d80",
        "mtl_sha256": "bbaff126401a4c2f7497d513fedc67f945f37656d150536a869de512351b0378",
    },
}

CLASSICAL_STATUE_MESHES = {
    "aphrodite": {
        "vertices": 7679,
        "uvs": 8095,
        "faces": 15362,
        "bounds": ((-0.272519, -0.950613, -0.27307), (0.270632, 0.949454, 0.270004)),
        "obj_sha256": "3a464219fa5d2b5972df0d4f5d40c54e9c938c9cb52e6cd07f0b6f9feb794b2f",
    },
    "apollo": {
        "vertices": 7712,
        "uvs": 8702,
        "faces": 15496,
        "bounds": ((-0.321803, -0.950264, -0.264836), (0.319405, 0.944923, 0.2613)),
        "obj_sha256": "c3bd3c0ef29c25a5870f898fbf5351bc4c35d3a75006c44ca009b25e8d4e97e2",
    },
    "ares": {
        "vertices": 7573,
        "uvs": 8436,
        "faces": 15194,
        "bounds": ((-0.351857, -0.950359, -0.320849), (0.34903, 0.948805, 0.318852)),
        "obj_sha256": "e6bb7f514f35e4a3085868dbbb6ae8b56399b188943514567beb2834f6b2b1fa",
    },
    "artemis": {
        "vertices": 7850,
        "uvs": 8922,
        "faces": 15764,
        "bounds": ((-0.363721, -0.950585, -0.309868), (0.371154, 0.946258, 0.306799)),
        "obj_sha256": "77e02ff29ee593853c6c66587692fc3304b1d4448c830e74101bec4f18f88e84",
    },
    "athena": {
        "vertices": 7478,
        "uvs": 8220,
        "faces": 14987,
        "bounds": ((-0.303702, -0.950659, -0.275005), (0.303663, 0.948371, 0.274812)),
        "obj_sha256": "d8604dc7237b5ae50c86b903c73e65d7508d12d680e7bd49c2a512e14b3eecb3",
    },
    "demeter": {
        "vertices": 7125,
        "uvs": 8060,
        "faces": 14322,
        "bounds": ((-0.302686, -0.950655, -0.30065), (0.300406, 0.938781, 0.300169)),
        "obj_sha256": "d4e025c1c380e7e45e7f43e03a22ffec75411b73ace20fb3e58342d2a7256dda",
    },
    "dionysus": {
        "vertices": 7101,
        "uvs": 8330,
        "faces": 14346,
        "bounds": ((-0.341604, -0.950216, -0.314931), (0.337469, 0.94937, 0.314225)),
        "obj_sha256": "5e23445fca7cf66ecc4e090a45cf171686a001d34bfe5af23ed55edb24ac8825",
    },
    "helios": {
        "vertices": 7959,
        "uvs": 8917,
        "faces": 15974,
        "bounds": ((-0.403177, -0.95067, -0.325507), (0.401184, 0.948828, 0.320855)),
        "obj_sha256": "bdfcfab9b25a10da36be6208e4a3f8f7715b5dcac9eb8e17455b0ff61a08d000",
    },
    "hera": {
        "vertices": 7742,
        "uvs": 8626,
        "faces": 15556,
        "bounds": ((-0.312313, -0.950644, -0.257301), (0.311414, 0.948554, 0.253257)),
        "obj_sha256": "2062e2c46bd205460a6b7508de418c93797fcd5b09d57d57d48850e34acdbcf0",
    },
    "phaeton": {
        "vertices": 8980,
        "uvs": 10188,
        "faces": 18019,
        "bounds": ((-0.360537, -0.95069, -0.378815), (0.358184, 0.948414, 0.375674)),
        "obj_sha256": "503f782384e579171344298c6e60d820ef0c9c633453556ee09808547c4eb73f",
    },
    "poseidon": {
        "vertices": 7860,
        "uvs": 8405,
        "faces": 15724,
        "bounds": ((-0.329095, -0.950656, -0.327778), (0.326036, 0.948942, 0.327283)),
        "obj_sha256": "4480a09093eedacb1db8d058b89daa4675779012867dd2d8f796abcc0e3a5361",
    },
    "bellerophon": {
        "vertices": 8306,
        "faces": 16664,
        "bounds": ((-0.652495, -0.950701, -0.567467), (0.651989, 0.948718, 0.567187)),
        "obj_sha256": "e1e3f7b4bccf18b988f2373d51b7186360fdb3024af2e829229a2471a678aa02",
    },
    "heracles": {
        "vertices": 7673,
        "uvs": 8400,
        "faces": 15362,
        "bounds": ((-0.482484, -0.950014, -0.336283), (0.478052, 0.948783, 0.333309)),
        "obj_sha256": "06d91d387367586c02c46a424c5f5afb92176083d5a38f6b7f299c8feab27436",
    },
    "orpheus": {
        "vertices": 7797,
        "uvs": 8723,
        "faces": 15654,
        "bounds": ((-0.332329, -0.950271, -0.279354), (0.333052, 0.949338, 0.27867)),
        "obj_sha256": "08f84e990bb343dc3ac4ff8e3f35e3e0a5771334d1f2b957f687ec5623f2823d",
    },
    "perseus": {
        "vertices": 7999,
        "uvs": 8709,
        "faces": 16006,
        "bounds": ((-0.606804, -0.950597, -0.304916), (0.602681, 0.948725, 0.3046)),
        "obj_sha256": "9f1053c4eecda2d2133728c25b65e08d5c9a0118cdc60a46c1e6bc667ebf24a1",
    },
    "theseus": {
        "vertices": 7168,
        "uvs": 7930,
        "faces": 14347,
        "bounds": ((-0.5429, -0.950655, -0.434966), (0.540717, 0.949612, 0.432257)),
        "obj_sha256": "f89d1bb990c41c0a4cecfcb88e1cf844ad6f76c56aec92ad4570578574242a6f",
    },
    "lion_couchant": {
        "vertices": 7125,
        "uvs": 7756,
        "faces": 14258,
        "bounds": ((-0.349328, -0.592257, -0.950861), (0.344386, 0.590737, 0.949143)),
        "obj_sha256": "f906dc612fef7ae056d3a3a6604e3369320ec8ecc31460c36ce78e6e1dd89a92",
    },
    "lion_statant": {
        "vertices": 7774,
        "faces": 15556,
        "bounds": ((-0.443836, -0.948623, -0.608577), (0.444001, 0.94977, 0.604723)),
        "obj_sha256": "840ffa27010fa40d727ddc0010fd366d5e90754d53063625487f89e130694a25",
    },
}

for _family, _mesh in CLASSICAL_STATUE_MESHES.items():
    _mtl = "theseus.mtl" if _family == "theseus" else None
    MESH_EXPECTATIONS[_family] = {
        "definition": f"{_family}_statue.json",
        "obj": f"{_family}.obj",
        "mtl": _mtl,
        "display": "zeus_statue_display.json",
        "vertices": _mesh["vertices"],
        "uvs": _mesh.get("uvs", 0),
        "normals": 0,
        "faces": _mesh["faces"],
        "face_sizes": {3},
        "face_index_styles": (
            {"v/vt"} if _family in AUTHORED_UV_STATUE_FAMILIES else {"v"}
        ),
        "bounds": _mesh["bounds"],
        "obj_sha256": _mesh["obj_sha256"],
        "mtl_sha256": (
            "13d13a0c2e863bd88ffb5225ecee4eee764de19c98ff11001d70fc7f5bd35ebc"
            if _mtl is not None
            else None
        ),
    }

ARCHITECTURAL_MESHES = {
    "corbel_baroque": (2998, 2998, 6000, ((-0.603151, -0.951584, -0.415316), (0.602464, 0.949194, 0.413651)), "4abe249a782b62cf324edf4d4e048a70f50e8f71804eaddd576ce406d39907de"),
    "corbel_corinthian": (2927, 2927, 6000, ((-0.554186, -0.950371, -0.535875), (0.552583, 0.950332, 0.532099)), "215d986a2d1ee9eda1553a1800a5164d17eb9fe683371dd4594730757e2177ea"),
    "corbel_georgian": (3002, 2950, 6000, ((-0.419612, -0.952227, -0.278674), (0.418604, 0.950997, 0.277652)), "0d2462f882bf6955e202d9ebf50ad9c01cc9b292b82104cfac1ce4132dc894f0"),
    "corbel_ionic": (3002, 3002, 6000, ((-0.628039, -0.952134, -0.205662), (0.627682, 0.951118, 0.204273)), "77d3e65bfa8c9aa223e7988edb868e4de712cdb58c6fc42011b7c800f33a8bc5"),
    "corbel_renaissance": (3000, 3000, 6000, ((-0.721641, -0.951219, -0.360548), (0.720121, 0.950909, 0.343673)), "f9b2126ee3f46f94b02504cb4737c363cc9194c7474ef9ea230f87956f4d34f1"),
    "capital_byzantine": (6738, 6737, 17999, ((-0.844934, -0.949771, -0.844628), (0.844163, 0.946852, 0.844323)), "a0e565b4a6d0f71b3798b63eb0842a8597f0021df26b25767ccfcea267c7e943"),
    "capital_corinthian": (4981, 4981, 12000, ((-0.824838, -0.936864, -0.824684), (0.826304, 0.950031, 0.824666)), "a654a67e3589a393afd67f67bd4a4f7a0d8a67fc6fb493595d2f075382759386"),
    "capital_gothic": (2897, 2897, 5999, ((-0.868816, -0.944658, -0.867839), (0.868321, 0.947437, 0.867734)), "0d19a83fe836db5993efe5ee4c903ad0a1e4d7c181fa1680a5a232339f971f9f"),
    "capital_greek_ionic": (2954, 2954, 5999, ((-0.949489, -0.551712, -0.680649), (0.953366, 0.549951, 0.679622)), "aeb6a3385165b57adacc2de6a494746e6b51ac7e2aaa332e8bde0d532ccdf9a7"),
    "capital_roman_composite": (5216, 5216, 11999, ((-0.79035, -0.939774, -0.791866), (0.789086, 0.946814, 0.791622)), "39e7b39528fdd3d2373ce7bf4006d212fd2bd6ee51316fbe534419e540a4716f"),
    "capital_tuscan": (3002, 3002, 6000, ((-0.944664, -0.763193, -0.95193), (0.943608, 0.765774, 0.950844)), "29e9ace4c2b6391a32350d638710c63baee33187426138d5cf8868d0ccdb0fa0"),
}

for _stem, (_vertices, _normals, _faces, _bounds, _obj_sha256) in ARCHITECTURAL_MESHES.items():
    MESH_EXPECTATIONS[_stem] = {
        "definition": f"{_stem}.json",
        "obj": f"{_stem}.obj",
        "mtl": f"{_stem}.mtl",
        "display": "corbel_display.json" if _stem.startswith("corbel_") else "capital_display.json",
        "vertices": _vertices,
        "uvs": 0,
        "normals": _normals,
        "faces": _faces,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": _bounds,
        "obj_sha256": _obj_sha256,
        "mtl_sha256": "bbaff126401a4c2f7497d513fedc67f945f37656d150536a869de512351b0378",
    }

BUST_MESHES = {
    "aphrodite": (3002, 6000, ((-0.842693, -0.950727, -0.515089), (0.841291, 0.949721, 0.513471)), "1e7fe0f624aa3c6899611c941f5594d803076d7275042c5d368e296bb8d0cd29"),
    "apollo": (2975, 6000, ((-0.821714, -0.95096, -0.489048), (0.829553, 0.952449, 0.476336)), "2a34f269622f259b3492a2be089a54338ae704904abbb2cc3caf6ca0d0af64e7"),
    "ares": (2993, 5999, ((-0.822448, -0.952453, -0.474176), (0.820868, 0.950176, 0.476073)), "6e36a1a6086e7795bc1928be2a967826a227e788e53803419ccee688edcb75d7"),
    "artemis": (3002, 6000, ((-0.884952, -0.95506, -0.554321), (0.882504, 0.951411, 0.55011)), "0dd97fde0d76e09cd5d2f78ae7532b6e730fc1fa1b18388335f5ac546fb4a115"),
    "athena": (2998, 6000, ((-0.814771, -0.952555, -0.52884), (0.813391, 0.951131, 0.521861)), "7d0c52ade1219c93c2d907c41cccccaf2b8fb4891cce6583fd7fb2ecbbd86767"),
    "demeter": (2933, 6000, ((-0.847711, -0.952149, -0.453596), (0.84948, 0.952731, 0.454698)), "59e3f35e518c392a0ba791c8313e63bb747355e8e5a125012cc396e490dff826"),
    "dionysus": (2747, 5999, ((-0.741655, -0.95053, -0.504331), (0.743257, 0.949631, 0.490514)), "c011890a5d09dec6cd097fa4f07d5f346ad8248b683fe5dd16e3bb129ae36bbc"),
    "hephaestus": (2996, 6000, ((-0.753923, -0.951627, -0.48797), (0.752224, 0.950533, 0.490868)), "a6bec87214262238b69cf17cdba981eb9e2555a6c4c0ddfeab96ddd638935fa1"),
    "hera": (2999, 5999, ((-0.736055, -0.952899, -0.416772), (0.732933, 0.952342, 0.416016)), "ab81048c96367f5c3e18a1fd267f7934d05d20bc91a1e419d8a4a0219f4d24cf"),
    "hermes": (2994, 6000, ((-0.85351, -0.95184, -0.53253), (0.850535, 0.95153, 0.529926)), "1b7ebf5011e169c3973d32ba4a00bbbe76a91e4dbe4269ad76ef99422e6d2c42"),
    "poseidon": (3001, 5999, ((-0.954798, -0.834358, -0.515363), (0.954915, 0.842417, 0.518941)), "f279c7e4b138706b361b184408a7baf7cac3446c38adf5a9b5f2ae3b70fe14b9"),
    "zeus": (2917, 6000, ((-0.846728, -0.953391, -0.505578), (0.844175, 0.953051, 0.501167)), "83f3c3b5cf6f2ab248277477523f9f964fce70b51cd38363af7f0c890899e508"),
}

for _subject, (_vertices, _faces, _bounds, _obj_sha256) in BUST_MESHES.items():
    _stem = f"bust_{_subject}"
    MESH_EXPECTATIONS[_stem] = {
        "definition": f"{_stem}.json",
        "obj": f"{_stem}.obj",
        "mtl": f"{_stem}.mtl",
        "display": "bust_display.json",
        "vertices": _vertices,
        "uvs": 0,
        "normals": _vertices,
        "faces": _faces,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": _bounds,
        "obj_sha256": _obj_sha256,
        "mtl_sha256": "bbaff126401a4c2f7497d513fedc67f945f37656d150536a869de512351b0378",
    }

DECOR_MESHES = {
    "finial_balanos": (2992, 2992, 6000, ((-0.471109, -0.950291, -0.471031), (0.470664, 0.952781, 0.470937)), "5d2eb9ee0bcc05a465fdaede5bbc52cc019be410a54e7986c65dcfb3a109b52c", "finial_display.json"),
    "finial_kynara": (3002, 3002, 6000, ((-0.49611, -0.95121, -0.552465), (0.495942, 0.950493, 0.550597)), "bc871e48c1d3f99aea154792d5696fdd42507439efafb20ad728eeb95488afc8", "finial_display.json"),
    "finial_phlox": (3002, 3002, 6000, ((-0.508766, -0.950932, -0.505926), (0.508071, 0.948998, 0.503935)), "4fb4d5616a4f4829a9dea84e1e4ad4d09ca2a4621497a00c291d640a14907250", "finial_display.json"),
    "finial_sphaira": (3002, 3002, 6000, ((-0.628295, -0.95047, -0.630944), (0.628855, 0.950091, 0.628846)), "26703cfa4369a6c113a568884ab980e28d350a4dca99df20eb2cce92b6e775d0", "finial_display.json"),
    "finial_strobilos": (3000, 3000, 5999, ((-0.486649, -0.951152, -0.492061), (0.485498, 0.951982, 0.485381)), "8de7cdde89f8801876d7558c7245c76c52fc61157877cfc3afb8e15c2f510032", "finial_display.json"),
    "basin_louterion": (3002, 3002, 6000, ((-0.95149, -0.91206, -0.949226), (0.950185, 0.911247, 0.950938)), "5aeb049ff2827a053cb4e9491da60d5c6d7b60e496d609a5eab64f626a37d0d9", "decor_display.json"),
    "fountain_krene": (2993, 2993, 5999, ((-0.503117, -0.951196, -0.372632), (0.503461, 0.950475, 0.372531)), "746624bd8a541b56d0594b1dbce883d0bc5a89f8d2d372a60ee8d798b0bc2016", "krene_display.json"),
    "fountain_pege": (2707, 2707, 6000, ((-0.95222, -0.92886, -0.949882), (0.95496, 0.930623, 0.946094)), "c77bd3bb0fd4ae31e02dee40ba61386224209a4f1e013b69a8aa893ea6bdc1b1", "decor_display.json"),
    "monument_obeliskos": (3002, 2992, 6000, ((-0.241004, -0.951161, -0.241016), (0.240043, 0.95025, 0.240015)), "1c28aefbd5f9aea30981c6ed185baf50d572f85c8e42ba4c749754c711f18da2", "monument_display.json"),
}

for _stem, (_vertices, _normals, _faces, _bounds, _obj_sha256, _display) in DECOR_MESHES.items():
    MESH_EXPECTATIONS[_stem] = {
        "definition": f"{_stem}.json",
        "obj": f"{_stem}.obj",
        "mtl": f"{_stem}.mtl",
        "display": _display,
        "vertices": _vertices,
        "uvs": 0,
        "normals": _normals,
        "faces": _faces,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": _bounds,
        "obj_sha256": _obj_sha256,
        "mtl_sha256": "bbaff126401a4c2f7497d513fedc67f945f37656d150536a869de512351b0378",
    }

PLINTH_MESHES = {
    "plinth_astragalos": (3000, 3000, 6000, ((-0.573742, -0.951135, -0.573777), (0.572926, 0.950127, 0.572588)), "4a705d84abd3704f9248f2e051255841a82c26f1603411205fc38afd7ff360c5"),
    "plinth_bathron": (3002, 2996, 6000, ((-0.772151, -0.951058, -0.772285), (0.771277, 0.950213, 0.771612)), "2543b6f83228c87f69bb106a86441813dc2e76d5910468baa8bea025d9c3824e"),
    "plinth_kion": (3002, 3002, 6000, ((-0.951526, -0.912782, -0.951733), (0.948068, 0.911534, 0.94985)), "04f426f5a9d161eaf78f6aa35eb6babd7c284620047f39aa35f337e4196bd6a8"),
    "plinth_stephanos": (2937, 2937, 6000, ((-0.789804, -0.950898, -0.789703), (0.789154, 0.947018, 0.789239)), "d21862cd4d56b3809737d1b8dc6b5426acc1e33714ecc94aa70617e5abc9fc14"),
    "plinth_triphyllon": (2986, 2986, 6000, ((-0.910882, -0.949999, -0.926197), (0.907355, 0.949729, 0.924002)), "9f18250c28fcb761de10d529f03b25bc5d598a4d5c6f970b53a99cf3c7cd160c"),
}

for _stem, (_vertices, _normals, _faces, _bounds, _obj_sha256) in PLINTH_MESHES.items():
    MESH_EXPECTATIONS[_stem] = {
        "definition": f"{_stem}.json",
        "obj": f"{_stem}.obj",
        "mtl": f"{_stem}.mtl",
        "display": "plinth_display.json",
        "vertices": _vertices,
        "uvs": 0,
        "normals": _normals,
        "faces": _faces,
        "face_sizes": {3},
        "face_index_styles": {"v//vn"},
        "bounds": _bounds,
        "obj_sha256": _obj_sha256,
        "mtl_sha256": "bbaff126401a4c2f7497d513fedc67f945f37656d150536a869de512351b0378",
    }


def load_json(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def png_dimensions(path: Path) -> tuple[int, int]:
    header = path.read_bytes()[:24]
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
        raise AssertionError(f"Not a PNG: {path}")
    if header[12:16] != b"IHDR":
        raise AssertionError(f"PNG lacks an IHDR header: {path}")
    return struct.unpack(">II", header[16:24])


def all_block_ids() -> list[str]:
    return [
        block_id
        for family in GENERATOR.ALL_FAMILIES
        for block_id in GENERATOR.family_block_ids(family)
    ]


def parse_obj(path: Path) -> dict[str, object]:
    positions = 0
    position_values: list[tuple[float, float, float]] = []
    texture_coordinates = 0
    normals = 0
    faces = 0
    face_sizes: set[int] = set()
    face_index_styles: set[str] = set()
    material_faces: Counter[str] = Counter()
    current_material = ""

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line.startswith("v "):
            values = tuple(float(value) for value in line.split()[1:])
            if len(values) != 3 or not all(math.isfinite(value) for value in values):
                raise AssertionError(f"Invalid vertex in {path}: {line}")
            positions += 1
            position_values.append(values)
        elif line.startswith("vt "):
            values = tuple(float(value) for value in line.split()[1:])
            if len(values) != 2 or not all(math.isfinite(value) for value in values):
                raise AssertionError(f"Invalid texture coordinate in {path}: {line}")
            texture_coordinates += 1
        elif line.startswith("vn "):
            values = tuple(float(value) for value in line.split()[1:])
            if len(values) != 3 or not all(math.isfinite(value) for value in values):
                raise AssertionError(f"Invalid normal in {path}: {line}")
            normals += 1
        elif line.startswith("usemtl "):
            current_material = line.split(maxsplit=1)[1]
        elif line.startswith("f "):
            tokens = line.split()[1:]
            faces += 1
            face_sizes.add(len(tokens))
            material_faces[current_material] += 1
            for token in tokens:
                indices = token.split("/")
                if len(indices) > 3 or not indices[0]:
                    raise AssertionError(f"Invalid OBJ face index in {path}: {line}")
                if len(indices) == 1:
                    face_index_styles.add("v")
                elif len(indices) == 2 and indices[1]:
                    face_index_styles.add("v/vt")
                elif len(indices) == 3 and indices[1] and indices[2]:
                    face_index_styles.add("v/vt/vn")
                elif len(indices) == 3 and not indices[1] and indices[2]:
                    face_index_styles.add("v//vn")
                else:
                    raise AssertionError(
                        f"Incomplete OBJ face index in {path}: {line}"
                    )
                position_index = int(indices[0])
                if not 1 <= position_index <= positions:
                    raise AssertionError(f"Position index out of range in {path}: {line}")
                if len(indices) >= 2 and indices[1]:
                    texture_index = int(indices[1])
                    if not 1 <= texture_index <= texture_coordinates:
                        raise AssertionError(f"UV index out of range in {path}: {line}")
                if len(indices) == 3 and indices[2]:
                    normal_index = int(indices[2])
                    if not 1 <= normal_index <= normals:
                        raise AssertionError(
                            f"Normal index out of range in {path}: {line}"
                        )

    bounds = (
        tuple(min(values[index] for values in position_values) for index in range(3)),
        tuple(max(values[index] for values in position_values) for index in range(3)),
    )

    return {
        "vertices": positions,
        "uvs": texture_coordinates,
        "normals": normals,
        "faces": faces,
        "face_sizes": face_sizes,
        "face_index_styles": face_index_styles,
        "material_faces": material_faces,
        "bounds": bounds,
    }


def expected_material_texture_names(
    prefix: str = "", suffix: str = ""
) -> set[str]:
    names: set[str] = set()
    for material in GENERATOR.MATERIALS:
        stem = f"{prefix}{material}{suffix}"
        names.update(
            {
                f"{stem}.png",
                f"{stem}_aged.png",
                f"{stem}_aged_n.png",
                f"{stem}_s.png",
            }
        )
    return names
