import hashlib
import json
import unittest
import terminal_observer
RUN_ID = "run-ABC123"
FP = "a" * 64
PID = 4242
START_TICKS = "1000"
MAXN = 9223372036854775807
OVER = 9223372036854775808
def pack(report):
    first = json.dumps(report, separators=(",", ":")).encode("utf-8")
    return first, {"runId": RUN_ID, "sourceFingerprint": FP, "pid": PID, "startTicks": START_TICKS, "terminalReportSha256": hashlib.sha256(first).hexdigest()}
def build_valid(status="completed"):
    first, binding = pack({"runId": RUN_ID, "sourceFingerprint": FP, "pid": PID, "terminal": True, "status": status})
    obs = {"pidBefore": PID, "pidAfter": PID, "startTicksBefore": START_TICKS, "startTicksAfter": START_TICKS, "uptimeBeforeNs": 0, "firstReportReadNs": 1, "secondReportReadNs": 2, "uptimeAfterNs": 3, "rssBytes": 1, "deviceAvailableBytes": 536870912, "terminalObservationGap": None}
    ack = ("\n".join(["ODAI-TERMINAL-ACK-v1", RUN_ID, FP, str(PID), START_TICKS, binding["terminalReportSha256"]]).encode("ascii") + b"\n")
    return first, binding, obs, ack
def report_binding(**changes):
    r = {"runId": RUN_ID, "sourceFingerprint": FP, "pid": PID, "terminal": True, "status": "completed"}
    r.update(changes)
    return pack(r)
class TestTerminalObserver(unittest.TestCase):
    def run_ack(self, first, second, binding, obs):
        snap = (dict(binding), dict(obs), bytes(first) if isinstance(first, (bytes, bytearray)) else first, bytes(second) if isinstance(second, (bytes, bytearray)) else second)
        try:
            return terminal_observer.acknowledgement(first, second, binding, obs)
        finally:
            self.assertEqual(binding, snap[0]); self.assertEqual(obs, snap[1]); self.assertEqual(first, snap[2]); self.assertEqual(second, snap[3])
    def binding_for(self, raw):
        return {"runId": RUN_ID, "sourceFingerprint": FP, "pid": PID, "startTicks": START_TICKS, "terminalReportSha256": hashlib.sha256(raw).hexdigest()}
    def call_raw(self, raw):
        with self.assertRaises(ValueError):
            self.run_ack(raw, raw, self.binding_for(raw), build_valid()[2])
    def test_valid(self):
        for status in ("completed", "failed"):
            with self.subTest(status=status):
                first, binding, obs, ack = build_valid(status)
                self.assertEqual(self.run_ack(first, first, binding, obs), ack)
    def test_metric_boundaries(self):
        first, binding, obs, ack = build_valid()
        obs.update({"uptimeBeforeNs": 0, "firstReportReadNs": 0, "secondReportReadNs": 0, "uptimeAfterNs": MAXN, "rssBytes": 1610612736, "deviceAvailableBytes": MAXN})
        self.assertEqual(self.run_ack(first, first, binding, obs), ack)
    def test_two_read_mismatch(self):
        first, binding, obs, _ = build_valid()
        o = json.loads(first); o["status"] = "failed"
        second = json.dumps(o, separators=(",", ":")).encode("utf-8")
        with self.assertRaises(ValueError):
            self.run_ack(first, second, binding, obs)
    def test_same_length_changed(self):
        first, binding, obs, _ = build_valid()
        second = (b'{"runId":"run-ABC124","sourceFingerprint":"' + FP.encode() + b'","pid":4242,"terminal":true,"status":"completed"}')
        with self.assertRaises(ValueError):
            self.run_ack(first, second, binding, obs)
    def test_report_field_mismatch(self):
        for label, base, change in [("missing_terminal", {"runId": RUN_ID, "sourceFingerprint": FP, "pid": PID, "status": "completed"}, None), ("false_terminal", None, {"terminal": False}), ("wrong_run", None, {"runId": "run-OTHER"}), ("wrong_fp", None, {"sourceFingerprint": "b" * 64}), ("wrong_pid", None, {"pid": 9999})]:
            with self.subTest(label=label):
                first, binding = (pack(base) if base is not None else report_binding(**change))
                with self.assertRaises(ValueError):
                    self.run_ack(first, first, binding, build_valid()[2])
    def test_wrong_hash(self):
        first, binding, obs, _ = build_valid()
        binding["terminalReportSha256"] = "f" * 64
        with self.assertRaises(ValueError):
            self.run_ack(first, first, binding, obs)
    def test_observation(self):
        for key in ("uptimeBeforeNs", "firstReportReadNs", "secondReportReadNs", "uptimeAfterNs"):
            for label, val in (("missing", None), ("bool", True), ("float", 1.5), ("negative", -1), ("overflow", OVER)):
                with self.subTest(group="time", key=key, label=label):
                    first, binding, obs, _ = build_valid()
                    if val is None: del obs[key]
                    else: obs[key] = val
                    with self.assertRaises(ValueError):
                        self.run_ack(first, first, binding, obs)
        with self.subTest(group="order"):
            first, binding, obs, _ = build_valid()
            obs["firstReportReadNs"] = 5; obs["secondReportReadNs"] = 2
            with self.assertRaises(ValueError):
                self.run_ack(first, first, binding, obs)
        for field, val in (("pidBefore", 9999), ("pidAfter", 9999), ("startTicksBefore", "2000"), ("startTicksAfter", "2000")):
            with self.subTest(group="endpoint", field=field):
                first, binding, obs, _ = build_valid()
                obs[field] = val
                with self.assertRaises(ValueError):
                    self.run_ack(first, first, binding, obs)
    def test_resource_bounds(self):
        for label, key, val in (("rss_zero", "rssBytes", 0), ("rss_above", "rssBytes", 1610612737), ("rss_bool", "rssBytes", True), ("rss_missing", "rssBytes", None), ("avail_below", "deviceAvailableBytes", 536870911), ("avail_above", "deviceAvailableBytes", 9223372036854775808), ("avail_bool", "deviceAvailableBytes", True), ("avail_missing", "deviceAvailableBytes", None)):
            with self.subTest(label=label):
                first, binding, obs, _ = build_valid()
                if val is None: del obs[key]
                else: obs[key] = val
                with self.assertRaises(ValueError):
                    self.run_ack(first, first, binding, obs)
    def test_gap(self):
        for label, val in (("missing", None), ("nonnull", {})):
            with self.subTest(label=label):
                first, binding, obs, _ = build_valid()
                if val is None: del obs["terminalObservationGap"]
                else: obs["terminalObservationGap"] = val
                with self.assertRaises(ValueError):
                    self.run_ack(first, first, binding, obs)
    def test_invalid_bindings(self):
        _, binding, obs, _ = build_valid()
        base = dict(binding)
        first = build_valid()[0]
        for i, b in enumerate([{**base, "runId": "run@x"}, {**base, "runId": "a" * 81}, {**base, "sourceFingerprint": "a" * 63}, {**base, "pid": True}, {**base, "pid": 0}, {**base, "pid": 2147483648}, {**base, "startTicks": "01"}, {**base, "startTicks": "abc"}, {**base, "terminalReportSha256": "g" * 64}]):
            with self.subTest(i=i):
                with self.assertRaises(ValueError):
                    self.run_ack(first, first, b, obs)
    def test_input_bytes(self):
        first, binding, obs, _ = build_valid()
        for label, a, b in (("type1", "notbytes", first), ("type2", first, 123), ("empty", b"", b""), ("oversize", b"x" * 1048577, b"x" * 1048577)):
            with self.subTest(label=label):
                with self.assertRaises(ValueError):
                    self.run_ack(a, b, binding, obs)
    def test_raw_rejection(self):
        p = (b'{"runId":"run-ABC123","sourceFingerprint":"' + FP.encode() + b'","pid":4242,"terminal":true,"status":"completed"}')
        for label, raw in [("dup_identity", p[:-1] + b',"runId":"run-ABC123"}'), ("nested_dup", p[:-1] + b',"n":{"a":1,"a":2}}'), ("nan", p[:-1] + b',"x":NaN}'), ("infinity", p[:-1] + b',"x":Infinity}'), ("bad_utf8", b'{"runId":"\xff"}'), ("bad_json", b'{"runId":'), ("trailing", p + b' EXTRA'), ("arr_root", b'[1,2,3]'), ("num_root", b'42')]:
            with self.subTest(label=label):
                self.call_raw(raw)
    def test_excessive_nesting(self):
        deep = b'{"a":' * 1500 + b'1' + b'}' * 1500
        with self.assertRaises(ValueError):
            self.run_ack(deep, deep, self.binding_for(deep), build_valid()[2])
if __name__ == "__main__":
    unittest.main()