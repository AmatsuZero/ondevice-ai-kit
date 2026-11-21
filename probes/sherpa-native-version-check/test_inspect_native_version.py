import struct
import unittest

from inspect_native_version import inspect_binary

NAMES = (b'SherpaOnnxGetVersionStr', b'SherpaOnnxGetGitSha1', b'SherpaOnnxGetGitDate')
MODES = {'a64': (64, 183, 12), 'thumb': (32, 40, 16), 'x86': (32, 3, 28), 'x64': (64, 62, 8)}
BASE = 0x100000
PT_LOAD = 1
PF_R = 4
PF_X = 1
VALUES = (b'v1', b'sha1-test', b'2026-11-15')


def _bs(*xs):
    return bytes(xs)


def _align(n, a):
    return (n + a - 1) // a * a


def _header(cls, machine, phoff, shoff, phnum):
    ident = bytes([0x7f]) + b'ELF' + bytes([1 if cls == 32 else 2, 1, 1, 0]) + bytes(8)
    if cls == 32:
        return ident + struct.pack('<HHIIIIIHHHHHH', 3, machine, 1, 0, phoff, shoff, 0, 52, 32, phnum, 40, 3, 0)
    return ident + struct.pack('<HHIQQQIHHHHHH', 3, machine, 1, 0, phoff, shoff, 0, 64, 56, phnum, 64, 3, 0)


def _phdr(cls, typ, off, va, size, flags):
    if cls == 32:
        return struct.pack('<IIIIIIII', typ, off, va, va, size, size, flags, 0x1000)
    return struct.pack('<IIQQQQQQ', typ, flags, off, va, va, size, size, 0x1000)


def _shdr(cls, typ, off, size, link=0, entsize=0):
    if cls == 32:
        return struct.pack('<IIIIIIIIII', 0, typ, 0, BASE + off, off, size, link, 0, 1, entsize)
    return struct.pack('<IIQQQQIIQQ', 0, typ, 0, BASE + off, off, size, link, 0, 1, entsize)


def _symbol(cls, nameoff, value, size):
    if cls == 32:
        return struct.pack('<IIIBBH', nameoff, value & 0xffffffff, size, 0x12, 0, 1)
    return struct.pack('<IBBHQQ', nameoff, 0x12, 0, 1, value & 0xffffffffffffffff, size)


def _encode_code(mode, code_va, lit_va):
    if mode == 'a64':
        page = code_va & ~0xfff
        target = lit_va & ~0xfff
        delta = (target - page) >> 12
        assert -0x100000 <= delta < 0x100000
        raw = delta & 0x1fffff
        adrp = (1 << 31) | (0x10 << 24) | ((raw & 3) << 29) | ((raw >> 2) << 5)
        addimm = lit_va - target
        assert 0 <= addimm <= 0xfff
        add = (1 << 31) | (0x11 << 24) | (addimm << 10)
        return struct.pack('<III', adrp, add, 0xd65f03c0)
    if mode == 'thumb':
        pool = (lit_va - ((code_va + 10) & 0xffffffff)) & 0xffffffff
        assert ((code_va + 8) & ~3) + 4 == code_va + 12
        return struct.pack('<HHHHHHI', 0xb580, 0x466f, 0x4801, 0x4478, 0xbd80, 0xbf00, pool)
    if mode == 'x86':
        nxt = (code_va + 11) & 0xffffffff
        lit = lit_va & 0xffffffff
        imm = (lit - nxt - 0x1234) & 0xffffffff
        disp = lit - ((nxt + imm) & 0xffffffff)
        assert disp == 0x1234
        return (_bs(0x55, 0x89, 0xe5, 0x83, 0xe4, 0xfc, 0xe8) + bytes(4) +
                _bs(0x58, 0x81, 0xc0) + struct.pack('<I', imm) +
                _bs(0x8d, 0x80) + struct.pack('<i', disp) +
                _bs(0x89, 0xec, 0x5d, 0xc3))
    disp = lit_va - (code_va + 7)
    return _bs(0x48, 0x8d, 0x05) + struct.pack('<i', disp) + _bs(0xc3)


def _fixture(mode, values=None, phnum=1):
    cls, machine, fsize = MODES[mode]
    ehsize, phsize, shsize, symsize = (52, 32, 40, 16) if cls == 32 else (64, 56, 64, 24)
    phoff = ehsize
    shoff = _align(phoff + 2 * phsize, 8)
    str_off = shoff + 3 * shsize
    strtab = bytearray(bytes(1))
    nameoffs = []
    for name in NAMES:
        nameoffs.append(len(strtab))
        strtab.extend(name)
        strtab.extend(bytes(1))
    dynsym_off = _align(str_off + len(strtab), 8)
    code_off = _align(dynsym_off + 3 * symsize, 16)
    literal_off = _align(code_off + 3 * fsize, 8)
    vals = VALUES if values is None else values
    lits = []
    litoffs = []
    cur = literal_off
    for val in vals:
        litoffs.append(cur)
        part = bytes(val) + bytes(1)
        lits.append(part)
        cur += len(part)
    buf = bytearray(cur)
    buf[:ehsize] = _header(cls, machine, phoff, shoff, phnum)
    buf[phoff:phoff + phsize] = _phdr(cls, PT_LOAD, 0, BASE, cur, PF_R | PF_X)
    sh0 = bytes(shsize)
    shstr = _shdr(cls, 3, str_off, len(strtab))
    shdyn = _shdr(cls, 11, dynsym_off, 3 * symsize, 1, symsize)
    buf[shoff:shoff + shsize] = sh0
    buf[shoff + shsize:shoff + 2 * shsize] = shstr
    buf[shoff + 2 * shsize:shoff + 3 * shsize] = shdyn
    buf[str_off:str_off + len(strtab)] = strtab
    for i in range(3):
        code_va = BASE + code_off + i * fsize
        lit_va = BASE + litoffs[i]
        code = _encode_code(mode, code_va, lit_va)
        assert len(code) == fsize
        buf[code_off + i * fsize:code_off + (i + 1) * fsize] = code
        value = code_va | 1 if machine == 40 else code_va
        sym = _symbol(cls, nameoffs[i], value, fsize)
        buf[dynsym_off + i * symsize:dynsym_off + (i + 1) * symsize] = sym
    pos = literal_off
    for part in lits:
        buf[pos:pos + len(part)] = part
        pos += len(part)
    meta = dict(cls=cls, machine=machine, mode=mode, ehsize=ehsize, phoff=phoff,
                phsize=phsize, shoff=shoff, shsize=shsize, str_off=str_off,
                dynsym_off=dynsym_off, symsize=symsize, code_off=code_off,
                fsize=fsize, literal_off=literal_off, file_size=cur, base=BASE,
                litoffs=dict(zip(NAMES, litoffs)), nameoffs=dict(zip(NAMES, nameoffs)))
    return buf, meta


def _set_symbol_name(buf, meta, idx, nameoff):
    struct.pack_into('<I', buf, meta['dynsym_off'] + idx * meta['symsize'], nameoff)


def _set_symbol_value(buf, meta, idx, value):
    off = meta['dynsym_off'] + idx * meta['symsize'] + (8 if meta['cls'] == 64 else 4)
    struct.pack_into('<Q' if meta['cls'] == 64 else '<I', buf, off, value)


class InspectNativeVersionTests(unittest.TestCase):
    def _inspect(self, mode, values=None):
        buf, meta = _fixture(mode, values)
        return inspect_binary(bytes(buf)), meta

    def test_supported_architectures_and_binding(self):
        expected = {name.decode(): val.decode() for name, val in zip(NAMES, VALUES)}
        for mode in MODES:
            with self.subTest(mode=mode):
                result, meta = self._inspect(mode)
                self.assertEqual(result['elf_class'], meta['cls'])
                self.assertEqual(result['machine'], meta['machine'])
                self.assertEqual({k: v['value'] for k, v in result['symbols'].items()}, expected)
                sym = result['symbols'][NAMES[0].decode()]
                self.assertEqual(sym['literalFileOffset'], meta['litoffs'][NAMES[0]])
                self.assertGreaterEqual(sym['codeFileOffset'], meta['code_off'])
                self.assertTrue(sym['codeHex'])

    def test_literal_offset_arithmetic_for_each_architecture(self):
        for mode in MODES:
            with self.subTest(mode=mode):
                buf, meta = _fixture(mode)
                code = meta['code_off']
                if mode == 'a64':
                    off = code + 4
                    struct.pack_into('<I', buf, off, struct.unpack_from('<I', buf, off)[0] + (1 << 10))
                elif mode == 'thumb':
                    off = code + 12
                    struct.pack_into('<I', buf, off, struct.unpack_from('<I', buf, off)[0] + 1)
                elif mode == 'x86':
                    off = code + 20
                    struct.pack_into('<i', buf, off, struct.unpack_from('<i', buf, off)[0] + 1)
                else:
                    off = code + 3
                    struct.pack_into('<i', buf, off, struct.unpack_from('<i', buf, off)[0] + 1)
                result = inspect_binary(bytes(buf))
                self.assertEqual(result['symbols'][NAMES[0].decode()]['value'], '1')

    def test_rejects_truncated_headers_and_bad_tables(self):
        buf, meta = _fixture('x64')
        with self.assertRaises(ValueError):
            inspect_binary(bytes(buf[:10]))
        with self.assertRaises(ValueError):
            inspect_binary(bytes(buf[:meta['ehsize'] - 1]))
        b = bytearray(buf)
        struct.pack_into('<I', b, 40, len(b) + 100)
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))
        b = bytearray(buf)
        struct.pack_into('<H', b, 54, 1)
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))
        b = bytearray(buf)
        b[4] = 1
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))
        b = bytearray(buf)
        struct.pack_into('<H', b, 18, 999)
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))

    def test_rejects_missing_or_duplicate_target_symbols(self):
        buf, meta = _fixture('x64')
        b = bytearray(buf)
        _set_symbol_name(b, meta, 0, meta['nameoffs'][NAMES[1]])
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))
        b = bytearray(buf)
        _set_symbol_name(b, meta, 1, meta['nameoffs'][NAMES[0]])
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))

    def test_rejects_bad_pointer_or_ret(self):
        rets = {'a64': (8, '<I', 0xffffffff), 'thumb': (8, '<H', 0xbf00),
                'x86': (27, '<B', 0x90), 'x64': (7, '<B', 0x90)}
        for mode, (delta, fmt, bad) in rets.items():
            with self.subTest(mode=mode):
                buf, meta = _fixture(mode)
                struct.pack_into(fmt, buf, meta['code_off'] + delta, bad)
                with self.assertRaises(ValueError):
                    inspect_binary(bytes(buf))
        buf, meta = _fixture('x64')
        buf[meta['code_off']] = 0x90
        with self.assertRaises(ValueError):
            inspect_binary(bytes(buf))

    def test_rejects_unmapped_nonfilebacked_and_ambiguous_va(self):
        buf, meta = _fixture('x64')
        b = bytearray(buf)
        _set_symbol_value(b, meta, 0, meta['base'] + meta['file_size'] + 0x1000)
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))
        b = bytearray(buf)
        struct.pack_into('<Q', b, meta['phoff'] + 32, meta['literal_off'])
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))
        b = bytearray(buf)
        struct.pack_into('<H', b, 56, 2)
        b[meta['phoff'] + meta['phsize']:meta['phoff'] + 2 * meta['phsize']] = _phdr(
            meta['cls'], PT_LOAD, 0, meta['base'], meta['file_size'], PF_R | PF_X)
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))

    def test_rejects_missing_nul_and_non_utf8_literals(self):
        buf, meta = _fixture('x64')
        b = bytearray(buf)
        for off in range(meta['litoffs'][NAMES[0]], len(b)):
            b[off] = 0x41
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))
        buf, _ = _fixture('x64', (bytes([0xff]), b'sha1-test', b'2026-11-15'))
        with self.assertRaises(ValueError):
            inspect_binary(bytes(buf))

    def test_aarch64_signed_adrp_offset_literal_va(self):
        buf, meta = _fixture('a64')
        b = bytearray(buf)
        new_code_off = _align(meta['file_size'], 0x1000) + 0x1000
        b.extend(bytes(new_code_off + meta['fsize'] - len(b)))
        code_va = meta['base'] + new_code_off
        lit_va = meta['base'] + meta['litoffs'][NAMES[0]]
        page = code_va & ~0xfff
        target = lit_va & ~0xfff
        delta = (target - page) >> 12
        self.assertLess(delta, 0)
        self.assertGreaterEqual(delta, -0x100000)
        raw = delta & 0x1fffff
        adrp = (1 << 31) | (0x10 << 24) | ((raw & 3) << 29) | ((raw >> 2) << 5)
        addimm = lit_va - target
        add = (1 << 31) | (0x11 << 24) | (addimm << 10)
        b[new_code_off:new_code_off + meta['fsize']] = struct.pack('<III', adrp, add, 0xd65f03c0)
        _set_symbol_value(b, meta, 0, code_va)
        struct.pack_into('<Q', b, meta['phoff'] + 32, len(b))
        struct.pack_into('<Q', b, meta['phoff'] + 40, len(b))
        result = inspect_binary(bytes(b))
        sym = result['symbols'][NAMES[0].decode()]
        self.assertEqual(sym['codeVA'], code_va)
        self.assertEqual(sym['literalVA'], lit_va)
        self.assertEqual(sym['literalFileOffset'], meta['litoffs'][NAMES[0]])
        self.assertEqual(sym['value'], VALUES[0].decode())

    def test_rejects_bad_aarch64_add_forms(self):
        buf, meta = _fixture('a64')
        off = meta['code_off'] + 4
        orig = struct.unpack_from('<I', buf, off)[0]
        cases = (
            ('wrong source register', orig | (1 << 5)),
            ('ADDS', orig | (1 << 29)),
            ('SUB', orig | (1 << 30)),
            ('32-bit', orig & ~(1 << 31)),
            ('shift', orig | (1 << 22)),
        )
        for label, value in cases:
            with self.subTest(case=label):
                b = bytearray(buf)
                struct.pack_into('<I', b, off, value)
                with self.assertRaises(ValueError):
                    inspect_binary(bytes(b))

    def test_rejects_nonzero_i386_call_displacement(self):
        buf, meta = _fixture('x86')
        b = bytearray(buf)
        struct.pack_into('<I', b, meta['code_off'] + 7, 1)
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))

    def test_rejects_even_arm_target_symbol(self):
        buf, meta = _fixture('thumb')
        b = bytearray(buf)
        _set_symbol_value(b, meta, 0, meta['base'] + meta['code_off'])
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))

    def test_rejects_out_of_range_defining_section_index(self):
        buf, meta = _fixture('x64')
        b = bytearray(buf)
        shndx_off = meta['dynsym_off'] + (6 if meta['cls'] == 64 else 14)
        struct.pack_into('<H', b, shndx_off, 0xffff)
        with self.assertRaises(ValueError):
            inspect_binary(bytes(b))


if __name__ == '__main__':
    unittest.main()
