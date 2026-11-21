"""Primary-owned acceptance checks; no device or implementation substitutions."""
import os
import stat
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import terminal_file_io as fio


class FileBoundaries(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(dir='/private/tmp', prefix='w3-file-accept-')
        self.addCleanup(temp.cleanup)
        self.base = Path(temp.name).resolve()
        self.root = self.base / 'root'
        self.root.mkdir()
        (self.root / 'report').write_bytes(b'complete-report')

    def test_exact_int_limit(self):
        class IntSubclass(int):
            pass
        with self.assertRaises(ValueError):
            fio.read_stable(self.root, 'report', IntSubclass(64))

    def test_exact_string_relative(self):
        class StrSubclass(str):
            pass
        with self.assertRaises(ValueError):
            fio.read_stable(self.root, StrSubclass('report'), 64)

    def test_symlink_ancestor_of_root_read(self):
        alias = self.base / 'alias'
        alias.symlink_to(self.base, target_is_directory=True)
        with self.assertRaises(ValueError):
            fio.read_stable(alias / 'root', 'report', 64)

    def test_symlink_ancestor_of_root_write(self):
        alias = self.base / 'alias'
        alias.symlink_to(self.base, target_is_directory=True)
        with self.assertRaises(ValueError):
            fio.write_new_durable(alias / 'root', 'ack', b'ack')
        self.assertFalse((self.root / 'ack').exists())

    def _mutate_during_read(self, relative, action):
        identity = (self.root / relative).stat()
        real_read = os.read
        occurred = []

        def read(fd, count):
            current = os.fstat(fd)
            data = real_read(fd, count)
            if not occurred and (current.st_dev, current.st_ino) == (
                    identity.st_dev, identity.st_ino):
                occurred.append(True)
                action()
            return data

        try:
            with mock.patch('os.read', side_effect=read):
                with self.assertRaises((ValueError, OSError)):
                    fio.read_stable(self.root, relative, 64)
        finally:
            self.assertEqual(occurred, [True], 'fixture mutation must execute')

    def test_root_replacement_during_read(self):
        def replace():
            self.root.rename(self.base / 'old-root')
            self.root.mkdir()
            (self.root / 'report').write_bytes(b'replacement')
        self._mutate_during_read('report', replace)

    def test_parent_replacement_during_read(self):
        parent = self.root / 'nested'
        parent.mkdir()
        (parent / 'report').write_bytes(b'complete-report')

        def replace():
            parent.rename(self.base / 'old-parent')
            parent.mkdir()
            (parent / 'report').write_bytes(b'replacement')
        self._mutate_during_read('nested/report', replace)

    def test_short_read_is_rejected(self):
        real_read = os.read

        def short(fd, count):
            return real_read(fd, count)[:1]

        with mock.patch('os.read', side_effect=short):
            with self.assertRaises(ValueError):
                fio.read_stable(self.root, 'report', 64)

    def test_zero_progress_write_cleans_owned_stage(self):
        with mock.patch('os.write', return_value=0):
            with self.assertRaises(OSError):
                fio.write_new_durable(self.root, 'ack', b'ack')
        self.assertEqual(sorted(p.name for p in self.root.iterdir()), ['report'])

    def test_write_error_cleans_owned_stage(self):
        with mock.patch('os.write', side_effect=OSError('fixture-write-failure')):
            with self.assertRaisesRegex(OSError, 'fixture-write-failure') as caught:
                fio.write_new_durable(self.root, 'ack', b'ack')
        self.assertIs(type(caught.exception), OSError)
        self.assertEqual(sorted(p.name for p in self.root.iterdir()), ['report'])

    def test_publication_collision_preserves_other_writer(self):
        real_link = os.link
        called = []

        def link(src, dst, *args, **kwargs):
            called.append(True)
            (self.root / 'ack').write_bytes(b'other-writer')
            return real_link(src, dst, *args, **kwargs)

        with mock.patch('os.link', side_effect=link):
            with self.assertRaises((ValueError, FileExistsError)):
                fio.write_new_durable(self.root, 'ack', b'ack')
        self.assertEqual(called, [True])
        self.assertEqual((self.root / 'ack').read_bytes(), b'other-writer')
        self.assertEqual(sorted(p.name for p in self.root.iterdir()), ['ack', 'report'])

    def test_file_fsync_precedes_publication(self):
        real_fsync, real_link = os.fsync, os.link
        file_synced = []

        def fsync(fd):
            if stat.S_ISREG(os.fstat(fd).st_mode):
                file_synced.append(True)
            return real_fsync(fd)

        def link(src, dst, *args, **kwargs):
            self.assertEqual(file_synced, [True])
            self.assertFalse((self.root / 'ack').exists())
            return real_link(src, dst, *args, **kwargs)

        with mock.patch('os.fsync', side_effect=fsync), mock.patch('os.link', side_effect=link):
            fio.write_new_durable(self.root, 'ack', b'ack')
        self.assertEqual((self.root / 'ack').read_bytes(), b'ack')

    def test_directory_fsync_failure_can_leave_published_target(self):
        real_fsync = os.fsync
        observed = []

        def fsync(fd):
            if stat.S_ISDIR(os.fstat(fd).st_mode):
                observed.append('directory-fsync-failed')
                raise OSError('fixture-directory-fsync-failure')
            return real_fsync(fd)

        with mock.patch('os.fsync', side_effect=fsync):
            with self.assertRaisesRegex(OSError, 'fixture-directory-fsync-failure') as caught:
                fio.write_new_durable(self.root, 'ack', b'ack')
        self.assertIs(type(caught.exception), fio.PublishedButNotDurableError)
        self.assertIs(caught.exception.published, True)
        self.assertIs(caught.exception.durability_verified, False)
        self.assertIs(caught.exception.staging_may_remain, False)
        self.assertIsInstance(caught.exception.__cause__, OSError)
        self.assertIn('published target; directory durability unverified', str(caught.exception))
        self.assertEqual(observed, ['directory-fsync-failed'])
        self.assertEqual((self.root / 'ack').read_bytes(), b'ack')
        self.assertEqual(sorted(p.name for p in self.root.iterdir()), ['ack', 'report'])

    def test_postpublication_unlink_failure_preserves_complete_target(self):
        real_unlink = os.unlink

        def unlink(path, *args, **kwargs):
            if kwargs.get('dir_fd') is not None and os.fsdecode(path).startswith('.'):
                raise OSError('fixture-staging-unlink-failure')
            return real_unlink(path, *args, **kwargs)

        with mock.patch('os.unlink', side_effect=unlink):
            with self.assertRaisesRegex(OSError, 'fixture-staging-unlink-failure') as caught:
                fio.write_new_durable(self.root, 'ack', b'ack')
        self.assertIs(type(caught.exception), fio.PublishedButNotDurableError)
        self.assertIs(caught.exception.published, True)
        self.assertIs(caught.exception.durability_verified, False)
        self.assertIs(caught.exception.staging_may_remain, True)
        self.assertIsInstance(caught.exception.__cause__, OSError)
        self.assertIn('published target; directory durability unverified', str(caught.exception))
        self.assertEqual((self.root / 'ack').read_bytes(), b'ack')
        self.assertEqual((self.root / 'report').read_bytes(), b'complete-report')
        stages = [p for p in self.root.iterdir() if p.name.startswith('.')]
        self.assertEqual(len(stages), 1)
        self.assertEqual(stages[0].stat().st_ino, (self.root / 'ack').stat().st_ino)


if __name__ == '__main__':
    unittest.main()
