'''Pure-local frozen-contract validator for w3 qwen-terminal acceptance.

One bounded component only: proves host fixture behavior from a prepared
local freeze. No filesystem writes, subprocess, network, imports with side
effects, device action, downloads or delegation. Returns the parsed original
contract on success, or raises ValueError/AssertionError on any violation
(filesystem-missing may propagate OSError). Not production code.
'''
from __future__ import annotations

import hashlib
import json
import math
import os
import stat
from pathlib import Path

OUTPUT = 'artifacts/verification/w3-qwen-terminal-20261112'
FP = 'ff5b57b0b1ff08d8d8c984efd04dfbb86f62a5cc4cab02700c5804a0c7c0c3c5'
APP = 'io.ondeviceai.acceptance.qwenterminal'
RUN = 'w3-qwen-terminal-20261112-01'
RUNNER = APP + '.test/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation'
BUDGETS = dict(maxRssBytes=1610612736, minAvailableBytes=536870912,
               minInitialAvailableBytes=2147483648, phaseSeconds=120,
               totalSeconds=240, startupSeconds=20, afterAckSeconds=15, ackMillis=30000)
PLAN = dict(runId=RUN, sourceFingerprint=FP, caseCount=1, maxNativeDecodeCalls=2,
            maxStreamsCreated=2, maxConstructors=1, selectedOriginalIndex=25,
            order=['fleurs-en_us-1776'],
            manifestSHA256='8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f',
            sampledRssStopAboveBytes=1610612736, deviceAvailableStopBelowBytes=536870912,
            caseStopAboveSeconds=120, totalStopAboveSeconds=240, terminalAckTimeoutMillis=30000,
            automaticRetry=False, microphoneStarts=0, systemAsrRequests=0,
            productionChanges=False, currentUser='0', preciseClockAlignmentVerified=False)
BINDINGS = {
    'probes/android-qwen-terminal/QwenTerminalInstrumentation.kt': 'test-src/QwenTerminalInstrumentation.kt',
    'probes/android-terminal-observation/TerminalObservationBarrier.kt': 'test-src/TerminalObservationBarrier.kt',
    'probes/android-terminal-observation/TerminalAckFile.kt': 'test-src/TerminalAckFile.kt',
    'probes/android-qwen-arena/QwenArenaPolicy.kt': 'main-src/QwenArenaPolicy.kt',
    'probes/android-qwen-load/QwenLoadCore.kt': 'main-src/QwenLoadCore.kt',
}
BINDINGS.update({'probes/android-qwen-file/' + n: 'main-src/' + n for n in
                 ('QwenTwoStageCore.kt', 'QwenFileActivity.kt', 'QwenFileInputs.kt', 'QwenFileReport.kt')})
SOURCE_BINDINGS = BINDINGS
OBS = ('terminal_observer.py', 'terminal_file_io.py', 'terminal_observation_coordinator.py',
       'adb_process_sampler.py', 'adb_terminal_report_reader.py',
       'adb_terminal_ack_publisher.py', 'adb_terminal_observation.py', 'terminal_polling.py')
DRIVERS = ('device_acceptance.py', 'terminal_device_contract.py', 'terminal_device_prepare.py', 'terminal_device_lifecycle.py', 'terminal_source_adapter.py', 'terminal_device_restore.py', 'terminal_device_collect.py')
REQUIRED = set(BINDINGS) | {'probes/android-terminal-observation/' + n for n in OBS}
REQUIRED |= {'probes/android-qwen-terminal/' + n for n in DRIVERS}
REQUIRED |= {'probes/android-qwen-terminal/' + n for n in
             ('prepare_build.py', 'build_acceptance.py', 'check_wrapper.py', 'TerminalWrapperChecks.kt')}
REQUIRED |= {'probes/android-qwen-file/device_acceptance.py', 'probes/android-qwen-file/cases.json',
             '.omx/artifacts/w3-model-reselection-20261110/model-manifest.json',
             '.omx/artifacts/w3-model-reselection-20261110/android-target-local.json',
             'docs/superpowers/specs/2026-11-12-w3-terminal-observation-design.md',
             'docs/superpowers/plans/2026-11-12-w3-terminal-observation.md'}
REVIEWS = ('docs/validation/w3-qwen-terminal-driver-source-review-20261112.json',
           'docs/validation/w3-qwen-terminal-driver-parent-review-20261112.json')
REVIEWED = {'probes/android-qwen-terminal/' + n for n in DRIVERS}
REVIEWED |= {'probes/android-terminal-observation/' + n for n in OBS}
LOCAL = ('main.apk', 'test.apk', 'package-verified.json', 'candidate-accepted.json',
         'run-plan.json', 'production-before.json', 'source/test-assets/cases.json')

MAX_JSON = 4 * 1024 * 1024


def verify_frozen_contract(root, out):
    root = Path(root)
    out = Path(out)
    _check_root_out(root, out)
    contract_path, _ = _safe_under_root(out, 'diagnostic-frozen.json')
    with open(contract_path, 'rb') as fh:
        text = fh.read(MAX_JSON + 1)
    if len(text) > MAX_JSON:
        raise ValueError('diagnostic JSON exceeds 4MiB')
    contract = _strict_json(text.decode('utf-8'))
    if not isinstance(contract, dict):
        raise ValueError('contract must be a JSON object')
    _validate_meta(contract)
    _validate_budgets(contract.get('budgets'))
    files = contract.get('files')
    if not isinstance(files, dict) or not files:
        raise ValueError('files must be a nonempty object')
    for rel, expected in files.items():
        _validate_rel_path(rel)
        _expect_hex_sha(expected)
        full, _ = _safe_under_root(root, rel)
        if _sha256_file(full) != expected:
            raise ValueError('hash mismatch for ' + rel)
    for name in REQUIRED:
        if name not in files:
            raise ValueError('missing required frozen file: ' + name)
    for name in REVIEWS:
        if name not in files:
            raise ValueError('missing review receipt: ' + name)
    for n in LOCAL:
        if (OUTPUT + '/' + n) not in files:
            raise ValueError('missing local entry: ' + n)
    _validate_candidate_accepted(root, out, files)
    _validate_package(root, out, files)
    _validate_run_plan(out, files)
    _validate_production(root, out, files)
    _validate_reviews(root, files)
    return contract


def _check_root_out(root, out):
    for p in (root, out):
        if not p.is_absolute():
            raise ValueError('root/out must be absolute: ' + str(p))
        for part in p.parts:
            if part in ('.', '..'):
                raise ValueError('lexical dot/dotdot not allowed: ' + str(p))
    if out != root / OUTPUT:
        raise ValueError('out must equal root/OUTPUT')
    if not root.is_dir():
        raise ValueError('root is not an existing directory')
    if not out.is_dir():
        raise ValueError('out is not an existing directory')
    for p in (root, out):
        _check_no_symlink_ancestors(p)


def _check_no_symlink_ancestors(p):
    cur = p
    while True:
        st = os.lstat(cur)
        if stat.S_ISLNK(st.st_mode):
            raise ValueError('symlink in path: ' + str(cur))
        parent = cur.parent
        if parent == cur:
            break
        cur = parent


def _validate_rel_path(key):
    if not isinstance(key, str) or not key:
        raise ValueError('empty path key')
    if key.startswith('/') or key.startswith(chr(92)):
        raise ValueError('absolute path key: ' + key)
    if chr(0) in key or chr(92) in key:
        raise ValueError('invalid path key: ' + key)
    for comp in key.split('/'):
        if comp == '' or comp == '.' or comp == '..':
            raise ValueError('invalid path component: ' + key)
    return key


def _is_hex64(s):
    return isinstance(s, str) and len(s) == 64 and all(c in '0123456789abcdef' for c in s)


def _expect_hex_sha(value):
    if not _is_hex64(value):
        raise ValueError('sha must be lowercase 64-hex: ' + repr(value))
    return value


def _safe_under_root(root, rel):
    _validate_rel_path(rel)
    acc = root
    for comp in rel.split('/'):
        acc = acc / comp
        st = os.lstat(acc)
        if stat.S_ISLNK(st.st_mode):
            raise ValueError('symlink at: ' + str(acc))
    if not stat.S_ISREG(st.st_mode):
        raise ValueError('not a regular file: ' + rel)
    return acc, st


def _sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as fh:
        for chunk in iter(lambda: fh.read(1048576), b''):
            h.update(chunk)
    return h.hexdigest()


def _strict_json(text):
    def _pairs(pairs):
        seen = set()
        for k, v in pairs:
            if k in seen:
                raise ValueError('duplicate JSON key: ' + str(k))
            seen.add(k)
        return dict(pairs)

    def _reject_constant(tok):
        raise ValueError('JSON constant rejected: ' + tok)

    def _finite_float(s):
        val = float(s)
        if not math.isfinite(val):
            raise ValueError('non-finite JSON float: ' + s)
        return val

    return json.loads(text, object_pairs_hook=_pairs,
                     parse_constant=_reject_constant, parse_float=_finite_float)


def _read_json(path):
    with open(path, 'rb') as fh:
        data = fh.read(MAX_JSON + 1)
    if len(data) > MAX_JSON:
        raise ValueError('JSON exceeds 4MiB: ' + str(path))
    return _strict_json(data.decode('utf-8'))


def _validate_meta(c):
    if type(c.get('schemaVersion')) is not int or c['schemaVersion'] != 1:
        raise ValueError('schemaVersion must be int 1')
    if c.get('runId') != RUN:
        raise ValueError('runId mismatch')
    if c.get('app') != APP:
        raise ValueError('app mismatch')
    if c.get('runner') != RUNNER:
        raise ValueError('runner mismatch')
    if c.get('sourceFingerprint') != FP:
        raise ValueError('sourceFingerprint mismatch')
    if c.get('independentSourceReviewAccepted') is not True:
        raise ValueError('independentSourceReviewAccepted must be True')
    if c.get('independentParentReviewAccepted') is not True:
        raise ValueError('independentParentReviewAccepted must be True')
    if c.get('automaticRetry') is not False:
        raise ValueError('automaticRetry must be False')
    if c.get('nativePolicyEffectVerified') is not False:
        raise ValueError('nativePolicyEffectVerified must be False')
    if c.get('currentUser') != '0' or type(c.get('currentUser')) is not str:
        raise ValueError('currentUser must be str 0')


def _validate_budgets(b):
    if not isinstance(b, dict):
        raise ValueError('budgets must be object')
    if set(b.keys()) != set(BUDGETS.keys()):
        raise ValueError('budgets key set mismatch')
    for k, v in BUDGETS.items():
        if type(b[k]) is not int or b[k] != v:
            raise ValueError('budget ' + k + ' mismatch')


def _strict_equal(a, b):
    if isinstance(a, bool) or isinstance(b, bool):
        return type(a) is bool and type(b) is bool and a == b
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return type(a) is type(b) and a == b
    if isinstance(a, dict) and isinstance(b, dict):
        if set(a.keys()) != set(b.keys()):
            return False
        return all(_strict_equal(a[k], b[k]) for k in a)
    if isinstance(a, list) and isinstance(b, list):
        return len(a) == len(b) and all(_strict_equal(x, y) for x, y in zip(a, b))
    if isinstance(a, str) and isinstance(b, str):
        return a == b
    return type(a) is type(b) and a == b


def _validate_candidate_accepted(root, out, files):
    c = _read_json(out / 'candidate-accepted.json')
    if not isinstance(c, dict):
        raise ValueError('candidate-accepted must be object')
    if c.get('sourceFingerprint') != FP:
        raise ValueError('candidate sourceFingerprint mismatch')
    source_files = c.get('sourceFiles')
    if not isinstance(source_files, dict) or not source_files:
        raise ValueError('sourceFiles must be nonempty object')
    for rel, h in source_files.items():
        _validate_rel_path(rel)
        _expect_hex_sha(h)
    canonical = json.dumps(source_files, sort_keys=True, separators=(',', ':')).encode('utf-8')
    if hashlib.sha256(canonical).hexdigest() != FP:
        raise ValueError('sourceFiles FP mismatch')
    required_src = set(BINDINGS.values()) | {
        'gradlew', 'gradle/wrapper/gradle-wrapper.jar',
        'gradle/wrapper/gradle-wrapper.properties', 'settings.gradle.kts',
        'build.gradle.kts', 'AndroidManifest.xml', 'test-assets/cases.json'}
    for k in required_src:
        if k not in source_files:
            raise ValueError('sourceFiles missing required key: ' + k)
    for rel, h in source_files.items():
        full, _ = _safe_under_root(out, 'source/' + rel)
        if _sha256_file(full) != h:
            raise ValueError('prepared source hash mismatch: ' + rel)
        fkey = OUTPUT + '/source/' + rel
        if fkey not in files or files[fkey] != h:
            raise ValueError('source not frozen under files: ' + fkey)
    for repo_path, src_rel in SOURCE_BINDINGS.items():
        if files.get(repo_path) != source_files[src_rel]:
            raise ValueError('binding hash mismatch: ' + repo_path)
    hr = c.get('hostReceipt')
    if not isinstance(hr, dict):
        raise ValueError('hostReceipt must be object')
    for key, hash_key in (('path', 'sha256'), ('baseHashesPath', 'baseHashesSHA256'),
                          ('terminalAdapterHashesPath', 'terminalAdapterHashesSHA256')):
        rel = hr.get(key)
        _validate_rel_path(rel)
        _expect_hex_sha(hr.get(hash_key))
        if files.get(rel) != hr[hash_key]:
            raise ValueError('hostReceipt path not bound in files: ' + rel)


def _validate_package(root, out, files):
    p = _read_json(out / 'package-verified.json')
    if not isinstance(p, dict):
        raise ValueError('package-verified must be object')
    if p.get('sourceFingerprint') != FP:
        raise ValueError('package sourceFingerprint mismatch')
    if p.get('exactRunnerVerified') != 'io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation':
        raise ValueError('exactRunnerVerified mismatch')
    if type(p.get('actualTestAssetsVerified')) is not int or p['actualTestAssetsVerified'] != 65:
        raise ValueError('actualTestAssetsVerified must be int 65')
    perms = p.get('permissions')
    if not isinstance(perms, dict):
        raise ValueError('permissions must be object')
    if perms.get('main.apk') != [] or perms.get('test.apk') != []:
        raise ValueError('package permissions must be empty lists')
    rl = p.get('runtimeLibraries')
    if not isinstance(rl, dict) or len(rl) != 4:
        raise ValueError('runtimeLibraries must have exactly 4 entries')
    for name, h in rl.items():
        _validate_rel_path(name)
        _expect_hex_sha(h)
    classes = p.get('actualClassesVerified')
    if not isinstance(classes, list):
        raise ValueError('actualClassesVerified must be list')
    if len(classes) != 19:
        raise ValueError('actualClassesVerified must have exactly 19 entries')
    for c in classes:
        if not isinstance(c, str) or not c:
            raise ValueError('actualClassesVerified entries must be nonempty strings')
    if len(set(classes)) != 19:
        raise ValueError('actualClassesVerified must have 19 unique entries')
    for needed in ('QwenTerminalInstrumentation', 'TerminalAckFile', 'TerminalObservationBarrier'):
        if needed not in classes:
            raise ValueError('actualClassesVerified missing: ' + needed)
    pf = p.get('files')
    if not isinstance(pf, dict):
        raise ValueError('package files must be object')
    for name, pkg in (('main.apk', APP), ('test.apk', APP + '.test')):
        row = pf.get(name)
        if not isinstance(row, dict):
            raise ValueError('package file row missing: ' + name)
        if row.get('package') != pkg:
            raise ValueError('package file package mismatch: ' + name)
        sfile = out / name
        actual = _sha256_file(sfile)
        if row.get('sha256') != actual:
            raise ValueError('package sha256 mismatch: ' + name)
        if files.get(OUTPUT + '/' + name) != actual:
            raise ValueError('package contract hash mismatch: ' + name)
        b = row.get('bytes')
        if type(b) is not int or b < 0:
            raise ValueError('package bytes must be nonnegative int: ' + name)
        if b != sfile.stat().st_size:
            raise ValueError('package bytes size mismatch: ' + name)


def _validate_run_plan(out, files):
    plan = _read_json(out / 'run-plan.json')
    if not isinstance(plan, dict):
        raise ValueError('run-plan must be object')
    for k, v in PLAN.items():
        if k not in plan or not _strict_equal(plan[k], v):
            raise ValueError('run-plan field mismatch: ' + k)
    cases = _read_json(out / 'source' / 'test-assets' / 'cases.json')
    if not isinstance(cases, dict) or not isinstance(cases.get('cases'), list):
        raise ValueError('cases.json must be object with cases array')
    rows = cases['cases']
    if len(rows) != 65:
        raise ValueError('cases.json must have 65 rows')
    if not isinstance(rows[25], dict) or rows[25].get('id') != 'fleurs-en_us-1776':
        raise ValueError('case row 25 id mismatch')


def _validate_production(root, out, files):
    prod = _read_json(out / 'production-before.json')
    if not isinstance(prod, dict) or not prod:
        raise ValueError('production-before must be nonempty object')
    for rel, h in prod.items():
        _validate_rel_path(rel)
        _expect_hex_sha(h)
        full, _ = _safe_under_root(root, rel)
        if _sha256_file(full) != h:
            raise ValueError('production hash mismatch: ' + rel)
        if files.get(rel) != h:
            raise ValueError('production not bound in files: ' + rel)


def _validate_reviews(root, files):
    for name in REVIEWS:
        row = _read_json(root / name)
        if not isinstance(row, dict):
            raise ValueError('review must be object: ' + name)
        if row.get('verdict') != 'accept':
            raise ValueError('review verdict must be accept: ' + name)
        sha = row.get('sourceSHA256')
        if not isinstance(sha, dict):
            raise ValueError('review sourceSHA256 must be object: ' + name)
        for r in REVIEWED:
            if r not in sha:
                raise ValueError('review missing reviewed source: ' + name + ' ' + r)
        for k, v in sha.items():
            _validate_rel_path(k)
            _expect_hex_sha(v)
            if k not in files:
                raise ValueError('review source not in files: ' + k)
            full, _ = _safe_under_root(root, k)
            actual = _sha256_file(full)
            if actual != v or files[k] != v:
                raise ValueError('review source hash mismatch: ' + k)
