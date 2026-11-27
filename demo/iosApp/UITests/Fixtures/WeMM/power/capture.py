#!/usr/bin/env python3
import json
import os
import posixpath
import re
import stat
import subprocess
import sys
import tempfile
import time
import uuid
from pathlib import Path
from urllib.parse import unquote, urlsplit

BUNDLE = 'io.ondeviceai.w6.unifiedvideo.fixtures'
TARGET = "Test Case '-[SpeechDemoUITests.W6UnifiedVideoUITests testRealMovieAnswer]' started."
TARGET_START = re.compile(r"^Test Case '-\[SpeechDemoUITests\.W6UnifiedVideoUITests testRealMovieAnswer\]' started\.$")
TARGET_END = re.compile(r"^Test Case '-\[SpeechDemoUITests\.W6UnifiedVideoUITests testRealMovieAnswer\]' (?:passed|failed|skipped|finished)\b")
ANY_START = re.compile(r"^Test Case '.+' started\.$")
TAP = 'Tap ' + chr(34) + 'multimodal-generate' + chr(34) + ' Button'


def fail(message):
    raise RuntimeError(message)


def response_result(value, label):
    if (not isinstance(value, dict) or not isinstance(value.get('info'), dict)
            or value['info'].get('outcome') != 'success'
            or not isinstance(value.get('result'), dict)):
        fail(f'{label} returned an unsuccessful or malformed response')
    return value['result']


def devicectl_json(device, *args):
    fd, filename = tempfile.mkstemp(prefix='w6-capture-', suffix='.json')
    os.close(fd)
    os.unlink(filename)
    try:
        command = ['xcrun', 'devicectl', 'device', *args, '--device', device,
                   '--timeout', '10', '--json-output', filename]
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=10, check=False)
        except (OSError, subprocess.TimeoutExpired) as exc:
            fail(f'devicectl {" ".join(args)} failed: {exc}')
        if result.returncode:
            detail = (result.stderr or result.stdout).strip()
            fail(f'devicectl {" ".join(args)} exited {result.returncode}: {detail}')
        try:
            with open(filename, encoding='utf-8') as stream:
                return json.load(stream)
        except (OSError, json.JSONDecodeError) as exc:
            fail(f'could not read devicectl JSON for {" ".join(args)}: {exc}')
    finally:
        try:
            os.unlink(filename)
        except FileNotFoundError:
            pass


def log_state(log_path):
    try:
        text = log_path.read_text(encoding='utf-8', errors='replace')
    except OSError as exc:
        fail(f'cannot read phase log: {exc}')
    lines = [line.rstrip('\r\n') for line in text.splitlines(keepends=True) if line.endswith('\n')]
    started = launched = idle = False
    for line in lines:
        is_target_start = TARGET_START.fullmatch(line) is not None
        if any(marker in line for marker in ('TEST EXECUTE FAILED', 'Testing failed:',
                                             'TEST EXECUTE SUCCEEDED', 'Testing passed:')):
            fail('movie tests reached a terminal summary before capture')
        if not started:
            if is_target_start:
                started = True
            continue
        if TAP in line:
            fail('generate Tap appeared in the target test; capture is no longer eligible')
        if ANY_START.fullmatch(line):
            if is_target_start:
                fail('target test started more than once')
            fail('another test started before capture')
        if TARGET_END.search(line):
            fail('target test ended before capture')
        if line.rstrip().endswith(' Launch ' + BUNDLE):
            launched = True
        if launched and line.rstrip().endswith(' Wait for ' + BUNDLE + ' to idle'):
            idle = True
    return started and launched and idle


def file_url_path(value, label):
    if not isinstance(value, str) or re.search(r'%(?![0-9A-Fa-f]{2})', value):
        fail(f'{label} is not a valid local file URL')
    try:
        parsed = urlsplit(value)
        path = unquote(parsed.path, errors='strict')
    except (ValueError, UnicodeDecodeError) as exc:
        fail(f'{label} is not a valid local file URL: {exc}')
    if (parsed.scheme != 'file' or parsed.netloc or parsed.query or parsed.fragment
            or not path.startswith('/') or '\x00' in path
            or any(part == '..' for part in path.split('/'))):
        fail(f'{label} is not an absolute local file URL')
    return canonical_path(path)


def canonical_path(path):
    path = posixpath.normpath(path)
    if path == '/var' or path.startswith('/var/'):
        path = '/private' + path
    return path


def device_binding(device):
    details = response_result(devicectl_json(device, 'info', 'details'), 'device details')
    hardware = details.get('hardwareProperties')
    if not isinstance(hardware, dict) or hardware.get('udid') != device:
        fail('device details do not contain the exact requested physical UDID')
    if str(hardware.get('platform', '')).lower() != 'ios' or hardware.get('reality') != 'physical':
        fail('requested device is not a physical iOS device')
    core_device = details.get('identifier')
    try:
        uuid.UUID(core_device)
    except (ValueError, TypeError, AttributeError):
        fail('device details do not contain a valid CoreDevice UUID')
    connection = details.get('connectionProperties')
    transport = connection.get('transportType') if isinstance(connection, dict) else None
    if not isinstance(transport, str) or not transport:
        fail('device details do not identify a transport')

    apps = response_result(devicectl_json(device, 'info', 'apps'), 'installed apps')
    if apps.get('deviceIdentifier') != core_device or not isinstance(apps.get('apps'), list):
        fail('installed-app response is not bound to the exact CoreDevice')
    matches = [app for app in apps['apps']
               if isinstance(app, dict) and app.get('bundleIdentifier') == BUNDLE]
    if len(matches) != 1:
        fail(f'expected one installed {BUNDLE} app; found {len(matches)}')
    app_path = file_url_path(matches[0].get('url'), 'installed app URL')
    executable = canonical_path(app_path.rstrip('/') + '/SpeechDemo')
    return core_device, app_path, executable, transport


def matching_pid(processes, core_device, executable):
    result = response_result(processes, 'running-process list')
    if result.get('deviceIdentifier') != core_device or not isinstance(result.get('runningProcesses'), list):
        fail('process response is not bound to the exact CoreDevice')
    expected = canonical_path(executable)
    matches = []
    for process in result['runningProcesses']:
        if not isinstance(process, dict) or not isinstance(process.get('executable'), str):
            continue
        try:
            path = file_url_path(process['executable'], 'process executable URL')
        except RuntimeError:
            continue
        if path == expected:
            pid = process.get('processIdentifier')
            if type(pid) is not int or pid <= 0:
                fail('matching process has no valid positive processIdentifier')
            matches.append(pid)
    if len(matches) != 1:
        fail(f'expected one process at the exact app executable path; found {len(matches)}')
    return matches[0]


def main():
    if len(sys.argv) != 4:
        fail('usage: python3 capture.py DEVICE_ID existing_phase_log new_output_dir')
    device, log_arg, output_arg = sys.argv[1:]
    log_path = Path(log_arg)
    try:
        if not stat.S_ISREG(log_path.lstat().st_mode) or log_path.stat().st_size != 0:
            fail('phase log must be an existing regular zero-length file')
    except OSError as exc:
        fail(f'cannot inspect phase log: {exc}')
    output = Path(output_arg)
    try:
        output.mkdir()
        (output / 'watching').touch(exist_ok=False)
    except OSError as exc:
        fail(f'cannot exclusively create output directory and watching marker: {exc}')
    print('watching', flush=True)

    deadline = time.monotonic() + 15 * 60
    while time.monotonic() < deadline:
        if log_state(log_path):
            break
        time.sleep(0.25)
    else:
        fail('timed out waiting for the target test launch and idle window')

    core_device, app_path, executable, transport = device_binding(device)
    if not log_state(log_path):
        fail('test log left the eligible capture window')
    pid = matching_pid(devicectl_json(device, 'info', 'processes'), core_device, executable)
    binding = {'device': device, 'coreDevice': core_device, 'bundle_id': BUNDLE,
               'app_path': app_path, 'executable_path': executable,
               'pid': pid, 'transport': transport}
    (output / 'binding.json').write_text(json.dumps(binding, indent=2) + '\n', encoding='utf-8')
    if not log_state(log_path):
        fail('test log left the eligible capture window immediately before xctrace')

    command = ['xcrun', 'xctrace', 'record', '--device', device,
               '--template', 'Power Profiler', '--attach', str(pid),
               '--time-limit', '180s', '--output', str(output / 'workload.trace')]
    try:
        result = subprocess.run(command, capture_output=True, check=False)
        stdout, stderr, status = result.stdout, result.stderr, result.returncode
    except OSError as exc:
        stdout, stderr, status = b'', str(exc).encode(), 127
    (output / 'xctrace.stdout').write_bytes(stdout)
    (output / 'xctrace.stderr').write_bytes(stderr)
    (output / 'xctrace.exit-status').write_text(f'{status}\n', encoding='ascii')
    if status:
        detail = stderr.decode(errors='replace').strip()
        fail(f'xctrace exited {status}: {detail}')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError) as exc:
        print(f'capture.py: {exc}', file=sys.stderr, flush=True)
        sys.exit(1)
