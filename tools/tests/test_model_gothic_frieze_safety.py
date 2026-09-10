import unittest

from test_model_corinthian_frieze_safety import FriezeSafetyChecks
from daedalon_test_support import REPO_ROOT, load_json


class GothicFriezeSafetyTests(FriezeSafetyChecks,unittest.TestCase):
    family='gothic_frieze'
    evidence_file='gothic-frieze-source.json'
    source_digest='864d92fb085e364bb1574839ec21c0a15a51c82817ecea52ea8acb50817bb8e5'
    depth=.256
    sections=1
    min_faces=10000
    max_faces=15000
    search_terms=('gothic','quatrefoil','tracery','lancet')

    def test_one_complete_block_and_proportional_ornament(self):
        evidence=load_json(REPO_ROOT/'docs/evidence/gothic-frieze-source.json')
        prepared=evidence['preparation']
        self.assertEqual(15250,evidence['source']['triangles'])
        self.assertEqual(1,prepared['repeat_width_m'])
        self.assertFalse(prepared['ornament_stretched'])
        self.assertAlmostEqual(1/1.348,prepared['ornament_uniform_scale'])
        self.assertTrue(.019<prepared['plain_fascia_added_height_m']<.020)
        self.assertEqual(['straight_0'],[key for key in self.parts if key.startswith('straight_')])
        vertices=[p for tri in self.parts['straight_0'] for p in tri]
        for axis in (0,1):
            self.assertAlmostEqual(0,min(p[axis] for p in vertices),places=6)
            self.assertAlmostEqual(1,max(p[axis] for p in vertices),places=6)


if __name__=='__main__': unittest.main()
