import shlex
import unittest
from adb_terminal_ack_publisher import (
    AdbTerminalAckPublisher, RemoteAckPublicationError)
ADB='adb'; SERIAL='serial-01'
PACKAGE='io.ondeviceai.acceptance.qwenterminal'; RUN_ID='fresh-01'
DIR='files/w3-qwen-terminal'
FINAL=DIR+'/ack-fresh-01.txt'; STAGE=FINAL+'.stage'
PAYLOAD=b'payload-bytes-123456'
class _BadBytes(bytes):
    pass
class LogRunner:
    def __init__(self, fi=None, fe=None, bp=None, br=None):
        self.calls=[]; self.fi=fi; self.fe=fe; self.bp=bp; self.br=br
    def __call__(self, argv, to, sb):
        i=len(self.calls); self.calls.append((list(argv),to,sb))
        if self.fi is not None and i==self.fi: raise self.fe
        if self.bp is not None and i==self.bp: return self.br
        return PAYLOAD if i==1 else b''
    def argv(self,i): return self.calls[i][0]
def ht(a): return shlex.split(a[4])
class T(unittest.TestCase):
    def test_ctor_invalid_table(self):
        b=dict(timeout_seconds=1.0, runner=lambda *a,**k: b'')
        cases=[
            dict(adb='', serial=SERIAL, package=PACKAGE, run_id=RUN_ID, **b),
            dict(adb='a\x00b', serial=SERIAL, package=PACKAGE, run_id=RUN_ID, **b),
            dict(adb=ADB, serial='bad!', package=PACKAGE, run_id=RUN_ID, **b),
            dict(adb=ADB, serial='x'*129, package=PACKAGE, run_id=RUN_ID, **b),
            dict(adb=ADB, serial=SERIAL, package='BadPackage', run_id=RUN_ID, **b),
            dict(adb=ADB, serial=SERIAL, package=PACKAGE, run_id='bad id!', **b),
            dict(adb=ADB, serial=SERIAL, package=PACKAGE, run_id=RUN_ID, timeout_seconds=0),
            dict(adb=ADB, serial=SERIAL, package=PACKAGE, run_id=RUN_ID, timeout_seconds=3.0),
            dict(adb=ADB, serial=SERIAL, package=PACKAGE, run_id=RUN_ID, timeout_seconds=True),
        ]
        for c in cases:
            with self.subTest(c['adb']):
                with self.assertRaises(ValueError):
                    AdbTerminalAckPublisher(**c)
    def test_invalid_payload_and_callback(self):
        for args in [(b'',), (b'x'*1025,)]:
            r=LogRunner(); p=AdbTerminalAckPublisher(ADB,SERIAL,PACKAGE,RUN_ID,timeout_seconds=1.0,runner=r)
            with self.assertRaises(ValueError):
                p.publish(*args, before_link=lambda: None)
            self.assertEqual(r.calls, [])
        r=LogRunner(); p=AdbTerminalAckPublisher(ADB,SERIAL,PACKAGE,RUN_ID,timeout_seconds=1.0,runner=r)
        with self.assertRaises(ValueError):
            p.publish(PAYLOAD, before_link=None)
        self.assertEqual(r.calls, [])
        self.assertTrue(p.publish(PAYLOAD, before_link=lambda: None)['published'])
        self.assertEqual(len(r.calls),5)
    def test_success_order_argv_scripts_inputs_flags(self):
        r=LogRunner(); p=AdbTerminalAckPublisher(ADB,SERIAL,PACKAGE,RUN_ID,timeout_seconds=1.5,runner=r)
        res=p.publish(PAYLOAD, before_link=lambda: None)
        self.assertEqual(len(r.calls),5)
        self.assertEqual(r.argv(0)[0:4],[ADB,'-s',SERIAL,'exec-in'])
        for i in (1,2,3,4): self.assertEqual(r.argv(i)[0:4],[ADB,'-s',SERIAL,'exec-out'])
        sw=ht(r.argv(0)); self.assertEqual(sw[0:4],['run-as',PACKAGE,'sh','-c'])
        self.assertIn('umask 077',sw[4]); self.assertIn('set -C',sw[4]); self.assertIn('cat > '+shlex.quote(STAGE),sw[4])
        swt=shlex.split(sw[4]); self.assertNotIn('mkdir',swt); self.assertNotIn('rm',swt); self.assertNotIn('unlink',swt)
        sv=ht(r.argv(1)); self.assertEqual(sv[0:4],['run-as',PACKAGE,'sh','-c'])
        self.assertIn('test -f '+shlex.quote(STAGE),sv[4]); self.assertIn('test ! -L '+shlex.quote(STAGE),sv[4]); self.assertIn('head -c 1025 '+shlex.quote(STAGE),sv[4])
        self.assertEqual(ht(r.argv(2)),['run-as',PACKAGE,'toybox','fsync',STAGE])
        self.assertEqual(ht(r.argv(3)),['run-as',PACKAGE,'toybox','ln','-T',STAGE,FINAL])
        self.assertEqual(ht(r.argv(4)),['run-as',PACKAGE,'toybox','fsync',DIR])
        self.assertEqual(r.calls[0][2],PAYLOAD)
        for i in (1,2,3,4): self.assertIsNone(r.calls[i][2])
        for c in r.calls: self.assertEqual(c[1],1.5)
        self.assertEqual(res,{'published':True,'durability_verified':True,'stage_retained':True,'final_relative':FINAL,'stage_relative':STAGE})
    def test_stage_readback_mismatch_and_type(self):
        for resp in (b'mismatch',_BadBytes(b'')):
            r=LogRunner(bp=1,br=resp); p=AdbTerminalAckPublisher(ADB,SERIAL,PACKAGE,RUN_ID,timeout_seconds=1.0,runner=r)
            with self.assertRaises(RemoteAckPublicationError) as ctx:
                p.publish(PAYLOAD, before_link=lambda: None)
            e=ctx.exception
            self.assertEqual(e.phase,'stage-verify'); self.assertTrue(e.stage_may_remain)
            self.assertFalse(e.durability_verified); self.assertFalse(e.publication_possible)
            self.assertIsInstance(e.__cause__,ValueError); self.assertEqual(len(r.calls),2)
    def test_runner_failure_each_phase(self):
        names=['stage-write','stage-verify','stage-sync','publish-link','directory-sync']
        for idx in range(5):
            exc=RuntimeError('boom-%d'%idx); r=LogRunner(fi=idx,fe=exc)
            p=AdbTerminalAckPublisher(ADB,SERIAL,PACKAGE,RUN_ID,timeout_seconds=1.0,runner=r)
            with self.assertRaises(RemoteAckPublicationError) as ctx:
                p.publish(PAYLOAD, before_link=lambda: None)
            e=ctx.exception
            self.assertEqual(e.phase,names[idx]); self.assertIs(e.__cause__,exc)
            self.assertTrue(e.stage_may_remain); self.assertFalse(e.durability_verified)
            self.assertEqual(e.publication_possible,names[idx] in ('publish-link','directory-sync'))
            self.assertEqual(len(r.calls),idx+1)
    def test_nonempty_or_nonbytes_response_stops(self):
        names={0:'stage-write',2:'stage-sync',3:'publish-link',4:'directory-sync'}
        table=[(0,b'x'),(0,_BadBytes(b'')),(2,b'x'),(2,_BadBytes(b'')),(3,b'x'),(3,_BadBytes(b'')),(4,b'x'),(4,_BadBytes(b''))]
        for idx,resp in table:
            r=LogRunner(bp=idx,br=resp); p=AdbTerminalAckPublisher(ADB,SERIAL,PACKAGE,RUN_ID,timeout_seconds=1.0,runner=r)
            with self.assertRaises(RemoteAckPublicationError) as ctx:
                p.publish(PAYLOAD, before_link=lambda: None)
            e=ctx.exception
            self.assertEqual(e.phase,names[idx]); self.assertIsInstance(e.__cause__,ValueError)
            self.assertEqual(len(r.calls),idx+1)
    def test_before_link_callback_abort(self):
        err=RuntimeError('before-fail')
        def cb(): raise err
        r=LogRunner(); p=AdbTerminalAckPublisher(ADB,SERIAL,PACKAGE,RUN_ID,timeout_seconds=1.0,runner=r)
        with self.assertRaises(RemoteAckPublicationError) as ctx:
            p.publish(PAYLOAD, before_link=cb)
        e=ctx.exception
        self.assertEqual(e.phase,'before-link'); self.assertIs(e.__cause__,err)
        self.assertTrue(e.stage_may_remain); self.assertFalse(e.durability_verified)
        self.assertFalse(e.publication_possible); self.assertEqual(len(r.calls),3)
    def test_repeated_instance_attempt_rejected(self):
        r=LogRunner(); p=AdbTerminalAckPublisher(ADB,SERIAL,PACKAGE,RUN_ID,timeout_seconds=1.0,runner=r)
        p.publish(PAYLOAD, before_link=lambda: None)
        with self.assertRaises(RuntimeError): p.publish(PAYLOAD, before_link=lambda: None)
        self.assertEqual(len(r.calls),5)
        exc=RuntimeError('fail'); r2=LogRunner(fi=2,fe=exc)
        p2=AdbTerminalAckPublisher(ADB,SERIAL,PACKAGE,RUN_ID,timeout_seconds=1.0,runner=r2)
        with self.assertRaises(RemoteAckPublicationError): p2.publish(PAYLOAD, before_link=lambda: None)
        with self.assertRaises(RuntimeError): p2.publish(PAYLOAD, before_link=lambda: None)
        self.assertEqual(len(r2.calls),3)
if __name__=='__main__': unittest.main()