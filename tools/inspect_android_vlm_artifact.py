#!/usr/bin/env python3
"""Read-only validation and inventory for an Android VLM AAR artifact."""

import argparse
import hashlib
import json
import re
import stat
import subprocess
import sys
import tempfile
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path


def _safe_member(name):
    normalized = name.replace("\\", "/")
    return not (
        normalized.startswith("/")
        or normalized.startswith("../")
        or "/../" in normalized
        or normalized == ".."
        or any(part == ".." for part in normalized.split("/"))
    )


def _is_symlink(member):
    mode = (member.external_attr >> 16) & 0o170000
    return mode == stat.S_IFLNK


ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def _sdk_report(aapt2, manifest_bytes):
    report = {
        "min_sdk": "unverified",
        "target_sdk": "unverified",
        "status": "unverified",
        "manifest_format": "unverified",
    }
    if manifest_bytes is None:
        report.update(status="unavailable", error="AndroidManifest.xml is missing")
        return report

    # AARs published by Android Gradle tooling commonly carry a text XML
    # manifest.  Parse it directly rather than wrapping it for aapt2, whose
    # dump badging input expects binary AXML.
    if manifest_bytes.lstrip().startswith(b"<"):
        try:
            root = ET.fromstring(manifest_bytes)
            uses_sdk = root.find("uses-sdk")
            if uses_sdk is not None:
                minimum = uses_sdk.get(f"{ANDROID_NS}minSdkVersion")
                target = uses_sdk.get(f"{ANDROID_NS}targetSdkVersion")
                if minimum is not None:
                    report["min_sdk"] = _number_or_text(minimum)
                if target is not None:
                    report["target_sdk"] = _number_or_text(target)
            report["manifest_format"] = "text-xml"
            report["status"] = (
                "verified"
                if report["min_sdk"] != "unverified" and report["target_sdk"] != "unverified"
                else "partial"
                if report["min_sdk"] != "unverified" or report["target_sdk"] != "unverified"
                else "unverified"
            )
            return report
        except ET.ParseError as exc:
            report.update(status="unavailable", manifest_format="text-xml", error=str(exc))
            return report

    if not aapt2:
        return report
    # aapt2 dump badging accepts an APK-like zip, not an AAR directly.  A
    # temporary wrapper containing only the library's binary manifest keeps
    # this inspection read-only and avoids treating an AAR as an app package.
    with tempfile.TemporaryDirectory(prefix="android-vlm-aapt2-") as directory:
        wrapper = Path(directory) / "manifest.apk"
        with zipfile.ZipFile(wrapper, "w") as archive:
            archive.writestr("AndroidManifest.xml", manifest_bytes)
        try:
            completed = subprocess.run(
                [aapt2, "dump", "badging", str(wrapper)],
                capture_output=True,
                text=True,
                check=False,
            )
        except (OSError, ValueError) as exc:
            report.update(status="unavailable", error=str(exc))
            return report
        if completed.returncode != 0:
            report.update(status="unavailable", error=(completed.stderr or completed.stdout).strip())
            return report
        output = completed.stdout + "\n" + completed.stderr
    minimum = re.search(r"\b(?:minSdkVersion|sdkVersion):'([^']+)'", output)
    target = re.search(r"\btargetSdkVersion:'([^']+)'", output)
    if minimum:
        report["min_sdk"] = _number_or_text(minimum.group(1))
    if target:
        report["target_sdk"] = _number_or_text(target.group(1))
    report["manifest_format"] = "binary-axml"
    report["status"] = (
        "verified"
        if minimum and target
        else "partial"
        if minimum or target
        else "unverified"
    )
    return report


def _number_or_text(value):
    try:
        return int(value)
    except ValueError:
        return value


def _sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inspect(aar, aapt2=None):
    path = Path(aar)
    result = {
        "artifact": str(path),
        "status": "failed",
        "size": None,
        "sha256": None,
        "path_safety": {"unsafe": False, "unsafe_paths": []},
        "required": {"manifest": False, "classes_jar": False},
        "jni_abis": [],
        "meta_inf": {"licenses": [], "notices": []},
        "license_files": [],
        "notice_files": [],
        "sdk": {"min_sdk": "unverified", "target_sdk": "unverified", "status": "unverified"},
        "errors": [],
    }
    if not path.is_file():
        result["errors"].append(f"AAR not found: {path}")
        return result
    result["size"] = path.stat().st_size
    result["sha256"] = _sha256(path)
    manifest_bytes = None
    try:
        with zipfile.ZipFile(path) as archive:
            members = archive.infolist()
            names = [member.filename for member in members]
            unsafe = [member.filename for member in members if not _safe_member(member.filename) or _is_symlink(member)]
            result["path_safety"] = {"unsafe": bool(unsafe), "unsafe_paths": sorted(unsafe)}
            result["required"] = {
                "manifest": "AndroidManifest.xml" in names,
                "classes_jar": "classes.jar" in names,
            }
            if result["required"]["manifest"]:
                manifest_bytes = archive.read("AndroidManifest.xml")
            if not result["path_safety"]["unsafe"]:
                for member in members:
                    name = member.filename.replace("\\", "/")
                    if name.startswith("jni/"):
                        parts = name.split("/")
                        if len(parts) > 2 and parts[1] and parts[2]:
                            result["jni_abis"].append(parts[1])
                    if name.upper().startswith("META-INF/"):
                        leaf = name.rsplit("/", 1)[-1].lower()
                        if "license" in leaf:
                            result["meta_inf"]["licenses"].append(member.filename)
                        if "notice" in leaf:
                            result["meta_inf"]["notices"].append(member.filename)
                    leaf = name.rsplit("/", 1)[-1].lower()
                    if "license" in leaf:
                        result["license_files"].append(member.filename)
                    if "notice" in leaf:
                        result["notice_files"].append(member.filename)
            if not result["required"]["manifest"]:
                result["errors"].append("missing AndroidManifest.xml")
            if not result["required"]["classes_jar"]:
                result["errors"].append("missing classes.jar")
            if result["path_safety"]["unsafe"]:
                result["errors"].append("unsafe ZIP path(s)")
    except (zipfile.BadZipFile, OSError) as exc:
        result["errors"].append(f"invalid AAR ZIP: {exc}")
        return result
    result["jni_abis"] = sorted(set(result["jni_abis"]))
    result["meta_inf"]["licenses"] = sorted(set(result["meta_inf"]["licenses"]))
    result["meta_inf"]["notices"] = sorted(set(result["meta_inf"]["notices"]))
    result["license_files"] = sorted(set(result["license_files"]))
    result["notice_files"] = sorted(set(result["notice_files"]))
    result["sdk"] = _sdk_report(aapt2, manifest_bytes)
    result["status"] = "passed" if not result["errors"] else "failed"
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aar", type=Path, help="path to the AAR file")
    parser.add_argument("--aapt2", help="aapt2 executable used for dump badging")
    parser.add_argument("--json", action="store_true", help="emit a JSON report")
    args = parser.parse_args(argv)
    report = inspect(args.aar, args.aapt2)
    if args.json:
        print(json.dumps(report, sort_keys=True, indent=2))
    else:
        print(f"AAR: {report['artifact']}")
        print(f"status={report['status']} size={report['size']} sha256={report['sha256']}")
        print(f"manifest={report['required']['manifest']} classes.jar={report['required']['classes_jar']}")
        print(f"jni_abis={','.join(report['jni_abis']) or 'none'}")
        print(f"minSdk={report['sdk']['min_sdk']} targetSdk={report['sdk']['target_sdk']}")
        for error in report["errors"]:
            print(f"ERROR: {error}", file=sys.stderr)
    return 1 if report["errors"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
