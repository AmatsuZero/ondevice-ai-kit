"""Create a test-only runner overlay without editing frozen candidate inputs."""
import argparse
import hashlib
from pathlib import Path


BASE_SHA = 'c81edb656aa138cd9fd140ecd8ca460e25f8510be6dcaf469eee6eba70d560c4'


def generate(base):
    if hashlib.sha256(base.encode()).hexdigest() != BASE_SHA:
        raise ValueError('TTS probe source differs from the reviewed exact candidate')
    text = base.replace('TtsProbeInstrumentation', 'PersistentTtsProbeInstrumentation')
    def replace(old, new):
        nonlocal text
        if text.count(old) != 1:
            raise ValueError(f'Expected one insertion point: {old}')
        text = text.replace(old, new)
    replace('        start()', '        configureReport(arguments)\n        start()')
    replace('    override fun onStart() {', '    override fun onStart() {\n        if (recorderCheck()) return')
    for marker in ['normal-start', 'normal-done', 'cancel-request completed=${pending.isCompleted}',
                   'cancel-joined', 'followup-done']:
        old = f'Log.i("ODAI_TTS_PROBE", "{marker}")'
        replace(old, old + f'\n                checkpoint("{marker}")')
    replace('                client.close()', '                checkpoint("close-start")\n                client.close()\n                checkpoint("close-done")')
    replace('            check(sdkProbe.third == "closed") { "SDK close not enforced: ${sdkProbe.third}" }',
            '            check(sdkProbe.third == "closed") { "SDK close not enforced: ${sdkProbe.third}" }\n            checkpoint("close-rejected")')
    replace('class PersistentTtsProbeInstrumentation : Instrumentation() {',
            'class PersistentTtsProbeInstrumentation : Instrumentation() {\n' +
            Path(__file__).with_name('Recorder.kt.inc').read_text())
    return text


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('base', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    with args.output.open('x') as stream:
        stream.write(generate(args.base.read_text()))
