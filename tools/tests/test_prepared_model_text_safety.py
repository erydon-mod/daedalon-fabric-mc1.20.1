import unittest
from daedalon_test_support import MESH_ROOT,REPO_ROOT,load_json,sha256

class PreparedModelTextSafetyTests(unittest.TestCase):
    def test_hash_locked_exports_match_git_lf_checkout_bytes(self):
        for family in ('corinthian-frieze','ionic-frieze','gothic-frieze','byzantine-frieze','anthophoros'):
            evidence=load_json(REPO_ROOT/f'docs/evidence/{family}-source.json')
            for name,digest in evidence['outputs'].items():
                path=MESH_ROOT/name
                self.assertNotIn(b'\r\n',path.read_bytes(),f'{name}: checksum changes when Git normalizes line endings')
                self.assertEqual(digest,sha256(path))

if __name__=='__main__':unittest.main()
