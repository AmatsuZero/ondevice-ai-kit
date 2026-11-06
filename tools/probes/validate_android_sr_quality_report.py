"""Pure validation for Android super-resolution quality reports.

This is a test-only evidence tool. It performs no IO and does not verify
model quality; qualityVerdict is expected to remain 'unverified'.
"""


_HEX64 = set("0123456789abcdef")
_EXPECTED_KINDS = ("nearest", "bilinear", "esrgan")
_FRAMES_PER_RUN = 48


class ReportValidationError(ValueError):
    """Raised when a report fails schema or evidence-identity validation."""


def _is_int(x):
    return isinstance(x, int) and not isinstance(x, bool)


def _is_lower_hex64(s):
    if not isinstance(s, str) or len(s) != 64:
        return False
    return all(c in _HEX64 for c in s)


def validate_report(report, *, run_id, input_sha256, model_sha256,
                    source_hashes, output_size):
    """Validate a report dict against expected evidence identity.

    Args:
        report: parsed report mapping (must be a dict).
        run_id: expected runId string.
        input_sha256: expected exactly-matching inputSha256.
        model_sha256: expected exactly-matching modelSha256.
        source_hashes: expected exactly-matching sourceInventory.sources mapping.
        output_size: expected (width, height) tuple for every run.

    Returns:
        A concise checked summary dict:
            {runId, output_count, total_paired_frames, qualityVerdict}.

    Raises:
        ReportValidationError: on any schema, identity, or shape mismatch.
    """
    if not isinstance(report, dict):
        raise ReportValidationError("report must be a dict")

    sv = report.get("schemaVersion")
    if not _is_int(sv) or sv != 1:
        raise ReportValidationError("schemaVersion must be int 1")

    rid = report.get("runId")
    if not isinstance(rid, str) or rid != run_id:
        raise ReportValidationError("runId must match expected run_id")

    pid = report.get("pid")
    if not _is_int(pid) or pid <= 0:
        raise ReportValidationError("pid must be a positive int")

    terminal = report.get("terminal")
    if terminal is not True:
        raise ReportValidationError("terminal must be exactly True")

    status = report.get("status")
    if status != "completed":
        raise ReportValidationError("status must be 'completed'")

    qv = report.get("qualityVerdict")
    if qv != "unverified":
        raise ReportValidationError("qualityVerdict must be 'unverified'")

    if report.get("inputSha256") != input_sha256:
        raise ReportValidationError("inputSha256 does not match expected")

    if report.get("modelSha256") != model_sha256:
        raise ReportValidationError("modelSha256 does not match expected")

    si = report.get("sourceInventory")
    if not isinstance(si, dict):
        raise ReportValidationError("sourceInventory must be a dict")
    sources = si.get("sources")
    if not isinstance(sources, dict) or sources != source_hashes:
        raise ReportValidationError(
            "sourceInventory.sources must exactly match expected mapping")

    runs = report.get("runs")
    if not isinstance(runs, list):
        raise ReportValidationError("runs must be a list")
    if len(runs) != len(_EXPECTED_KINDS):
        raise ReportValidationError(
            "runs must contain exactly %d entries" % len(_EXPECTED_KINDS))

    if (not isinstance(output_size, (tuple, list)) or len(output_size) != 2
            or any(not _is_int(value) or value <= 0 for value in output_size)):
        raise ReportValidationError("output_size must contain two positive ints")
    expected_size = tuple(output_size)
    codec_signature = None
    total_frames = 0

    for i, run in enumerate(runs):
        if not isinstance(run, dict):
            raise ReportValidationError("runs[%d] must be a dict" % i)

        kind = run.get("kind")
        if kind != _EXPECTED_KINDS[i]:
            raise ReportValidationError(
                "runs[%d] kind must be %r, got %r" % (i, _EXPECTED_KINDS[i], kind))

        if run.get("status") != "completed":
            raise ReportValidationError(
                "runs[%d] (%s) status must be 'completed'" % (i, kind))

        cf = run.get("completedFrames")
        if not _is_int(cf) or cf != _FRAMES_PER_RUN:
            raise ReportValidationError(
                "runs[%d] (%s) completedFrames must be int %d"
                % (i, kind, _FRAMES_PER_RUN))

        fr = run.get("frames")
        if not _is_int(fr) or fr != _FRAMES_PER_RUN:
            raise ReportValidationError(
                "runs[%d] (%s) frames must be int %d"
                % (i, kind, _FRAMES_PER_RUN))

        w = run.get("width")
        h = run.get("height")
        if not _is_int(w) or not _is_int(h):
            raise ReportValidationError(
                "runs[%d] (%s) width/height must be int" % (i, kind))
        if (w, h) != expected_size:
            raise ReportValidationError(
                "runs[%d] (%s) width/height %r must match output_size %r"
                % (i, kind, (w, h), expected_size))

        ob = run.get("outputBytes")
        if not _is_int(ob) or ob <= 0:
            raise ReportValidationError(
                "runs[%d] (%s) outputBytes must be a positive int" % (i, kind))

        osha = run.get("outputSha256")
        if not _is_lower_hex64(osha):
            raise ReportValidationError(
                "runs[%d] (%s) outputSha256 must be lowercase 64-hex" % (i, kind))

        codecs = run.get("codecs")
        if not isinstance(codecs, list) or len(codecs) != 2:
            raise ReportValidationError(
                "runs[%d] (%s) codecs must be exactly 2 objects" % (i, kind))
        dec, enc = codecs
        if not isinstance(dec, dict) or not isinstance(enc, dict):
            raise ReportValidationError(
                "runs[%d] (%s) codec entries must be dicts" % (i, kind))
        if dec.get("encoder") is not False:
            raise ReportValidationError(
                "runs[%d] (%s) first codec must be decoder (encoder False)" % (i, kind))
        if enc.get("encoder") is not True:
            raise ReportValidationError(
                "runs[%d] (%s) second codec must be encoder (encoder True)" % (i, kind))
        dname = dec.get("name")
        ename = enc.get("name")
        if not isinstance(dname, str) or not dname or \
                not isinstance(ename, str) or not ename:
            raise ReportValidationError(
                "runs[%d] (%s) codec names must be nonempty strings" % (i, kind))

        sig = (dname, ename)
        if codec_signature is None:
            codec_signature = sig
        elif sig != codec_signature:
            raise ReportValidationError(
                "runs[%d] (%s) codec selection differs from earlier runs" % (i, kind))

        total_frames += cf

    return {
        "runId": rid,
        "output_count": len(runs),
        "total_paired_frames": total_frames,
        "qualityVerdict": qv,
    }
