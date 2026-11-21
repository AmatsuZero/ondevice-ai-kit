"""Parent-owned one-shot load/resource acceptance. No audio or ASR decoding."""
from pathlib import Path
import hashlib
import json
import re
import shlex
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-load-emulator-20261110'
EVIDENCE = ROOT / '.omx/artifacts/w3-model-reselection-20261110'
APP = 'io.ondeviceai.acceptance.qwenload'
TEST = APP + '.test'
RUN = 'w3-qwen-load-emulator-20261110-01'
STAGE = '/data/local/tmp/' + RUN
MODEL = 'no_backup/qwen3-asr-0.6b-int8-393f8a14'
REPORT = 'files/w3-qwen-load/report-' + RUN + '.json'
RUNNER = TEST + '/' + APP + '.QwenLoadInstrumentation'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def save(name, data):
    with (OUT / name).open('x') as stream:
        json.dump(data, stream, indent=2)
        stream.write('\n')


def budget_reason(rss_bytes, available_bytes, elapsed_seconds):
    if rss_bytes > 1536 * 1024 * 1024:
        return 'sampled_rss_above_1_5_gib'
    if available_bytes < 512 * 1024 * 1024:
        return 'device_available_below_512_mib'
    if elapsed_seconds > 120:
        return 'load_window_above_120_seconds'
    return None


def parse_kb(text, key):
    match = re.search(r'^' + re.escape(key) + r':\s+(\d+) kB$', text, re.MULTILINE)
    if not match:
        raise ValueError('missing exact kB field ' + key)
    return int(match.group(1)) * 1024


def main(action):
    assert action in ('preflight', 'prepare', 'run', 'collect', 'restore')
    package = json.loads((OUT / 'package-verified.json').read_text())
    source = json.loads((OUT / 'candidate-accepted.json').read_text())
    for rel, sha in source['sourceFiles'].items():
        assert digest(OUT / 'source' / rel) == sha, rel
    for name, row in package['files'].items():
        assert digest(OUT / name) == row['sha256']
    for rel, sha in json.loads((EVIDENCE / 'production-before.json').read_text()).items():
        assert digest(ROOT / rel) == sha, rel
    serial = 'emulator-5576'
    adb = ['rtk', 'proxy', str(Path.home() / 'Library/Android/sdk/platform-tools/adb'), '-s', serial]

    def call(*args, accepted=(0,), timeout=30):
        proc = subprocess.run(adb + list(args), capture_output=True, timeout=timeout)
        if proc.returncode not in accepted:
            raise RuntimeError((args, proc.returncode, proc.stdout.decode(errors='replace')[-1000:], proc.stderr.decode(errors='replace')[-1000:]))
        return proc

    def text(*args, **kwargs):
        return call(*args, **kwargs).stdout.decode().strip()

    def exists(path, app=False):
        args = ['shell'] + (['run-as', APP] if app else []) + ['test', '-e', path]
        return call(*args, accepted=(0, 1)).returncode == 0

    def identity():
        actual = {k: text('shell', 'getprop', k) for k in ('ro.product.model', 'ro.build.version.sdk', 'ro.product.cpu.abi')}
        assert actual == {'ro.product.model': 'Android SDK built for arm64', 'ro.build.version.sdk': '36', 'ro.product.cpu.abi': 'arm64-v8a'}, actual
        assert 'ActiveInstrumentation' not in text('shell', 'dumpsys', 'activity', 'processes')
        return actual

    def gate():
        power = text('shell', 'dumpsys', 'power')
        policy = text('shell', 'dumpsys', 'window', 'policy')
        result = {'awake': 'mWakefulness=Awake' in power, 'unlocked': 'showing=false' in policy}
        assert all(result.values()), result
        return result

    def ordinary():
        packages = [x.removeprefix('package:') for x in text('shell', 'pm', 'list', 'packages', 'io.ondeviceai').splitlines()]
        return {p: text('shell', 'pm', 'path', p) for p in packages if p not in (APP, TEST)}

    def ordinary_models():
        base = 'no_backup/sherpa-whisper-tiny-65176e2d-vad9e2449e1/'
        names = ('silero_vad.onnx', 'tiny-encoder.int8.onnx', 'tiny-decoder.int8.onnx', 'tiny-tokens.txt')
        result = {}
        for name in names:
            path = base + name
            probe = call('shell', 'run-as', 'io.ondeviceai.demo', 'test', '-f', path, accepted=(0, 1))
            result[name] = text('shell', 'run-as', 'io.ondeviceai.demo', 'sha256sum', path).split()[0] if probe.returncode == 0 else None
        return result

    def installed_sha(app):
        path = text('shell', 'pm', 'path', app).removeprefix('package:')
        assert path.startswith('/data/app/') and path.endswith('/base.apk') and '\n' not in path
        return text('shell', 'sha256sum', path).split()[0]

    def verify_installed():
        for name, app in [('main.apk', APP), ('test.apk', TEST)]:
            assert installed_sha(app) == package['files'][name]['sha256']

    manifest = json.loads((EVIDENCE / 'model-manifest.json').read_text())
    models = {k: v for k, v in manifest['files'].items() if k.endswith('.onnx') or k.startswith('tokenizer/')}

    def transfer(label, local, expected):
        assert digest(local) == expected
        remote = STAGE + '/' + label
        chunks = []
        temporary = OUT / 'transfer-part.tmp'
        with local.open('rb') as stream:
            while block := stream.read(8 * 1024 * 1024):
                part = remote + '.part-' + str(len(chunks)).zfill(3)
                sha = hashlib.sha256(block).hexdigest()
                assert not exists(part)
                temporary.write_bytes(block)
                call('push', str(temporary), part)
                assert text('shell', 'sha256sum', part).split()[0] == sha
                temporary.unlink()
                chunks.append({'path': part, 'bytes': len(block), 'sha256': sha})
                print('verified transfer', label, len(chunks), flush=True)
        assert not exists(remote)
        call('shell', 'cat ' + ' '.join(shlex.quote(x['path']) for x in chunks) + ' > ' + shlex.quote(remote))
        assert text('shell', 'sha256sum', remote).split()[0] == expected
        save('transfer-' + label + '.json', {'remote': remote, 'sha256': expected, 'bytes': local.stat().st_size, 'chunks': chunks})
        return remote

    current = identity()
    assert text('shell', 'getprop', 'ro.kernel.qemu') == '1'
    assert text('emu', 'avd', 'name').splitlines() == ['W3_Qwen_Load_20261110', 'OK']
    if action == 'preflight':
        power = text('shell', 'dumpsys', 'power'); policy = text('shell', 'dumpsys', 'window', 'policy')
        print(json.dumps({'identity': current, 'awake': 'mWakefulness=Awake' in power, 'unlocked': 'showing=false' in policy,
                          'availableBytes': parse_kb(text('shell', 'cat', '/proc/meminfo'), 'MemAvailable'),
                          'newAliasPackages': text('shell', 'pm', 'list', 'packages', APP), 'sourceAndPackageVerified': True}))
    elif action == 'prepare':
        before_gate = gate()
        assert not text('shell', 'pm', 'list', 'packages', APP) and not exists(STAGE)
        save('device-before.json', {'identity': current, 'gate': before_gate, 'ordinary': ordinary(),
                                   'ordinaryModels': ordinary_models(), 'stage': STAGE})
        call('shell', 'mkdir', STAGE)
        for name, app in [('main.apk', APP), ('test.apk', TEST)]:
            remote = transfer(name, OUT / name, package['files'][name]['sha256'])
            result = text('shell', 'pm', 'install', '-t', remote, timeout=60)
            save('install-' + name + '.json', {'result': result})
            assert result == 'Success' and installed_sha(app) == package['files'][name]['sha256']
        assert RUNNER in text('shell', 'pm', 'list', 'instrumentation', APP)
        call('shell', 'run-as', APP, 'mkdir', '-p', MODEL + '/tokenizer')
        for name, row in models.items():
            local = Path(manifest['directory']) / name
            assert local.stat().st_size == row['bytes']
            remote = transfer(name.replace('/', '-'), local, row['sha256'])
            call('shell', 'run-as', APP, 'cp', remote, MODEL + '/' + name, timeout=60)
            assert text('shell', 'run-as', APP, 'sha256sum', MODEL + '/' + name).split()[0] == row['sha256']
        # Same-UID /proc readability is a hard prerequisite, checked before model execution.
        own_status = text('shell', 'run-as', APP, 'cat', '/proc/self/status')
        assert parse_kb(own_status, 'VmRSS') > 0
        before = json.loads((OUT / 'device-before.json').read_text())
        assert ordinary() == before['ordinary'] and ordinary_models() == before['ordinaryModels']
        save('prepared.json', {'models': models, 'sourceFingerprint': package['sourceFingerprint'],
                               'ordinaryUnchanged': True, 'sameUidProcReadable': True, 'nativeCalls': 0})
    elif action == 'run':
        assert (OUT / 'prepared.json').is_file()
        verify_installed(); initial_gate = gate()
        assert not exists(REPORT, app=True)
        available = parse_kb(text('shell', 'cat', '/proc/meminfo'), 'MemAvailable')
        assert available >= 2 * 1024 * 1024 * 1024, available
        save('run-started.json', {'runId': RUN, 'gate': initial_gate, 'sourceFingerprint': package['sourceFingerprint'],
                                 'availableBytesBefore': available, 'automaticRetry': False, 'plannedConstructors': 1,
                                 'plannedStreams': 0, 'plannedDecode': 0, 'plannedMicrophone': 0})
        start = time.monotonic(); samples = []; pid = None; stat_start = None; stop_reason = None; terminal_gap = None
        args = adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'runId', RUN, '-e', 'sourceFingerprint',
                      package['sourceFingerprint'], RUNNER]
        with (OUT / 'instrumentation.log').open('x') as log, (OUT / 'rss-samples.jsonl').open('x') as trace:
            proc = subprocess.Popen(args, stdout=log, stderr=subprocess.STDOUT)
            while proc.poll() is None:
                tick = time.monotonic()
                try:
                    actual = text('shell', 'pidof', APP, accepted=(0, 1))
                    if actual:
                        assert re.fullmatch(r'[1-9][0-9]*', actual), actual
                        if pid is None: pid = int(actual)
                        assert int(actual) == pid, 'process identity changed'
                        stat = text('shell', 'run-as', APP, 'cat', '/proc/' + str(pid) + '/stat')
                        observed_start = stat[stat.rindex(')') + 2:].split()[19]
                        if stat_start is None: stat_start = observed_start
                        assert stat_start == observed_start, 'PID reused'
                        rss = parse_kb(text('shell', 'run-as', APP, 'cat', '/proc/' + str(pid) + '/status'), 'VmRSS')
                        mem = parse_kb(text('shell', 'cat', '/proc/meminfo'), 'MemAvailable')
                        sample = {'elapsedSeconds': time.monotonic() - start, 'pid': pid, 'startTicks': stat_start,
                                  'rssBytes': rss, 'deviceAvailableBytes': mem}
                        samples.append(sample); trace.write(json.dumps(sample) + '\n'); trace.flush()
                        stop_reason = budget_reason(rss, mem, sample['elapsedSeconds'])
                    elif pid is not None:
                        stop_reason = 'observed_process_disappeared'
                    elif time.monotonic() - start > 20:
                        stop_reason = 'no_process_observed_within_20_seconds'
                except Exception as error:
                    if proc.poll() is not None:
                        terminal_gap = type(error).__name__ + ':' + str(error)
                        break
                    stop_reason = 'resource_observation_failed:' + type(error).__name__ + ':' + str(error)
                if stop_reason:
                    # Exact verified one-shot alias only. No other package is stopped.
                    verify_installed(); call('shell', 'am', 'force-stop', APP)
                    break
                time.sleep(max(0, .25 - (time.monotonic() - tick)))
            try:
                proc.wait(timeout=15)
            except subprocess.TimeoutExpired:
                proc.terminate(); proc.wait(timeout=5)
                stop_reason = stop_reason or 'host_adb_did_not_finish_after_exact_alias_stop'
        save('host-result.json', {'exitCode': proc.returncode, 'seconds': time.monotonic() - start, 'stopReason': stop_reason,
                                  'samples': len(samples), 'sampledMaxRssBytes': max((s['rssBytes'] for s in samples), default=None),
                                  'pid': pid, 'startTicks': stat_start, 'terminalObservationGap': terminal_gap,
                                  'truePeakMeasured': False, 'automaticRetry': False})
        print('One-shot run ended; collect this run only', stop_reason, flush=True)
    elif action == 'collect':
        assert (OUT / 'run-started.json').is_file(); verify_installed()
        if exists(REPORT, app=True):
            first = call('exec-out', 'run-as', APP, 'cat', REPORT).stdout
            second = call('exec-out', 'run-as', APP, 'cat', REPORT).stdout
            (OUT / 'device-report.json').write_bytes(first); (OUT / 'device-report-confirm.json').write_bytes(second)
            remote = text('shell', 'run-as', APP, 'sha256sum', REPORT).split()[0]
            assert first == second and hashlib.sha256(first).hexdigest() == remote
            report = json.loads(first)
            assert report['runId'] == RUN and report['sourceFingerprint'] == package['sourceFingerprint']
            assert all(report[k] == 0 for k in ('streamsCreated', 'nativeDecodeCalls', 'microphoneStarts', 'networkRequests'))
            receipt = {'reportPresent': True, 'twoReadsIdentical': True, 'sha256': remote,
                       'terminal': report['terminal'], 'status': report['status']}
        else:
            receipt = {'reportPresent': False, 'terminalAccepted': False}
        before = json.loads((OUT / 'device-before.json').read_text())
        model_checks = {name: text('shell', 'run-as', APP, 'sha256sum', MODEL + '/' + name).split()[0] == row['sha256']
                        for name, row in models.items()}
        assert all(model_checks.values()), model_checks
        receipt['modelHashesAfterMatch'] = model_checks
        assert ordinary() == before['ordinary'] and ordinary_models() == before['ordinaryModels']
        save('collection.json', receipt); print(json.dumps(receipt))
    elif action == 'restore':
        assert (OUT / 'collection.json').is_file()
        before = json.loads((OUT / 'device-before.json').read_text())
        assert ordinary() == before['ordinary'] and ordinary_models() == before['ordinaryModels']
        verify_installed()
        for app in (TEST, APP): assert text('shell', 'pm', 'uninstall', app) == 'Success'
        known = set()
        for path in OUT.glob('transfer-*.json'):
            row = json.loads(path.read_text()); known.add(row['remote'].removeprefix(STAGE + '/'))
            known.update(x['path'].removeprefix(STAGE + '/') for x in row['chunks'])
        assert set(text('shell', 'ls', '-1', STAGE).splitlines()) == known
        call('shell', 'rm', '-rf', STAGE)
        assert ordinary() == before['ordinary'] and ordinary_models() == before['ordinaryModels']
        result = text('shell', 'am', 'start', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
        assert 'Error' not in result
        save('restoration.json', {'removedOnlyExactNewAliases': [TEST, APP], 'exactStagingRemoved': STAGE,
                                 'ordinaryAppsModelsUnchanged': True, 'emulatorLauncherReturned': True, 'supplementalEmulatorEvidenceOnly': True})


if __name__ == '__main__':
    main(sys.argv[1])
