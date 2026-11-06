"""Generate test-only persistent runners from the two frozen candidate inputs."""

import argparse
import hashlib
from pathlib import Path


SOURCES = {
    'asr-lifecycle': (
        '5e056e07af1f3c3d5b180b3df51b937557390db80692e98287a164de0a3997de',
        'SherpaAsrLifecycleInstrumentation',
    ),
    'sr': (
        '910d4724c7806aff2c2995da05f40e3c452b6b0e73ec4603c0c6504df9f8343d',
        'VideoSrInstrumentation',
    ),
}
ASR_KEYS = (
    'status', 'scope', 'microphoneStarts', 'audioPersisted', 'transcriptPersisted',
    'threadStacksPersisted',
    'cancel.nativeDecodeObserved', 'cancel.busyWhileNativeDecode',
    'cancel.busyBeforeDrain', 'cancel.noResultDelivered',
    'cancel.sameBackendReentryMatched', 'cancel.callerInputUnchanged',
    'close.nativeDecodeObserved', 'close.waitedForActiveNativeRequest',
    'close.rejectedDuringDrain', 'close.noResultDelivered',
    'close.rejectedAfterClose', 'close.idempotent', 'close.callerInputUnchanged',
)


def generate(base, *, kind):
    if kind not in SOURCES:
        raise ValueError(f'Unsupported candidate report kind: {kind}')
    digest, original_name = SOURCES[kind]
    if hashlib.sha256(base.encode()).hexdigest() != digest:
        raise ValueError('Candidate instrumentation source differs from the frozen input')
    text = base

    def replace(old, new):
        nonlocal text
        if text.count(old) != 1:
            raise ValueError(f'Expected one insertion point: {old}')
        text = text.replace(old, new)

    recorder = Path(__file__).with_name('Recorder.kt.inc').read_text()
    keys = ASR_KEYS if kind == 'asr-lifecycle' else ('status',)
    recorder = recorder.replace('@KIND@', kind).replace(
        '@RESULT_KEYS@', ', '.join(f'"{key}"' for key in keys))
    replace(f'class {original_name} : Instrumentation() {{',
            f'class Persistent{original_name} : Instrumentation() {{\n' + recorder)
    if kind == 'asr-lifecycle':
        replace('        start()', '        configureReport(arguments)\n        start()')
    else:
        replace('super.onCreate(arguments); start()',
                'super.onCreate(arguments); configureReport(arguments); start()')
        # Keep the native checks; remove stack text from both failure diagnostics.
        replace('.put("error", error.stackTraceToString())',
                '.put("errorClass", error.javaClass.name)')
        replace('.putString("error", error.stackTraceToString())',
                '.putString("errorClass", error.javaClass.name)')
    start = '    override fun onStart() {'
    added = '\n        if (recorderCheck()) return'
    if kind == 'sr':
        added += ('\n        startActivitySync(android.content.Intent(targetContext, MainActivity::class.java)'
                  '\n            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))')
    replace(start, start + added)
    return text


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('base', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--kind', required=True, choices=tuple(SOURCES))
    args = parser.parse_args()
    generated = generate(args.base.read_text(), kind=args.kind)
    with args.output.open('x') as stream:
        stream.write(generated)
