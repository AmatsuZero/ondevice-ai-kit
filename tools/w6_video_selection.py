#!/usr/bin/env python3
"""Select a fresh, deterministic Perception Test subset from SHA-bound metadata."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import sys

SEED = "w6-qwen35-4b-fresh36-v1"
CATEGORIES = ["counterfactual", "memory", "abstraction", "physics", "semantics", "camera_motion"]
POLICY = {"videos": 36, "categories": CATEGORIES, "videosPerCategory": 6,
          "answerLabelsPerCategory": {"A": 2, "B": 2, "C": 2}, "questionsPerVideo": 1,
          "maxDurationSeconds": 40, "maxResolutionPixels": 2073600,
          "maxArchiveMemberBytes": 128 * 2**20, "slots": [0, 1, 2, 0, 1, 2],
          "outcomeIndependent": True, "noSubstituteOnFailure": True}


def require(ok, message):
    if not ok:
        raise ValueError(message)


def pairs(values):
    result = {}
    for key, value in values:
        require(key not in result, "duplicate JSON key")
        result[key] = value
    return result


def parse(raw):
    return json.loads(raw, object_pairs_hook=pairs,
                      parse_constant=lambda x: (_ for _ in ()).throw(ValueError("nonfinite JSON")))


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def pinned(ref):
    require(isinstance(ref, dict) and set(ref) == {"path", "sha256"}, "invalid source reference")
    require(isinstance(ref["path"], str) and Path(ref["path"]).is_absolute(), "source path must be absolute")
    require(isinstance(ref["sha256"], str) and re.fullmatch(r"[0-9a-f]{64}", ref["sha256"]), "invalid SHA")
    raw = Path(ref["path"]).read_bytes()
    require(hashlib.sha256(raw).hexdigest() == ref["sha256"], "source SHA mismatch")
    return parse(raw)


def inputs(contract_path):
    raw = Path(contract_path).read_bytes()
    contract = parse(raw)
    require(set(contract) == {"schemaVersion", "sources"} and type(contract["schemaVersion"]) is int
            and contract["schemaVersion"] == 1, "unsupported contract schema")
    refs = contract["sources"]
    require(isinstance(refs, dict) and set(refs) == {"annotations", "cutMapping", "zipIndex", "exclusions"}, "invalid sources")
    values = {key: pinned(ref) for key, ref in refs.items()}
    exclusion = values["exclusions"]
    require(isinstance(exclusion, dict) and set(exclusion) == {"schemaVersion", "videoIds", "sources"}
            and type(exclusion["schemaVersion"]) is int and exclusion["schemaVersion"] == 1, "invalid exclusion schema")
    ids = exclusion["videoIds"]
    require(isinstance(ids, list) and all(isinstance(x, str) and re.fullmatch(r"video_\d+", x) for x in ids), "invalid exclusion IDs")
    require(ids == sorted(set(ids)), "exclusion IDs must be sorted and unique")
    require(isinstance(exclusion["sources"], list) and exclusion["sources"], "missing exclusion sources")
    union, seen = set(), set()
    for ref in exclusion["sources"]:
        require(isinstance(ref, dict) and set(ref) == {"path", "sha256", "rowKeys"}, "invalid history reference")
        require(ref["path"] not in seen, "duplicate history source")
        seen.add(ref["path"])
        history = pinned({key: ref[key] for key in ("path", "sha256")})
        keys = ref["rowKeys"]
        require(isinstance(keys, list) and keys and len(set(keys)) == len(keys)
                and all(key in ("items", "trainItems", "validItems") for key in keys), "invalid history row keys")
        for key in keys:
            rows = history.get(key) if isinstance(history, dict) else None
            require(isinstance(rows, list), "history rows must be an array")
            for row in rows:
                vid = row.get("videoId") if isinstance(row, dict) else None
                require(isinstance(vid, str) and re.fullmatch(r"video_\d+", vid), "invalid historical video")
                union.add(vid)
    require(union == set(ids), "exclusion union differs from supplied history")
    return contract, values, set(ids), hashlib.sha256(raw).hexdigest()


def category(question):
    if question["question"] == "Is the camera moving or static?":
        return "camera_motion"
    if question["area"] in ("memory", "abstraction"):
        return question["area"]
    return "counterfactual" if question["reasoning"] == "counterfactual" else question["area"]


def select(contract_path):
    contract, data, excluded, contract_sha = inputs(contract_path)
    annotations, cuts, index = (data[key] for key in ("annotations", "cutMapping", "zipIndex"))
    require(isinstance(annotations, dict) and isinstance(cuts, dict) and isinstance(index, dict), "invalid input documents")
    require(isinstance(index.get("members"), list), "invalid ZIP index")
    members = {}
    for row in index["members"]:
        require(isinstance(row, dict) and isinstance(row.get("name"), str), "invalid ZIP member")
        require(row["name"] not in members, "duplicate ZIP member")
        members[row["name"]] = row
    pools = {(cat, label): [] for cat in CATEGORIES for label in range(3)}
    for vid, video in annotations.items():
        require(isinstance(vid, str) and re.fullmatch(r"video_\d+", vid) and isinstance(video, dict), "invalid video")
        if vid in excluded:
            continue
        require(vid in cuts and type(cuts[vid]) is int and cuts[vid] >= -1, "missing or invalid cut mapping")
        member = members.get(f"videos/{vid}.mp4")
        if cuts[vid] != -1 or member is None:
            continue
        meta = video["metadata"]
        require(meta["video_id"] == vid, "video identity mismatch")
        frames, rate, resolution = meta["num_frames"], meta["frame_rate"], meta["resolution"]
        require(type(frames) is int and frames > 0 and type(rate) in (int, float)
                and math.isfinite(rate) and rate > 0, "invalid duration")
        require(isinstance(resolution, list) and len(resolution) == 2
                and all(type(n) is int and n > 0 for n in resolution), "invalid resolution")
        for key in ("bytes", "compressedBytes", "crc32", "method", "flags", "headerOffset"):
            require(type(member.get(key)) is int and member[key] >= 0, "invalid ZIP member metadata")
        duration = frames / rate
        if not (meta["split"] == "valid" and duration <= 40 and resolution[0] * resolution[1] <= 2073600
                and 0 < member["bytes"] <= 128 * 2**20 and member["compressedBytes"] > 0
                and member["method"] == 12 and member["flags"] == 0):
            continue
        questions = video["mc_question"]
        require(isinstance(questions, list), "invalid questions")
        question_ids = set()
        for q in questions:
            require(isinstance(q, dict) and type(q.get("id")) is int and q["id"] >= 0
                    and q["id"] not in question_ids, "invalid or duplicate question ID")
            question_ids.add(q["id"])
            require(type(q.get("answer_id")) is int and q["answer_id"] in range(3)
                    and isinstance(q.get("question"), str) and bool(q["question"])
                    and isinstance(q.get("options"), list) and len(q["options"]) == 3
                    and all(isinstance(x, str) and bool(x) for x in q["options"]), "invalid question or answer")
            require(q["area"] in ("memory", "abstraction", "physics", "semantics")
                    and q["reasoning"] in ("descriptive", "explanatory", "predictive", "counterfactual"), "unknown question category")
            cat, label = category(q), q["answer_id"]
            rank = hashlib.sha256(f'{SEED}|{cat}|{label}|{vid}|{q["id"]}'.encode()).hexdigest()
            pools[cat, label].append((rank, vid, q["id"], q, meta, member))
    for pool in pools.values():
        pool.sort(key=lambda row: row[:3])
    used, items = set(excluded), []
    for cat in CATEGORIES:
        for label in POLICY["slots"]:
            choice = next((row for row in pools[cat, label] if row[1] not in used), None)
            require(choice is not None, f"insufficient unique videos: {cat}/{label}")
            rank, vid, qid, q, meta, member = choice
            used.add(vid)
            items.append({"videoId": vid, "questionId": qid, "category": cat, "answerIndex": label,
                          "answerLetter": chr(65 + label), "question": q["question"], "options": q["options"],
                          "area": q["area"], "reasoning": q["reasoning"], "tags": q.get("tag", []),
                          "durationSeconds": meta["num_frames"] / meta["frame_rate"],
                          "numFrames": meta["num_frames"], "frameRate": meta["frame_rate"],
                          "resolution": meta["resolution"], "cutFrame": -1,
                          "archiveMember": member["name"], "archiveMemberBytes": member["bytes"],
                          "archiveCompressedBytes": member["compressedBytes"], "archiveCRC32": member["crc32"],
                          "archiveMethod": member["method"], "archiveFlags": member["flags"],
                          "archiveHeaderOffset": member["headerOffset"], "id": f"pt-fresh36-{vid}-q{qid}",
                          "index": len(items), "selectionRankSHA256": rank})
    return {"schemaVersion": 1, "dataset": "google-deepmind/perception_test", "split": "valid",
            "selectionSeed": SEED, "contractSha256": contract_sha, "policy": POLICY,
            "sourceHashes": {key: ref["sha256"] for key, ref in contract["sources"].items()},
            "excludedPreviousVideoIds": sorted(excluded),
            "archive": {key: index[key] for key in ("url", "archiveBytes", "etag", "generation")},
            "candidateCounts": {cat: {str(label): len(pools[cat, label]) for label in range(3)} for cat in CATEGORIES},
            "selectedCompressedBytes": sum(row["archiveCompressedBytes"] for row in items),
            "selectedRawBytes": sum(row["archiveMemberBytes"] for row in items), "items": items,
            "claimLimit": "Metadata-only selection, exclusion complete only within declared sources; not media, training-clean or product acceptance."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--contract", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        result = select(args.contract)
        args.output.mkdir(exist_ok=False)
        path = args.output / "selection.json"
        with path.open("x") as stream:
            json.dump(result, stream, ensure_ascii=False, indent=2, allow_nan=False)
            stream.write("\n")
        print(json.dumps({"status": "selected", "videos": 36, "selectionSha256": sha(path)}))
        return 0
    except (ValueError, KeyError, TypeError, OSError) as error:
        print(f"selection error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
