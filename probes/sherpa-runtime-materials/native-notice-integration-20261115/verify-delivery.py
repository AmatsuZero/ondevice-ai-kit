from pathlib import Path
from collections import Counter
import hashlib, json, zipfile

PREFIX = 'assets/ondeviceai/sherpa-asr/notices/'


def verify_notice_archive(path, expected):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        counts = Counter(names)
        actual = {name for name in names if name.startswith(PREFIX) and not name.endswith('/')}
        if actual != set(expected):
            raise ValueError('notice member set differs')
        for name, record in expected.items():
            if counts[name] != 1:
                raise ValueError('duplicate or missing notice member: ' + name)
            data = archive.read(name)
            if len(data) != record['bytes'] or hashlib.sha256(data).hexdigest() != record['sha256']:
                raise ValueError('notice byte mismatch: ' + name)
    return {'path': str(path), 'sha256': hashlib.sha256(Path(path).read_bytes()).hexdigest(), 'noticeCount': len(expected), 'noticeBytes': sum(x['bytes'] for x in expected.values())}


def mutate_archive(source, destination, member, mode):
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(destination, 'w') as changed:
        for info in original.infolist():
            data = original.read(info.filename)
            if info.filename == member:
                if mode == 'delete':
                    continue
                if mode == 'change-byte':
                    data = bytes([data[0] ^ 1]) + data[1:]
                else:
                    raise ValueError('unknown mutation')
            changed.writestr(info, data)


def run(apk, expected_path, output_dir):
    expected = json.loads(Path(expected_path).read_text())
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    good = verify_notice_archive(apk, expected)
    members = [
        'assets/ondeviceai/sherpa-asr/notices/native/darts-clone-simple-sentencepiece-v0.7/NOTICE.txt',
        'assets/ondeviceai/sherpa-asr/notices/native/UPSTREAM-MODIFICATIONS.md',
        'assets/ondeviceai/sherpa-asr/notices/native/index.json',
    ]
    failures = []
    for number, member in enumerate(members):
        if member not in expected:
            raise AssertionError('missing required mutation target')
        for mode in ('delete', 'change-byte'):
            altered = output_dir / (str(number) + '-' + mode + '.apk')
            mutate_archive(apk, altered, member, mode)
            try:
                verify_notice_archive(altered, expected)
            except ValueError as error:
                failures.append({'member': member, 'mutation': mode, 'rejected': True, 'error': str(error), 'path': str(altered), 'sha256': hashlib.sha256(altered.read_bytes()).hexdigest()})
            else:
                raise AssertionError('mutated APK was accepted: ' + mode + ' ' + member)
    result = {'original': good, 'negativeCases': failures, 'originalAPKUnchanged': hashlib.sha256(Path(apk).read_bytes()).hexdigest() == good['sha256'], 'scope': 'known JVM/native notice subsets retention only; no runtime, complete composition or release acceptance'}
    (output_dir / 'verification.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


if __name__ == '__main__':
    import sys
    print(json.dumps(run(*sys.argv[1:]), indent=2))
