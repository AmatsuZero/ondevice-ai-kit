import struct

_NAMES = (b'SherpaOnnxGetVersionStr', b'SherpaOnnxGetGitSha1', b'SherpaOnnxGetGitDate')
_ALLOW = {183: 64, 40: 32, 3: 32, 62: 64}
_NUL = bytes(1)
_PT_LOAD = 1
_PF_X = 1
_SHT_DYNSYM = 11
_SHT_STRTAB = 3
_STT_FUNC = 2


def _need(cond, msg='malformed ELF'):
    if not cond:
        raise ValueError(msg)


def _unpack(fmt, data, off):
    n = struct.calcsize(fmt)
    _need(off >= 0 and off + n <= len(data), 'truncated table')
    return struct.unpack_from(fmt, data, off)


def _cstr(buf, off):
    _need(0 <= off < len(buf), 'bad string table offset')
    end = buf.find(_NUL, off)
    _need(end >= 0, 'unterminated string')
    return buf[off:end]


def inspect_binary(data: bytes) -> dict:
    _need(isinstance(data, (bytes, bytearray)), 'data must be bytes')
    _need(len(data) >= 16, 'truncated ELF identification')
    _need(data[:4] == bytes([0x7f]) + b'ELF', 'not ELF')
    cls = {1: 32, 2: 64}.get(data[4])
    _need(cls is not None, 'unsupported ELF class')
    _need(data[5] == 1, 'not little-endian')
    _need(data[6] == 1, 'bad ELF version')
    if cls == 32:
        hfmt, ehsize, phsize, shsize, symsize, bits = '<HHIIIIIHHHHHH', 52, 32, 40, 16, 32
    else:
        hfmt, ehsize, phsize, shsize, symsize, bits = '<HHIQQQIHHHHHH', 64, 56, 64, 24, 64
    mask = (1 << bits) - 1
    _need(len(data) >= ehsize, 'truncated ELF header')
    (e_type, e_machine, e_version, _entry, e_phoff, e_shoff, _flags, e_ehsize,
     e_phentsize, e_phnum, e_shentsize, e_shnum, _shstrndx) = _unpack(hfmt, data, 16)
    _need(e_type == 3, 'not ET_DYN')
    _need(_ALLOW.get(e_machine) == cls, 'unsupported machine/class')
    _need(e_version == 1 and e_ehsize == ehsize, 'bad ELF version/header size')
    _need(e_phnum > 0 and e_shnum > 0 and e_phoff and e_shoff, 'missing tables')
    _need(e_phentsize == phsize and e_shentsize == shsize, 'bad table entry size')
    _need(e_phoff + e_phnum * phsize <= len(data), 'program table out of bounds')
    _need(e_shoff + e_shnum * shsize <= len(data), 'section table out of bounds')

    loads = []
    for i in range(e_phnum):
        o = e_phoff + i * phsize
        if cls == 32:
            typ, off, va, _pa, filesz, memsz, flags, _align = _unpack('<IIIIIIII', data, o)
        else:
            typ, flags, off, va, _pa, filesz, memsz, _align = _unpack('<IIQQQQQQ', data, o)
        if typ != _PT_LOAD:
            continue
        _need(filesz <= memsz, 'PT_LOAD filesz exceeds memsz')
        _need(off + filesz <= len(data), 'PT_LOAD outside file')
        _need(va + filesz <= 1 << bits, 'PT_LOAD wraps VA space')
        if filesz:
            loads.append({'start': va, 'end': va + filesz, 'off': off, 'flags': flags})

    def map_range(va, n, executable=False):
        _need(n > 0, 'empty VA range')
        _need(0 <= va <= mask and va + n <= 1 << bits, 'bad VA range')
        hit = None
        for s in loads:
            covers = s['start'] <= va and va + n <= s['end']
            overlaps = va < s['end'] and s['start'] < va + n
            if covers:
                _need(hit is None, 'ambiguous VA mapping')
                hit = s
            elif overlaps:
                raise ValueError('ambiguous VA mapping')
        _need(hit is not None, 'unmapped VA')
        if executable:
            _need(hit['flags'] & _PF_X, 'code is not executable')
        return hit['off'] + va - hit['start'], hit

    def read_range(va, n, executable=False):
        off, seg = map_range(va, n, executable)
        return data[off:off + n], off, seg

    shs = []
    keys = ('name', 'type', 'flags', 'addr', 'offset', 'size', 'link', 'info', 'addralign', 'entsize')
    for i in range(e_shnum):
        o = e_shoff + i * shsize
        vals = _unpack('<IIIIIIIIII' if cls == 32 else '<IIQQQQIIQQ', data, o)
        sh = dict(zip(keys, vals))
        if sh['type'] not in (0, 8) and sh['size']:
            _need(sh['offset'] + sh['size'] <= len(data), 'section outside file')
        shs.append(sh)

    found = {name: [] for name in _NAMES}
    for sh in shs:
        if sh['type'] != _SHT_DYNSYM:
            continue
        _need(sh['entsize'] == symsize, 'bad dynsym entry size')
        _need(sh['size'] >= symsize and sh['size'] % symsize == 0, 'bad dynsym size')
        _need(sh['link'] < len(shs), 'bad dynsym link')
        strsh = shs[sh['link']]
        _need(strsh['type'] == _SHT_STRTAB and strsh['size'] > 0, 'bad dynstr')
        _need(strsh['offset'] + strsh['size'] <= len(data), 'bad dynstr bounds')
        strtab = data[strsh['offset']:strsh['offset'] + strsh['size']]
        for i in range(sh['size'] // symsize):
            o = sh['offset'] + i * symsize
            if cls == 32:
                st_name, st_value, st_size, st_info, _other, st_shndx = _unpack('<IIIBBH', data, o)
            else:
                st_name, st_info, _other, st_shndx, st_value, st_size = _unpack('<IBBHQQ', data, o)
            if (st_info & 0xf) != _STT_FUNC or st_shndx == 0:
                continue
            name = _cstr(strtab, st_name)
            if name not in found:
                continue
            _need(0 < st_shndx < 0xff00 and st_shndx < len(shs), 'bad symbol section index')
            found[name].append((st_value, st_size))

    syms = {}
    for name in _NAMES:
        _need(len(found[name]) == 1, 'missing or duplicate symbol')
        syms[name] = found[name][0]

    out = {}
    for name in _NAMES:
        raw, size = syms[name]
        if e_machine == 40:
            _need(raw & 1, 'ARM symbol is not Thumb')
            code_va = raw & ~1
        else:
            code_va = raw
        _need(size > 0, 'empty symbol')
        code_off, _ = map_range(code_va, size, True)

        if e_machine == 183:
            _need(size >= 12, 'symbol size below AArch64 form')
            b, _, _ = read_range(code_va, 12, True)
            w0, w1, w2 = struct.unpack('<III', b)
            _need((w0 & 0x9f000000) == 0x90000000 and (w0 & 31) == 0, 'bad ADRP')
            immlo = (w0 >> 29) & 3
            immhi = (w0 >> 5) & 0x7ffff
            imm = (immhi << 2) | immlo
            if imm & 0x100000:
                imm -= 1 << 21
            _need((w1 & 0xffc003ff) == 0x91000000, 'bad ADD immediate')
            add = (w1 >> 10) & 0xfff
            _need(w2 == 0xd65f03c0, 'bad RET')
            page = code_va & ~0xfff
            literal_va = (page + (imm << 12) + add) & mask
            code_hex = b.hex()
        elif e_machine == 40:
            _need(size >= 10, 'symbol size below Thumb form')
            b, _, _ = read_range(code_va, 10, True)
            h0, h1, h2, h3, h4 = struct.unpack('<5H', b)
            _need(h0 == 0xb580 and h1 == 0x466f and h3 == 0x4478 and h4 == 0xbd80,
                  'bad Thumb prologue/return')
            _need((h2 & 0xf800) == 0x4800 and ((h2 >> 8) & 7) == 0, 'bad LDR literal')
            imm8 = h2 & 0xff
            _need(imm8 == 1, 'unsupported LDR literal offset')
            pool_va = ((((code_va + 8) & ~3) + imm8 * 4) & mask)
            _need(pool_va >= code_va + 10, 'literal pool overlaps code')
            gap = pool_va - (code_va + 10)
            if gap:
                _need(gap == 2, 'bad literal pool alignment')
                pad, _, _ = read_range(code_va + 10, gap, True)
                _need(pad == bytes([0x00, 0xbf]) * (gap // 2), 'bad literal padding')
            pool_off, _ = map_range(pool_va, 4)
            _need(size >= pool_va - code_va + 4, 'symbol size below Thumb pool')
            pool_word = struct.unpack_from('<I', data, pool_off)[0]
            literal_va = (pool_word + code_va + 10) & mask
            code_hex = b.hex()
        elif e_machine == 3:
            _need(size >= 28, 'symbol size below i386 form')
            b, _, _ = read_range(code_va, 28, True)
            _need(tuple(b[0:6]) == (0x55, 0x89, 0xe5, 0x83, 0xe4, 0xfc), 'bad i386 prologue')
            _need(b[6] == 0xe8 and tuple(b[7:11]) == (0, 0, 0, 0), 'bad i386 CALL')
            _need(b[11] == 0x58 and tuple(b[12:14]) == (0x81, 0xc0), 'bad i386 POP/ADD')
            imm = struct.unpack_from('<I', b, 14)[0]
            _need(tuple(b[18:20]) == (0x8d, 0x80), 'bad i386 LEA')
            disp = struct.unpack_from('<i', b, 20)[0]
            _need(tuple(b[24:28]) == (0x89, 0xec, 0x5d, 0xc3), 'bad i386 epilogue')
            literal_va = (code_va + 11 + imm + disp) & mask
            code_hex = b.hex()
        else:
            _need(size >= 8, 'symbol size below x86-64 form')
            b, _, _ = read_range(code_va, 8, True)
            _need(tuple(b[0:3]) == (0x48, 0x8d, 0x05) and b[7] == 0xc3, 'bad x86-64 form')
            disp = struct.unpack_from('<i', b, 3)[0]
            literal_va = (code_va + 7 + disp) & mask
            code_hex = b.hex()

        lit_off, seg = map_range(literal_va, 1)
        avail = seg['end'] - literal_va
        chunk = data[lit_off:lit_off + min(avail, 257)]
        nul = chunk.find(_NUL)
        _need(nul >= 0 and nul <= 256, 'unterminated or oversized literal')
        lit_len = nul + 1
        map_range(literal_va, lit_len)
        raw = chunk[:nul]
        try:
            value = raw.decode('utf-8')
        except UnicodeDecodeError as exc:
            raise ValueError('non-UTF-8 literal') from exc
        out[name.decode()] = {
            'symbolValue': syms[name][0],
            'codeVA': code_va,
            'codeFileOffset': code_off,
            'codeHex': code_hex,
            'literalVA': literal_va,
            'literalFileOffset': lit_off,
            'value': value,
        }
    return {'elf_class': cls, 'machine': e_machine, 'symbols': out}
