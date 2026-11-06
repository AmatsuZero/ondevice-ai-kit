"""Read-only F10 dataset preflight; a declaration is never calibration approval."""
import argparse
from collections import Counter
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import unicodedata
from urllib.parse import urlsplit


PURPOSES = {'local-calibration', 'local-evaluation'}
SPLITS = {'calibration', 'test'}


def _require(condition, message):
    if not condition:
        raise ValueError(message)


def _fields(value, names):
    _require(type(value) is dict and set(value) == set(names.split()), 'invalid object fields')


def _token(value):
    _require(type(value) is str and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._:-]{0,127}', value),
             'invalid token')


def _sha256(value):
    _require(type(value) is str and re.fullmatch('[a-f0-9]{64}', value), 'invalid SHA256')


@contextmanager
def _regular_file(root, relative):
    """Open beneath root via directory descriptors, refusing symlinks and FIFOs."""
    _require(type(relative) is str and relative and not relative.startswith('/') and
             '\\' not in relative and all(p not in ('', '.', '..') for p in relative.split('/')),
             'invalid relative file path')
    descriptors = []
    try:
        directory = os.open(root, os.O_RDONLY | os.O_DIRECTORY)
        descriptors.append(directory)
        parts = relative.split('/')
        for part in parts[:-1]:
            directory = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=directory)
            descriptors.append(directory)
        descriptor = os.open(parts[-1], os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
        descriptors.append(descriptor)
        before = os.fstat(descriptor)
        _require(stat.S_ISREG(before.st_mode), 'expected a regular file')
        # fdopen owns a duplicate, so every descriptor has one unambiguous closer.
        with os.fdopen(os.dup(descriptor), 'rb') as stream:
            yield stream
        after = os.fstat(descriptor)
        _require((before.st_size, before.st_mtime_ns, before.st_ctime_ns) ==
                 (after.st_size, after.st_mtime_ns, after.st_ctime_ns), 'file changed during read')
    except OSError:
        raise ValueError('unreadable or invalid file reference') from None
    finally:
        for descriptor in reversed(descriptors):
            os.close(descriptor)


def _file_digest(reference, root):
    _fields(reference, 'path sha256')
    _sha256(reference['sha256'])
    digest = hashlib.sha256()
    with _regular_file(root, reference['path']) as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    actual = digest.hexdigest()
    _require(actual == reference['sha256'], 'file digest mismatch')
    return actual


def validate_manifest(document: dict, root: Path) -> dict:
    """Check declarations, hashes and leakage. Read local files; change nothing."""
    _fields(document, 'schemaVersion kind dataClass task dataset purpose redistributeDataset '
                      'licenseEvidence authorization binding samples')
    _require(type(document['schemaVersion']) is int and document['schemaVersion'] == 1, 'invalid schemaVersion')
    _require(document['kind'] == 'identity-calibration-manifest', 'invalid manifest kind')
    _require(document['dataClass'] in ('synthetic', 'real'), 'invalid dataClass')
    task = document['task']
    _require(task in ('verification', 'identification', 'passive-pad'), 'invalid task')
    dataset = document['dataset']
    _fields(dataset, 'id revision sourceURL')
    _token(dataset['id'])
    _token(dataset['revision'])
    _require(type(dataset['sourceURL']) is str, 'invalid source URL')
    try:
        url = urlsplit(dataset['sourceURL'])
        _require(not any(c.isspace() or unicodedata.category(c) == 'Cc' for c in dataset['sourceURL']) and
                 url.scheme == 'https' and url.hostname and url.username is None and url.password is None,
                 'invalid source URL')
        url.port  # Validate the optional port; urlsplit itself permits malformed ports.
    except ValueError:
        raise ValueError('invalid source URL') from None
    purpose = document['purpose']
    _require(type(purpose) is str and purpose in PURPOSES, 'invalid purpose')
    authorization = document['authorization']
    _fields(authorization, 'allowedPurposes redistributionAllowed evidence')
    allowed = authorization['allowedPurposes']
    _require(type(allowed) is list and allowed and all(type(p) is str and p in PURPOSES for p in allowed),
             'invalid allowed purposes')
    _require(len(allowed) == len(set(allowed)) and purpose in allowed, 'undeclared or duplicate purpose')
    _require(type(document['redistributeDataset']) is bool and type(authorization['redistributionAllowed']) is bool,
             'invalid redistribution declaration')
    _require(not document['redistributeDataset'] or authorization['redistributionAllowed'], 'redistribution denied')
    binding = document['binding']
    _fields(binding, 'backendId modelId modelRevision modelSHA256 preprocessRevision scoreMetric scoreDirection')
    for key in ('backendId', 'modelId', 'modelRevision', 'preprocessRevision', 'scoreMetric'):
        _token(binding[key])
    _sha256(binding['modelSHA256'])
    directions = (('higher-is-bona-fide', 'lower-is-bona-fide') if task == 'passive-pad' else
                  ('higher-is-match', 'lower-is-match'))
    _require(binding['scoreDirection'] in directions, 'invalid score direction for task')
    root = Path(root).resolve(strict=True)
    license_digest = _file_digest(document['licenseEvidence'], root)
    authorization_digest = _file_digest(authorization['evidence'], root)
    samples = document['samples']
    _require(type(samples) is list and samples, 'missing samples')
    seen_ids, seen_digests = set(), set()
    subject_splits, group_splits, session_roles, group_roles = {}, {}, {}, {}
    groups = {split: dict(subjects=set(), roles=Counter(), labels=Counter(), pai=Counter()) for split in SPLITS}
    for row in samples:
        _fields(row, 'id subjectId sessionId mediaGroupId split role label pai file')
        for key in ('id', 'subjectId', 'sessionId', 'mediaGroupId'):
            _token(row[key])
        split, role, label = row['split'], row['role'], row['label']
        _require(type(split) is str and split in SPLITS, 'invalid sample split')
        if task == 'passive-pad':
            _require(role == 'presentation' and label in ('bona-fide', 'attack'), 'invalid PAD role or label')
            if label == 'attack':
                _token(row['pai'])
            else:
                _require(row['pai'] is None, 'bona-fide PAI must be null')
        else:
            _require(role in ('enrollment', 'probe') and label == 'bona-fide' and row['pai'] is None,
                     'invalid identity role, label or PAI')
        _require(row['id'] not in seen_ids, 'duplicate sample ID')
        seen_ids.add(row['id'])
        digest = _file_digest(row['file'], root)
        _require(digest not in seen_digests, 'duplicate media digest')
        seen_digests.add(digest)
        for splits, key in ((subject_splits, row['subjectId']), (group_splits, row['mediaGroupId'])):
            _require(splits.setdefault(key, split) == split, 'calibration/test subject or media-group leakage')
        if task != 'passive-pad':
            session = (row['subjectId'], row['sessionId'])
            _require(session_roles.setdefault(session, role) == role, 'enrollment/probe session leakage')
            _require(group_roles.setdefault(row['mediaGroupId'], role) == role,
                     'enrollment/probe media-group leakage')
        group = groups[split]
        group['subjects'].add(row['subjectId'])
        group['roles'][role] += 1
        group['labels'][label] += 1
        if row['pai'] is not None:
            group['pai'][row['pai']] += 1
    counts = {}
    for split, group in sorted(groups.items()):
        if task == 'passive-pad':
            _require(set(group['labels']) == {'bona-fide', 'attack'}, 'both PAD classes required in each split')
        else:
            _require(set(group['roles']) == {'enrollment', 'probe'}, 'both identity roles required in each split')
        counts[split] = dict(sampleCount=sum(group['roles'].values()), subjectCount=len(group['subjects']),
                             **{key: dict(sorted(group[key].items())) for key in ('roles', 'labels', 'pai')})
    canonical = json.dumps(document, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()
    return dict(schemaVersion=1, kind='identity-calibration-manifest-summary', task=task,
                dataClass=document['dataClass'], dataset=dict(id=dataset['id'], revision=dataset['revision']),
                purpose=purpose, redistributeDataset=document['redistributeDataset'], binding=dict(binding),
                sampleCount=len(samples), splits=counts,
                evidenceSHA256=dict(license=license_digest, authorization=authorization_digest),
                manifestCanonicalSHA256=hashlib.sha256(canonical).hexdigest(), validationStatus='pass-bounded',
                authorizationStatus='declared-scope-only', calibrationStatus='unverified',
                unverified=['legal authority and consent', 'label correctness and undisclosed derivatives',
                            'trial completeness and representative data', 'recognition accuracy and thresholds',
                            'PAD performance', 'production policy and release readiness'])


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        _require(key not in result, 'duplicate JSON key')
        result[key] = value
    return result


def _reject_constant(value):
    raise ValueError('non-finite JSON number')


def load_document(path: Path):
    """Read strict JSON from one explicit regular document, without following links."""
    path = Path(path).absolute()
    with _regular_file(path.parent.resolve(strict=True), path.name) as stream:
        return json.load(stream, object_pairs_hook=_unique_object, parse_constant=_reject_constant)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest', type=Path)
    args = parser.parse_args()
    try:
        path = args.manifest.absolute()
        document = load_document(path)
        report = validate_manifest(document, path.parent)
    except (OSError, ValueError, TypeError):
        parser.exit(2, 'invalid calibration manifest; check schema, declarations, files and split isolation\n')
    print(json.dumps(report, sort_keys=True, indent=2, allow_nan=False))


if __name__ == '__main__':
    main()
