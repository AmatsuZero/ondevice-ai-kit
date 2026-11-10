#!/usr/bin/env python3
'''Candidate host runner for Qwen3-ASR 0.6B int8 screening; not promoted.'''
import argparse
import gc
import hashlib
import json
import os
import resource
import sys
import time


def _sha256_file(path, chunk=1 << 20):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for blk in iter(lambda: f.read(chunk), b''):
            h.update(blk)
    return h.hexdigest()


def _sha256_bytes(b):
    return hashlib.sha256(b).hexdigest()


def _is_hex64(s):
    return isinstance(s, str) and len(s) == 64 and all(
        c in '0123456789abcdefABCDEF' for c in s)


def _write_all(fd, data):
    mv = memoryview(data)
    while mv:
        mv = mv[os.write(fd, mv):]


def write_report(fd, report):
    data = json.dumps(report, ensure_ascii=False, allow_nan=False).encode('utf-8')
    os.lseek(fd, 0, os.SEEK_SET)
    os.ftruncate(fd, 0)
    _write_all(fd, data)
    os.fsync(fd)


def safe_join(root, rel):
    if not isinstance(root, str) or not isinstance(rel, str):
        raise ValueError('path args must be strings')
    if rel == '' or os.path.isabs(rel):
        raise ValueError('path not relative: %r' % rel)
    if os.path.islink(root):
        raise ValueError('symlink root not allowed')
    if os.path.abspath(root) != os.path.realpath(root):
        raise ValueError('model root must be canonical (no symlink ancestors)')
    root_real = os.path.realpath(root)
    if not os.path.isdir(root_real):
        raise ValueError('root must be a real directory')
    parts = rel.split('/')
    for part in parts:
        if part == '' or part == '.' or part == '..':
            raise ValueError('invalid path component: %r' % part)
    cur = root_real
    if os.path.islink(cur):
        raise ValueError('symlink root not allowed')
    for part in parts:
        cur = os.path.join(cur, part)
        if os.path.islink(cur):
            raise ValueError('symlink component not allowed: %r' % part)
    target = os.path.realpath(cur)
    if target != root_real and not target.startswith(root_real + os.sep):
        raise ValueError('path escape: %r' % rel)
    return target


MANDATORY = ['conv_frontend.onnx', 'encoder.int8.onnx', 'decoder.int8.onnx',
             'tokenizer/vocab.json', 'tokenizer/merges.txt', 'tokenizer/tokenizer_config.json']
COHORTS = {'mandarin', 'english-near', 'english-far', 'silence', 'nonspeech-noise'}
LOCALES = {'zh-CN', 'en-US'}


def validate_protocol(proto):
    if not isinstance(proto, dict):
        raise ValueError('protocol not object')
    sv = proto.get('schemaVersion')
    if not isinstance(sv, int) or isinstance(sv, bool) or sv != 1:
        raise ValueError('schemaVersion must be 1')
    if proto.get('runtimeVersion') != '2.0.1':
        raise ValueError('runtimeVersion must be 2.0.1')
    mode = proto.get('languageMode', 'forced')
    if mode not in ('forced', 'auto', 'admit-auto-then-forced'):
        raise ValueError('languageMode must be forced, auto or admit-auto-then-forced')
    run_id = proto.get('runId')
    if not isinstance(run_id, str) or run_id == '':
        raise ValueError('runId must be nonempty string')
    model = proto.get('model')
    if not isinstance(model, dict):
        raise ValueError('model missing')
    root = model.get('directory')
    if not isinstance(root, str) or not os.path.isabs(root) or not os.path.isdir(root):
        raise ValueError('model.directory invalid')
    files = model.get('files')
    if not isinstance(files, dict) or not set(MANDATORY).issubset(files):
        raise ValueError('model.files missing mandatory paths')
    for rel, meta in files.items():
        if not isinstance(meta, dict) or 'bytes' not in meta or 'sha256' not in meta:
            raise ValueError('model file meta invalid: %r' % rel)
        b = meta['bytes']
        if not isinstance(b, int) or isinstance(b, bool) or b < 0:
            raise ValueError('model file bytes must be nonnegative int: %r' % rel)
        s = meta['sha256']
        if not _is_hex64(s):
            raise ValueError('model file sha256 must be 64-hex: %r' % rel)
    cases = proto.get('cases')
    if not isinstance(cases, list) or len(cases) == 0:
        raise ValueError('cases must be nonempty list')
    seen = set()
    for c in cases:
        if not isinstance(c, dict):
            raise ValueError('case not object')
        cid = c.get('id')
        if not isinstance(cid, str) or cid == '':
            raise ValueError('case id invalid')
        if cid in seen:
            raise ValueError('duplicate case id: %s' % cid)
        seen.add(cid)
        if c.get('cohort') not in COHORTS:
            raise ValueError('bad cohort: %s' % cid)
        loc = c.get('locale')
        if loc not in LOCALES:
            raise ValueError('bad locale: %s' % cid)
        ref = c.get('reference')
        if not isinstance(ref, str):
            raise ValueError('reference must be string: %s' % cid)
        if c['cohort'] in ('silence', 'nonspeech-noise'):
            if ref != '':
                raise ValueError('%s reference must be empty: %s' % (c['cohort'], cid))
        else:
            if ref == '':
                raise ValueError('non-silence reference must be nonempty: %s' % cid)
        if not isinstance(c.get('pcmPath'), str) or not os.path.isabs(c['pcmPath']):
            raise ValueError('pcmPath must be absolute: %s' % cid)
        samples = c.get('samples')
        if not isinstance(samples, int) or isinstance(samples, bool):
            raise ValueError('samples must be int: %s' % cid)
        if samples < 1 or samples > 480000:
            raise ValueError('samples out of range: %s' % cid)
        if not _is_hex64(c.get('sha256')):
            raise ValueError('sha256 invalid: %s' % cid)
    return root, files, cases, mode


def verify_model(root, files):
    mapping = {}
    for rel, meta in files.items():
        path = safe_join(root, rel)
        if not os.path.isfile(path):
            raise ValueError('model file missing: %s' % rel)
        if os.path.getsize(path) != meta['bytes']:
            raise ValueError('model size mismatch: %s' % rel)
        if _sha256_file(path).lower() != str(meta['sha256']).lower():
            raise ValueError('model sha mismatch: %s' % rel)
        mapping[rel] = {'bytes': meta['bytes'], 'sha256': meta['sha256'], 'verified': True}
    tok = os.path.join(root, 'tokenizer')
    if not os.path.isdir(tok) or os.path.islink(tok):
        raise ValueError('tokenizer dir invalid')
    return mapping


def verify_cases(cases):
    loaded = []
    for c in cases:
        path = c['pcmPath']
        if not os.path.isfile(path):
            raise ValueError('pcm missing: %s' % c['id'])
        if os.path.getsize(path) != 2 * c['samples']:
            raise ValueError('pcm size mismatch: %s' % c['id'])
        with open(path, 'rb') as f:
            pcm = f.read()
        if _sha256_bytes(pcm).lower() != c['sha256'].lower():
            raise ValueError('pcm sha mismatch: %s' % c['id'])
        loaded.append((c, pcm))
    return loaded


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--protocol', required=True)
    ap.add_argument('--output', required=True)
    args = ap.parse_args()

    try:
        fd = os.open(args.output, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError:
        sys.stderr.write('output already exists: %s\n' % args.output)
        return 2

    report = {
        'status': 'running', 'runId': None, 'protocolSHA256': None,
        'runnerSHA256': None, 'runtimeVersion': '2.0.1',
        'config': {'num_threads': 2, 'sample_rate': 16000, 'feature_dim': 128,
                   'decoding_method': 'greedy_search', 'provider': 'cpu',
                   'max_total_len': 512, 'max_new_tokens': 128,
                   'temperature': 1e-6, 'top_p': 0.8, 'seed': 42, 'hotwords': '',
                   'languageMode': None},
        'rows': [], 'modelFiles': {}, 'cleanup': {}, 'error': None,
        'resource': {}, 'pid': os.getpid(), 'model_load_elapsed': None,
    }
    write_report(fd, report)

    recognizer = None
    try:
        proto_bytes = open(args.protocol, 'rb').read()
        protocol_sha = _sha256_bytes(proto_bytes)
        proto = json.loads(proto_bytes.decode('utf-8'))
        root, files, cases, mode = validate_protocol(proto)
        model_files = verify_model(root, files)
        loaded = verify_cases(cases)
        runner_sha = _sha256_file(__file__)

        report['runId'] = proto['runId']
        report['protocolSHA256'] = protocol_sha
        report['runnerSHA256'] = runner_sha
        report['modelFiles'] = model_files
        report['config']['languageMode'] = mode
        write_report(fd, report)

        rows = []
        for case, pcm in loaded:
            rows.append({
                'id': case['id'], 'cohort': case['cohort'], 'locale': case['locale'],
                'samples': case['samples'], 'pcmSha256': case['sha256'],
                'reference': case['reference'], 'status': 'not-attempted', 'text': None,
                'elapsed': None, 'error_type': None, 'error_message': None,
                'languageOption': None, 'admission': None,
                'nativeDecodeCalls': 0, 'admissionRejected': False})
        report['rows'] = rows
        write_report(fd, report)

        import numpy as np
        import sherpa_onnx
        if sherpa_onnx.__version__ != '2.0.1':
            raise RuntimeError('unsupported sherpa_onnx %s' % sherpa_onnx.__version__)
        from pathlib import Path
        t0 = time.monotonic()
        recognizer = sherpa_onnx.OfflineRecognizer.from_qwen3_asr(
            conv_frontend=str(Path(root) / 'conv_frontend.onnx'),
            encoder=str(Path(root) / 'encoder.int8.onnx'),
            decoder=str(Path(root) / 'decoder.int8.onnx'),
            tokenizer=str(Path(root) / 'tokenizer'),
            num_threads=2, sample_rate=16000, feature_dim=128,
            decoding_method='greedy_search', debug=False, provider='cpu',
            max_total_len=512, max_new_tokens=128,
            temperature=1e-6, top_p=0.8, seed=42, hotwords='')
        report['model_load_elapsed'] = time.monotonic() - t0

        any_error = False
        for idx, (case, pcm) in enumerate(loaded):
            row = rows[idx]
            row['status'] = 'running'
            report['rows'] = rows
            write_report(fd, report)
            stream = None
            samples = None
            t1 = time.monotonic()
            try:
                if mode == 'admit-auto-then-forced':
                    stream = recognizer.create_stream()
                    admit_text = ''
                    t_admit = time.monotonic()
                    try:
                        stream.set_option('language', '')
                        samples = np.frombuffer(pcm, dtype='<i2').astype(np.float32) / 32768.0
                        stream.accept_waveform(16000, samples)
                        row['nativeDecodeCalls'] += 1
                        write_report(fd, report)
                        recognizer.decode_stream(stream)
                        admit_text = stream.result.text
                        if stream is not None:
                            stream = None
                        row['admission'] = {'rawText': admit_text,
                                            'elapsed': time.monotonic() - t_admit,
                                            'languageOption': '', 'status': 'recognized'}
                        row['languageOption'] = ''
                        write_report(fd, report)
                        if admit_text == '':
                            row['text'] = ''
                            row['admissionRejected'] = True
                            row['languageOption'] = ''
                            row['status'] = 'recognized'
                            row['elapsed'] = time.monotonic() - t1
                        else:
                            lang = 'Chinese' if case['locale'] == 'zh-CN' else 'English'
                            stream = recognizer.create_stream()
                            stream.set_option('language', lang)
                            stream.accept_waveform(16000, samples)
                            row['nativeDecodeCalls'] += 1
                            write_report(fd, report)
                            recognizer.decode_stream(stream)
                            final_text = stream.result.text
                            if stream is not None:
                                stream = None
                            row['text'] = final_text
                            row['languageOption'] = lang
                            row['status'] = 'recognized'
                            row['elapsed'] = time.monotonic() - t1
                    finally:
                        if stream is not None:
                            stream = None
                        if samples is not None:
                            try:
                                samples.fill(0)
                            except Exception:
                                pass
                else:
                    if mode == 'auto':
                        lang = ''
                    else:
                        lang = 'Chinese' if case['locale'] == 'zh-CN' else 'English'
                    stream = recognizer.create_stream()
                    try:
                        stream.set_option('language', lang)
                        row['languageOption'] = lang
                        samples = np.frombuffer(pcm, dtype='<i2').astype(np.float32) / 32768.0
                        stream.accept_waveform(16000, samples)
                        row['nativeDecodeCalls'] += 1
                        write_report(fd, report)
                        recognizer.decode_stream(stream)
                        row['text'] = stream.result.text
                        row['status'] = 'recognized'
                        row['elapsed'] = time.monotonic() - t1
                    finally:
                        if stream is not None:
                            stream = None
                        if samples is not None:
                            try:
                                samples.fill(0)
                            except Exception:
                                pass
            except Exception as e:
                row['status'] = 'error'
                row['error_type'] = type(e).__name__
                row['error_message'] = str(e)
                any_error = True
            rows[idx] = row
            report['rows'] = rows
            write_report(fd, report)

        report['status'] = 'failed' if any_error else 'completed'
    except Exception as e:
        report['status'] = 'failed'
        report['error'] = {'type': type(e).__name__, 'message': str(e)}
    finally:
        had_recognizer = recognizer is not None
        if recognizer is not None:
            recognizer = None
        gc.collect()
        report['cleanup'] = {'python_refs_dropped': had_recognizer,
                             'note': 'Python references dropped/GC requested; native release not proven by Python API'}
        report['resource'] = {'ru_maxrss': resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
                              'platform': sys.platform}
        report['pid'] = os.getpid()
        if report['status'] not in ('completed', 'failed'):
            report['status'] = 'failed'
        try:
            write_report(fd, report)
        finally:
            os.close(fd)
    return 1 if report['status'] == 'failed' else 0


if __name__ == '__main__':
    sys.exit(main())