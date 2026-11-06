'''Pure reader for on-device super-resolution observation reports.

adb exec-out cat may run before the AtomicFile report exists, so a failed
command or an incomplete payload means the report is not yet readable and is
reported as None. Once a complete JSON document is present its shape is
checked strictly; a malformed report raises ObservationError. Detailed media
and source validation belongs to a separate validator.
'''

import json


class ObservationError(ValueError):
    '''A well-formed JSON report violates the expected observation shape.'''


def read_observation(returncode, stdout, *, run_id):
    '''Return the parsed report dict, or None when it is not yet readable.

    This is a pure function: no IO, no process restart, no sleeps. Only
    decode failures collapse to None. Bad argument types and parseable but
    malformed reports raise ObservationError. A valid report is returned
    unchanged, including nonterminal progress.
    '''
    if not isinstance(returncode, int) or isinstance(returncode, bool):
        raise ObservationError('returncode must be an int')
    if returncode != 0:
        return None  # connection unavailable

    if not isinstance(run_id, str):
        raise ObservationError('run_id must be a str')
    if not isinstance(stdout, (str, bytes)):
        raise ObservationError('stdout must be str or bytes')

    if isinstance(stdout, bytes):
        try:
            text = stdout.decode('utf-8')
        except UnicodeDecodeError:
            return None  # non-UTF8 bytes
    else:
        text = stdout

    if not text.strip():
        return None  # empty or whitespace

    try:
        report = json.loads(text)
    except json.JSONDecodeError:
        return None  # partial/invalid JSON or adb cat error text

    if not isinstance(report, dict):
        raise ObservationError('observation must be a JSON object')

    if report.get('runId') != run_id:
        raise ObservationError('runId missing or does not match run_id')

    terminal = report.get('terminal')
    if not isinstance(terminal, bool):
        raise ObservationError('terminal must be a bool')

    status = report.get('status')
    if terminal:
        consistent = status in ('completed', 'failed')
    else:
        consistent = status == 'running'
    if not consistent:
        raise ObservationError('terminal and status are inconsistent')

    return report
