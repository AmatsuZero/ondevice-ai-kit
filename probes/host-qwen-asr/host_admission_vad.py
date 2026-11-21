#!/usr/bin/env python3
import argparse, hashlib, json, os, sys, gc, math
import time as _time


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def sha256_bytes(b):
    return hashlib.sha256(b).hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--protocol', required=True)
    ap.add_argument('--output', required=True)
    args = ap.parse_args()

    # 1) reserve output exclusive at start
    try:
        out = open(args.output, 'x')
    except FileExistsError:
        sys.stderr.write('OUTPUT exists, preserved')
        return 3

    report = {
        'tool': 'host_admission_vad',
        'runtimeVersion': None,
        'protocolSha256': None,
        'runnerSha256': sha256_file(os.path.abspath(__file__)),
        'config': None,
        'plannedIds': [],
        'cases': [],
        'status': 'running',
        'exitCode': 0,
        'nativeRelease': 'unproven',
        'asrCalls': 0,
    }

    def persist():
        out.seek(0)
        out.truncate()
        json.dump(report, out, ensure_ascii=False, allow_nan=False)
        out.flush()
        os.fsync(out.fileno())

    try:
        # read & verify protocol before any native work
        with open(args.protocol, 'rb') as f:
            pbytes = f.read()
        protocol = json.loads(pbytes.decode('utf-8'))
        report['protocolSha256'] = sha256_bytes(pbytes)

        rv = protocol.get('runtimeVersion')
        if rv != '2.0.1':
            raise ValueError('runtimeVersion expected 2.0.1 got %r' % (rv,))
        report['runtimeVersion'] = rv

        model = protocol['model']
        mbytes = open(model['path'], 'rb').read()
        if len(mbytes) != model['bytes'] or sha256_bytes(mbytes) != model['sha256']:
            raise ValueError('MODEL hash/size mismatch')

        cases = protocol['cases']
        if not cases:
            raise ValueError('no cases')
        ids = [c['id'] for c in cases]
        if len(ids) != len(set(ids)):
            raise ValueError('DUPLICATE case ids')
        for c in cases:
            s = c['samples']
            if not isinstance(s, int) or isinstance(s, bool) or s < 1 or s > 480000:
                raise ValueError('INVALID samples for ' + str(c['id']))

        # 2) read/hash/size-validate ALL pcm before importing sherpa_onnx
        loaded = {}
        for c in cases:
            pcm = open(c['pcmPath'], 'rb').read()
            if sha256_bytes(pcm) != c['sha256']:
                raise ValueError('pcm sha256 mismatch for ' + str(c['id']))
            if len(pcm) != c['samples'] * 2:
                raise ValueError('pcm size mismatch for ' + str(c['id']))
            loaded[c['id']] = pcm

        # report running with all plannedIDs BEFORE any native object
        report['plannedIds'] = ids
        report['status'] = 'running'
        persist()

        # import native modules only after full validation
        import numpy as np
        import sherpa_onnx
        if getattr(sherpa_onnx, '__version__', None) != '2.0.1':
            raise ValueError('sherpa_onnx __version__ expected 2.0.1 got %r' % (getattr(sherpa_onnx, '__version__', None),))

        cfg = sherpa_onnx.VadModelConfig()
        cfg.silero_vad.model = model['path']
        cfg.silero_vad.threshold = 0.5
        cfg.silero_vad.min_silence_duration = 0.5
        cfg.silero_vad.min_speech_duration = 0.25
        cfg.silero_vad.max_speech_duration = 30
        cfg.silero_vad.window_size = 512
        cfg.sample_rate = 16000
        cfg.num_threads = 1
        cfg.provider = 'cpu'
        cfg.debug = False
        report['config'] = {'threshold': 0.5, 'min_silence_duration': 0.5,
                            'min_speech_duration': 0.25, 'max_speech_duration': 30,
                            'window_size': 512, 'sample_rate': 16000,
                            'num_threads': 1, 'provider': 'cpu'}

        any_error = False
        for c in cases:
            cid = c['id']
            pcm = loaded[cid]
            row = {'id': cid, 'status': 'running', 'cohort': c.get('cohort'),
                   'admitted': False,
                   'model': {'sha256': model['sha256'], 'bytes': model['bytes']},
                   'pcm': {'sha256': c['sha256'], 'bytes': len(pcm)},
                   'floatHashBefore': None, 'floatHashAfter': None,
                   'rawSamples': None, 'floatSamples': None, 'paddedSamples': 0,
                   'segments': [], 'elapsed': 0.0, 'error': None}
            t0 = _time.monotonic()
            vad = None
            wave = None
            try:
                raw = np.frombuffer(pcm, dtype='<i2')
                wave = raw.astype(np.float32) / 32768.0
                fbefore = sha256_bytes(wave.tobytes())
                vad = sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds=60)
                n = len(wave)
                padded = 0
                i = 0
                while i < n:
                    end = min(i + 512, n)
                    win = wave[i:end]
                    if len(win) < 512:
                        padded = 512 - len(win)
                        win = np.concatenate([win, np.zeros(padded, dtype=np.float32)])
                    vad.accept_waveform(win)
                    while not vad.empty():
                        seg = vad.front
                        row['segments'].append({'start': int(seg.start), 'length': int(len(seg.samples))})
                        vad.pop()
                    i = end
                vad.flush()
                while not vad.empty():
                    seg = vad.front
                    row['segments'].append({'start': int(seg.start), 'length': int(len(seg.samples))})
                    vad.pop()
                fafter = sha256_bytes(wave.tobytes())
                if fbefore != fafter:
                    raise ValueError('float array mutated during VAD for ' + str(cid))
                row['floatHashBefore'] = fbefore
                row['floatHashAfter'] = fafter
                row['rawSamples'] = int(len(raw))
                row['floatSamples'] = int(n)
                row['paddedSamples'] = int(padded)
                row['admitted'] = bool(row['segments'])
                row['status'] = 'recognized'
                row['elapsed'] = float(_time.monotonic() - t0)
                if not math.isfinite(row['elapsed']):
                    raise ValueError('nonfinite elapsed')
            except Exception as e:
                any_error = True
                row['status'] = 'error'
                row['error'] = {'type': type(e).__name__, 'message': str(e)}
                row['elapsed'] = float(_time.monotonic() - t0)
            finally:
                vad = None
                if wave is not None:
                    try:
                        wave.fill(0)
                    except Exception:
                        pass
                gc.collect()
            report['cases'].append(row)
            persist()

        if any_error:
            report['status'] = 'failed'
            report['exitCode'] = 1
        else:
            report['status'] = 'completed'
            report['exitCode'] = 0
        persist()
        return report['exitCode']
    except Exception as e:
        report['status'] = 'failed'
        report['exitCode'] = 1
        report['error'] = {'type': type(e).__name__, 'message': str(e)}
        try:
            persist()
        except Exception:
            pass
        return 1
    finally:
        try:
            out.close()
        except Exception:
            pass


if __name__ == '__main__':
    sys.exit(main())
