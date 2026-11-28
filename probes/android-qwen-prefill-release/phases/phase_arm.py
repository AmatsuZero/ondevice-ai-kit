"""One independently authorized arm; the frozen host owns launch, sampling and stop."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import time
import traceback

if not __debug__:
    raise SystemExit("python -O is not permitted")

evidence = Path(sys.argv[1]).resolve()
repo = Path(__file__).resolve().parents[3]
artifacts = repo / ".omx/artifacts"
manifest_path = repo / "probes/android-qwen-prefill-release/phases/run-contract.json"
c = json.loads(manifest_path.read_bytes())
assert c["runId"] == "w3-station-phases-20261122-01"
assert evidence.parent == artifacts / c["runId"]
source = repo / c["phaseTrace"]["sourceRoot"]
root = evidence / "observation"
meta = json.loads((evidence / "preflight.json").read_bytes())
host = source / c["phaseTrace"]["hostDirectory"]
spec = importlib.util.spec_from_file_location("device_io", artifacts / "w3-window-notebook-20261122-01/run_once.py")
io = importlib.util.module_from_spec(spec)
spec.loader.exec_module(io)
io.serial = (evidence / "serial.txt").read_text().strip()
io.root = root
io.app = c["apks"]["app"]["package"]
io.rid = c["runId"]
io.actual = root / "host"
io.captured = root / "captured"

def identity():
    os.kill(int((evidence / "emulator.pid").read_text()), 0)
    assert io.run("emu", "avd", "name").decode().splitlines() == [meta["avdName"], "OK"]
    assert io.shell("getprop", "ro.kernel.qemu") == "1"

io.identity = identity

def fetch_phase_log():
    identity()
    return io.run('logcat', '-d', '-b', 'main', '-v', 'raw', 'W3Phase:I', '*:S')

def guard():
    assert hashlib.sha256(manifest_path.read_bytes()).hexdigest() == "898e85be15edd9bc80bf1bb388ec5bfbc44b7229c5cbbe2e0cd6991b2d609181"
    for n, wanted in c["hostInputHashes"].items():
        assert io.sha((host / n).read_bytes()) == wanted, n

def state():
    identity()
    hardware = {
        k: io.shell("getprop", k) for k in
        ("ro.build.fingerprint", "ro.build.version.sdk", "ro.product.cpu.abi", "ro.hardware")
    }
    hardware["serial"] = io.serial
    hardware["avd"] = meta["avdName"]
    hardware["pageSize"] = int(io.shell("getconf", "PAGE_SIZE"))
    hardware["cpuCount"] = int(io.shell("nproc"))
    mem = io.shell("cat", "/proc/meminfo")
    kb = {ln.split(":")[0]: int(ln.split()[1]) for ln in mem.splitlines() if ln.startswith(("MemTotal:", "MemAvailable:"))}
    hardware["memTotalBytes"] = kb["MemTotal"] * 1024
    hardware["avdConfigSha256"] = meta["avdConfigSha256"]
    hardware["emulatorPid"] = int((evidence / "emulator.pid").read_text())
    assert hardware["ro.build.version.sdk"] == "37" and hardware["ro.product.cpu.abi"] == "arm64-v8a" and hardware["pageSize"] == 16384
    thermal = io.shell("dumpsys", "thermalservice")
    assert "Thermal Status" in thermal or "Thermal status" in thermal, thermal[:200]
    return dict(hardware=hardware, availableBytes=kb["MemAvailable"]*1024, meminfo=mem,
                thermal=thermal, uptime=io.shell("cat", "/proc/uptime"))

def prepare_and_run():
    guard()
    root.mkdir()
    io.run("wait-for-device", timeout=60)
    for _ in range(60):
        if io.shell("getprop", "sys.boot_completed") == "1":
            break
        time.sleep(1)
    assert io.shell("getprop", "sys.boot_completed") == "1"
    pre = state()
    initial = set(io.packages())
    scoped = {r["package"] for r in c["apks"].values()}
    assert not initial.intersection(scoped | {"io.ondeviceai.demo", "io.ondeviceai.demo.test"})
    pre["dataFreeBytes"] = int(io.shell("df", "-k", "/data").splitlines()[-1].split()[3])*1024
    assert pre["dataFreeBytes"] > meta["totalModelBytes"] + 300_000_000
    io.record("device-before.json", pre)
    io.record("initial-packages.json", sorted(initial))
    owned = []
    try:
        installs = {}
        for row in c["apks"].values():
            apk = source / row["relativePath"]
            assert io.sha(apk.read_bytes()) == row["sha256"]
            assert io.run("install", str(apk), timeout=60).decode().strip().endswith("Success")
            owned.append(row["package"])
            pm_path, = io.shell("pm", "path", row["package"]).splitlines()
            assert pm_path.startswith("package:")
            installs[row["package"]] = io.shell("sha256sum", pm_path.removeprefix("package:")).split()[0]
            assert installs[row["package"]] == row["sha256"]
        io.record("installed.json", installs)
        component = f"instrumentation:{io.app}.test/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation (target={io.app})"
        assert component in io.shell("pm","list","instrumentation").splitlines()
        io.private("test","!","-e",io.model)
        io.private("mkdir","-p","files/w3-ack-tools",io.model+"/tokenizer")
        model_root = Path(json.loads((artifacts/"w3-model-reselection-20261110/model-manifest.json").read_bytes())["directory"])
        transfers = [io.upload(artifacts/"w3-decode-window-20261122-GXmgFj/rename_noreplace",io.helper,c["ackHelperSHA256"],"500")]
        io.private("test","-x",io.helper)
        for name, digest in c["models"].items():
            transfers.append(io.upload(model_root/name, io.model+"/"+name, digest, "400"))
        io.record("transfers.json", transfers)
        io.shell("input","keyevent","KEYCODE_WAKEUP")
        io.shell("wm","dismiss-keyguard")
        def launch_gate():
            prelaunch = state()
            assert prelaunch["availableBytes"] >= c["deviceAvailableStopBelowBytes"]
            assert prelaunch["hardware"] == pre["hardware"]
            guard()
            io.private("test","!","-e",io.terminal)
            assert io.private("sha256sum",io.helper).split()[0] == c["ackHelperSHA256"]
            io.record("before-launch.json", prelaunch)
        def stop():
            identity()
            io.shell("am","force-stop",io.app)
        sys.path.insert(0, str(host))
        from terminal_device_lifecycle import run_diagnostic
        sys.path.insert(0,str(manifest_path.parent))
        from phase_capture import capture
        io.actual.mkdir()
        io.captured.mkdir()
        def observe():
            return run_diagnostic(io.actual, adb=io.adb, serial=io.serial,
                       source_fingerprint=c["sourceFingerprint"],verify_launch=launch_gate,
                       before_transport=guard,stop_alias=stop)
        print("starting one diagnostic", flush=True)
        result = capture(root, fetch_phase_log, observe, io.collect)
        print(result['finish'],flush=True)
    finally:
        original = sys.exception()
        errors = []
        uninstalled = []
        identity_lost = False

        def _identity_check(where):
            try:
                identity()
            except Exception as exc:
                return False, 'identity check failed at %s: %r' % (where, exc)
            return True, ''

        for pkg in reversed(owned):
            ok, why = _identity_check('uninstall ' + str(pkg))
            if not ok:
                identity_lost = True
                errors.append(why)
                break
            try:
                status = io.run('uninstall', pkg).decode().strip()
            except Exception as exc:
                errors.append('uninstall %s raised: %r' % (pkg, exc))
                continue
            if status != 'Success':
                errors.append('uninstall %s bad status: %r' % (pkg, status))
                continue
            uninstalled.append(pkg)

        package_list_restored = False
        extra = missing = None
        if not identity_lost:
            ok, why = _identity_check('inventory')
            if not ok:
                identity_lost = True
                errors.append(why)
            else:
                try:
                    current = io.packages()
                except Exception as exc:
                    errors.append('packages() failed: %r' % (exc,))
                else:
                    before, after = set(initial), set(current)
                    extra = sorted(after - before)
                    missing = sorted(before - after)
                    package_list_restored = not extra and not missing
                    if not package_list_restored:
                        errors.append('package list not restored: extra=%r missing=%r'
                                      % (extra, missing))

        receipt = {
            'uninstalled': uninstalled,
            'packageListRestored': package_list_restored and not errors,
            'errors': list(errors),
        }
        if extra is not None:
            receipt['extra'] = extra
            receipt['missing'] = missing
        try:
            io.record('restored.json', receipt)
        except Exception as exc:
            errors.append('record failed: %r' % (exc,))

        if errors:
            if original is not None:
                for err in errors:
                    original.add_note(err)
            else:
                raise RuntimeError('; '.join(errors))

if __name__ == "__main__":
    try:
        prepare_and_run()
    except BaseException:
        traceback.print_exc()
        raise SystemExit(1)
