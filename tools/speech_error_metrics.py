import argparse
import hashlib
import json
import sys
import unicodedata
from pathlib import Path


_PAIR_KEYS = {"id", "reference", "hypothesis"}
_INPUT_KEYS = {"schemaVersion", "pairs"}


def _validate_text(value, name):
    if type(value) is not str:
        raise ValueError(f"{name} must be an exact str")
    if any(0xD800 <= ord(char) <= 0xDFFF for char in value):
        raise ValueError(f"{name} contains a Unicode surrogate")
    return value


def _validate_casefold(casefold):
    if type(casefold) is not bool:
        raise ValueError("casefold must be an exact bool")


def _normalize(text, casefold):
    text = unicodedata.normalize("NFC", text)
    if casefold:
        text = unicodedata.normalize("NFC", text.casefold())
    return " ".join(text.split())


def _score_units(reference, hypothesis):
    rows = len(reference) + 1
    columns = len(hypothesis) + 1
    distance = [[0] * columns for _ in range(rows)]
    for i in range(rows):
        distance[i][0] = i
    for j in range(columns):
        distance[0][j] = j

    for i in range(1, rows):
        for j in range(1, columns):
            substitution_cost = reference[i - 1] != hypothesis[j - 1]
            distance[i][j] = min(
                distance[i - 1][j] + 1,
                distance[i][j - 1] + 1,
                distance[i - 1][j - 1] + substitution_cost,
            )

    substitutions = deletions = insertions = 0
    i, j = len(reference), len(hypothesis)
    while i or j:
        if (
            i
            and j
            and reference[i - 1] == hypothesis[j - 1]
            and distance[i][j] == distance[i - 1][j - 1]
        ):
            i -= 1
            j -= 1
        elif i and j and distance[i][j] == distance[i - 1][j - 1] + 1:
            substitutions += 1
            i -= 1
            j -= 1
        elif i and distance[i][j] == distance[i - 1][j] + 1:
            deletions += 1
            i -= 1
        else:
            insertions += 1
            j -= 1

    errors = substitutions + deletions + insertions
    reference_units = len(reference)
    return {
        "referenceUnits": reference_units,
        "hypothesisUnits": len(hypothesis),
        "substitutions": substitutions,
        "deletions": deletions,
        "insertions": insertions,
        "errors": errors,
        "rate": errors / reference_units if reference_units else None,
        "defined": reference_units != 0,
    }


def _score_normalized(reference, hypothesis):
    return {
        "word": _score_units(reference.split(), hypothesis.split()),
        "character": _score_units(
            [char for char in reference if not char.isspace()],
            [char for char in hypothesis if not char.isspace()],
        ),
    }


def score_pair(reference: str, hypothesis: str, *, casefold: bool = False) -> dict:
    _validate_casefold(casefold)
    reference = _normalize(_validate_text(reference, "reference"), casefold)
    hypothesis = _normalize(_validate_text(hypothesis, "hypothesis"), casefold)
    return _score_normalized(reference, hypothesis)


def _normalization_policy(casefold):
    return {
        "unicodeNormalization": "NFC, optional casefold, then NFC",
        "whitespace": "Unicode whitespace runs collapse to one space; leading and trailing whitespace are trimmed",
        "wordUnits": "Unicode whitespace-delimited str.split() tokens; no language-aware segmentation",
        "characterUnits": "Unicode codepoints excluding whitespace; not grapheme clusters",
        "preserved": "Punctuation, accents, and digits are not stripped",
        "casefold": casefold,
        "distance": "Minimum unit Levenshtein edit distance",
        "tracebackTieOrder": ["exact match", "substitution (diagonal)", "deletion", "insertion"],
    }


def score_corpus(pairs: list[dict], *, casefold: bool = False) -> dict:
    _validate_casefold(casefold)
    if type(pairs) is not list or not pairs:
        raise ValueError("pairs must be a nonempty exact list")

    rows = []
    identifiers = set()
    totals = {
        metric: {
            "referenceUnits": 0,
            "hypothesisUnits": 0,
            "substitutions": 0,
            "deletions": 0,
            "insertions": 0,
            "errors": 0,
        }
        for metric in ("word", "character")
    }
    for index, pair in enumerate(pairs):
        if type(pair) is not dict or set(pair) != _PAIR_KEYS:
            raise ValueError(f"pairs[{index}] must be an exact dict with id, reference, and hypothesis")
        identifier = _validate_text(pair["id"], f"pairs[{index}].id")
        if not identifier or identifier != identifier.strip():
            raise ValueError(f"pairs[{index}].id must be nonempty and trimmed")
        if identifier in identifiers:
            raise ValueError(f"duplicate id in pairs: {identifier}")
        identifiers.add(identifier)

        reference = _validate_text(pair["reference"], f"pairs[{index}].reference")
        hypothesis = _validate_text(pair["hypothesis"], f"pairs[{index}].hypothesis")
        scored = score_pair(reference, hypothesis, casefold=casefold)
        row = {
            "id": identifier,
            **scored,
            "referenceSHA256": hashlib.sha256(reference.encode("utf-8")).hexdigest(),
            "hypothesisSHA256": hashlib.sha256(hypothesis.encode("utf-8")).hexdigest(),
        }
        rows.append(row)
        for metric in totals:
            for field in totals[metric]:
                totals[metric][field] += scored[metric][field]

    aggregate = {}
    for metric, values in totals.items():
        reference_units = values["referenceUnits"]
        aggregate[metric] = {
            **values,
            "rate": values["errors"] / reference_units if reference_units else None,
            "defined": reference_units != 0,
        }

    return {
        "schemaVersion": 1,
        "kind": "speech-error-metrics",
        "normalization": _normalization_policy(casefold),
        "rows": rows,
        "aggregate": aggregate,
        "qualityVerdict": "unverified",
        "productionReady": False,
    }


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON object key")
        result[key] = value
    return result


def _reject_json_constant(value):
    raise ValueError(f"invalid JSON constant: {value}")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Offline speech CER/WER evaluator")
    parser.add_argument("input", help="UTF-8 JSON input file")
    parser.add_argument("--casefold", action="store_true", help="casefold text before scoring")
    args = parser.parse_args(argv)

    try:
        raw = Path(args.input).read_bytes()
        document = json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=_unique_object,
            parse_constant=_reject_json_constant,
        )
        if (
            type(document) is not dict
            or set(document) != _INPUT_KEYS
            or type(document["schemaVersion"]) is not int
            or document["schemaVersion"] != 1
        ):
            raise ValueError("invalid input schema")
        result = score_corpus(document["pairs"], casefold=args.casefold)
        result["inputSHA256"] = hashlib.sha256(raw).hexdigest()
        output = json.dumps(result, allow_nan=False, separators=(",", ":")) + "\n"
    except (OSError, UnicodeError, json.JSONDecodeError, ValueError, RecursionError):
        print("error: invalid input", file=sys.stderr)
        return 1

    sys.stdout.write(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
