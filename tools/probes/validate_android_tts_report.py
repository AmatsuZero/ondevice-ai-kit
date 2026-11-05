"""Validate a private TTS terminal report; transport success alone is insufficient."""
import argparse
import json
from pathlib import Path
import re


PHASES = ['started', 'normal-start', 'normal-done', 'cancel-request completed=false',
          'cancel-joined', 'followup-done', 'close-start', 'close-done', 'close-rejected', 'finished']


def validate(report, run_id, sdk_commit):
    def require(condition, message):
        if not condition:
            raise ValueError(message)

    require(isinstance(report, dict), 'report must be an object')
    require(re.fullmatch(r'[A-Za-z0-9-]{1,80}', run_id) is not None, 'invalid run ID')
    require(re.fullmatch(r'[0-9a-f]{40}', sdk_commit) is not None, 'invalid commit')
    for key, expected in [('schemaVersion', 1), ('runId', run_id), ('sdkCommit', sdk_commit),
                          ('mode', 'tts'), ('terminal', True), ('phase', 'finished')]:
        require(type(report.get(key)) is type(expected) and report[key] == expected, key)
    require(type(report.get('pid')) is int and report['pid'] > 0, 'pid')
    require(report.get('phases') == PHASES, 'missing/out-of-order lifecycle phases')
    result = report.get('result')
    require(isinstance(result, dict), 'result')
    for key, expected in [('probeStatus', 'pass'), ('sdkStatus', 'success'),
                          ('sdkBackend', 'android.text-to-speech'),
                          ('sdkCancellationStatus', 'cancelled-followup-success'),
                          ('sdkAfterCloseStatus', 'closed')]:
        require(result.get(key) == expected, key)
    require('probeError' not in result, 'probe error')
    for key in ['engine', 'sdkVoice']:
        require(isinstance(result.get(key), str) and bool(result[key]), key)
    for key, minimum in [('outputBytes', 45), ('voiceCount', 1), ('languageStatus', 0)]:
        require(type(result.get(key)) is int and result[key] >= minimum, key)
    return 'pass'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--sdk-commit', required=True)
    args = parser.parse_args()
    try:
        print(validate(json.loads(args.report.read_text()), args.run_id, args.sdk_commit))
    except (ValueError, OSError) as error:
        parser.exit(1, f'TTS report rejected: {error}\n')
