"""Verify all13 original archives and retained source text; local-only."""
from pathlib import Path
import hashlib
import importlib.util
import json
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
OLD = ROOT/'.omx/artifacts/w3-runtime-materials-20261111/ort-source-archives'
RECOVERY = ROOT/'.omx/artifacts/w3-runtime-materials-20261111/ort-source-archives-recovery-20261112'
PRESERVED_PARTIAL_SHA256 = {
    'pytorch_cpuinfo': '088ecd3fad94789f753c8f2abccb4710c78a79bca03fe5aef28b43d8d9327386',
    'onnx': '3d34ecc291ac74f88bb0dd9003ed4b2c5bbddeb5fb84448da936542ee8372294',
}


def read(p):
    return json.loads(p.read_bytes())


def sha(p, algorithm='sha256'):
    with p.open('rb') as f:
        return hashlib.file_digest(f, algorithm).hexdigest()


def verify_preserved_partial(path, deps_key):
    """Reject same-size substitutions in the retained historical evidence."""
    assert deps_key in PRESERVED_PARTIAL_SHA256
    assert path.is_file()
    assert sha(path) == PRESERVED_PARTIAL_SHA256[deps_key]
    return True


def main():
    m=read(HERE/'complete-manifest.json')
    assert sha(ROOT/m['baseManifest'])==m['baseManifestSHA256']
    assert m['sourceCommit']=='33ca9628233dc8f002435e868d4c2e9f82766ca1'
    original_check=ROOT/'probes/sherpa-runtime-materials/2.0.1-ort-source-notices-20261111/check_materials.py'
    spec=importlib.util.spec_from_file_location('original_source_material_check',original_check)
    original=importlib.util.module_from_spec(spec);spec.loader.exec_module(original)
    prior=original.main()
    assert prior['verifiedSourceArchives']==11
    canonical={r['depsKey']:r for r in read(ROOT/'probes/sherpa-runtime-materials/2.0.1-ort-dependency-map-20261111/dependency-notice-map.json')['dependencies'] if r['role']=='source-dependency'}
    available={r['depsKey']:dict(r,fullPath=OLD/r['archivePath']) for r in read(OLD/'available-downloads.json')['receipts']}
    for r in read(RECOVERY/'results.json')['results']:
        assert r['accepted'] and r['sha1Matches']
        available[r['depsKey']]=dict(r,fullPath=RECOVERY/'downloads'/(r['depsKey']+'.zip'))
    assert set(available)==set(canonical) and len(m['dependencies'])==13
    total=0;count=0;roots=0;supplemental=0
    for dep in m['dependencies']:
        key=dep['depsKey'];expected=canonical[key];actual=available[key];path=actual['fullPath']
        for field in ['url','role','declaredArchiveSHA1','finalBinaryMembershipVerified']:
            assert dep[field]==expected[field]
        assert sha(path,'sha1')==dep['archiveSHA1']==expected['declaredArchiveSHA1']==actual['sha1']
        assert sha(path)==dep['archiveSHA256']==actual['sha256']
        assert path.stat().st_size==dep['archiveBytes']==actual['bytes']
        assert dep['archiveDigestVerifiedHere'] is True and dep['finalBinaryMembershipVerified'] is False
        total+=dep['archiveBytes']
        opened=zipfile.ZipFile(path) if path.name.endswith('.zip') else tarfile.open(path,'r:gz')
        with opened:
            for notice in dep['rootNotices']+dep.get('supplementalSourceEvidence',[]):
                repository_path=ROOT/notice['repositoryPath']
                assert repository_path.resolve().is_relative_to(ROOT.resolve()) and not repository_path.is_symlink()
                stream=opened.open(notice['archiveMember']) if isinstance(opened,zipfile.ZipFile) else opened.extractfile(notice['archiveMember'])
                with stream: data=stream.read(1048577)
                assert 0<len(data)<=1048576 and len(data)==notice['bytes'] and data==repository_path.read_bytes()
                assert sha(repository_path)==notice['sha256'];data.decode('utf8');count+=1
        roots+=len(dep['rootNotices']);supplemental+=len(dep.get('supplementalSourceEvidence',[]))
    assert total==m['verifiedArchiveBytes']==53628041
    assert roots==m['archiveBackedRootNoticeFiles']==24
    assert supplemental==m['supplementalMp11SourceFiles']==3 and count==27
    assert m['sourceArchiveDigestVerifiedCount']==13 and m['sourceArchiveDigestMissing']==[]
    assert m['originalTimeoutReceiptsAndPartialsPreserved'] and m['globalProxyChanged'] is False
    cpuinfo_partial = OLD/'downloads/pytorch_cpuinfo.zip'
    onnx_partial = OLD/'unattempted/downloads/onnx.zip'
    assert cpuinfo_partial.stat().st_size==3145728
    assert onnx_partial.stat().st_size==6291456
    verify_preserved_partial(cpuinfo_partial, 'pytorch_cpuinfo')
    verify_preserved_partial(onnx_partial, 'onnx')
    for flag in ['historicPatchExecutionVerified','exhaustiveRecursiveCompositionVerified','sourceToAARVerified','finalBinaryMembershipVerified','productionNoticeIntegrationVerified','productionReady','w3Complete']:
        assert m[flag] is False
    result={'acceptedWithinStatedScope':True,'verifiedSourceArchives':13,'verifiedArchiveBytes':total,
            'archiveBackedRootNoticeFiles':24,'mp11SupplementalSourceFiles':3,'archiveBackedTextFiles':27,
            'prior11ArchiveEvidencePreserved':True,'originalTimeoutsAndPartialsPreserved':True,
            'oldMaterialFilesUnchanged':prior['oldMaterialFilesUnchanged'],'frozenGuards':prior['frozenGuards'],
            'physicalHeapRunConsumed':False,'newDeviceOrInferenceCalls':0,'productionReady':False,'w3Complete':False}
    print(json.dumps(result))
    return result


if __name__=='__main__':
    main()
