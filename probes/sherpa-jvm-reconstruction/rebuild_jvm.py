import argparse, hashlib, io, json, os, pathlib, stat, subprocess, sys, time, zipfile

SOURCE_COMMIT = 'af2d8d81dbfa61c4cea4c568da78f3982edca005'
MODULE = 'META-INF/sherpa_onnx_release.kotlin_module'
CLASS_COUNT = 124
K2 = 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler'
CONFIG_KEYS = {'repoRoot', 'outputRoot', 'java', 'compilerClasspath', 'compileClasspath', 'sourceMap', 'runtimeAAR', 'expectedAARSHA256', 'expectedClassesJarSHA256', 'sourceCommit'}
LINK_KEYS = {'resolvedEvidencePath', 'resolvedMode', 'resolvedBytes', 'resolvedSHA256', 'resolvedGitBlobSHA1'}

def need(cond, msg):
    if not cond: raise ValueError(msg)
def sha256(data): return hashlib.sha256(data).hexdigest()
def blob_sha1(data): return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
def sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''): h.update(chunk)
    return h.hexdigest()
def identity(path):
    p = pathlib.Path(path)
    return {'path': str(p), 'bytes': p.stat().st_size, 'sha256': sha256_file(p)}
def allowed_output(repo, out):
    repo = pathlib.Path(repo).resolve(strict=False); out = pathlib.Path(out).resolve(strict=False)
    if out == repo: return False
    if repo not in out.parents: return True
    return out.relative_to(repo).parts[:2] == ('.omx', 'artifacts')
def verify_sources(root, mapping, expected_count=22):
    need(set(mapping) == {'sourceCommit', 'links'}, 'bad sourceMap keys')
    need(mapping['sourceCommit'] == SOURCE_COMMIT, 'bad sourceCommit')
    links = mapping['links']; need(type(links) is list and len(links) == expected_count, 'bad source link count')
    rp = pathlib.Path(root).resolve(strict=True); found = []
    raws, names, resolved = set(), set(), set()
    for link in links:
        need(type(link) is dict and set(link) == LINK_KEYS, 'bad source link keys')
        raw = link['resolvedEvidencePath']; need(isinstance(raw, str) and bool(raw), 'bad evidence path')
        p = pathlib.Path(raw); p = p if p.is_absolute() else pathlib.Path(root) / p
        need(not p.is_symlink(), 'source evidence symlink')
        q = p.resolve(strict=True)
        need(q != rp and rp in q.parents, 'evidence outside repoRoot')
        need(not q.is_symlink() and stat.S_ISREG(q.lstat().st_mode), 'evidence not ordinary file')
        need(raw not in raws and q not in resolved and p.name not in names, 'duplicate source evidence')
        need(link['resolvedMode'] == '100644', 'bad resolvedMode')
        need(type(link['resolvedBytes']) is int, 'bad resolvedBytes')
        need(isinstance(link['resolvedSHA256'], str) and isinstance(link['resolvedGitBlobSHA1'], str), 'bad source hash')
        data = q.read_bytes()
        need(link['resolvedBytes'] == len(data) and link['resolvedSHA256'].lower() == sha256(data), 'source bytes/sha mismatch')
        need(link['resolvedGitBlobSHA1'].lower() == blob_sha1(data), 'source git blob mismatch')
        raws.add(raw); names.add(p.name); resolved.add(q); found.append(q)
    return sorted(found)
def verify_aar(path, expected_aar, expected_jar):
    data = path.read_bytes(); need(sha256(data) == expected_aar.lower(), 'runtime AAR sha mismatch')
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        names = [i.filename for i in z.infolist() if not i.is_dir()]
        need(len(names) == len(set(names)), 'duplicate AAR member')
        need(names.count('classes.jar') == 1, 'classes.jar count')
        jar = z.read('classes.jar')
    need(sha256(jar) == expected_jar.lower(), 'classes.jar sha mismatch')
    with zipfile.ZipFile(io.BytesIO(jar)) as z:
        names = [i.filename for i in z.infolist() if not i.is_dir()]
        need(len(names) == len(set(names)), 'duplicate classes.jar member')
        classes = [n for n in names if n.endswith('.class')]
        need(len(classes) == CLASS_COUNT and set(names) == (set(classes) | {MODULE}), 'classes.jar members')
        return {n: z.read(n) for n in names}
def read_members(directory):
    if not directory.is_dir(): return {}
    return {p.relative_to(directory).as_posix(): p.read_bytes() for p in sorted(directory.rglob('*')) if p.is_file()}
def compare_members(original, rebuilt, expected_class_count=124):
    oc = sorted(k for k in original if k.endswith('.class')); rc = sorted(k for k in rebuilt if k.endswith('.class'))
    keys = sorted(set(original) | set(rebuilt)); common = set(original) & set(rebuilt)
    missing = [k for k in keys if k not in rebuilt]; extra = [k for k in keys if k not in original]
    different = [k for k in keys if k in common and original[k] != rebuilt[k]]
    module_equal = MODULE in common and original[MODULE] == rebuilt[MODULE]
    identities = []
    for k in keys:
        ob, rb = original.get(k), rebuilt.get(k)
        identities.append({'path': k, 'originalBytes': None if ob is None else len(ob), 'originalSHA256': None if ob is None else sha256(ob), 'rebuiltBytes': None if rb is None else len(rb), 'rebuiltSHA256': None if rb is None else sha256(rb), 'equal': k in common and ob == rb})
    counts_ok = expected_class_count > 0 and len(oc) == expected_class_count and len(rc) == expected_class_count
    accepted = counts_ok and not missing and not extra and not different and module_equal
    return {'accepted': accepted, 'originalClassCount': len(oc), 'rebuiltClassCount': len(rc), 'missing': missing, 'extra': extra, 'different': different, 'matchedClassCount': sum(1 for k in common if k.endswith('.class') and original[k] == rebuilt[k]), 'moduleMetadataEqual': module_equal, 'memberIdentities': identities}
def write_json(path, obj): path.write_text(json.dumps(obj, indent=2, sort_keys=True) + '\n')
def abs_path(cfg, key):
    v = cfg[key]; need(isinstance(v, str) and pathlib.Path(v).is_absolute(), key + ' must be absolute')
    return pathlib.Path(v)
def abs_list(cfg, key):
    v = cfg[key]; need(type(v) is list and v and all(isinstance(x, str) and pathlib.Path(x).is_absolute() for x in v), key + ' must be absolute jars')
    return [pathlib.Path(x) for x in v]

def main(argv=None):
    ap = argparse.ArgumentParser(description='Rebuild Kotlin JVM classes for byte-evidence comparison')
    ap.add_argument('config')
    try:
        args = ap.parse_args(argv); cfg = json.loads(pathlib.Path(args.config).read_text())
        need(isinstance(cfg, dict) and CONFIG_KEYS <= set(cfg), 'missing config keys')
        need(cfg['sourceCommit'] == SOURCE_COMMIT, 'bad sourceCommit')
        repo, out = abs_path(cfg, 'repoRoot'), abs_path(cfg, 'outputRoot')
        java, source_map, runtime_aar = abs_path(cfg, 'java'), abs_path(cfg, 'sourceMap'), abs_path(cfg, 'runtimeAAR')
        compilers, compile_cp = abs_list(cfg, 'compilerClasspath'), abs_list(cfg, 'compileClasspath')
        need(repo.is_dir() and source_map.is_file() and runtime_aar.is_file(), 'missing input path')
        need(java.is_file() and os.access(java, os.X_OK), 'java not executable')
        need(not out.exists(), 'outputRoot exists'); need(allowed_output(repo, out), 'outputRoot inside repo')
        need(all(x.is_file() for x in compilers + compile_cp), 'missing jar')
        need(isinstance(cfg['expectedAARSHA256'], str) and isinstance(cfg['expectedClassesJarSHA256'], str), 'bad expected hash')
        sources = verify_sources(repo, json.loads(source_map.read_text()))
        original = verify_aar(runtime_aar, cfg['expectedAARSHA256'].lower(), cfg['expectedClassesJarSHA256'].lower())
        out.mkdir(parents=False, exist_ok=False); classes = out / 'classes'; classes.mkdir()
        cmd = [str(java), '-cp', os.pathsep.join(map(str, compilers)), K2, '-no-stdlib', '-no-reflect', '-jvm-target', '1.8', '-module-name', 'sherpa_onnx_release', '-classpath', os.pathsep.join(map(str, compile_cp)), '-d', str(classes)] + [str(s) for s in sources]
        inv = {'java': identity(java), 'compilerClasspath': [identity(x) for x in compilers], 'compileClasspath': [identity(x) for x in compile_cp], 'sources': [identity(x) for x in sources], 'sourceCommit': SOURCE_COMMIT, 'command': cmd}
        start = time.monotonic(); proc = subprocess.run(cmd, capture_output=True, text=True)
        inv['process'] = {'exitCode': proc.returncode, 'elapsedSeconds': time.monotonic() - start, 'stdout': proc.stdout, 'stderr': proc.stderr}
        write_json(out / 'invocation.json', inv)
        if proc.returncode != 0:
            write_json(out / 'report.json', {'accepted': False, 'error': 'compiler_failed', 'exitCode': proc.returncode, 'stdout': proc.stdout, 'stderr': proc.stderr})
            return 1
        comparison = compare_members(original, read_members(classes)); write_json(out / 'comparison.json', comparison)
        report = {'accepted': comparison['accepted'], 'error': None if comparison['accepted'] else 'byte_mismatch', 'exitCode': proc.returncode, 'stdout': proc.stdout, 'stderr': proc.stderr, 'comparison': comparison}
        write_json(out / 'report.json', report)
        return 0 if comparison['accepted'] else 1
    except Exception as exc:
        print('rebuild_jvm: ' + str(exc), file=sys.stderr); return 1
if __name__ == '__main__': raise SystemExit(main())
