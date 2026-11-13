import copy
import hashlib
import json
import re

import terminal_file_io as tf
from terminal_device_restore import _read_json, _read_locals, _validate_bindings
from terminal_polling import _binding_snapshot, _parse_report
from terminal_observation_coordinator import _validate_sample, _validate_clock
from terminal_observer import acknowledgement
from terminal_device_lifecycle import parse_instrumentation_finish
from adb_terminal_observation import _preflight_outputs
from adb_terminal_report_reader import AdbTerminalReportReader

APP = 'io.ondeviceai.acceptance.qwenterminal'
RUN = 'w3-qwen-terminal-20261112-01'
FIXED_FP = 'ff5b57b0b1ff08d8d8c984efd04dfbb86f62a5cc4cab02700c5804a0c7c0c3c5'
MODEL = 'no_backup/qwen3-asr-0.6b-int8-393f8a14'
REPORT = 'files/w3-qwen-terminal/report-' + RUN + '.json'
REPORT_DIR = 'files/w3-qwen-terminal'
MODELS = (
    'conv_frontend.onnx',
    'decoder.int8.onnx',
    'encoder.int8.onnx',
    'tokenizer/merges.txt',
    'tokenizer/tokenizer_config.json',
    'tokenizer/vocab.json',
)
MANIFEST_SHA256 = '8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f'
CASE_COUNT = 1
MAX_NATIVE_DECODE_CALLS = 2

_DEVICE_OUTPUT_RELS = {
    'device-report.json': 'device-report.json',
    'device-report-confirm.json': 'device-report-confirm.json',
    'collection.json': 'collection.json',
}


def _is_int(v):
    return isinstance(v, int) and not isinstance(v, bool)


def _ctx_get(ctx, key, default=None):
    if isinstance(ctx, dict):
        return ctx.get(key, default)
    return getattr(ctx, key, default)


def _require(cond, msg):
    if not cond:
        raise ValueError(msg)


def _load_local(root, ctx, serial):
    _require(isinstance(serial, str) and serial != '', 'serial must be non-blank')
    _require(serial.strip() == serial, 'serial must not have surrounding whitespace')
    _require(not any(ord(c) < 0x20 for c in serial), 'serial must not contain control chars')

    _validate_bindings(ctx, serial)

    package = ctx['package']
    _require(isinstance(package, dict), 'package must be a receipt dict')
    _require(package.get('sourceFingerprint') == FIXED_FP, 'package sourceFingerprint must equal fixed FP')
    _require(isinstance(package.get('files'), dict) and set(package['files'].keys()) == {'main.apk', 'test.apk'}, 'package files must map main.apk and test.apk')

    plan = ctx['plan']
    _require(isinstance(plan, dict), 'plan must be a dict')
    _require(plan.get('sourceFingerprint') == FIXED_FP, 'plan sourceFingerprint must equal fixed FP')
    _require(plan.get('runId') == RUN, 'plan runId must equal fixed RUN')
    _require(plan.get('manifestSHA256') == MANIFEST_SHA256, 'plan manifestSHA256 must equal fixed plan')
    _require(_is_int(plan.get('caseCount')) and plan['caseCount'] == CASE_COUNT, 'plan caseCount must equal 1')
    _require(_is_int(plan.get('maxNativeDecodeCalls')) and plan['maxNativeDecodeCalls'] == MAX_NATIVE_DECODE_CALLS,
             'plan maxNativeDecodeCalls must equal 2')

    models = ctx['models']
    _require(isinstance(models, dict) and set(models.keys()) == set(MODELS),
             'models must contain exactly the 6 MODELS keys')
    for _name, _row in models.items():
        _require(isinstance(_row, dict), 'model row must be dict')
        _require(_is_int(_row.get('bytes')) and _row['bytes'] > 0, 'model bytes must be positive exact int')
        _require(isinstance(_row.get('sha256'), str) and re.fullmatch(r'[0-9a-f]{64}', _row['sha256']),
                 'model sha256 must be 64-char lowercase hex')

    adb = ctx['adb']
    _require(isinstance(adb, list) and len(adb) == 5, 'adb must be a 5-element list')
    _require(adb[0] == 'rtk' and adb[1] == 'proxy' and adb[3] == '-s' and adb[4] == serial,
             'adb command structure mismatch')
    executable = adb[2]
    _require(isinstance(executable, str) and executable != '' and chr(0) not in executable,
             'adb executable must be non-empty without NUL')
    _require(all(isinstance(a, str) and a != '' and chr(0) not in a for a in adb),
             'adb command parts must be non-empty strings without NUL')

    locals_info = _read_locals(root, serial, ctx)

    _preflight_outputs(root, _DEVICE_OUTPUT_RELS)

    run_started = _read_json(root, 'run-started.json')
    host_result = _read_json(root, 'host-result.json')
    terminal_receipt = _read_json(root, 'terminal-receipt.json')
    terminal_before_link = _read_json(root, 'terminal-before-link.json')

    observed = tf.read_stable(root, 'terminal-observed.json', 1048576)
    instr_log = tf.read_stable(root, 'instrumentation.log', 1048576)
    ack_raw = tf.read_stable(root, 'terminal-ack.txt', 1024)

    sha256 = hashlib.sha256(observed).hexdigest()

    _require(run_started.get('runId') == RUN, 'run-started runId mismatch')
    _require(run_started.get('sourceFingerprint') == FIXED_FP, 'run-started fingerprint mismatch')
    started_ns = run_started.get('startedNs')
    _require(_is_int(started_ns) and 0 <= started_ns <= 2 ** 63 - 1, 'startedNs must be valid int64')
    _require(run_started.get('automaticRetry') is False, 'automaticRetry must be False')
    _require(_is_int(run_started.get('plannedConstructors')) and run_started.get('plannedConstructors') == 1, 'plannedConstructors must be 1')
    _require(_is_int(run_started.get('maxPlannedStreams')) and run_started.get('maxPlannedStreams') == 2, 'maxPlannedStreams must be 2')
    _require(_is_int(run_started.get('maxPlannedDecode')) and run_started.get('maxPlannedDecode') == 2, 'maxPlannedDecode must be 2')
    _require(_is_int(run_started.get('plannedMicrophone')) and run_started.get('plannedMicrophone') == 0, 'plannedMicrophone must be 0')
    _require(_is_int(run_started.get('inputCount')) and run_started.get('inputCount') == 1, 'inputCount must be 1')

    _require(host_result.get('runId') == RUN, 'host-result runId mismatch')
    _require(host_result.get('sourceFingerprint') == FIXED_FP, 'host-result fingerprint mismatch')
    _require(host_result.get('finishVerified') is True, 'finishVerified must be True')
    _require(host_result.get('qualityAccepted') is False, 'qualityAccepted must be False')
    _require(host_result.get('truePeakMeasured') is False, 'truePeakMeasured must be False')
    _require(host_result.get('automaticRetry') is False, 'automaticRetry must be False')
    host_observation = host_result.get('observation')
    _require(isinstance(host_observation, dict), 'host observation missing')
    _require(host_observation.get('qualityAccepted') is False, 'host observation qualityAccepted False')
    _require(host_observation.get('truePeakMeasured') is False, 'host observation truePeakMeasured False')
    _require(_is_int(host_observation.get('pollSamples')) and host_observation['pollSamples'] > 0,
             'pollSamples must be positive int')
    host_pub = host_observation.get('publication')
    _require(isinstance(host_pub, dict), 'host publication missing')
    _require(host_pub.get('published') is True, 'publication.published True')
    _require(host_pub.get('durability_verified') is True, 'publication.durability_verified True')
    _require(host_pub.get('stage_retained') is True, 'publication.stage_retained True')
    _require(host_pub.get('final_relative') == REPORT_DIR + '/ack-' + RUN + '.txt',
             'publication final_relative mismatch')
    _require(host_pub.get('stage_relative') == REPORT_DIR + '/ack-' + RUN + '.txt.stage',
             'publication stage_relative mismatch')

    _require(_is_int(terminal_receipt.get('schemaVersion')) and terminal_receipt['schemaVersion'] == 1,
             'receipt schemaVersion must be 1')
    _require(terminal_receipt.get('clockDomain') == 'host-monotonic-ns', 'receipt clockDomain mismatch')
    _require(terminal_receipt.get('terminalReportSha256') == sha256, 'receipt terminalReportSha256 mismatch')
    binding5 = terminal_receipt.get('binding')
    _require(isinstance(binding5, dict), 'receipt binding missing')
    _require(set(binding5.keys()) == {'runId', 'sourceFingerprint', 'pid', 'startTicks', 'terminalReportSha256'}, 'receipt binding must have exactly five fields')
    for _k in ('runId', 'sourceFingerprint', 'pid', 'startTicks', 'terminalReportSha256'):
        _require(_k in binding5, 'receipt binding missing ' + _k)
    _require(binding5['runId'] == RUN, 'binding runId mismatch')
    _require(binding5['sourceFingerprint'] == FIXED_FP, 'binding fingerprint mismatch')
    _require(_is_int(binding5['pid']) and 0 < binding5['pid'] <= 2147483647, 'binding pid must be positive int')
    _require(isinstance(binding5['startTicks'], str) and re.fullmatch(r'[1-9][0-9]*', binding5['startTicks']),
             'binding startTicks must be positive canonical decimal')
    _require(binding5['terminalReportSha256'] == sha256, 'binding terminalReportSha256 mismatch')

    binding4 = _binding_snapshot({k: binding5[k] for k in ('runId', 'sourceFingerprint', 'pid', 'startTicks')})
    parsed_report = _parse_report(observed, binding4)

    _require(parsed_report.get('terminal') is True, 'report terminal must be True')
    _require(parsed_report.get('status') in ('completed', 'failed'), 'report status completed/failed')
    _require(_is_int(parsed_report.get('actualConstructors')) and 0 <= parsed_report['actualConstructors'] <= 1,
             'actualConstructors out of range')
    _require(_is_int(parsed_report.get('nativeDecodeCalls')) and 0 <= parsed_report['nativeDecodeCalls'] <= 2,
             'nativeDecodeCalls out of range')
    _require(_is_int(parsed_report.get('streamsCreated')) and 0 <= parsed_report['streamsCreated'] <= 2,
             'streamsCreated out of range')
    _require(_is_int(parsed_report.get('microphoneStarts')) and parsed_report['microphoneStarts'] == 0,
             'microphoneStarts must be 0')
    _require(_is_int(parsed_report.get('networkRequests')) and parsed_report['networkRequests'] == 0,
             'networkRequests must be 0')
    _require(parsed_report.get('inputManifestSHA256') == MANIFEST_SHA256, 'report manifest hash mismatch')

    sample_before = terminal_receipt.get('sampleBefore')
    sample_after = terminal_receipt.get('sampleAfter')
    _validate_sample(sample_before, binding4)
    _validate_sample(sample_after, binding4)

    _require(_is_int(terminal_before_link.get('schemaVersion')) and terminal_before_link['schemaVersion'] == 1,
             'before-link schemaVersion must be 1')
    _require(terminal_before_link.get('clockDomain') == 'host-monotonic-ns', 'before-link clockDomain mismatch')
    before_binding5 = terminal_before_link.get('binding')
    _require(before_binding5 == binding5, 'before-link binding must equal receipt binding')
    _require(terminal_before_link.get('terminalReportSha256') == sha256, 'before-link sha256 mismatch')
    sample_before_link = terminal_before_link.get('sampleBeforeLink')
    _validate_sample(sample_before_link, binding4)
    sample_started = terminal_before_link.get('sampleStartedNs')
    sample_finished = terminal_before_link.get('sampleFinishedNs')
    _validate_clock(sample_started, 'sampleStartedNs')
    _validate_clock(sample_finished, 'sampleFinishedNs')

    observation = terminal_receipt.get('observation')
    _require(isinstance(observation, dict), 'receipt observation missing')
    _require(observation.get('rssBytes') == max(sample_before['rssBytes'], sample_after['rssBytes']),
             'observation rssBytes must equal max of sample rssBytes')
    _require(observation.get('deviceAvailableBytes') == min(sample_before['deviceAvailableBytes'], sample_after['deviceAvailableBytes']),
             'observation deviceAvailableBytes must equal min of sample deviceAvailableBytes')
    _require(observation == host_observation.get('observation'), 'receipt observation must equal host observation')
    _validate_clock(observation.get('uptimeBeforeNs'), 'observation.uptimeBeforeNs')
    _validate_clock(observation.get('firstReportReadNs'), 'observation.firstReportReadNs')
    _validate_clock(observation.get('secondReportReadNs'), 'observation.secondReportReadNs')
    _validate_clock(observation.get('uptimeAfterNs'), 'observation.uptimeAfterNs')
    _require(_is_int(started_ns) and _is_int(observation.get('uptimeBeforeNs')), 'started_ns/uptimeBeforeNs ints required')
    _require(started_ns <= observation['uptimeBeforeNs'], 'started_ns <= uptimeBeforeNs required')
    _require(_is_int(observation.get('uptimeAfterNs')) and _is_int(sample_started) and _is_int(sample_finished),
             'observation/before-link ints required')
    _require(observation['uptimeAfterNs'] <= sample_started <= sample_finished,
             'uptimeAfterNs <= sampleStartedNs <= sampleFinishedNs required')

    computed_ack = acknowledgement(observed, observed, binding5, observation)
    _require(computed_ack == ack_raw, 'local ACK bytes mismatch')

    finish = parse_instrumentation_finish(
        instr_log, expected_report_sha256=sha256, terminal_status=parsed_report['status'])
    host_finish = host_result.get('finish')
    _require(isinstance(host_finish, dict), 'host.finish must be a dict')
    _require(set(host_finish) == set(finish), 'host.finish keyset must match parsed finish')
    for _key in finish:
        _exp = finish[_key]
        _act = host_finish[_key]
        _require(type(_act) is type(_exp), 'host.finish ' + _key + ' type mismatch')
        _require(_act == _exp, 'host.finish ' + _key + ' value mismatch')

    return {
        'raw': observed,
        'report': parsed_report,
        'sha256': sha256,
        'binding': binding4,
        'before': copy.deepcopy(locals_info),
        'models': copy.deepcopy(models),
        'adb': executable,
        'serial': serial,
    }

def _verify_remote(ctx, data):
    if ctx['text']('shell', 'am', 'get-current-user') != '0':
        raise ValueError('current user is not 0')
    ctx['identity']()
    ctx['gate']()
    if ctx['ordinary']() != data['before']['ordinary']:
        raise ValueError('ordinary map drift')
    if ctx['ordinary_models']() != data['before']['ordinary_models']:
        raise ValueError('ordinary_models drift')
    for alias, frozen in data['before']['ordinary_apk_hashes'].items():
        if ctx['installed_sha'](alias) != frozen:
            raise ValueError('apk hash drift: ' + alias)
    ctx['verify_installed']()
    for name in MODELS:
        expected = data['models'][name]['sha256']
        observed = ctx['text']('shell', 'run-as', APP, 'sha256sum', MODEL + '/' + name)
        if observed != expected + '  ' + MODEL + '/' + name:
            raise ValueError('model hash drift: ' + name)
    return None


def _read_reports(root, ctx, data, receipt):
    def runner(argv, timeout):
        if argv[:3] != [data['adb'], '-s', data['serial']]:
            raise ValueError('unexpected runner argv prefix')
        return ctx['call'](*argv[3:], timeout=timeout).stdout

    reader = AdbTerminalReportReader(
        data['adb'], data['serial'], APP, RUN,
        timeout_seconds=2.0, runner=runner,
    )

    first = reader.read()
    tf.write_new_durable(root, 'device-report.json', first)
    receipt['reportPresent'] = True

    second = reader.read()
    tf.write_new_durable(root, 'device-report-confirm.json', second)

    receipt['twoReadsIdentical'] = (first == second)

    if first != second:
        raise ValueError('two consecutive reads differ')

    if first != data['raw']:
        raise ValueError('remote report raw mismatch vs local observed')
    if hashlib.sha256(first).hexdigest() != data['sha256']:
        raise ValueError('remote report sha256 mismatch')

    remote = ctx['text']('shell', 'run-as', APP, 'sha256sum', REPORT, timeout=2)
    if remote != data['sha256'] + '  ' + REPORT:
        raise ValueError('remote REPORT sha256sum mismatch')
    return None


def collect(root, ctx, *, serial):
    data = _load_local(root, ctx, serial)
    receipt = {
        "schemaVersion": 1,
        "runId": RUN,
        "sourceFingerprint": FIXED_FP,
        "serial": data['serial'],
        "reportPresent": False,
        "twoReadsIdentical": False,
        "terminalAccepted": False,
        "ordinaryUnchanged": False,
        "modelHashesAfterMatch": {m: False for m in MODELS},
        "errors": [],
        "qualityAccepted": False,
        "truePeakMeasured": False,
        "nativePolicyEffectVerified": False,
        "automaticRetry": False,
    }
    final_write_started = False
    try:
        _verify_remote(ctx, data)
        _read_reports(root, ctx, data, receipt)
        _verify_remote(ctx, data)
        for m in MODELS:
            receipt["modelHashesAfterMatch"][m] = True
        receipt["ordinaryUnchanged"] = True
        receipt["terminalAccepted"] = True
        receipt["sha256"] = data['sha256']
        receipt["status"] = data['report']['status']
        final_write_started = True
        tf.write_new_durable(
            root, "collection.json",
            json.dumps(receipt, sort_keys=True, allow_nan=False,
                       separators=(",", ":")).encode())
        return receipt
    except BaseException as primary:
        if final_write_started:
            raise
        receipt["terminalAccepted"] = False
        receipt["errors"].append(
            {"class": type(primary).__name__, "message": str(primary)})
        try:
            tf.write_new_durable(
                root, "collection.json",
                json.dumps(receipt, sort_keys=True, allow_nan=False,
                           separators=(",", ":")).encode())
        except BaseException as secondary:
            primary.add_note(f"{type(secondary).__name__}: {secondary}")
        raise