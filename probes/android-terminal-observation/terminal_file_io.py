"""Local single-writer file primitives.

Scope / assumptions:
- Private single-writer directory: only this process creates files inside
  `root`. inode comparison cannot atomically conditional-unlink against a
  malicious same-UID concurrent replacer, and these primitives alone do not
  imply whole-driver / whole-filesystem durability.
- No ADB, no observation coordinator, no device acceptance logic.
- Python stdlib only.
"""

import errno
import os
import stat
from pathlib import Path

MAX_BYTES = 1048576


class PublishedButNotDurableError(OSError):
    published = True
    durability_verified = False

    def __init__(self, message, *, staging_may_remain=False):
        self.staging_may_remain = staging_may_remain
        super().__init__(message)


def _validate_root(root):
    root_path = Path(root)
    root_abs = root_path.absolute()
    try:
        root_resolved = root_path.resolve(strict=True)
    except FileNotFoundError:
        raise OSError('root path does not exist: %s' % (root,))
    except OSError as exc:
        raise OSError('cannot resolve root path: %s' % (exc,))
    if root_abs != root_resolved:
        raise ValueError(
            'root must be a canonical absolute path without symlink ancestors')
    if not root_resolved.is_dir():
        raise ValueError('root must be an existing directory')
    return root_resolved


def _split_relative(relative):
    if not isinstance(relative, str) or relative == '':
        raise ValueError('relative must be a nonempty string')
    if relative.startswith('/') or relative.startswith(os.sep):
        raise ValueError('relative must be a plain relative path')
    parts = relative.replace(os.sep, '/').split('/')
    for part in parts:
        if part == '':
            raise ValueError('empty path component')
        if part in ('.', '..'):
            raise ValueError('invalid path component')
        if chr(0) in part:
            raise ValueError('NUL in path component')
    return parts


def _open_component(dirfd, name, is_dir):
    flags = os.O_RDONLY | os.O_NOFOLLOW
    if is_dir:
        flags |= os.O_DIRECTORY
    try:
        if dirfd >= 0:
            return os.open(name, flags, dir_fd=dirfd)
        return os.open(name, flags)
    except OSError as exc:
        if exc.errno == errno.ELOOP:
            raise ValueError('symlink not allowed in path')
        if is_dir and exc.errno == errno.ENOTDIR:
            raise ValueError('invalid path shape: component not a directory')
        raise


def _revalidate_named_chain(named_root, fds, parts):
    """Re-walk the NAMED path and confirm every component still refers to the
    same (dev, ino) as the retained descriptor opened earlier, using nofollow
    semantics. fds[0] is the retained root descriptor; fds[i + 1] is the
    retained descriptor for parts[i]. This rejects reads/writes where the
    named root or a named parent directory was renamed or replaced so the name
    now points elsewhere."""
    root_cur = _open_component(-1, named_root, True)
    try:
        rs = os.fstat(root_cur)
        rf = os.fstat(fds[0])
        if rs.st_dev != rf.st_dev or rs.st_ino != rf.st_ino:
            raise ValueError('unstable: root path replaced')
    finally:
        os.close(root_cur)
    for i in range(len(parts) - 1):
        cur = _open_component(fds[i], parts[i], True)
        try:
            cs = os.fstat(cur)
            retained = os.fstat(fds[i + 1])
            if cs.st_dev != retained.st_dev or cs.st_ino != retained.st_ino:
                raise ValueError('unstable: directory path replaced')
        finally:
            os.close(cur)


def read_stable(root, relative, max_bytes):
    root_path = _validate_root(root)
    if type(max_bytes) is not int:
        raise ValueError('max_bytes must be an exact int')
    if max_bytes < 1 or max_bytes > MAX_BYTES:
        raise ValueError('max_bytes out of range')
    if type(relative) is not str:
        raise ValueError('relative must be a string')
    parts = _split_relative(relative)

    fds = []
    filefd = -1
    try:
        root_fd = _open_component(-1, str(root_path), True)
        fds.append(root_fd)
        parent = root_fd
        for part in parts[:-1]:
            nfd = _open_component(parent, part, True)
            fds.append(nfd)
            parent = nfd
        dirfd = parent

        final = parts[-1]
        try:
            filefd = os.open(final,
                             os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK,
                             dir_fd=dirfd)
        except OSError as exc:
            if exc.errno == errno.ELOOP:
                raise ValueError('symlink not allowed in path')
            raise

        st = os.fstat(filefd)
        if stat.S_ISLNK(st.st_mode):
            raise ValueError('symlink not allowed')
        if not stat.S_ISREG(st.st_mode):
            raise ValueError('not a regular file')
        if st.st_size == 0:
            raise ValueError('empty file')
        if st.st_size > max_bytes:
            raise ValueError('file exceeds max_bytes')

        st_before = st
        data = os.read(filefd, max_bytes + 1)
        if len(data) == 0:
            raise ValueError('unstable: empty read')
        if len(data) > max_bytes:
            raise ValueError('unstable: content exceeded max_bytes')
        if len(data) != st_before.st_size:
            raise ValueError('unstable: short read')

        st_after = os.fstat(filefd)
        if (st_after.st_size != st_before.st_size
                or st_after.st_mtime_ns != st_before.st_mtime_ns
                or st_after.st_ctime_ns != st_before.st_ctime_ns):
            raise ValueError('unstable: metadata changed')

        path_st = os.stat(final, dir_fd=dirfd, follow_symlinks=False)
        if path_st.st_dev != st_after.st_dev or path_st.st_ino != st_after.st_ino:
            raise ValueError('unstable: path replaced')

        _revalidate_named_chain(str(root_path), fds, parts)

        return data
    finally:
        if filefd >= 0:
            os.close(filefd)
        for fd in fds:
            if fd >= 0:
                os.close(fd)


def write_new_durable(root, relative, payload):
    root_path = _validate_root(root)
    if type(payload) is not bytes:
        raise ValueError('payload must be bytes')
    if len(payload) == 0:
        raise ValueError('payload must be nonempty')
    if len(payload) > MAX_BYTES:
        raise ValueError('payload exceeds max size')
    if type(relative) is not str:
        raise ValueError('relative must be a string')
    parts = _split_relative(relative)

    fds = []
    filefd = -1
    staging = None
    staging_ino = None
    try:
        root_fd = _open_component(-1, str(root_path), True)
        fds.append(root_fd)
        parent = root_fd
        for part in parts[:-1]:
            nfd = _open_component(parent, part, True)
            fds.append(nfd)
            parent = nfd
        dirfd = parent

        final = parts[-1]
        try:
            os.stat(final, dir_fd=dirfd, follow_symlinks=False)
            raise ValueError('target already exists')
        except OSError as exc:
            if exc.errno != errno.ENOENT:
                raise

        while True:
            staging = '.' + os.urandom(16).hex()
            try:
                filefd = os.open(staging,
                                 os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                                 0o600, dir_fd=dirfd)
            except OSError as exc:
                if exc.errno == errno.EEXIST:
                    continue
                raise
            break
        staging_ino = os.fstat(filefd).st_ino

        view = memoryview(payload)
        offset = 0
        total = len(view)
        while offset < total:
            prev = offset
            n = os.write(filefd, view[offset:])
            if n <= 0:
                raise OSError('zero-progress write')
            offset += n
            if offset <= prev:
                raise OSError('no progress writing payload')

        os.fsync(filefd)
        _revalidate_named_chain(str(root_path), fds, parts)
        try:
            os.link(staging, final, src_dir_fd=dirfd, dst_dir_fd=dirfd)
        except OSError as exc:
            if exc.errno == errno.EEXIST:
                raise FileExistsError('target exists')
            raise
        try:
            os.unlink(staging, dir_fd=dirfd)
        except OSError as exc:
            raise PublishedButNotDurableError('published target; directory durability unverified: ' + str(exc), staging_may_remain=True) from exc
        staging = None
        try:
            os.fsync(dirfd)
        except OSError as exc:
            raise PublishedButNotDurableError('published target; directory durability unverified: ' + str(exc)) from exc
    except Exception:
        if staging is not None and staging_ino is not None and fds:
            parent_fd = fds[-1]
            try:
                cur = os.stat(staging, dir_fd=parent_fd, follow_symlinks=False)
                if cur.st_ino == staging_ino:
                    os.unlink(staging, dir_fd=parent_fd)
            except OSError:
                pass
        raise
    finally:
        if filefd >= 0:
            os.close(filefd)
        for fd in fds:
            if fd >= 0:
                os.close(fd)
