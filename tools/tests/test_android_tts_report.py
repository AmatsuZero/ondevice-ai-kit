import copy
import importlib.util
from pathlib import Path
import unittest


PATH = Path(__file__).resolve().parents[1] / 'probes/validate_android_tts_report.py'
SPEC = importlib.util.spec_from_file_location('tts_report', PATH)
RUN_ID = 'tts-20261105-one'
COMMIT = 'e0438c030a987ceeff9f9207c2475684749e4d8b'


def fixture():
    return dict(schemaVersion=1, runId=RUN_ID, sdkCommit=COMMIT, pid=123,
                mode='tts', terminal=True, phase='finished',
                phases=['started', 'normal-start', 'normal-done',
                        'cancel-request completed=false', 'cancel-joined', 'followup-done',
                        'close-start', 'close-done', 'close-rejected', 'finished'],
                result=dict(probeStatus='pass', sdkStatus='success', outputBytes=500,
                            engine='com.google.android.tts', voiceCount=5, languageStatus=0,
                            sdkBackend='android.text-to-speech', sdkVoice='en-us',
                            sdkCancellationStatus='cancelled-followup-success', sdkAfterCloseStatus='closed'))


class TtsReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(cls.module)

    def validate(self, report):
        return self.module.validate(report, RUN_ID, COMMIT)

    def test_final_report_retains_real_assertion_fields(self):
        self.assertEqual(self.validate(fixture()), 'pass')

    def test_fail_partial_stale_and_false_pass_are_rejected(self):
        for key, value in [('terminal', False), ('phase', 'close-start'), ('runId', 'stale'),
                           ('sdkCommit', '0' * 40), ('mode', 'recorder-pass'), ('pid', 0),
                           ('schemaVersion', 2), ('phases', ['finished'])]:
            with self.subTest(key=key):
                report = fixture(); report[key] = value
                with self.assertRaises(ValueError): self.validate(report)
        for key, value in [('probeStatus', 'fail'), ('sdkStatus', 'fail'), ('outputBytes', 44),
                           ('sdkAfterCloseStatus', 'unexpected-success'),
                           ('sdkCancellationStatus', 'completed-before-cancel'), ('engine', ''),
                           ('voiceCount', 0), ('languageStatus', -1)]:
            with self.subTest(key=key):
                report = copy.deepcopy(fixture()); report['result'][key] = value
                with self.assertRaises(ValueError): self.validate(report)

    def test_missing_or_malformed_fields_are_rejected(self):
        for report in [None, {}, {'result': None}, dict(fixture(), result={}),
                       dict(fixture(), phases='finished')]:
            with self.assertRaises(ValueError): self.validate(report)

    def test_wrong_backend_missing_voice_or_error_cannot_pass(self):
        for key, value in [('sdkBackend', 'other-backend'), ('sdkVoice', None),
                           ('sdkVoice', ''), ('probeError', 'assertion failed')]:
            with self.subTest(key=key, value=value):
                report = fixture(); report['result'][key] = value
                with self.assertRaises(ValueError): self.validate(report)


if __name__ == '__main__':
    unittest.main()
