#!/usr/bin/env python3
"""Android realtime-SR device probe (stdlib only, commit-free).

Evidence is intentionally separated: host (local fixture + build), install
(adb install result), device (run-as / on-device checks) and display
(device-reported only; never inferred from a baseline). Originals are never
modified: no pm clear, no recorder/input controls, no credential or provider
actions, no device restart on failure.
"""
import argparse
import hashlib
import json
import os
import platform
import shlex
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

PKG = "io.ondeviceai.realtimesr.demo"
COMP = PKG + "/io.ondeviceai.realtimesr.SrRunActivity"  # debug-only exported run activity
KNOWN_SHA = "df677798a8604a7a6289fcf50a0fef3844a047631e0041ae7d5563c55f3746ec"
FIXTURE_PROFILES = {
    KNOWN_SHA: "1920x1080",
    "e698bee8c98c3158eb5e99e26f1cbe13334b52557720b2129a3e31dd2fa67dcb": "960x540",
}
CASES = ("original", "original-egl", "cunny", "auto", "short-abba", "short-spatial")
JAVA17 = "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
SHORT_T, POLL = 240, 2


def now():
    return datetime.now(timezone.utc).isoformat()


def sha_bytes(b):
    return hashlib.sha256(b).hexdigest()


def sha_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for c in iter(lambda: f.read(1 << 20), b""):
            h.update(c)
    return h.hexdigest()


def atomic(path, data):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, str):
        data = data.encode()
    t = p.with_name(p.name + ".tmp")
    t.write_bytes(data)
    os.replace(t, p)


def cmd(argv, *, input=None, cwd=None, env=None, timeout=None, check=True,
        stderr=subprocess.STDOUT):
    cp = subprocess.run(argv, input=input, stdout=subprocess.PIPE, stderr=stderr,
                        cwd=cwd, env=env, timeout=timeout)
    out = cp.stdout.decode("utf-8", "replace") if cp.stdout else ""
    if check and cp.returncode != 0:
        raise RuntimeError("cmd failed (%d): %s\n%s" %
                           (cp.returncode, shlex.join(argv), out[-2000:]))
    return cp


class Adb:
    def __init__(self, serial):
        self.base = ["adb", "-s", serial]

    def raw(self, *args, **kw):
        return cmd(self.base + list(args), **kw)

    def sh(self, *args, **kw):
        cp = self.raw("shell", *args, **kw)
        return cp.returncode, cp.stdout.decode("utf-8", "replace") if cp.stdout else ""

    def state(self):
        cp = self.raw("get-state", check=False)
        return cp.stdout.decode().strip() if cp.stdout else ""

    def exec_out(self, *args, **kw):
        kw.setdefault("check", False)
        kw["stderr"] = subprocess.DEVNULL
        return self.raw("exec-out", *args, **kw)


def push_fixture(adb, remote, data):
    # adb flattens argv into one device-shell string; quote the -c script.
    script = "cat > " + remote
    q = "'" + script.replace("'", "'\\''") + "'"
    p = subprocess.Popen(
        adb.base + ["shell", "run-as", PKG, "sh", "-c", q],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    out, _ = p.communicate(data)
    if p.returncode != 0:
        raise RuntimeError("fixture push failed: " + out.decode("utf-8", "replace")[-1000:])


def timeout_for(case):
    return SHORT_T


def wait_terminal(adb, path, timeout):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if adb.state() != "device":
            return "disconnected", None
        cp = adb.exec_out("run-as", PKG, "cat", path)
        if cp.returncode == 0 and cp.stdout:
            try:
                return "ok", json.loads(cp.stdout)
            except ValueError:
                pass
        time.sleep(POLL)
    return "timeout", None


def count_data(run_b, evt_b):
    d = {}
    if run_b:
        try:
            j = json.loads(run_b)
            if isinstance(j, dict):
                for k in ("frames", "frameCount", "processedFrames",
                          "outputFrames", "framesProcessed"):
                    if k in j:
                        d[k] = j[k]
        except ValueError:
            pass
    if evt_b:
        n = len([l for l in evt_b.decode("utf-8", "replace").splitlines() if l.strip()])
        if n:
            d["events"] = n
    return d or None


def required_for(case):
    return {"terminal.json", "run.json", "events.jsonl"}


def validate_decode(term, run, events_b, rid, launch_case, expected_sha=KNOWN_SHA):
    def fail(message):
        return message

    if expected_sha not in FIXTURE_PROFILES:
        return fail('unknown expected fixtureSha256')
    if not isinstance(term, dict) or not isinstance(run, dict):
        return fail('terminal and run.json must be JSON objects')
    if term.get('runId') != rid or run.get('runId') != rid:
        return fail('runId mismatch')
    if type(run.get('schemaVersion')) is not int or run['schemaVersion'] != 1:
        return fail('invalid schemaVersion')
    if type(run.get('generation')) is not int or run['generation'] < 0:
        return fail('invalid generation')
    if type(run.get('loops')) is not int or run['loops'] != 1:
        return fail('invalid loops')
    expected_mode = 'AUTO' if launch_case == 'auto' else ('SPATIAL' if launch_case == 'spatial' else ('CUNNY' if launch_case == 'cunny' else 'ORIGINAL'))
    if launch_case not in ('original', 'original-egl', 'cunny', 'auto', 'spatial') or run.get('mode') != expected_mode:
        return fail('mode does not match launch case')
    if type(run.get('completed')) is not bool or not run['completed']:
        return fail('run is not complete')
    if run.get('fixtureSha256') != expected_sha:
        return fail('invalid fixtureSha256')

    if not isinstance(term.get('case'), str) or term['case'] != launch_case:
        return fail('terminal case mismatch')
    if term.get('status') != 'completed':
        return fail('invalid status')
    if type(term.get('frameCallbacks')) is not int or term['frameCallbacks'] != 900:
        return fail('invalid frameCallbacks')
    if type(term.get('lastAudioUs')) is not int or not 29000000 <= term['lastAudioUs'] <= 31000000:
        return fail('invalid lastAudioUs')
    if not isinstance(term.get('detail'), str):
        return fail('invalid terminal detail')
    if type(term.get('presentationSupported')) is not bool:
        return fail('invalid presentationSupported')

    try:
        raw = events_b.decode('utf-8') if isinstance(events_b, bytes) else events_b
        if not isinstance(raw, str):
            return fail('events must be UTF-8 bytes or text')
        rows = [json.loads(line) for line in raw.splitlines() if line.strip()]
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        return fail(f'invalid events JSONL: {exc}')
    if not all(isinstance(row, dict) for row in rows):
        return fail('event rows must be JSON objects')

    decoded = [row for row in rows if row.get('stage') == 'decodeRelease']
    if len(decoded) != 900:
        return fail(f'expected 900 decodeRelease rows, got {len(decoded)}')
    for ordinal, row in enumerate(decoded):
        if row.get('runId') != rid:
            return fail('decodeRelease runId mismatch')
        if type(row.get('generation')) is not int or row['generation'] != run['generation']:
            return fail('decodeRelease generation mismatch')
        if type(row.get('loop')) is not int or row['loop'] != 0:
            return fail('decodeRelease loop mismatch')
        if type(row.get('sourceOrdinal')) is not int or row['sourceOrdinal'] != ordinal:
            return fail('decodeRelease sourceOrdinal must be chronological 0..899')

        pts_us = row.get('ptsUs')
        if type(pts_us) is not int:
            return fail('invalid decodeRelease ptsUs')
        numerator = ordinal * 1000000
        floor_pts = numerator // 30
        ceil_pts = (numerator + 29) // 30
        if not floor_pts - 1 <= pts_us <= ceil_pts + 1:
            return fail(f'invalid decodeRelease ptsUs for sourceOrdinal {ordinal}')

        if type(row.get('targetNs')) is not int or row['targetNs'] < 0:
            return fail('invalid decodeRelease targetNs')

    return None


def device_sha(adb, remote):
    _, out = adb.sh("run-as", PKG, "sha256sum", remote, check=False)
    tok = out.split()
    return tok[0] if tok else ""


def collect_device(adb):
    d = {}
    for k in ("ro.product.model", "ro.product.device", "ro.hardware",
              "ro.board.platform", "ro.build.version.sdk"):
        _, v = adb.sh("getprop", k, check=False)
        d[k] = v.strip()
    _, wm = adb.sh("wm", "size", check=False)
    d["wm_size"] = wm.strip()
    _, disp = adb.sh("dumpsys", "display", check=False)
    d["display_head"] = "\n".join(disp.splitlines()[:6])
    _, power = adb.sh("dumpsys", "power", check=False)
    d["wakefulness"] = next((x.strip() for x in power.splitlines()
                             if "mWakefulness=" in x), "")
    d["display_evidence"] = "device-reported only; no physical display inference from baseline"
    return d


def one_run(adb, outroot, rid, case, launch_case, timeout, fx, expected_sha):
    rec = {"id": rid, "case": case, "launch_case": launch_case,
           "fixture_sha256": expected_sha, "fixture_profile": FIXTURE_PROFILES[expected_sha], "artifacts": {},
           "decode_completion": False, "display_gate": "not_assessed"}
    remote = "files/sr-runs/" + rid
    rc, _ = adb.sh("run-as", PKG, "mkdir", "-p", remote, check=False)
    if rc:
        rec.update(status="failed", detail="could not create device run directory")
        return rec
    push_fixture(adb, remote + "/fixture.mp4", fx)
    dsha = device_sha(adb, remote + "/fixture.mp4")
    rec["device_fixture_sha"] = dsha
    if dsha != expected_sha:
        rec.update(status="failed", detail="device fixture sha mismatch")
        return rec
    started = time.monotonic()
    adb.sh("am", "start", "-n", COMP,
           "--es", "runId", rid, "--es", "srCase", launch_case)
    how, term = wait_terminal(adb, remote + "/terminal.json", timeout)
    rec["launchToTerminalObservationSeconds"] = round(time.monotonic() - started, 3)
    rec["wait"] = how
    rec["terminal"] = term
    local = outroot / rid
    local.mkdir(parents=True, exist_ok=True)
    blobs = {}
    retrieval_errors = []
    for name in ("terminal.json", "run.json", "events.jsonl"):
        cp = adb.exec_out("run-as", PKG, "cat", remote + "/" + name)
        if cp.returncode != 0 or not cp.stdout:
            retrieval_errors.append(name)
            continue
        blobs[name] = cp.stdout
        atomic(local / name, cp.stdout)
        rec["artifacts"][name] = sha_bytes(cp.stdout)
    rec["missing_required"] = sorted(required_for(case) - set(blobs))
    if retrieval_errors:
        rec["artifact_errors"] = retrieval_errors
    if how != "ok":
        rec.update(status="failed", detail="wait " + how)
        return rec
    if not isinstance(term, dict):
        rec.update(status="failed", detail="terminal is not a JSON object")
        return rec
    if rec["missing_required"] or retrieval_errors:
        rec.update(status="failed", detail="required device artifacts missing or unreadable")
        return rec
    run = json.loads(blobs['run.json'])
    problem = validate_decode(term, run, blobs['events.jsonl'], rid, launch_case, expected_sha=expected_sha)
    if problem:
        rec['decodeError'] = problem
    try:
        report_script = Path(__file__).with_name('android_realtime_sr_report.py')
        assessed = subprocess.run(
            [sys.executable, str(report_script), 'assess', '--index', str(outroot / 'index.json'), '--run', str(local), '--out', str(local / 'result.json')],
            capture_output=True, text=True, check=False,
        )
        rec['assessmentReturnCode'] = assessed.returncode
        if assessed.returncode != 0:
            rec['assessmentError'] = assessed.stderr.strip() or assessed.stdout.strip() or f'assess exited {assessed.returncode}'
    except Exception as exc:
        rec['assessmentError'] = str(exc)
    rec['decode_completion'] = problem is None
    rec['status'] = 'success' if (
        rec['decode_completion']
        and not rec.get('assessmentError')
        and rec.get('assessmentReturnCode') == 0
    ) else 'failed'
    if rec['status'] == 'success':
        try:
            gates = json.loads((local / 'result.json').read_text())['gates']
            rec['gates'] = gates
            rec['display_gate'] = gates['endToEnd']
        except (OSError, json.JSONDecodeError, KeyError, TypeError) as error:
            rec['status'] = 'failed'
            rec['resultError'] = str(error)
    return rec


def legs_for(case):
    if case == "short-spatial":
        return [("original-egl", "original-egl"), ("spatial", "spatial"),
                ("cunny", "cunny"), ("spatial", "spatial"),
                ("original-egl", "original-egl")]
    if case == "short-abba":
        return [("original-egl", "original-egl"), ("cunny", "cunny"),
                ("cunny", "cunny"), ("original-egl", "original-egl")]
    return [(case, case)]


def main():
    ap = argparse.ArgumentParser(description="Android realtime-SR device probe")
    ap.add_argument("--serial", required=True)
    ap.add_argument("--fixture", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--case", required=True)
    ap.add_argument("--build", action="store_true", help="opt in to assembling the debug APK")
    ap.add_argument("--apk", type=Path, help="use this APK instead of the project debug APK")
    ap.add_argument("--project", default=".", help="gradle project root")
    a = ap.parse_args()
    if a.apk is not None and a.build:
        ap.error("--apk cannot be used with --build")
    if a.case not in CASES:
        print("case %r is unimplemented; supported cases: %s" %
              (a.case, ", ".join(CASES)), file=sys.stderr)
        return 2

    outroot = Path(a.out).resolve()
    if outroot.exists() and (not outroot.is_dir() or any(outroot.iterdir())):
        print(f'Error: --out destination already exists and is not empty: {outroot}', file=sys.stderr)
        return 2
    outroot.mkdir(parents=True, exist_ok=True)
    man = {"started": now(), "serial": a.serial, "case": a.case,
           "host": {}, "build": {}, "install": {}, "device": {}, "runs": []}
    try:
        fx_path = Path(a.fixture).resolve()
        fx = fx_path.read_bytes()
        hs = sha_bytes(fx)
        man["host"] = {"fixture": str(fx_path), "sha256": hs,
                       "known_sha256s": list(FIXTURE_PROFILES), "match": hs in FIXTURE_PROFILES,
                       "fixture_profile": FIXTURE_PROFILES.get(hs),
                       "python": platform.python_version(), "os": platform.platform()}
        if hs not in FIXTURE_PROFILES:
            raise RuntimeError("fixture sha mismatch; refusing to touch device")

        env = os.environ.copy()
        if (platform.system() == "Darwin" and not env.get("JAVA_HOME")
                and Path(JAVA17).exists()):
            env["JAVA_HOME"] = JAVA17
        root = Path(a.project).resolve()
        gw = str(root / "gradlew")
        if a.build:
            cmd([gw, "-PenableRealtimeSrDemo=true",
                 ":examples:android-realtime-sr:assembleDebug"],
                cwd=str(root), env=env)
        if a.apk is not None:
            apk = a.apk.resolve()
        else:
            apk_dir = root / "examples/android-realtime-sr/build/outputs/apk/debug"
            apks = sorted(apk_dir.glob("*.apk"))
            if len(apks) != 1:
                raise RuntimeError("expected exactly one debug apk, found %d" % len(apks))
            apk = apks[0]
        badging = cmd(["aapt", "dump", "badging", str(apk)])
        expected_badging = "package: name='%s'" % PKG
        if expected_badging not in badging.stdout.decode("utf-8", "replace"):
            raise RuntimeError("APK package name is not %s" % PKG)
        man["build"] = {"gradlew": gw, "apk": str(apk), "sha256": sha_file(apk),
                        "java_home": env.get("JAVA_HOME", ""),
                        "package": PKG, "build_opt_in": a.build}

        adb = Adb(a.serial)
        if adb.state() != "device":
            raise RuntimeError("device not connected: " + adb.state())
        man["device"] = collect_device(adb)
        cp = adb.raw("install", "-r", "-t", str(apk), check=False)
        ins = cp.stdout.decode("utf-8", "replace") if cp.stdout else ""
        man["install"] = {"rc": cp.returncode, "output": ins[-2000:],
                          "success": cp.returncode == 0 and "Success" in ins.splitlines()}
        if not man["install"]["success"]:
            raise RuntimeError("install failed")
        pm_rc, pm = adb.sh("pm", "path", PKG, check=False)
        man["install"]["pm_path"] = pm.strip()
        if pm_rc or not pm.startswith("package:"):
            raise RuntimeError("installed package not found")

        subprocess.run([sys.executable, str(Path(__file__).with_name('android_realtime_sr_report.py')), 'index', '--fixture', str(fx_path), '--out', str(outroot / 'index.json')], check=True)
        per_leg_timeout = SHORT_T if a.case == "short-abba" else timeout_for(a.case)
        for label, launch in legs_for(a.case):
            rid = uuid.uuid4().hex
            rec = one_run(adb, outroot, rid, label, launch, per_leg_timeout, fx, hs)
            man["runs"].append(rec)
            atomic(outroot / "manifest.json", json.dumps(man, indent=2))
        man["status"] = ("success" if man["runs"] and
                         all(r["status"] == "success" for r in man["runs"])
                         else "partial" if man["runs"] else "unverified")
    except Exception as e:
        man["status"] = "error"
        man["error"] = str(e)
    man["finished"] = now()
    atomic(outroot / "manifest.json", json.dumps(man, indent=2))
    print("status=%s out=%s" % (man.get("status"), outroot), file=sys.stderr)
    return 0 if man.get("status") == "success" else 1


if __name__ == "__main__":
    sys.exit(main())
