"""Boundary acceptance for the isolated ACK transport; no device operations."""
import unittest

from adb_terminal_ack_publisher import AdbTerminalAckPublisher, RemoteAckPublicationError


CONFIG = dict(adb='adb', serial='serial-01',
              package='io.ondeviceai.acceptance.qwenterminal', run_id='fresh-01')


class StringSubclass(str):
    pass


class BytesSubclass(bytes):
    pass


class AckPublisherBoundaries(unittest.TestCase):
    def test_identifiers_reject_trailing_newlines_and_string_subclasses(self):
        calls = []
        for key in ['serial', 'package', 'run_id']:
            for value in [CONFIG[key] + '\n', StringSubclass(CONFIG[key]), 'é']:
                with self.subTest(key=key, value=repr(value)):
                    with self.assertRaises(ValueError):
                        AdbTerminalAckPublisher(**dict(CONFIG, **{key: value}),
                            runner=lambda *args: calls.append(args))
        with self.assertRaises(ValueError):
            AdbTerminalAckPublisher(**dict(CONFIG, adb=StringSubclass('adb')))
        self.assertEqual(calls, [])

    def test_invalid_timeout_is_value_error_before_transport(self):
        for timeout in [10**1000, -(10**1000), float('nan'), float('inf'),
                        float('-inf'), True, False, 0, -1, 2.001, '1', None]:
            with self.subTest(timeout=repr(timeout)):
                with self.assertRaises(ValueError):
                    AdbTerminalAckPublisher(**CONFIG, timeout_seconds=timeout)

    def test_every_transport_requires_exact_bytes_response(self):
        payload = b'\x00\xff\n'
        for bad_index in range(5):
            for bad_type in [BytesSubclass, bytearray]:
                calls = []
                callback = []
                def runner(argv, timeout, stdin):
                    index = len(calls)
                    calls.append(argv)
                    response = payload if index == 1 else b''
                    return bad_type(response) if index == bad_index else response
                publisher = AdbTerminalAckPublisher(**CONFIG, runner=runner)
                with self.subTest(index=bad_index, response=bad_type.__name__):
                    with self.assertRaises(RemoteAckPublicationError) as raised:
                        publisher.publish(payload, before_link=lambda: callback.append(True))
                    self.assertIsInstance(raised.exception.__cause__, ValueError)
                    self.assertEqual(len(calls), bad_index + 1)
                    self.assertEqual(callback, [True] if bad_index >= 3 else [])
                    self.assertIs(raised.exception.publication_possible, bad_index >= 3)
                    with self.assertRaises(RuntimeError):
                        publisher.publish(payload, before_link=lambda: callback.append(True))
                    self.assertEqual(len(calls), bad_index + 1)

    def test_invalid_arguments_do_not_consume_and_arbitrary_bytes_are_allowed(self):
        payload = b'\x00\xff\n'
        calls = []
        callback = []
        def runner(argv, timeout, stdin):
            index = len(calls)
            calls.append((argv, stdin))
            return payload if index == 1 else b''
        publisher = AdbTerminalAckPublisher(**CONFIG, runner=runner)
        for invalid in [b'', b'x' * 1025, BytesSubclass(b'x'), bytearray(b'x'), 'x', None]:
            with self.subTest(payload=repr(invalid)):
                with self.assertRaises(ValueError):
                    publisher.publish(invalid, before_link=lambda: callback.append(True))
        with self.assertRaises(ValueError):
            publisher.publish(payload, before_link=None)
        self.assertEqual(calls, [])
        self.assertEqual(callback, [])
        receipt = publisher.publish(payload, before_link=lambda: callback.append(True))
        self.assertIs(receipt['published'], True)
        self.assertEqual(calls[0][1], payload)
        self.assertEqual(callback, [True])
        self.assertEqual(len(calls), 5)


if __name__ == '__main__':
    unittest.main()
