"""Parent acceptance regressions for the exact-input terminal ACK contract."""
import hashlib
import unittest

import terminal_observer
from test_terminal_observer import build_valid


class IntSubclass(int):
    pass


class BytesSubclass(bytes):
    pass


class StrSubclass(str):
    pass


class TestHostBoundaries(unittest.TestCase):
    def reject_raw(self, raw):
        _, binding, observation, _ = build_valid()
        binding['terminalReportSha256'] = hashlib.sha256(raw).hexdigest()
        with self.assertRaises(ValueError):
            terminal_observer.acknowledgement(raw, raw, binding, observation)

    def test_exact_bytes(self):
        raw, binding, observation, _ = build_valid()
        for changed in (bytearray(raw), BytesSubclass(raw)):
            for first, second in ((changed, raw), (raw, changed)):
                with self.subTest(type=type(changed).__name__, first=type(first).__name__):
                    with self.assertRaises(ValueError):
                        terminal_observer.acknowledgement(first, second, binding, observation)

    def test_exact_integer_binding(self):
        raw, binding, observation, _ = build_valid()
        binding['pid'] = IntSubclass(binding['pid'])
        with self.assertRaises(ValueError):
            terminal_observer.acknowledgement(raw, raw, binding, observation)

    def test_exact_integer_observations(self):
        raw, binding, original, _ = build_valid()
        fields = ('pidBefore', 'pidAfter', 'uptimeBeforeNs', 'firstReportReadNs',
                  'secondReportReadNs', 'uptimeAfterNs', 'rssBytes', 'deviceAvailableBytes')
        for field in fields:
            observation = dict(original)
            observation[field] = IntSubclass(observation[field])
            with self.subTest(field=field):
                with self.assertRaises(ValueError):
                    terminal_observer.acknowledgement(raw, raw, binding, observation)

    def test_exact_string_observations(self):
        raw, binding, original, _ = build_valid()
        for field in ('startTicksBefore', 'startTicksAfter'):
            observation = dict(original)
            observation[field] = StrSubclass(observation[field])
            with self.subTest(field=field):
                with self.assertRaises(ValueError):
                    terminal_observer.acknowledgement(raw, raw, binding, observation)

    def test_decoder_recursion_is_value_error(self):
        # Below the candidate's explicit depth limit, but near Python's stack limit.
        for depth in (980, 990, 999, 1000):
            raw = b'{"a":' * depth + b'1' + b'}' * depth
            with self.subTest(depth=depth):
                self.reject_raw(raw)

    def test_nonfinite_exponent(self):
        raw = build_valid()[0]
        for number in (b'1e999', b'-1e999'):
            with self.subTest(number=number):
                self.reject_raw(raw[:-1] + b',"extra":' + number + b'}')

    def test_trailing_non_json_whitespace(self):
        raw = build_valid()[0]
        for suffix in (b'\x0b', b'\x0c', b'\xc2\xa0'):
            with self.subTest(suffix=suffix):
                self.reject_raw(raw + suffix)

    def test_valid_json_whitespace(self):
        raw, binding, observation, _ = build_valid()
        raw = b' \n\r\t' + raw + b' \n\r\t'
        binding['terminalReportSha256'] = hashlib.sha256(raw).hexdigest()
        expected = ('\n'.join(['ODAI-TERMINAL-ACK-v1', binding['runId'],
                    binding['sourceFingerprint'], str(binding['pid']),
                    binding['startTicks'], binding['terminalReportSha256']]) + '\n').encode('ascii')
        self.assertEqual(terminal_observer.acknowledgement(raw, raw, binding, observation), expected)

    def test_start_ticks_canonical_decimal(self):
        raw, original, observation, _ = build_valid()
        for ticks in ('0', '١٢٣', '１２３', '1١', '1２'):
            binding = dict(original, startTicks=ticks)
            obs = dict(observation, startTicksBefore=ticks, startTicksAfter=ticks)
            with self.subTest(ticks=ticks):
                with self.assertRaises(ValueError):
                    terminal_observer.acknowledgement(raw, raw, binding, obs)


if __name__ == '__main__':
    unittest.main()
