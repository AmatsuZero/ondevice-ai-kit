import unittest
from test_android_realtime_sr_report import assess, make_index, make_run, dropped_stage, base_events


class TestAndroidRealtimeSrStrictThreshold(unittest.TestCase):
    def test_strict_threshold(self):
        for missing, n, status in [({40}, 4, 'pass'), ({40, 41}, 5, 'fail')]:
            events = dropped_stage(base_events(), 'display', missing)
            r = assess(make_index(), make_run(), events)
            self.assertEqual(r['counts']['late'] + r['counts']['unconfirmed'], n)
            self.assertEqual(r['gates']['missed'], status)
