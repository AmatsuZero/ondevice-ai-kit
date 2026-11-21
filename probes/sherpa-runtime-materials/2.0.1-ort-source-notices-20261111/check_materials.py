"""Parent-owned independent local verification; no network/device calls."""
from pathlib import Path
import base64
import hashlib
import importlib.util
import json
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT/'.omx/artifacts/w3-runtime-materials-20261111/ort-source-archives'
MATERIAL = ROOT/'probes/sherpa-runtime-materials/2.0.1-ort-source-notices-20261111'


def read(path):
    return json.loads(path.read_bytes())


def sha(path, algorithm='sha256'):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def main():
    contract = read(OUT/'input-contract.json')
    assert sha(ROOT/contract['mappingPath']) == contract['mappingSHA256']
    assert sha(ROOT/contract['depsPath']) == contract['depsSHA256']
    mapping = read(ROOT/contract['mappingPath'])
    manifest = read(MATERIAL/'source-notice-manifest.json')
    assert manifest['priorMapSHA256'] == contract['mappingSHA256']
    assert manifest['depsTxtSHA256'] == contract['depsSHA256']
    assert manifest['sourceCommit'] == mapping['sourceCommit'] == '33ca9628233dc8f002435e868d4c2e9f82766ca1'
    canonical = {r['depsKey']: r for r in mapping['dependencies'] if r['role']=='source-dependency'}
    actual = {r['depsKey']: r for r in manifest['dependencies']}
    assert len(actual) == len(manifest['dependencies']) == len(canonical) == 13
    assert 'protoc_linux_x64' not in actual
    available = read(OUT/'available-downloads.json')
    records = {r['depsKey']:r for r in available['receipts']}
    failed = {}
    for folder in [OUT, OUT/'unattempted', OUT/'final-unattempted']:
        result = read(folder/'download-results.json')
        for row in result['receipts']:
            if not row['accepted']:
                assert row['errorClass'] == 'TimeoutError'
                failed[row['depsKey']] = row
    assert set(failed) == set(manifest['sourceArchiveDigestMissing']) == set(available['missingArchives'])
    retained_count = 0
    verified_count = 0
    verified_bytes = 0
    for key, dep in actual.items():
        expected = canonical[key]
        for field in ['observedName','url','depsKey','depsLine','declaredArchiveSHA1','role','logLine','finalBinaryMembershipVerified']:
            assert dep[field] == expected[field], (key,field)
        assert dep['finalBinaryMembershipVerified'] is False
        if dep['archiveDigestVerifiedHere']:
            record = records[key]; archive = OUT/record['archivePath']
            assert archive.resolve().is_relative_to(OUT.resolve())
            assert archive.stat().st_size == dep['archiveBytes'] == record['bytes']
            assert sha(archive,'sha1') == dep['archiveSHA1'] == expected['declaredArchiveSHA1'] == record['sha1']
            assert sha(archive) == dep['archiveSHA256'] == record['sha256']
            notices = dep['rootNotices']+dep.get('supplementalSourceEvidence',[])
            opened = zipfile.ZipFile(archive) if archive.name.endswith('.zip') else tarfile.open(archive,'r:gz')
            with opened:
                for notice in notices:
                    if isinstance(opened,zipfile.ZipFile):
                        with opened.open(notice['archiveMember']) as f: data=f.read(1048577)
                    else:
                        with opened.extractfile(notice['archiveMember']) as f: data=f.read(1048577)
                    assert 0 < len(data) <= 1048576
                    target=MATERIAL/notice['path']; assert target.resolve().is_relative_to(MATERIAL.resolve())
                    assert data == target.read_bytes() and len(data) == notice['bytes']
                    assert sha(target) == notice['sha256'];data.decode('utf8');retained_count+=1
            verified_count+=1;verified_bytes+=record['bytes']
        else:
            assert key in failed and key not in records
            folder = 'cpuinfo-license-gitblob' if key == 'pytorch_cpuinfo' else 'onnx-license-gitblob'
            assert key in ['pytorch_cpuinfo','onnx']
            receipt=read(OUT/folder/'receipt.json')
            response=read(OUT/folder/('response.json' if key=='pytorch_cpuinfo' else 'license.response.json'))
            notice=dep['fixedCommitNotices'][0];target=MATERIAL/notice['path'];data=target.read_bytes()
            assert data == base64.b64decode(response['content']) == (OUT/folder/'LICENSE').read_bytes()
            assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest() == notice['gitBlobSHA1'] == receipt['gitBlobSHA1'] == response['sha']
            assert len(data)==notice['bytes']==receipt['bytes'] and sha(target)==notice['sha256']==receipt['sha256']
            assert notice['sourceCommit']==receipt['commit']
            if key=='pytorch_cpuinfo':assert receipt['commit']=='4628dc060ce4e82345dc166bbac875609db4ff69'
            else:assert read(OUT/folder/'tag-commit.response.json')['sha']==receipt['commit']
            data.decode('utf8');retained_count+=1
    assert verified_count == manifest['sourceArchiveDigestVerifiedCount']
    assert verified_bytes == manifest['sourceArchiveBytes'] == available['totalBytes']
    assert retained_count == manifest['rootNoticeCount']+manifest['fixedCommitNoticeCount']+manifest['supplementalSourceEvidenceCount']
    assert actual['mp11']['rootNotices'] == [] and len(actual['mp11']['supplementalSourceEvidence'])==3
    for field in ['historicPatchExecutionVerified','exhaustiveRecursiveCompositionVerified','sourceToAARVerified','productionNoticeIntegrationVerified','productionReady','w3Complete']:
        assert manifest[field] is False
    for path,wanted in read(OUT/'prior-material-hashes.json').items():assert sha(ROOT/path)==wanted,path
    guards={}
    for name,rel in [('arena108','probes/android-qwen-arena/device_acceptance.py'),('heap120','probes/android-qwen-heap/device_acceptance.py'),('emulator327','artifacts/verification/w3-qwen-arena-emulator-20261111/device_acceptance_emulator.py'),('kv288448','probes/android-qwen-kv288/device_acceptance.py')]:
        spec=importlib.util.spec_from_file_location('notice_guard_'+name,ROOT/rel);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
        guards[name]=len(m.verify_frozen_contract()['files'])
        if name=='heap120':assert not any((m.OUT/n).exists() for n in ['prepared.json','run-started.json','collection.json'])
    assert guards=={'arena108':108,'heap120':120,'emulator327':327,'kv288448':448}
    summary={'acceptedWithinStatedScope':True,'mappedSourceDependencies':13,'verifiedSourceArchives':verified_count,
             'archiveDigestMissing':sorted(failed),'retainedTextFiles':retained_count,'verifiedArchiveBytes':verified_bytes,
             'oldMaterialFilesUnchanged':len(read(OUT/'prior-material-hashes.json')),'frozenGuards':guards,
             'physicalHeapRunConsumed':False,'newDeviceOrInferenceCalls':0,'sourceToAARVerified':False,'productionReady':False,'w3Complete':False}
    print(json.dumps(summary))
    return summary


if __name__=='__main__':
    main()
