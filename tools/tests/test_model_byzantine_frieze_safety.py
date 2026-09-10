import unittest

from test_model_corinthian_frieze_safety import FriezeSafetyChecks
from daedalon_test_support import REPO_ROOT, load_json


class ByzantineFriezeSafetyTests(FriezeSafetyChecks,unittest.TestCase):
    family='byzantine_frieze'
    evidence_file='byzantine-frieze-source.json'
    source_digest='a556fb01677ff20b022942ce4ec765dc94275703584484fba8992d3c2b9e43ca'
    depth=.235
    sections=2
    decimated=True
    min_faces=9000
    max_faces=15000
    search_terms=('byzantine','guilloche','interlace','cross','medallion','palm','palmette')

    def test_complete_pillar_to_pillar_repeat_keeps_carvings_proportional(self):
        evidence=load_json(REPO_ROOT/'docs/evidence/byzantine-frieze-source.json')
        prepared=evidence['preparation']
        self.assertEqual(15197,evidence['source']['triangles'])
        self.assertEqual(2,prepared['repeat_width_m'])
        self.assertFalse(prepared['ornament_stretched'])
        self.assertFalse(prepared['main_panel_decimated'])
        self.assertEqual([-.737,.737],prepared['repeat_cut_source_x'])
        self.assertEqual(['half large pillar','palm','small pillar','interlace','cross medallion','interlace','small pillar','palm','half large pillar'],prepared['motifs_per_repeat'])
        bands=prepared['carving_bands']
        self.assertAlmostEqual(1,sum(b['height_m'] for b in bands))
        for band in bands:
            if band['uniform_scale'] is not None:
                x0,x1=band['source_x']; y0,y1=band['source_y']
                self.assertAlmostEqual(2,(x1-x0)*band['uniform_scale']*band['cycles'])
                self.assertAlmostEqual(band['height_m'],(y1-y0)*band['uniform_scale'])
        main=next(b for b in bands if b['name']=='main_panel')
        self.assertAlmostEqual(2/1.474,main['uniform_scale'])
        self.assertEqual(['straight_0','straight_1'],[key for key in self.parts if key.startswith('straight_')])
        # Both block halves contain their small pillar and the restored large
        # boundary pillar. Sample their shafts at the same original source height.
        y=main['output_y'][0]+(-.15-main['source_y'][0])*main['uniform_scale']
        for section,source_xs in [(0,(-.730,-.420)),(1,(.420,.730))]:
            for source_x in source_xs:
                x=(source_x+.737)*main['uniform_scale']-section
                nearby=[p for tri in self.parts[f'straight_{section}'] for p in tri
                        if abs(p[0]-x)<.045 and abs(p[1]-y)<.10 and p[2]>.09]
                self.assertGreater(len(nearby),10,'Missing large or small pillar shaft')


if __name__=='__main__': unittest.main()
