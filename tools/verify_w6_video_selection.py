#!/usr/bin/env python3
"""Independently reconstruct a W6 selection; never import or run its selector."""
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import re
import sys


def demand(condition, message):
    if not condition:
        raise ValueError(message)


def unique_object(items):
    demand(len({key for key, _ in items}) == len(items), "duplicate JSON key")
    return dict(items)


def parse(raw):
    return json.loads(raw, object_pairs_hook=unique_object,
                      parse_constant=lambda x: (_ for _ in ()).throw(ValueError("nonfinite JSON")))


def source(ref):
    demand(isinstance(ref, dict) and set(ref) == {"path", "sha256"}, "invalid source")
    demand(isinstance(ref["path"], str) and Path(ref["path"]).is_absolute(), "nonabsolute source")
    demand(isinstance(ref["sha256"], str) and re.fullmatch(r"[a-f0-9]{64}", ref["sha256"]), "invalid digest")
    raw = Path(ref["path"]).read_bytes()
    demand(hashlib.sha256(raw).hexdigest() == ref["sha256"], "source digest mismatch")
    return parse(raw)


def verify(contract_path, selection_path):
    contract_raw, selection_raw = Path(contract_path).read_bytes(), Path(selection_path).read_bytes()
    contract, selection = parse(contract_raw), parse(selection_raw)
    demand(type(contract.get("schemaVersion")) is int and contract["schemaVersion"] == 1
           and set(contract) == {"schemaVersion", "sources"}, "contract schema")
    refs = contract["sources"]
    demand(set(refs) == {"annotations", "cutMapping", "zipIndex", "exclusions"}, "source roles")
    documents = {role: source(ref) for role, ref in refs.items()}
    exclusion = documents["exclusions"]
    demand(set(exclusion) == {"schemaVersion", "videoIds", "sources"}
           and type(exclusion["schemaVersion"]) is int and exclusion["schemaVersion"] == 1, "exclusion schema")
    ids = exclusion["videoIds"]
    demand(isinstance(ids, list) and all(isinstance(v, str) and re.fullmatch(r"video_\d+", v) for v in ids)
           and ids == sorted(set(ids)), "invalid exclusion IDs")
    demand(isinstance(exclusion["sources"], list) and exclusion["sources"], "no historical sources")
    recovered, paths = set(), set()
    for entry in exclusion["sources"]:
        demand(set(entry) == {"path", "sha256", "rowKeys"} and entry["path"] not in paths, "invalid historical source")
        paths.add(entry["path"])
        history = source({"path": entry["path"], "sha256": entry["sha256"]})
        keys = entry["rowKeys"]
        demand(isinstance(keys, list) and keys and len(set(keys)) == len(keys)
               and set(keys) <= {"items", "trainItems", "validItems"}, "invalid historical row keys")
        for key in keys:
            demand(isinstance(history.get(key), list), "historical rows are not an array")
            for row in history[key]:
                demand(isinstance(row, dict) and isinstance(row.get("videoId"), str)
                       and re.fullmatch(r"video_\d+", row["videoId"]), "invalid historical video")
                recovered.add(row["videoId"])
    demand(recovered == set(ids), "incomplete exclusion union")
    categories = ("counterfactual", "memory", "abstraction", "physics", "semantics", "camera_motion")
    seed = "w6-qwen35-4b-fresh36-v1"
    policy = {"videos": 36, "categories": list(categories), "videosPerCategory": 6,
              "answerLabelsPerCategory": {"A": 2, "B": 2, "C": 2}, "questionsPerVideo": 1,
              "maxDurationSeconds": 40, "maxResolutionPixels": 2073600,
              "maxArchiveMemberBytes": 134217728, "slots": [0, 1, 2, 0, 1, 2],
              "outcomeIndependent": True, "noSubstituteOnFailure": True}
    annotations, cuts, archive = (documents[key] for key in ("annotations", "cutMapping", "zipIndex"))
    demand(isinstance(annotations, dict) and isinstance(cuts, dict) and isinstance(archive.get("members"), list), "input schema")
    members = {row["name"]: row for row in archive["members"]}
    demand(len(members) == len(archive["members"]), "duplicate archive member")
    candidates = []
    for vid, video in annotations.items():
        demand(isinstance(vid, str) and re.fullmatch(r"video_\d+", vid), "invalid video ID")
        if vid in recovered:
            continue
        demand(vid in cuts and type(cuts[vid]) is int and cuts[vid] >= -1, "missing cut")
        member = members.get("videos/" + vid + ".mp4")
        if cuts[vid] != -1 or member is None:
            continue
        m = video["metadata"]
        demand(m["video_id"] == vid and type(m["num_frames"]) is int and m["num_frames"] > 0, "metadata identity")
        demand(type(m["frame_rate"]) in (int, float) and math.isfinite(m["frame_rate"])
               and m["frame_rate"] > 0, "invalid rate")
        resolution = m["resolution"]
        demand(isinstance(resolution, list) and len(resolution) == 2
               and all(type(n) is int and n > 0 for n in resolution), "invalid resolution")
        demand(all(type(member.get(k)) is int and member[k] >= 0
                   for k in ("bytes", "compressedBytes", "crc32", "method", "flags", "headerOffset")), "member metadata")
        duration = m["num_frames"] / m["frame_rate"]
        if (m["split"] != "valid" or duration > 40 or resolution[0] * resolution[1] > 2073600
                or not 0 < member["bytes"] <= 134217728 or member["compressedBytes"] == 0
                or member["method"] != 12 or member["flags"] != 0):
            continue
        demand(isinstance(video["mc_question"], list), "questions schema")
        seen = set()
        for q in video["mc_question"]:
            demand(type(q["id"]) is int and q["id"] >= 0 and q["id"] not in seen, "question ID")
            seen.add(q["id"])
            demand(type(q["answer_id"]) is int and 0 <= q["answer_id"] <= 2
                   and isinstance(q["question"], str) and q["question"]
                   and isinstance(q["options"], list) and len(q["options"]) == 3
                   and all(isinstance(x, str) and x for x in q["options"]), "question content")
            demand(q["area"] in ("memory", "abstraction", "physics", "semantics")
                   and q["reasoning"] in ("descriptive", "explanatory", "predictive", "counterfactual"), "question class")
            if q["question"] == "Is the camera moving or static?":
                cat = "camera_motion"
            elif q["area"] in ("memory", "abstraction"):
                cat = q["area"]
            elif q["reasoning"] == "counterfactual":
                cat = "counterfactual"
            else:
                cat = q["area"]
            label = q["answer_id"]
            rank = hashlib.sha256(f'{seed}|{cat}|{label}|{vid}|{q["id"]}'.encode()).hexdigest()
            row = {"videoId": vid, "questionId": q["id"], "category": cat, "answerIndex": label,
                   "answerLetter": "ABC"[label], "question": q["question"], "options": q["options"],
                   "area": q["area"], "reasoning": q["reasoning"], "tags": q.get("tag", []),
                   "durationSeconds": duration, "numFrames": m["num_frames"], "frameRate": m["frame_rate"],
                   "resolution": resolution, "cutFrame": -1, "archiveMember": member["name"],
                   "archiveMemberBytes": member["bytes"], "archiveCompressedBytes": member["compressedBytes"],
                   "archiveCRC32": member["crc32"], "archiveMethod": member["method"], "archiveFlags": member["flags"],
                   "archiveHeaderOffset": member["headerOffset"], "id": f'pt-fresh36-{vid}-q{q["id"]}',
                   "selectionRankSHA256": rank}
            candidates.append(row)
    candidates.sort(key=lambda row: (row["selectionRankSHA256"], row["videoId"], row["questionId"]))
    used, rows = set(recovered), []
    for cat in categories:
        for label in (0, 1, 2, 0, 1, 2):
            row = next((r.copy() for r in candidates if r["category"] == cat
                        and r["answerIndex"] == label and r["videoId"] not in used), None)
            demand(row is not None, "insufficient unique videos")
            row["index"] = len(rows)
            rows.append(row)
            used.add(row["videoId"])
    counts = Counter((row["category"], row["answerIndex"]) for row in candidates)
    expected = {"schemaVersion": 1, "dataset": "google-deepmind/perception_test", "split": "valid",
                "selectionSeed": seed, "contractSha256": hashlib.sha256(contract_raw).hexdigest(), "policy": policy,
                "sourceHashes": {key: value["sha256"] for key, value in refs.items()},
                "excludedPreviousVideoIds": sorted(recovered),
                "archive": {key: archive[key] for key in ("url", "archiveBytes", "etag", "generation")},
                "candidateCounts": {cat: {str(label): counts[cat, label] for label in range(3)} for cat in categories},
                "selectedCompressedBytes": sum(row["archiveCompressedBytes"] for row in rows),
                "selectedRawBytes": sum(row["archiveMemberBytes"] for row in rows), "items": rows,
                "claimLimit": "Metadata-only selection, exclusion complete only within declared sources; not media, training-clean or product acceptance."}
    # Canonical JSON comparison preserves type distinctions such as true versus 1.
    demand(json.dumps(expected, sort_keys=True, allow_nan=False) == json.dumps(selection, sort_keys=True, allow_nan=False),
           "selection differs from independently reconstructed contract")
    return {"status": "passed", "videos": len(rows), "selectionSha256": hashlib.sha256(selection_raw).hexdigest(),
            "excludedVideos": len(recovered), "scope": "metadata selection only"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--selection", type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.contract, args.selection)))
        return 0
    except (ValueError, KeyError, TypeError, OSError, AttributeError) as error:
        print(f"verification error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
