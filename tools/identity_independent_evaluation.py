"""Independent Identity verification develop/evaluate orchestrator (verification only).

Two phases, reused from existing bound tools (manifest checks, FAR/FRR math,
empirical curves, score summarization). This module does NOT implement feature
extraction, curves, FAR/FRR math, manifest checks or prior metric tools; it only
orchestrates them and freezes/verifies an evidence-only candidate policy.

Scope is the 'verification' task. Identification and passive-PAD are rejected as
unsupported; this module never claims support for them, never performs live
inference, consent handling or production threshold activation.

All reads go through the strict ``load_document``; all writes are exclusive to a
new regular file. Reports never contain private identifiers, row IDs or paths.
Public functions return reports and never mutate their inputs.

Integrity anchor
----------------
``develop`` emits a ``policyArtifactDigest`` over the frozen policy. That digest
alone is NOT a freeze boundary: a mutated policy can be rehashed and still
accepted. ``evaluate`` therefore requires an *externally supplied* pin,
``expected_policy_digest`` — the original 64-lowercase-hex
``policyArtifactDigest`` captured by the evaluator at successful ``develop`` and
retained separately. ``evaluate`` binds the received policy to that pin AND to a
fresh recomputation of the digest. This is an integrity binding only; it is not
legal, consent, or release authority (those remain unverified).
"""

import argparse
import errno
import hashlib
import json
import math
import os
import re
import stat
from pathlib import Path

from tools.identity_calibration_manifest import load_document, validate_manifest
from tools.identity_calibration_scores import summarize


# --------------------------------------------------------------------------
# small helpers (do not duplicate metric math; only envelope/field checks)
# --------------------------------------------------------------------------

def _require(condition, message):
    if not condition:
        raise ValueError(message)


def _require_dict(value, name):
    _require(type(value) is dict, 'invalid ' + name + ' document')


def _fields(value, names):
    _require(type(value) is dict and set(value) == set(names.split()),
             'invalid object fields')


def _strict_number(value):
    # bool is an int subclass; reject it explicitly so JSON aliases cannot slip.
    return type(value) in (int, float) and not isinstance(value, bool)


def _strict_int(value):
    # exact int, never bool.
    return type(value) is int


def _finite_number(value, label):
    # Reject non-numeric, non-finite, and overflowing values (e.g. 10**400)
    # without letting OverflowError escape. This is envelope validation, not
    # metric math; the existing score/manifest tools remain the source of truth
    # for nested schema and curve computation.
    _require(_strict_number(value), 'invalid finite number: ' + label)
    try:
        as_float = float(value)
    except OverflowError:
        raise ValueError('number out of finite range: ' + label)
    _require(math.isfinite(as_float), 'non-finite number: ' + label)
    return as_float


# --------------------------------------------------------------------------
# specification (predeclared before either phase)
# --------------------------------------------------------------------------

SPEC_FIELDS = ('targetFar maxFrr minSubjects minGenuineResponses '
               'minImpostorResponses maxNonresponses')


def _validate_spec(spec):
    _fields(spec, SPEC_FIELDS)
    tf, mf = spec['targetFar'], spec['maxFrr']
    tf = _finite_number(tf, 'targetFar')
    mf = _finite_number(mf, 'maxFrr')
    _require(0.0 <= tf <= 1.0, 'invalid target FAR')
    _require(0.0 <= mf <= 1.0, 'invalid max FRR')
    for key in ('minSubjects', 'minGenuineResponses', 'minImpostorResponses'):
        _require(_strict_int(spec[key]) and spec[key] >= 2,
                 'invalid ' + key + ' floor')
    _require(_strict_int(spec['maxNonresponses']) and spec['maxNonresponses'] >= 0,
             'invalid max nonresponses')


# --------------------------------------------------------------------------
# coverage: complete enrollment x probe matrix + minima (both phases)
# --------------------------------------------------------------------------

def _coverage_check(selected, rows, spec, label):
    # ``selected`` comes from the validated manifest and ``rows`` from a
    # summarize-validated score document, so field presence is already enforced
    # by the existing validators before we iterate here.
    enrollments = [s for s in selected.values() if s['role'] == 'enrollment']
    probes = [s for s in selected.values() if s['role'] == 'probe']
    _require(enrollments and probes, 'missing ' + label + ' enrollment or probe roles')
    required = {(e['id'], p['id']) for e in enrollments for p in probes}
    actual = {(r['enrollmentId'], r['probeId']) for r in rows}
    _require(actual == required, 'incomplete ' + label + ' enrollment/probe matrix')
    subjects = {s['subjectId'] for s in selected.values()}
    _require(len(subjects) >= spec['minSubjects'],
             'insufficient ' + label + ' distinct subjects')
    genuine = impostor = nonresponse = 0
    for row in rows:
        enr = selected[row['enrollmentId']]
        prb = selected[row['probeId']]
        if row['score'] is None:
            nonresponse += 1
        elif enr['subjectId'] == prb['subjectId']:
            genuine += 1
        else:
            impostor += 1
    _require(genuine > 0 and impostor > 0, 'missing ' + label + ' class denominator')
    _require(genuine >= spec['minGenuineResponses'],
             'insufficient ' + label + ' genuine responses')
    _require(impostor >= spec['minImpostorResponses'],
             'insufficient ' + label + ' impostor responses')
    _require(nonresponse <= spec['maxNonresponses'],
             'excess ' + label + ' nonresponses beyond preregistered limit')
    return dict(subjectCount=len(subjects), enrollmentCount=len(enrollments),
                probeCount=len(probes), pairCount=len(rows),
                genuineResponses=genuine, impostorResponses=impostor,
                nonresponses=nonresponse)


# --------------------------------------------------------------------------
# threshold selection from the existing empirical curve
# --------------------------------------------------------------------------

def _select_threshold(points, target_far, max_frr, higher):
    feasible = []
    for point in points:
        thr = point.get('threshold')
        far = point.get('farOnResponse')
        frr = point.get('frrOnResponse')
        if thr is None:  # reject-all sentinel endpoint is never auto-selected
            continue
        if not _strict_number(thr):
            continue
        if far is None or frr is None:  # missing class denominator
            continue
        if far <= target_far and frr <= max_frr:
            feasible.append((frr, thr))
    if not feasible:
        return None
    min_frr = min(f for f, _ in feasible)
    candidates = [t for f, t in feasible if f == min_frr]
    # Deterministic tie rule: stricter threshold among equal FRR.
    # stricter = fewer accepts -> larger threshold for higher-is-match,
    # smaller threshold for lower-is-match.
    return max(candidates) if higher else min(candidates)


# --------------------------------------------------------------------------
# canonical digests
# --------------------------------------------------------------------------

def _dataset_contract_digest(document):
    # Identical dataset/sample/evidence declaration across the two authorized
    # purposes; only the top-level purpose is excluded.
    contract = {k: v for k, v in document.items() if k != 'purpose'}
    canonical = json.dumps(contract, sort_keys=True, separators=(',', ':'),
                           allow_nan=False)
    return hashlib.sha256(canonical.encode('utf-8')).hexdigest()


POLICY_FIELDS = ('schemaVersion kind task dataClass dataset binding decisionRule '
                 'tieRule selectionSpec selectedThreshold scoreDirection coverage '
                 'manifestCanonicalSHA256 scoresCanonicalSHA256 '
                 'datasetContractSHA256 evidenceSHA256 calibrationStatus '
                 'productionReady productionPolicyGenerated unverified '
                 'policyArtifactDigest').split()


def _policy_digest(artifact):
    clean = {k: v for k, v in artifact.items() if k != 'policyArtifactDigest'}
    canonical = json.dumps(clean, sort_keys=True, separators=(',', ':'),
                           allow_nan=False)
    return hashlib.sha256(canonical.encode('utf-8')).hexdigest()


_HEX64 = '[a-f0-9]{64}'


def _require_hex64(value, label):
    _require(type(value) is str and re.fullmatch(_HEX64, value) is not None,
             'invalid ' + label + ' digest')


UNVERIFIED = [
    'legal authority, consent and data authorization',
    'label correctness and undisclosed duplicate or derivative media',
    'trial completeness, independent probes and population representativeness',
    'empirical response-only curves are descriptive, not validated accuracy',
    'production thresholds, calibration and release readiness',
    'PAD or recognition acceptance',
]


# --------------------------------------------------------------------------
# develop: calibration manifests/scores + spec -> frozen candidate policy
# --------------------------------------------------------------------------

def develop(manifest, calibration_scores, spec, root):
    """Select a finite inclusive-threshold candidate from calibration evidence.

    Returns an evidence-only, unverified policy artifact. Never a production
    policy: calibrationStatus stays 'unverified' and productionReady /
    productionPolicyGenerated stay False.
    """
    _require_dict(manifest, 'manifest')
    _require_dict(calibration_scores, 'calibration score')
    _require_dict(spec, 'spec')
    _validate_spec(spec)
    checked = validate_manifest(manifest, root)
    if checked['task'] != 'verification':
        raise ValueError('unsupported task; verification only')
    if calibration_scores.get('split') != 'calibration':
        raise ValueError('calibration scores require calibration split')
    report = summarize(manifest, calibration_scores, root)
    points = report['metrics']['empiricalCurves']['overall']['points']
    selected = {s['id']: s for s in manifest['samples'] if s['split'] == 'calibration'}
    coverage = _coverage_check(selected, calibration_scores['rows'], spec, 'calibration')
    higher = checked['binding']['scoreDirection'].startswith('higher-')
    threshold = _select_threshold(points, spec['targetFar'], spec['maxFrr'], higher)
    if threshold is None:
        raise ValueError('no feasible candidate threshold')

    artifact = dict(
        schemaVersion=1,
        kind='identity-independent-candidate-policy',
        task='verification',
        dataClass=checked['dataClass'],
        dataset=dict(id=checked['dataset']['id'], revision=checked['dataset']['revision']),
        binding=dict(checked['binding']),
        decisionRule='inclusive-threshold',
        tieRule='stricter-threshold-among-equal-frr',
        selectionSpec=dict(spec),
        selectedThreshold=threshold,
        scoreDirection=checked['binding']['scoreDirection'],
        coverage=dict(calibration=coverage),
        manifestCanonicalSHA256=checked['manifestCanonicalSHA256'],
        scoresCanonicalSHA256=report['scoresCanonicalSHA256'],
        datasetContractSHA256=_dataset_contract_digest(manifest),
        evidenceSHA256=dict(checked['evidenceSHA256']),
        calibrationStatus='unverified',
        productionReady=False,
        productionPolicyGenerated=False,
        unverified=list(UNVERIFIED),
    )
    artifact['policyArtifactDigest'] = _policy_digest(artifact)
    return artifact


# --------------------------------------------------------------------------
# evaluate: exact frozen policy + evaluation manifest/scores -> report
# --------------------------------------------------------------------------

def _validate_policy(policy, expected_policy_digest):
    # The externally supplied pin is the integrity anchor. Its type/shape is
    # validated before any use so a missing/invalid pin is rejected cleanly.
    _require_hex64(expected_policy_digest, 'expected policy')
    _fields(policy, ' '.join(sorted(POLICY_FIELDS)))
    _require(policy['kind'] == 'identity-independent-candidate-policy',
             'invalid policy kind')
    _require(policy['task'] == 'verification', 'unsupported policy task')
    _require(_strict_int(policy['schemaVersion']) and policy['schemaVersion'] == 1,
             'invalid policy schemaVersion')
    sel = policy['selectedThreshold']
    _require(_strict_number(sel), 'invalid frozen threshold')
    _finite_number(sel, 'frozen threshold')
    _require(policy['decisionRule'] == 'inclusive-threshold', 'invalid decision rule')
    _require(policy['tieRule'] == 'stricter-threshold-among-equal-frr',
             'invalid tie rule')
    _require(type(policy['binding']) is dict, 'invalid binding')
    _require(type(policy['scoreDirection']) is str, 'invalid scoreDirection')
    _require(policy['scoreDirection'] == policy['binding'].get('scoreDirection'),
             'policy scoreDirection disagrees with its binding')
    _require(type(policy['dataset']) is dict, 'invalid dataset reference')
    _require_hex64(policy['datasetContractSHA256'], 'contract')
    _require_hex64(policy['policyArtifactDigest'], 'policy')
    _require(policy['calibrationStatus'] == 'unverified', 'policy must stay unverified')
    _require(policy['productionReady'] is False, 'policy must not be production ready')
    _require(policy['productionPolicyGenerated'] is False,
             'policy must not be generated')
    _validate_spec(policy['selectionSpec'])

    # Two independent digest bindings:
    # 1. the received policy still recomputes to its own embedded digest
    #    (catches tampering that was not rehashed);
    # 2. the embedded digest equals the externally pinned original digest
    #    (catches a policy that was mutated and rehashed to match).
    recomputed = _policy_digest(policy)
    _require(recomputed == policy['policyArtifactDigest'],
             'policy digest tampered')
    _require(expected_policy_digest == policy['policyArtifactDigest'],
             'policy digest does not match pinned anchor')


def _validate_scores_envelope(scores, checked, frozen_threshold):
    _require(type(scores) is dict, 'invalid score document')
    _require(_strict_int(scores.get('schemaVersion')) and scores['schemaVersion'] == 1,
             'invalid score schema')
    _require(scores.get('kind') == 'identity-calibration-scores', 'invalid score kind')
    _require(scores.get('manifestCanonicalSHA256') == checked['manifestCanonicalSHA256'],
             'manifest binding mismatch')
    _require(type(scores.get('binding')) is dict and scores.get('binding') == checked['binding'],
             'binding mismatch')
    _require(scores.get('task') == 'verification', 'task binding mismatch')
    _require(scores.get('split') == 'test', 'invalid score split')
    thr = scores.get('threshold')
    _require(_strict_number(thr), 'invalid test threshold')
    _finite_number(thr, 'test threshold')
    _require(thr == frozen_threshold, 'test threshold differs from frozen policy')
    _require(type(scores.get('rows')) is list and scores.get('rows'),
             'missing score rows')


def evaluate(policy, eval_manifest, test_scores, root, *, expected_policy_digest):
    """Evaluate test evidence at the exact frozen threshold; no reselection.

    The frozen policy is fully validated (fields, semantics, and digest) and is
    additionally bound to ``expected_policy_digest`` — the original 64-hex
    ``policyArtifactDigest`` captured at successful ``develop`` and retained by
    the evaluator. The caller must supply that pinned value; it is never derived
    from the received policy. This is an integrity binding, not legal/release
    authority. Manifest samples/dataset/evidence must be the SAME declaration as
    develop (only the top-level purpose may differ; both purposes must be
    declared in allowedPurposes). There is no threshold override.
    """
    _require_dict(policy, 'policy')
    _require_dict(eval_manifest, 'manifest')
    _require_dict(test_scores, 'score')
    _validate_policy(policy, expected_policy_digest)
    checked = validate_manifest(eval_manifest, root)
    if checked['task'] != 'verification':
        raise ValueError('unsupported task; verification only')
    contract = _dataset_contract_digest(eval_manifest)
    _require(contract == policy['datasetContractSHA256'], 'dataset contract mismatch')
    _require(checked['binding'] == policy['binding'], 'model binding mismatch')
    _require(checked['binding'].get('scoreDirection') == policy['scoreDirection'],
             'scoreDirection mismatch with evaluation manifest')
    _require(checked['dataset']['id'] == policy['dataset']['id'] and
             checked['dataset']['revision'] == policy['dataset']['revision'],
             'dataset mismatch')
    _validate_scores_envelope(test_scores, checked, policy['selectedThreshold'])

    # Real summarize runs BEFORE the custom coverage matrix so that malformed
    # score rows are rejected by the existing validator, not by raw KeyError in
    # the matrix walk.
    report = summarize(eval_manifest, test_scores, root)
    selected = {s['id']: s for s in eval_manifest['samples'] if s['split'] == 'test'}
    coverage = _coverage_check(selected, test_scores['rows'],
                               policy['selectionSpec'], 'test')

    metrics = report['metrics']
    far = metrics.get('farOnResponse')
    frr = metrics.get('frrOnResponse')
    spec = policy['selectionSpec']
    selection_met = (far is not None and frr is not None and
                     far <= spec['targetFar'] and frr <= spec['maxFrr'])

    return dict(
        schemaVersion=1,
        kind='identity-independent-evaluation-report',
        task='verification',
        dataClass=checked['dataClass'],
        dataset=dict(id=checked['dataset']['id'], revision=checked['dataset']['revision']),
        binding=dict(checked['binding']),
        decisionRule=policy['decisionRule'],
        tieRule=policy['tieRule'],
        selectionSpec=dict(spec),
        fixedThreshold=policy['selectedThreshold'],
        scoreDirection=checked['binding']['scoreDirection'],
        selectionStatus='met' if selection_met else 'unmet',
        coverage=dict(test=coverage),
        datasetContractSHA256=contract,
        manifestCanonicalSHA256=checked['manifestCanonicalSHA256'],
        scoresCanonicalSHA256=report['scoresCanonicalSHA256'],
        policyArtifactDigest=policy['policyArtifactDigest'],
        metrics=metrics,
        rateDenominators=report['rateDenominators'],
        validationStatus=report['validationStatus'],
        calibrationStatus='unverified',
        productionReady=False,
        productionPolicyGenerated=False,
        unverified=list(report['unverified']),
    )


# --------------------------------------------------------------------------
# CLI: exclusive new-file output, sanitized errors (exit 2)
# --------------------------------------------------------------------------

def _normalize_system_prefix(target):
    """Substitute a single leading macOS system volume alias with its concrete
    directory, but only after verifying via ``lstat``/``readlink`` that the
    component is exactly that known alias.

    The only trusted aliases are ``/var -> /private/var`` and
    ``/tmp -> /private/tmp``. A custom leading symlink (or any other target) is
    NOT trusted and is left in place so the ``O_NOFOLLOW`` walk below rejects it.
    Only one leading component is substituted; the remainder of the path is never
    resolved, so an inner symlink is still caught by the walk. On Linux, where
    ``/tmp`` and ``/var`` are real directories, ``lstat`` shows no symlink and the
    path is used as-is.

    The link target reported by ``readlink`` may be absolute (``/private/tmp``) or
    relative to the link's parent (``private/tmp`` on macOS). It is resolved
    against the link's parent directory and lexically normalized before comparing
    against the exact allowlisted absolute expected path, so a relative target
    such as ``private/tmp`` (which would otherwise mismatch ``/private/tmp``) is
    correctly recognized. No other symlinks anywhere in the path are followed or
    normalized.
    """
    parts = target.parts
    if len(parts) < 2:
        return target
    first = parts[1]
    comp = '/' + first
    aliases = {'/var': '/private/var', '/tmp': '/private/tmp'}
    if comp not in aliases:
        return target
    try:
        lst = os.lstat(comp)
    except OSError:
        return target
    if not stat.S_ISLNK(lst.st_mode):
        return target  # real directory (e.g. Linux); use as-is
    try:
        got = os.readlink(comp)
    except OSError:
        return target
    expected = aliases[comp]
    # readlink may be absolute or relative to the link's parent; resolve it
    # against the parent lexically and normalize before the exact comparison.
    resolved = os.path.normpath(os.path.join(os.path.dirname(comp), got))
    if resolved != expected:
        return target  # not the known alias; leave for the walk to reject
    # Substitute only the single leading component with the concrete directory.
    return Path(expected, *parts[2:])

def _write_output(path, document):
    """Exclusively create a new regular JSON file at ``path``.

    A single leading macOS system volume alias (``/var -> /private/var`` or
    ``/tmp -> /private/tmp``) is substituted, but only after verifying the
    component is in fact that exact symlink (via ``lstat``/``readlink``). Custom
    leading symlinks and any inner symlink are not trusted and are rejected. On
    Linux, where ``/tmp`` and ``/var`` are real directories, they are used as
    is. The remaining parent components are traversed component-by-component
    with ``O_DIRECTORY | O_NOFOLLOW``; a symlink component (custom or inner) is
    refused regardless of whether the kernel reports ``ELOOP`` or ``ENOTDIR``.

    The JSON is serialized *before* opening the file, so a serialization error
    creates no final file. The final entry is created with ``O_CREAT | O_EXCL |
    O_NOFOLLOW | O_NONBLOCK``: an existing regular file, symlink or FIFO is
    refused and never overwritten, and opening an existing FIFO cannot block.
    On a write error the just-created entry is removed only when the directory
    entry still names the same inode we created (best-effort). This is NOT a
    guarantee against a concurrent writer in the same directory swapping the
    name; the caller must use a trusted directory with a single writer.
    """
    target = Path(path).absolute()
    target = _normalize_system_prefix(target)
    parts = target.parts
    if len(parts) < 2:
        raise ValueError('invalid output path')
    filename = parts[-1]

    cur = os.open('/', os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        for comp in parts[1:-1]:
            if stat.S_ISLNK(os.lstat(comp, dir_fd=cur).st_mode):
                raise ValueError('output path traverses an untrusted symlink')
            try:
                nxt = os.open(comp, os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=cur)
            except OSError:
                raise ValueError('output path traverses an untrusted symlink')
            os.close(cur)
            cur = nxt

        # Serialize before opening so a bad document never materializes.
        payload = json.dumps(document, sort_keys=True, indent=2,
                             allow_nan=False) + '\n'
        try:
            fd = os.open(filename, os.O_WRONLY | os.O_CREAT | os.O_EXCL |
                         os.O_NOFOLLOW | os.O_NONBLOCK, 0o644, dir_fd=cur)
        except OSError:
            raise ValueError('output path exists, is a symlink/FIFO, or not creatable')
        created = os.fstat(fd)
        try:
            with os.fdopen(fd, 'w') as stream:
                stream.write(payload)
                stream.flush()
                os.fsync(stream.fileno())
        except Exception:
            # Best-effort cleanup: only remove the entry if it still is the file
            # we just created. We do NOT claim immunity to a concurrent writer
            # in the same directory replacing the name; a swapped entry is left
            # untouched. Safe only under the trusted-directory/single-writer
            # assumption documented above.
            try:
                st = os.lstat(filename, dir_fd=cur)
            except OSError:
                st = None
            if (st is not None and stat.S_ISREG(st.st_mode)
                    and st.st_ino == created.st_ino):
                try:
                    os.unlink(filename, dir_fd=cur)
                except OSError:
                    pass
            raise
    finally:
        os.close(cur)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)

    dev = sub.add_parser('develop', help='select frozen candidate policy')
    dev.add_argument('--manifest', type=Path, required=True)
    dev.add_argument('--calibration-scores', type=Path, required=True)
    dev.add_argument('--spec', type=Path, required=True)
    dev.add_argument('--output', type=Path, required=True)

    ev = sub.add_parser('evaluate', help='evaluate at frozen threshold')
    ev.add_argument('--policy', type=Path, required=True)
    ev.add_argument('--manifest', type=Path, required=True)
    ev.add_argument('--test-scores', type=Path, required=True)
    ev.add_argument('--expected-policy-digest', type=str, required=True,
                    help='original 64-lowercase-hex policyArtifactDigest captured '
                         'at develop and retained separately by the evaluator')
    ev.add_argument('--output', type=Path, required=True)

    args = parser.parse_args()
    try:
        if args.command == 'develop':
            manifest = load_document(args.manifest)
            calibration = load_document(args.calibration_scores)
            spec = load_document(args.spec)
            policy = develop(manifest, calibration, spec,
                             args.manifest.resolve().parent)
            _write_output(args.output, policy)
        else:
            policy = load_document(args.policy)
            manifest = load_document(args.manifest)
            test = load_document(args.test_scores)
            report = evaluate(policy, manifest, test,
                              args.manifest.resolve().parent,
                              expected_policy_digest=args.expected_policy_digest)
            _write_output(args.output, report)
    except (OSError, ValueError, TypeError):
        parser.exit(2, 'invalid independent evaluation request; '
                      'consult schema and constraints\n')


if __name__ == '__main__':
    main()
