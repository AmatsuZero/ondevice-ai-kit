import os
import shutil
import stat
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import terminal_file_io as tfio


def _tmp_root():
    cand = Path('/private/tmp')
    if cand.is_dir() and os.access(str(cand), os.W_OK):
        return cand
    return Path(tempfile.gettempdir())


def _rmtree(p):
    shutil.rmtree(p, ignore_errors=True)


class TestReadStable(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp(dir=str(_tmp_root())))
        self.addCleanup(lambda: _rmtree(self.root))

    def _write(self, rel, data):
        p = self.root / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)
        return p

    def test_positive_small(self):
        data = b'hello world'
        self._write('a.txt', data)
        self.assertEqual(tfio.read_stable(self.root, 'a.txt', 100), data)

    def test_positive_exact_max(self):
        data = b'x' * 50
        self._write('b.txt', data)
        self.assertEqual(tfio.read_stable(self.root, 'b.txt', 50), data)

    def test_absolute_path_rejected(self):
        self._write('c.txt', b'data')
        with self.assertRaises(ValueError):
            tfio.read_stable(self.root, '/etc/passwd', 10)

    def test_empty_relative_rejected(self):
        self._write('d.txt', b'data')
        with self.assertRaises(ValueError):
            tfio.read_stable(self.root, '', 10)

    def test_empty_component_rejected(self):
        self._write('d.txt', b'data')
        with self.assertRaises(ValueError):
            tfio.read_stable(self.root, 'sub//d.txt', 10)

    def test_dotdot_escape_rejected(self):
        self._write('e.txt', b'data')
        with self.assertRaises(ValueError):
            tfio.read_stable(self.root, '../escape.txt', 10)

    def test_root_symlink_rejected(self):
        real = Path(tempfile.mkdtemp(dir=str(_tmp_root())))
        self.addCleanup(lambda: _rmtree(real))
        (real / 'f.txt').write_bytes(b'data')
        link_name = self.root / 'rootlink'
        os.symlink(real, link_name)
        with self.assertRaises(ValueError):
            tfio.read_stable(link_name, 'f.txt', 10)

    def test_report_symlink_rejected(self):
        target = self._write('real.txt', b'data')
        link = self.root / 'link.txt'
        os.symlink(target, link)
        with self.assertRaises(ValueError):
            tfio.read_stable(self.root, 'link.txt', 10)

    def test_parent_symlink_rejected(self):
        real_sub = Path(tempfile.mkdtemp(dir=str(_tmp_root())))
        self.addCleanup(lambda: _rmtree(real_sub))
        (real_sub / 'inner.txt').write_bytes(b'data')
        link_sub = self.root / 'sublink'
        os.symlink(real_sub, link_sub)
        with self.assertRaises(ValueError):
            tfio.read_stable(self.root, 'sublink/inner.txt', 10)

    def test_missing_file_rejected(self):
        with self.assertRaises((ValueError, OSError)):
            tfio.read_stable(self.root, 'nope.txt', 10)

    def test_nonregular_dir_rejected(self):
        d = self.root / 'adir'
        d.mkdir()
        with self.assertRaises(ValueError):
            tfio.read_stable(self.root, 'adir', 10)

    def test_empty_file_rejected(self):
        self._write('empty.txt', b'')
        with self.assertRaises(ValueError):
            tfio.read_stable(self.root, 'empty.txt', 10)

    def test_oversize_rejected(self):
        self._write('big.txt', b'z' * 101)
        with self.assertRaises(ValueError):
            tfio.read_stable(self.root, 'big.txt', 100)

    def test_invalid_max_bytes_rejected(self):
        self._write('g.txt', b'data')
        for bad in (0, -1, 1048577, 'x'):
            with self.subTest(max=bad):
                with self.assertRaises(ValueError):
                    tfio.read_stable(self.root, 'g.txt', bad)

    def test_growth_detected(self):
        p = self._write('grow.txt', b'x' * 100)
        fixture_st = os.stat(p)
        real_read = os.read
        state = {'done': False}
        def fake_read(fd, n, *a, **k):
            st = os.fstat(fd)
            if (not state['done']
                    and st.st_dev == fixture_st.st_dev
                    and st.st_ino == fixture_st.st_ino):
                state['done'] = True
                with open(p, 'ab') as af:
                    af.write(b'x' * 50)
            return real_read(fd, n, *a, **k)
        with mock.patch('os.read', side_effect=fake_read):
            with self.assertRaises(ValueError):
                tfio.read_stable(self.root, 'grow.txt', 100)
        self.assertTrue(state['done'])

    def test_inode_replacement_detected(self):
        p = self._write('swap.txt', b'y' * 50)
        fixture_st = os.stat(p)
        real_read = os.read
        real_rename = os.rename
        state = {'done': False}
        def fake_read(fd, n, *a, **k):
            st = os.fstat(fd)
            if (not state['done']
                    and st.st_dev == fixture_st.st_dev
                    and st.st_ino == fixture_st.st_ino):
                state['done'] = True
                tmp = self.root / '.swap_tmp'
                with open(tmp, 'wb') as tf:
                    tf.write(b'z' * 60)
                real_rename(tmp, p)
            return real_read(fd, n, *a, **k)
        with mock.patch('os.read', side_effect=fake_read):
            with self.assertRaises(ValueError):
                tfio.read_stable(self.root, 'swap.txt', 50)
        self.assertTrue(state['done'])

    def test_symlink_replacement_during_read_rejected(self):
        p = self._write('repl.txt', b'q' * 40)
        os.link(p, self.root / 'repl_hard.txt')
        orig_st = os.stat(p)
        real_read = os.read
        state = {'done': False}
        def fake_read(fd, n, *a, **k):
            st = os.fstat(fd)
            if (not state['done']
                    and st.st_dev == orig_st.st_dev
                    and st.st_ino == orig_st.st_ino):
                state['done'] = True
                os.unlink(p)
                os.symlink(self.root / 'repl_hard.txt', p)
            return real_read(fd, n, *a, **k)
        with mock.patch('os.read', side_effect=fake_read):
            with self.assertRaises(ValueError):
                tfio.read_stable(self.root, 'repl.txt', 40)
        self.assertTrue(state['done'])


class TestWriteNewDurable(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp(dir=str(_tmp_root())))
        self.addCleanup(lambda: _rmtree(self.root))

    def _write_existing(self, rel, data):
        p = self.root / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)
        return p

    def test_positive_exact_bytes(self):
        payload = b'payload-data'
        tfio.write_new_durable(self.root, 'w.txt', payload)
        self.assertEqual((self.root / 'w.txt').read_bytes(), payload)

    def test_positive_large_payload(self):
        payload = b'A' * 1048576
        tfio.write_new_durable(self.root, 'big.bin', payload)
        self.assertEqual((self.root / 'big.bin').read_bytes(), payload)

    def test_leaves_other_files_unchanged(self):
        other = self._write_existing('other.txt', b'KEEP')
        tfio.write_new_durable(self.root, 'new.txt', b'new')
        self.assertEqual(other.read_bytes(), b'KEEP')

    def test_existing_file_rejected(self):
        self._write_existing('exists.txt', b'OLD')
        with self.assertRaises(ValueError):
            tfio.write_new_durable(self.root, 'exists.txt', b'NEW')

    def test_existing_symlink_not_overwritten(self):
        target = self._write_existing('target.txt', b'OLD')
        link = self.root / 'link.txt'
        os.symlink(target, link)
        with self.assertRaises((ValueError, OSError)):
            tfio.write_new_durable(self.root, 'link.txt', b'NEW')
        self.assertEqual(target.read_bytes(), b'OLD')

    def test_absolute_path_rejected(self):
        with self.assertRaises(ValueError):
            tfio.write_new_durable(self.root, '/tmp/evil.txt', b'x')

    def test_dotdot_escape_rejected(self):
        with self.assertRaises(ValueError):
            tfio.write_new_durable(self.root, '../escape.txt', b'x')

    def test_empty_relative_rejected(self):
        with self.assertRaises(ValueError):
            tfio.write_new_durable(self.root, '', b'x')

    def test_empty_component_rejected(self):
        with self.assertRaises(ValueError):
            tfio.write_new_durable(self.root, 'a//b.txt', b'x')

    def test_missing_parent_rejected(self):
        with self.assertRaises((ValueError, OSError)):
            tfio.write_new_durable(self.root, 'missing_dir/file.txt', b'x')

    def test_invalid_payload_rejected(self):
        for payload, label in [
            (b'', 'empty'), (b'x' * 1048577, 'oversize'),
            ('notbytes', 'not-bytes'), (123, 'int'),
            (bytearray(b'x'), 'bytearray'),
        ]:
            with self.subTest(label=label):
                with self.assertRaises(ValueError):
                    tfio.write_new_durable(self.root, 'p.txt', payload)

    def test_target_absent_during_write(self):
        payload = b'target-data'
        target = self.root / 'w.txt'
        real_write = os.write
        def fake_write(fd, data, *a, **k):
            self.assertFalse(target.exists())
            return real_write(fd, data, *a, **k)
        with mock.patch('os.write', side_effect=fake_write):
            tfio.write_new_durable(self.root, 'w.txt', payload)
        self.assertEqual(target.read_bytes(), payload)

    def test_fsync_ordering_file_before_parent(self):
        order = []
        real_fstat = os.fstat
        real_fsync = os.fsync
        def track_fsync(fd, *a, **k):
            st = real_fstat(fd)
            if stat.S_ISREG(st.st_mode):
                order.append('file')
            elif stat.S_ISDIR(st.st_mode):
                order.append('dir')
            return real_fsync(fd, *a, **k)
        with mock.patch('os.fsync', side_effect=track_fsync):
            tfio.write_new_durable(self.root, 'order.txt', b'data')
        self.assertIn('file', order)
        self.assertIn('dir', order)
        self.assertLess(order.index('file'), order.index('dir'))

    def test_fsync_failure_keeps_preexisting(self):
        other = self._write_existing('sibling.txt', b'KEEP')
        target = self.root / 'newfail.txt'
        def fail_fsync(fd, *a, **k):
            raise OSError('fsync failed')
        with mock.patch('os.fsync', side_effect=fail_fsync):
            with self.assertRaises(OSError):
                tfio.write_new_durable(self.root, 'newfail.txt', b'data')
        self.assertFalse(target.exists())
        self.assertTrue(other.exists())
        self.assertEqual(other.read_bytes(), b'KEEP')
        self.assertEqual(sorted(os.listdir(self.root)),
                         sorted([os.fsdecode(other.name)]))

    def test_partial_os_write_completes(self):
        payload = b'x' * 2000
        target = self.root / 'partial.txt'
        real_write = os.write
        state = {'calls': 0}
        def partial_write(fd, data, *a, **k):
            state['calls'] += 1
            if state['calls'] == 1 and len(data) > 1:
                return real_write(fd, data[:1], *a, **k)
            return real_write(fd, data, *a, **k)
        with mock.patch('os.write', side_effect=partial_write):
            tfio.write_new_durable(self.root, 'partial.txt', payload)
        self.assertEqual(target.read_bytes(), payload)


if __name__ == '__main__':
    unittest.main()
