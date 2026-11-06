"""Bound, descriptive offline score metrics; never a production calibration policy."""
import argparse
import hashlib
import json
import math
from pathlib import Path

from tools.identity_calibration_manifest import load_document, validate_manifest
from tools.identity_calibration_statistics import binary_counts, search_counts


def _require(condition, message):
    if not condition:
        raise ValueError(message)


def _fields(value, names):
    _require(type(value) is dict and set(value) == set(names.split()), 'invalid score fields')


def _number(value):
    _require(type(value) in (int, float), 'expected finite numeric score or threshold')
    try:
        finite = math.isfinite(value)
    except OverflowError:
        finite = False
    _require(finite, 'expected finite representable score or threshold')
    return value


def _accepted(score, threshold, higher):
    if score is None:
        return None
    score = _number(score)
    return score >= threshold if higher else score <= threshold


def _reference(row, key, selected, role):
    value = row[key]
    _require(type(value) is str and value in selected, 'unknown or cross-split sample reference')
    sample = selected[value]
    _require(sample['role'] == role, 'wrong sample role')
    return sample


def _verification(rows, selected, threshold, higher):
    seen, records = set(), []
    for row in rows:
        _fields(row, 'enrollmentId probeId score')
        enrollment = _reference(row, 'enrollmentId', selected, 'enrollment')
        probe = _reference(row, 'probeId', selected, 'probe')
        pair = (enrollment['id'], probe['id'])
        _require(pair not in seen, 'duplicate verification pair')
        seen.add(pair)
        records.append(dict(positive=enrollment['subjectId'] == probe['subjectId'],
                            accepted=_accepted(row['score'], threshold, higher)))
    counts = binary_counts(records)
    metrics = dict(farOnResponse=counts['negative']['errorRateOnResponse'],
                   frrOnResponse=counts['positive']['errorRateOnResponse'], counts=counts)
    return dict(kind='supplied-pairs', comparisonCount=len(rows), decision='inclusive-threshold'), metrics


def _pad(rows, selected, threshold, higher):
    seen, records, pai_records = set(), [], {}
    for row in rows:
        _fields(row, 'sampleId score')
        sample = _reference(row, 'sampleId', selected, 'presentation')
        _require(sample['id'] not in seen, 'duplicate presentation')
        seen.add(sample['id'])
        record = dict(positive=sample['label'] == 'bona-fide', accepted=_accepted(row['score'], threshold, higher))
        records.append(record)
        if sample['label'] == 'attack':
            pai_records.setdefault(sample['pai'], []).append(record)
    _require(seen == set(selected), 'incomplete PAD split coverage')
    counts = binary_counts(records)
    by_pai = {}
    for pai, group in sorted(pai_records.items()):
        group_counts = binary_counts(group)['negative']
        by_pai[pai] = dict(apcerOnResponse=group_counts['errorRateOnResponse'], counts=group_counts)
    rates = [group['apcerOnResponse'] for group in by_pai.values()]
    worst = max(rates) if rates and all(rate is not None for rate in rates) else None
    metrics = dict(bpcerOnResponse=counts['positive']['errorRateOnResponse'],
                   apcerWorstPAIOnResponse=worst, byPAI=by_pai, counts=counts)
    return dict(kind='complete-selected-split-presentations', presentationCount=len(rows),
                decision='inclusive-threshold'), metrics


def _identification(rows, selected, threshold, higher):
    gallery = {key: value for key, value in selected.items() if value['role'] == 'enrollment'}
    probes = {key: value for key, value in selected.items() if value['role'] == 'probe'}
    gallery_subjects = {sample['subjectId'] for sample in gallery.values()}
    matrix = {}
    for row in rows:
        _fields(row, 'enrollmentId probeId score')
        enrollment = _reference(row, 'enrollmentId', selected, 'enrollment')
        probe = _reference(row, 'probeId', selected, 'probe')
        pair = (probe['id'], enrollment['id'])
        _require(pair not in matrix, 'duplicate gallery/probe cell')
        matrix[pair] = None if row['score'] is None else _number(row['score'])
    _require(len(matrix) == len(gallery) * len(probes), 'incomplete gallery/probe matrix')
    records = []
    for probe_id, probe in probes.items():
        known = probe['subjectId'] in gallery_subjects
        values = [(sample['subjectId'], matrix[(probe_id, key)]) for key, sample in gallery.items()]
        tied = False
        if any(value is None for _, value in values):
            outcome = 'nonresponse'
        else:
            best = (max if higher else min)(value for _, value in values)
            identities = {subject for subject, value in values if value == best}
            tied = len(identities) > 1
            if tied or not _accepted(best, threshold, higher):
                outcome = 'rejected'
            elif known:
                outcome = 'correct' if probe['subjectId'] in identities else 'wrong'
            else:
                outcome = 'accepted'
        records.append(dict(known=known, outcome=outcome, tied=tied))
    protocol = dict(kind='complete-selected-split-gallery-matrix', rank=1,
                    tiePolicy='reject-top-score-identity-ties', galleryTemplates=len(gallery),
                    galleryIdentities=len(gallery_subjects), probeCount=len(probes))
    return protocol, search_counts(records)


def summarize(manifest: dict, scores: dict, root: Path) -> dict:
    """Validate declared provenance/protocol, then summarize at a caller threshold."""
    checked = validate_manifest(manifest, root)
    _fields(scores, 'schemaVersion kind manifestCanonicalSHA256 binding task split threshold rows')
    _require(type(scores['schemaVersion']) is int and scores['schemaVersion'] == 1, 'invalid score schema')
    _require(scores['kind'] == 'identity-calibration-scores', 'invalid score kind')
    _require(scores['manifestCanonicalSHA256'] == checked['manifestCanonicalSHA256'], 'manifest binding mismatch')
    _require(type(scores['binding']) is dict and scores['binding'] == checked['binding'], 'model binding mismatch')
    task = scores['task']
    _require(task == checked['task'], 'task binding mismatch')
    split = scores['split']
    _require(type(split) is str and split in ('calibration', 'test'), 'invalid score split')
    purpose = 'local-calibration' if split == 'calibration' else 'local-evaluation'
    _require(checked['purpose'] == purpose, 'declared purpose does not cover selected split operation')
    threshold = _number(scores['threshold'])
    rows = scores['rows']
    _require(type(rows) is list and rows, 'missing score rows')
    selected = {sample['id']: sample for sample in manifest['samples'] if sample['split'] == split}
    higher = checked['binding']['scoreDirection'].startswith('higher-')
    evaluate = {'verification': _verification, 'identification': _identification, 'passive-pad': _pad}[task]
    protocol, metrics = evaluate(rows, selected, threshold, higher)
    canonical = json.dumps(scores, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()
    return dict(schemaVersion=1, kind='identity-calibration-score-summary', task=task, split=split,
                manifestCanonicalSHA256=checked['manifestCanonicalSHA256'],
                scoresCanonicalSHA256=hashlib.sha256(canonical).hexdigest(),
                binding=dict(checked['binding']), dataClass=checked['dataClass'],
                threshold=threshold, scoreDirection=checked['binding']['scoreDirection'],
                protocol=protocol, metrics=metrics, validationStatus='pass-bounded',
                calibrationStatus='unverified', thresholdOrigin='caller-supplied-unverified',
                rateDenominators='error-rates-on-responses; nonresponse-rates-on-total-trials',
                unverified=['legal authority and actual score provenance', 'labels and undisclosed leakage',
                            'independent trials and population representativeness', 'ROC/DET and confidence inference',
                            'production thresholds and policies', 'PAD or recognition acceptance', 'release readiness'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('scores', type=Path)
    args = parser.parse_args()
    try:
        manifest, scores = load_document(args.manifest), load_document(args.scores)
        report = summarize(manifest, scores, args.manifest.absolute().parent)
    except (OSError, ValueError, TypeError):
        parser.exit(2, 'invalid bound score input; check declarations, binding, protocol and finite values\n')
    print(json.dumps(report, sort_keys=True, indent=2, allow_nan=False))


if __name__ == '__main__':
    main()
