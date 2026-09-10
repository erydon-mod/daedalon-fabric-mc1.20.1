import unittest

from test_model_corinthian_frieze_safety import FriezeSafetyChecks
from daedalon_test_support import REPO_ROOT, MESH_ROOT, load_json


class IonicFriezeSafetyTests(FriezeSafetyChecks,unittest.TestCase):
    family='ionic_frieze'
    evidence_file='ionic-frieze-source.json'
    source_digest='a54c49f6e9178e0a88af467979e33d78085eb726e60d4d587c4cb6684db3f799'
    depth=.34
    min_faces=3000
    search_terms=('ionic','horse','chariot','palm','palmette','procession')

    def test_complete_two_motif_repeat_and_one_metre_sections(self):
        evidence=load_json(REPO_ROOT/'docs/evidence/ionic-frieze-source.json')
        self.assertEqual(15397,evidence['source']['triangles'])
        prepared=evidence['preparation']
        self.assertEqual(['palm','chariot group','palm','mounted group'],prepared['motifs_per_repeat'])
        self.assertEqual([-.357,.724],prepared['repeat_cut_source_x'])
        self.assertAlmostEqual(1.081,prepared['source_repeat_width'])
        self.assertEqual(3,prepared['repeat_width_m'])
        self.assertEqual(1,prepared['height_m'])
        for phase in range(3):
            vertices=[p for tri in self.parts[f'straight_{phase}'] for p in tri]
            for axis in (0,1):
                self.assertAlmostEqual(0,min(p[axis] for p in vertices),places=6)
                self.assertAlmostEqual(1,max(p[axis] for p in vertices),places=6)
        # Interior bands keep sculpted geometry, so the three sections must be
        # distinct instead of three copies of the nearest horse/palm motif.
        signatures=[{tuple(p) for tri in self.parts[f'straight_{i}'] for p in tri
                     if .15<p[0]<.85 and .25<p[1]<.7} for i in range(3)]
        self.assertTrue(all(signatures))
        self.assertTrue(all(signatures[a]!=signatures[b] for a,b in ((0,1),(1,2),(2,0))))


if __name__=='__main__': unittest.main()
