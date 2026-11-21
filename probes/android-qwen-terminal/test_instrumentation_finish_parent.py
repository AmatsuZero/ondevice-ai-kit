"""Primary acceptance for actual Kotlin finish Bundle's adb textual envelope."""
import importlib.util
import os
from pathlib import Path
import unittest
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'probes/android-terminal-observation'))
TARGET=Path(os.environ.get('ODAI_FINISH_SOURCE',str(ROOT/'probes/android-qwen-terminal/terminal_device_lifecycle.py')))
SHA='a'*64


def envelope(code=-1):
    return ('INSTRUMENTATION_RESULT: terminalAckAttempted=true\n'
            'INSTRUMENTATION_RESULT: terminalAckReceived=true\n'
            'INSTRUMENTATION_RESULT: terminalReportSha256='+SHA+'\n'
            'INSTRUMENTATION_CODE: '+str(code)+'\n').encode()


class InstrumentationFinishParent(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TARGET.is_file(),'missing instrumentation finish parser')
        spec=importlib.util.spec_from_file_location('finish_parser_under_test',TARGET)
        self.module=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.module)
        self.assertTrue(hasattr(self.module,'parse_instrumentation_finish'),'missing instrumentation finish parser')

    def parse(self,raw=None,**overrides):
        values=dict(expected_report_sha256=SHA,terminal_status='completed');values.update(overrides)
        return self.module.parse_instrumentation_finish(envelope() if raw is None else raw,**values)

    def test_completed_and_failed_finish_require_different_real_result_codes(self):
        for status,code in [('completed',-1),('failed',0)]:
            with self.subTest(status=status):
                result=self.parse(envelope(code),terminal_status=status)
                self.assertIs(result['terminalAckAttempted'],True)
                self.assertIs(result['terminalAckReceived'],True)
                self.assertEqual(result['terminalReportSha256'],SHA)
                self.assertEqual(result['instrumentationCode'],code)
                self.assertEqual(result['terminalStatus'],status)
                self.assertIs(result['qualityAccepted'],False)

    def test_missing_false_duplicate_or_changed_ack_fields_fail(self):
        raw=envelope()
        bad=[raw.replace(b'terminalAckReceived=true',b'terminalAckReceived=false'),
             raw.replace(b'terminalAckReceived=true',b'terminalAckReceived=true '),
             raw.replace(b'INSTRUMENTATION_RESULT: terminalAckAttempted=true\n',b''),
             raw+b'INSTRUMENTATION_RESULT: terminalAckReceived=true\n',
             raw.replace(SHA.encode(),b'b'*64),
             raw.replace(b'terminalAckAttempted=true',b'terminalAckAttempted=1'),
             raw.replace(b'terminalAckReceived=true',b'terminalAckReceived=True')]
        for value in bad:
            with self.subTest(raw=value):
                with self.assertRaises(ValueError): self.parse(value)

    def test_wrong_duplicate_or_absent_finish_code_fails(self):
        for raw in [envelope(0),envelope(1),envelope()+b'INSTRUMENTATION_CODE: -1\n',
                    envelope().replace(b'INSTRUMENTATION_CODE: -1\n',b''),
                    envelope().replace(b'CODE: -1',b'CODE: -01')]:
            with self.subTest(raw=raw):
                with self.assertRaises(ValueError): self.parse(raw)

    def test_error_bundle_and_failure_envelopes_cannot_be_masked_by_valid_fields(self):
        for extra in [b'INSTRUMENTATION_RESULT: terminalAckErrorClass=IOException\n',
                      b'INSTRUMENTATION_RESULT: terminalAckErrorMessage=bad\n',
                      b'INSTRUMENTATION_FAILED: process crashed\n',
                      b'INSTRUMENTATION_ABORTED: stopped\n',b'Error: runner not found\n']:
            with self.subTest(extra=extra):
                with self.assertRaises(ValueError): self.parse(envelope()+extra)

    def test_config_type_size_encoding_and_nul_rejection(self):
        for raw in ['',b'',bytearray(envelope()),b'\0'+envelope(),b'\xff'+envelope(),b'x'*1048577]:
            with self.subTest(kind=type(raw),size=len(raw)):
                with self.assertRaises((TypeError,ValueError)): self.parse(raw)
        for override in [{'expected_report_sha256':'A'*64},{'expected_report_sha256':None},
                         {'terminal_status':True},{'terminal_status':'running'}]:
            with self.subTest(override=override):
                with self.assertRaises((TypeError,ValueError)): self.parse(**override)

    def test_crlf_status_and_nonprotocol_diagnostics_do_not_spoof_finish(self):
        result=self.parse(b'INSTRUMENTATION_STATUS: class=example\r\n'
            +b'INSTRUMENTATION_STATUS_CODE: 1\r\n'
            +b'local diagnostic: terminalAckReceived=false\r\n'
            +envelope().replace(b'\n',b'\r\n'))
        self.assertEqual(result['instrumentationCode'],-1)

    def test_subclasses_do_not_bypass_exact_input_types(self):
        class BytesSubclass(bytes): pass
        class StrSubclass(str): pass
        for raw,overrides in [(BytesSubclass(envelope()),{}),
                              (envelope(),{'expected_report_sha256':StrSubclass(SHA)}),
                              (envelope(),{'terminal_status':StrSubclass('completed')})]:
            with self.subTest(overrides=overrides,kind=type(raw)):
                with self.assertRaises((TypeError,ValueError)): self.parse(raw,**overrides)


if __name__=='__main__': unittest.main(verbosity=2)
