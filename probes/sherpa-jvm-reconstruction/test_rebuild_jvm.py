import hashlib, tempfile, unittest
from pathlib import Path
import rebuild_jvm as r

def sha(data): return hashlib.sha256(data).hexdigest()
def blob(data): return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
def make_link(root, name, data, **over):
    p = root / name; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(data)
    link = {'resolvedEvidencePath': str(p), 'resolvedMode': '100644', 'resolvedBytes': len(data), 'resolvedSHA256': sha(data), 'resolvedGitBlobSHA1': blob(data)}
    link.update(over)
    return link
def mapping(links): return {'sourceCommit': r.SOURCE_COMMIT, 'links': links}
def pair(cls=b'class', mod=b'module'): return {r.MODULE: mod, 'A.class': cls}

class SourceTests(unittest.TestCase):
    def test_accepts_one(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); link = make_link(root, 'A.kt', b'source')
            got = r.verify_sources(root, mapping([link]), expected_count=1)
            self.assertEqual(got, [root / 'A.kt'])
    def test_rejects_duplicate(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            a = make_link(root, 'a/A.kt', b'one'); b = make_link(root, 'b/A.kt', b'two')
            with self.assertRaises(ValueError): r.verify_sources(root, mapping([a, b]), expected_count=2)
    def test_rejects_tamper(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); link = make_link(root, 'A.kt', b'abcd')
            (root / 'A.kt').write_bytes(b'abce')
            with self.assertRaises(ValueError): r.verify_sources(root, mapping([link]), expected_count=1)

class CompareTests(unittest.TestCase):
    def test_exact(self):
        s = r.compare_members(pair(), pair(), expected_class_count=1)
        self.assertTrue(s['accepted']); self.assertTrue(s['moduleMetadataEqual']); self.assertEqual(s['matchedClassCount'], 1)
    def test_same_length_mutation(self):
        s = r.compare_members(pair(b'aaaa'), pair(b'aaab'), expected_class_count=1)
        self.assertFalse(s['accepted']); self.assertEqual(s['different'], ['A.class'])
    def test_missing_and_extra(self):
        s = r.compare_members(pair(), {r.MODULE: b'module'}, expected_class_count=1)
        self.assertFalse(s['accepted']); self.assertEqual(s['missing'], ['A.class'])
        s = r.compare_members(pair(), {**pair(), 'B.class': b'b'}, expected_class_count=1)
        self.assertFalse(s['accepted']); self.assertEqual(s['extra'], ['B.class'])
    def test_empty_and_wrong_counts(self):
        self.assertFalse(r.compare_members({}, {})['accepted'])
        s = r.compare_members(pair(), {**pair(), 'B.class': b'b'}, expected_class_count=1)
        self.assertFalse(s['accepted']); self.assertEqual(s['rebuiltClassCount'], 2)
    def test_module_mismatch(self):
        s = r.compare_members(pair(mod=b'm1'), pair(mod=b'm2'), expected_class_count=1)
        self.assertFalse(s['accepted']); self.assertFalse(s['moduleMetadataEqual'])

if __name__ == '__main__': unittest.main()
