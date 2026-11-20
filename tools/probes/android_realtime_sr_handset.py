import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

from tools.probes.android_realtime_sr_device import FIXTURE_PROFILES, PKG, sha_file
from tools.probes.android_google_sr_device import PROBE_OK


def save(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def run(argv, timeout=20):
    return subprocess.run(argv, capture_output=True, text=True, check=False, timeout=timeout)


def assess_legs(runs, expected_sequence):
    if not isinstance(runs, list) or len(runs) != len(expected_sequence):
        raise RuntimeError("probe report is incomplete")
    evidence = []
    ids = set()
    failed = False
    unknown = False
    for item, leg in zip(runs, expected_sequence):
        if not isinstance(item, dict):
            raise RuntimeError("probe run shape is invalid")
        run_id = item.get("id")
        if (item.get("launch_case") != leg or item.get("status") != "success"
                or item.get("decode_completion") is not True
                or not isinstance(run_id, str) or not run_id.strip()
                or run_id in ids):
            raise RuntimeError("probe run shape or completion is invalid")
        ids.add(run_id)
        gates = item.get("gates")
        if not isinstance(gates, dict):
            raise RuntimeError("probe run gates shape is invalid")
        gate_evidence = {}
        for gate in ("gpu", "endToEnd", "missed", "srDelivery"):
            value = gates.get(gate)
            gate_evidence[gate] = value
            if value == "fail":
                failed = True
            if leg == "original-egl" and gate in ("gpu", "srDelivery"):
                if value != "notApplicable":
                    unknown = True
            elif value != "pass" and value != "fail":
                unknown = True
        evidence.append({"id": run_id, "leg": leg, "gates": gate_evidence})
    gate = "fail" if failed else "unverified" if unknown else "pass"
    return gate, evidence


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--fixture", required=True)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--test-apk", required=True)
    parser.add_argument("--google-apk")
    parser.add_argument("--out", required=True)
    parser.add_argument("--preflight-only", action="store_true")
    args = parser.parse_args()

    out = Path(args.out).expanduser().resolve()
    if out.exists():
        parser.error("--out must be a new path")
    out.mkdir(parents=True)
    summary = {"status": "preflight-failed", "preflight": {}, "probes": {},
               "long_run": "not run", "shortGate": "unverified"}
    report = out / "summary.json"

    def finish(code):
        save(report, summary)
        print(f"Summary: {report} (status: {summary['status']})")
        return code

    try:
        paths = {
            "fixture": Path(args.fixture).expanduser().resolve(strict=True),
            "apk": Path(args.apk).expanduser().resolve(strict=True),
            "test_apk": Path(args.test_apk).expanduser().resolve(strict=True),
        }
        if args.google_apk:
            paths["google_apk"] = Path(args.google_apk).expanduser().resolve(strict=True)

        for tool in ("adb", "aapt", "ffmpeg", "ffprobe"):
            if not shutil.which(tool):
                raise RuntimeError(f"required tool unavailable: {tool}")

        fixture_hash = sha_file(paths["fixture"])
        if fixture_hash.lower() not in FIXTURE_PROFILES:
            raise RuntimeError("fixture hash is not in FIXTURE_PROFILES")
        fixture_profile = FIXTURE_PROFILES[fixture_hash.lower()]

        expected = {
            "apk": PKG,
            "test_apk": PKG + ".test",
            "google_apk": PKG + ".google",
        }
        hashes = {"fixture": fixture_hash}
        for name in ("apk", "test_apk", "google_apk"):
            if name not in paths:
                continue
            hashes[name] = sha_file(paths[name])
            result = run(["aapt", "dump", "badging", str(paths[name])])
            if result.returncode:
                raise RuntimeError(f"aapt badging failed for {name} (exit {result.returncode})")
            match = re.search(r"^package: name='([^']+)'", result.stdout, re.M)
            if not match or match.group(1) != expected[name]:
                raise RuntimeError(f"unexpected APK package for {name}")

        summary["preflight"] = {"status": "running", "serial": args.serial, "sha256": hashes,
                                "fixture_profile": fixture_profile}

        def adb(*argv):
            result = run(["adb", "-s", args.serial, *argv])
            if result.returncode:
                raise RuntimeError(f"adb command failed: {argv[0]}")
            return result.stdout.strip()

        if args.serial.lower().startswith("emulator-"):
            raise RuntimeError("emulator serial is not a physical target")
        state_result = run(["adb", "-s", args.serial, "get-state"])
        if state_result.returncode:
            stderr = state_result.stderr.lower()
            reasons = [word for word in ("offline", "unauthorized", "not found")
                       if word in stderr]
            detail = ", ".join(reasons) if reasons else "unavailable"
            raise RuntimeError(f"adb get-state returned {detail}")
        if state_result.stdout.strip() != "device":
            raise RuntimeError("selected serial is not in device state")

        props = {}
        for prop in (
            "ro.build.version.sdk", "ro.product.manufacturer", "ro.product.model",
            "ro.hardware", "ro.opengles.version", "ro.hardware.egl",
            "ro.kernel.qemu", "ro.boot.qemu",
        ):
            props[prop] = adb("shell", "getprop", prop)

        api = int(props["ro.build.version.sdk"] or "0")
        if api < 26:
            raise RuntimeError("Android API level must be at least 26")
        hardware = props["ro.hardware"].lower()
        if any(token in hardware for token in ("ranchu", "goldfish")):
            raise RuntimeError("emulator hardware detected")
        if props["ro.kernel.qemu"] == "1" or props["ro.boot.qemu"] == "1":
            raise RuntimeError("emulator qemu property detected")
        if not props["ro.product.model"] or not props["ro.hardware"]:
            raise RuntimeError("device model and hardware properties must be nonempty")

        sf = adb("shell", "dumpsys", "SurfaceFlinger")
        gles = next((line.strip() for line in sf.splitlines()
                     if line.strip().startswith("GLES:")), None)
        size = adb("shell", "wm", "size")
        gms_result = run(["adb", "-s", args.serial, "shell", "pm", "path",
                          "com.google.android.gms"])
        if gms_result.returncode == 0 and any(line.startswith("package:") and line[len("package:"):].strip() for line in gms_result.stdout.splitlines()):
            gms = True
        elif (gms_result.returncode in (0, 1) and not gms_result.stdout
              and not gms_result.stderr):
            gms = False
        else:
            gms = None
        summary["preflight"] = {
            "status": "success", "serial": args.serial, "api": api, "manufacturer": props["ro.product.manufacturer"],
            "model": props["ro.product.model"], "hardware": props["ro.hardware"],
            "gles_properties": {
                "ro.opengles.version": props["ro.opengles.version"],
                "ro.hardware.egl": props["ro.hardware.egl"],
            },
            "surfaceflinger_gles": gles, "gpu_evidence_available": bool(gles),
            "wm_size": size, "gms_present": gms, "sha256": hashes,
            "fixture_profile": fixture_profile,
        }
        if args.preflight_only:
            summary["status"] = "preflight-success"
            summary["performance_conclusion"] = "not assessed"
            return finish(0)

        collected_ids = set()
        probe_gates = []
        for probe_name, case, expected_sequence in (
            ("abba", "short-abba",
             ["original-egl", "cunny", "cunny", "original-egl"]),
            ("auto", "auto", ["auto"]),
        ):
            probe_dir = out / probe_name
            result = run([
                sys.executable, "-m", "tools.probes.android_realtime_sr_device",
                "--serial", args.serial, "--fixture", str(paths["fixture"]),
                "--apk", str(paths["apk"]), "--case", case, "--out", str(probe_dir),
            ], timeout=None)
            manifest_path = probe_dir / "manifest.json"
            if result.returncode or not manifest_path.is_file():
                raise RuntimeError(f"{case} probe failed (exit {result.returncode})")
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            if not isinstance(manifest, dict) or manifest.get("status") != "success":
                raise RuntimeError(f"{case} report is incomplete")
            host = manifest.get("host")
            build = manifest.get("build")
            if (manifest.get("serial") != args.serial or not isinstance(host, dict)
                    or host.get("sha256") != hashes["fixture"]
                    or not isinstance(build, dict) or build.get("sha256") != hashes["apk"]):
                raise RuntimeError(f"{case} report does not match selected assets")
            gate, evidence = assess_legs(manifest.get("runs"), expected_sequence)
            for run_evidence in evidence:
                if run_evidence["id"] in collected_ids:
                    raise RuntimeError("probe run IDs are not unique")
                collected_ids.add(run_evidence["id"])
            summary["probes"][probe_name] = {
                "artifact": str(manifest_path), "shortGate": gate, "legs": evidence,
            }
            probe_gates.append(gate)

        summary["shortGate"] = (
            "fail" if "fail" in probe_gates
            else "unverified" if "unverified" in probe_gates
            else "pass"
        )

        paused = out / "paused"
        result = run([
            sys.executable, "-m", "tools.probes.android_realtime_sr_paused",
            "--serial", args.serial, "--fixture", str(paths["fixture"]),
            "--apk", str(paths["apk"]), "--test-apk", str(paths["test_apk"]),
            "--out", str(paused),
        ], timeout=None)
        host_path = paused / "host.json"
        if result.returncode or not host_path.is_file():
            raise RuntimeError(f"paused probe failed (exit {result.returncode})")
        host = json.loads(host_path.read_text(encoding="utf-8"))
        if (not isinstance(host, dict) or host.get("success") is not True
                or not isinstance(host.get("pixels"), dict) or not host.get("pixels")):
            raise RuntimeError("paused probe host report lacks success or pixels")
        summary["probes"]["paused"] = {"artifact": str(host_path), "status": "success",
                                       "pixels": host["pixels"]}

        if args.google_apk and api >= 30:
            google_path = out / "google.json"
            result = run([
                sys.executable, "-m", "tools.probes.android_google_sr_device",
                "--serial", args.serial, "--apk", str(paths["google_apk"]),
                "--out", str(google_path),
            ], timeout=None)
            if result.returncode or not google_path.is_file():
                raise RuntimeError(f"Google SR probe failed (exit {result.returncode})")
            google = json.loads(google_path.read_text(encoding="utf-8"))
            if not isinstance(google, dict):
                raise RuntimeError("Google SR report shape is invalid")
            if google.get("status") not in PROBE_OK or google.get("serial") != args.serial or google.get("apkSha256") != hashes["google_apk"]:
                raise RuntimeError("Google SR report does not match the expected probe, serial, and APK SHA-256")
            summary["probes"]["google"] = {
                "artifact": str(google_path), "status": google.get("status"),
                "video_upscale_confirmed": False,
            }
        else:
            summary["probes"]["google"] = {
                "status": "unassessed",
                "reason": "API below 30" if args.google_apk else "Google APK omitted",
            }

        summary["status"] = "complete"

        summary["performance_conclusion"] = "probe collection only; no long-run acceptance"
        return finish(0)
    except Exception as exc:
        if summary["preflight"].get("status") != "success":
            summary["preflight"]["status"] = "failed"
        summary["status"] = "error" if summary["preflight"].get("status") == "success" else "preflight-failed"
        summary["error"] = str(exc)
        return finish(1)


if __name__ == "__main__":
    raise SystemExit(main())
