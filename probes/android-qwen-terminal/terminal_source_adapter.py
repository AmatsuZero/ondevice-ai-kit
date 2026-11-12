"""Terminal source adapter: pure source transformation of the immutable FILE driver."""
import hashlib

PINNED_SHA256 = 'b7a8a7084b92d86382fa096656c09339b5267ac8bb972968610a9fffacb11435'

_OLD_OUT = "OUT = ROOT / 'artifacts/verification/w3-qwen-file-20261111'"
_NEW_OUT = "OUT = ROOT / 'artifacts/verification/w3-qwen-terminal-20261112'"
_OLD_APP = "APP = 'io.ondeviceai.acceptance.qwenfile'"
_NEW_APP = "APP = 'io.ondeviceai.acceptance.qwenterminal'"
_OLD_RUN = "RUN = 'w3-qwen-file-20261111-01'"
_NEW_RUN = "RUN = 'w3-qwen-terminal-20261112-01'"
_OLD_REPORT = "REPORT = 'files/w3-qwen-file/report-' + RUN + '.json'"
_NEW_REPORT = "REPORT = 'files/w3-qwen-terminal/report-' + RUN + '.json'"
_OLD_RUNNER = "RUNNER = TEST + '/' + APP + '.QwenFileInstrumentation'"
_NEW_RUNNER = "RUNNER = TEST + '/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation'"
_OLD_ASSERT = "assert plan['maxNativeDecodeCalls'] == 130 and plan['caseCount'] == 65"
_NEW_ASSERT = "assert plan['maxNativeDecodeCalls'] == 2 and plan['caseCount'] == 1"
_OLD_CALL = "        proc = subprocess.run(adb + list(args), capture_output=True, timeout=timeout)"
_NEW_CALL = "        _terminal_before_transport()" + chr(10) + "        proc = subprocess.run(adb + list(args), capture_output=True, timeout=timeout)"
_TAIL_ANCHOR = "    elif action == 'prepare':"


def adapt_source(source: str) -> str:
    if type(source) is not str:
        raise TypeError('source must be the exact built-in str, not a subclass or other type')
    if hashlib.sha256(source.encode('utf-8')).hexdigest() != PINNED_SHA256:
        raise ValueError('source drift: SHA256 mismatch against pinned immutable FILE driver')
    out = source
    out = out.replace(_OLD_OUT, _NEW_OUT, 1)
    out = out.replace(_OLD_APP, _NEW_APP, 1)
    out = out.replace(_OLD_RUN, _NEW_RUN, 1)
    out = out.replace(_OLD_REPORT, _NEW_REPORT, 1)
    out = out.replace(_OLD_RUNNER, _NEW_RUNNER, 1)
    out = out.replace(_OLD_ASSERT, _NEW_ASSERT, 1)
    out = out.replace(_OLD_CALL, _NEW_CALL, 1)
    idx = out.index(_TAIL_ANCHOR)
    out = out[:idx] + "    else: return _terminal_dispatch(action, locals())" + chr(10)
    compile(out, '<adapted terminal source>', 'exec')
    return out