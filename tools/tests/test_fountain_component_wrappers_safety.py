import unittest

from daedalon_test_support import JAVA_ROOT


class FountainComponentWrapperSafetyTests(unittest.TestCase):
    def test_both_attachment_paths_unwrap_before_the_concrete_model_check(self):
        source = (JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java").read_text(encoding="utf-8")
        for component in ("plinthModel", "bowlModel"):
            self.assertIn(f"unwrapAttachedModel({component}) instanceof ObjMeshBakedModel", source)
            self.assertNotIn(f"if ({component} instanceof ObjMeshBakedModel", source)
        self.assertIn("return WrapperBakedModel.unwrap(model);", source)


if __name__ == "__main__":
    unittest.main()
