"""Prepare fragment acceptance; actual fake ADB, no Android or native model."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parent))
from terminal_prepare_fixture import PrepareFixture,ROOT,APP,TEST,RUN,FP,STAGE,MODELS
import terminal_file_io

TARGET=Path(os.environ.get('ODAI_PREPARE_SOURCE',str(ROOT/'probes/android-qwen-terminal/terminal_device_prepare.py')))


class TerminalPrepareParent(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TARGET.is_file(),'missing prepare ownership implementation')
        spec=importlib.util.spec_from_file_location('terminal_prepare_under_test',TARGET);self.module=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.module)
        self.f=PrepareFixture(self)

    def test_success_preclaims_every_mutation_and_verifies_real_bytes(self):
        result=self.f.call(self.module)
        self.assertEqual(result['runId'],RUN);self.assertEqual(result['sourceFingerprint'],FP)
        self.assertEqual(result['nativeCalls'],0);self.assertIs(result['ordinaryUnchanged'],True)
        before=json.loads((self.f.out/'device-before.json').read_text())
        self.assertEqual(before['serial'],'serial-01');self.assertEqual(before['ordinary'],self.f.before_ordinary)
        self.assertEqual(before['ordinaryModels'],self.f.before_models)
        claim=json.loads((self.f.out/'ownership-stage-claimed.json').read_text())
        self.assertEqual(claim['stage'],STAGE);self.assertEqual(claim['nonce'],'1'*32)
        children={p.name for p in (self.f.device/'stage'/RUN).iterdir()}
        self.assertEqual(children,set(claim['children']))
        self.assertEqual((self.f.device/'stage'/RUN/'.odai-owner').read_bytes(),b'1'*32+b'\n')
        for name,row in self.f.manifest['files'].items():
            self.assertEqual(self.f.sha(self.f.device/'apps'/APP/'no_backup/qwen3-asr-0.6b-int8-393f8a14'/name),row['sha256'])
        self.assertEqual(self.f.cleanup_calls,0);self.assertGreater(self.f.guards,20)
        self.assertEqual(self.f.context['ordinary'](),self.f.before_ordinary)

    def test_lock_refuses_before_claims_or_device_mutation(self):
        (self.f.root/'locked').touch()
        with self.assertRaises((AssertionError,ValueError)):self.f.call(self.module)
        self.assertEqual(self.f.mutations(),[]);self.assertEqual(self.f.cleanup_calls,0)
        self.assertFalse((self.f.out/'ownership-stage-claimed.json').exists())

    def test_existing_alias_or_dangling_stage_is_never_adopted(self):
        stage=self.f.device/'stage'/RUN;stage.symlink_to(self.f.root/'missing')
        with self.assertRaises((AssertionError,ValueError)):self.f.call(self.module)
        self.assertTrue(stage.is_symlink());self.assertEqual(self.f.mutations(),[])

    def test_stale_local_transfer_or_claim_refuses_without_overwrite(self):
        f=self.f.out/'transfer-part.tmp';f.write_bytes(b'protected old partial')
        with self.assertRaises((AssertionError,ValueError,OSError)):self.f.call(self.module)
        self.assertEqual(f.read_bytes(),b'protected old partial');self.assertEqual(self.f.mutations(),[])

    def test_stage_claim_persistence_failure_blocks_first_mutation(self):
        actual=terminal_file_io.write_new_durable;failure=OSError('claim fsync failed')
        def writer(root,relative,payload):
            if relative=='ownership-stage-claimed.json':raise failure
            return actual(root,relative,payload)
        # Candidate must call the real module primitive, so failures can be observed.
        with patch.object(terminal_file_io,'write_new_durable',side_effect=writer):
            with self.assertRaises(OSError) as caught:self.f.call(self.module)
        self.assertIs(caught.exception,failure);self.assertEqual(self.f.mutations(),[])

    def test_partial_push_failure_has_all_claimed_children_and_preserves_primary(self):
        self.f.fault('partial-push')
        with self.assertRaises(RuntimeError) as caught:self.f.call(self.module)
        self.assertIn('19',str(caught.exception));self.assertEqual(self.f.cleanup_calls,1)
        claim=json.loads((self.f.out/'ownership-stage-claimed.json').read_text())
        children={p.name for p in (self.f.device/'stage'/RUN).iterdir()}
        self.assertLessEqual(children,set(claim['children']))
        self.assertFalse(any(self.f.out.glob('transfer-*.json')))
        self.assertFalse((self.f.out/'prepared.json').exists())

    def test_install_success_then_transport_error_still_has_prior_alias_claim(self):
        self.f.fault('after-main-install')
        with self.assertRaises(RuntimeError):self.f.call(self.module)
        claim=json.loads((self.f.out/'ownership-install-main-claimed.json').read_text())
        self.assertEqual(claim['alias'],APP);self.assertEqual(claim['sha256'],self.f.package['files']['main.apk']['sha256'])
        self.assertTrue((self.f.device/'apps'/APP/'base.apk').exists())
        self.assertEqual(self.f.cleanup_calls,1);self.assertFalse((self.f.out/'prepared.json').exists())

    def test_cleanup_interrupt_never_masks_original_transport_failure(self):
        self.f.fault('after-owner')
        def cleanup():raise KeyboardInterrupt('secondary recovery interrupt')
        with self.assertRaises(RuntimeError) as caught:self.f.call(self.module,cleanup=cleanup)
        self.assertIn('19',str(caught.exception))
        notes=' '.join(getattr(caught.exception,'__notes__',[]))
        self.assertIn('secondary recovery interrupt',notes)

    def test_initial_or_model_drift_refuses_before_first_side_effect(self):
        (self.f.models/MODELS[0]).write_bytes(b'changed local model')
        with self.assertRaises((ValueError,AssertionError)):self.f.call(self.module)
        self.assertEqual(self.f.mutations(),[])

    def test_multiple_chunks_are_preclaimed_before_first_push(self):
        name='encoder.int8.onnx';path=self.f.models/name;path.write_bytes(b'x'*(8*1024*1024+17))
        row={'bytes':path.stat().st_size,'sha256':self.f.sha(path)}
        self.f.context['models'][name]=row;self.f.context['manifest']['files'][name]=row
        self.f.call(self.module)
        claim=json.loads((self.f.out/'ownership-stage-claimed.json').read_text())
        self.assertIn('encoder.int8.onnx.part-000',claim['children'])
        self.assertIn('encoder.int8.onnx.part-001',claim['children'])
        self.assertNotIn('encoder.int8.onnx.part-002',claim['children'])
        self.assertEqual(self.f.sha(self.f.device/'stage'/RUN/'encoder.int8.onnx'),row['sha256'])

    def test_nonce_and_binding_types_refuse_before_device_mutation(self):
        for nonce in ['../bad','1'*31,'A'*32,True,None]:
            with self.subTest(nonce=nonce):
                with self.assertRaises((TypeError,ValueError,AssertionError)):
                    self.f.call(self.module,nonce_factory=lambda:nonce)
        self.assertEqual(self.f.mutations(),[])


if __name__=='__main__':unittest.main(verbosity=2)
