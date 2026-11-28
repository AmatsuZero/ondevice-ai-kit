"""Isolated W3 AVD phase: boot a dedicated fresh emulator, invoke once, clean up."""

import os
import re
import shutil
import socket
import subprocess
import tempfile
import time
from contextlib import contextmanager
from pathlib import Path

NAME = "W3TraceStation20261122"
SERIAL = "emulator-5594"
PORTS = (5594, 5595)

CONFIG = (
    f"AvdId={NAME}\n"
    f"avd.ini.displayname={NAME}\n"
    "abi.type=arm64-v8a\n"
    "hw.cpu.arch=arm64\n"
    "hw.cpu.ncore=4\n"
    "hw.ramSize=4096\n"
    "disk.dataPartition.size=4G\n"
    "hw.lcd.width=720\n"
    "hw.lcd.height=1280\n"
    "hw.lcd.density=320\n"
    "image.sysdir.1=system-images/android-37.2/google_apis_playstore_ps16k/arm64-v8a/\n"
    "tag.id=google_apis_playstore_ps16k\n"
    "tag.display=Google Play\n"
    "hw.keyboard=yes\n"
)

_SDK = Path.home() / "Library" / "Android" / "sdk"
_ADB = str(_SDK / "platform-tools" / "adb")
_EMULATOR = str(_SDK / "emulator" / "emulator")


def _adb(args, timeout=10):
    cp = subprocess.run([_ADB, *args], capture_output=True, text=True, timeout=timeout, check=True)
    return cp.stdout


def _serials(text):
    return {
        line.split("\t", 1)[0].strip()
        for line in text.splitlines()
        if "\t" in line and line.split("\t", 1)[0].strip()
    }


def _assert_available(before):
    if SERIAL in _serials(before):
        raise RuntimeError(f"{SERIAL} already present in device inventory")
    for port in PORTS:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            try:
                sock.bind(("127.0.0.1", port))
            except OSError as exc:
                raise RuntimeError(f"localhost port {port} not free") from exc


def _props():
    out = _adb(("-s", SERIAL, "shell", "getprop"), timeout=5)
    return dict(re.findall(r"^\[([^\]]+)\]: \[([^\]]*)\]$", out, re.M))


def _boot_ok(proc):
    if proc.poll() is not None:
        raise RuntimeError("emulator exited before boot completed")
    try:
        if _adb(("-s", SERIAL, "emu", "avd", "name"), timeout=5).splitlines() != [NAME, "OK"]:
            return False
        props = _props()
        page = ""
        for cmd in (("getconf", "PAGE_SIZE"), ("getconf", "PAGESIZE")):
            try:
                page = _adb(("-s", SERIAL, "shell", *cmd), timeout=5).strip()
            except (subprocess.TimeoutExpired, subprocess.CalledProcessError):
                page = ""
            if page:
                break
    except (subprocess.TimeoutExpired, subprocess.CalledProcessError):
        return False
    return (
        props.get("sys.boot_completed") == "1"
        and props.get("ro.kernel.qemu") == "1"
        and props.get("ro.build.version.sdk") == "37"
        and props.get("ro.product.cpu.abi") == "arm64-v8a"
        and page == "16384"
    )


def _wait_boot(proc, timeout=120):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if _boot_ok(proc):
            return
        time.sleep(1)
    raise RuntimeError("emulator boot timed out")


def _terminate(proc, timeout=15):
    if proc.poll() is None:
        proc.terminate()
        try:
            proc.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait(timeout=timeout)


def _write_after(evidence, before, cleanup_errors):
    after = None
    last_error = None
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        try:
            candidate = _adb(("devices",))
        except (subprocess.TimeoutExpired, subprocess.CalledProcessError) as exc:
            last_error = exc
            candidate = None
        if candidate is not None:
            after = candidate
            last_error = None
            if SERIAL not in _serials(after):
                break
        time.sleep(0.5)

    if after is None:
        if last_error is not None:
            cleanup_errors.append(last_error)
        return
    (evidence / "devices-after.txt").write_text(after, encoding="utf-8")
    before_set, after_set = _serials(before), _serials(after)
    if SERIAL in after_set:
        cleanup_errors.append(RuntimeError(f"{SERIAL} still present after closure"))
    b, a = before_set - {SERIAL}, after_set - {SERIAL}
    (evidence / "devices-delta.txt").write_text(
        "added=" + ",".join(sorted(a - b)) + "\n"
        "removed=" + ",".join(sorted(b - a)) + "\n",
        encoding="utf-8",
    )


@contextmanager
def _owned_avd_home(owner):
    path = tempfile.mkdtemp(prefix="odai-w3-station-phase-")
    try:
        yield Path(path)
    finally:
        proc = owner.get("proc")
        if proc is not None and proc.poll() is None:
            owner["cleanup_errors"].append(RuntimeError(
                f"AVD directory retained because emulator termination was unconfirmed: {path}"
            ))
        else:
            try:
                shutil.rmtree(path)
            except BaseException as exc:
                owner["cleanup_errors"].append(exc)
        primary = owner.get("primary")
        if primary is not None:
            for exc in owner["cleanup_errors"]:
                primary.add_note(f"cleanup error: {exc!r}")
        elif owner["cleanup_errors"]:
            err = owner["cleanup_errors"][0]
            for extra in owner["cleanup_errors"][1:]:
                err.add_note(f"additional cleanup error: {extra!r}")
            raise err


def run_on_avd(evidence, invoke):
    evidence = Path(evidence)
    before = _adb(("devices",))
    (evidence / "devices-before.txt").write_text(before, encoding="utf-8")
    _assert_available(before)

    owner = {"cleanup_errors": [], "proc": None, "primary": None}
    with _owned_avd_home(owner) as avd_home:
        avd_dir = avd_home / f"{NAME}.avd"
        avd_dir.mkdir()
        (avd_home / f"{NAME}.ini").write_text(
            "avd.ini.encoding=UTF-8\n"
            f"path={avd_dir}\n"
            f"path.rel=avd/{NAME}.avd\n"
            "target=android-37.2\n",
            encoding="utf-8",
        )
        (avd_dir / "config.ini").write_text(CONFIG, encoding="utf-8")

        env = os.environ.copy()
        env["ANDROID_AVD_HOME"] = str(avd_home)

        proc = log_fh = None
        try:
            log_fh = (evidence / "emulator.log").open("wb")
            proc = subprocess.Popen(
                [_EMULATOR, "-avd", NAME, "-port", "5594", "-no-window",
                 "-no-audio", "-no-snapshot", "-no-boot-anim",
                 "-gpu", "swiftshader_indirect", "-cores", "4", "-memory", "4096"],
                stdout=log_fh, stderr=subprocess.STDOUT, env=env,
            )
            owner["proc"] = proc
            (evidence / "emulator.pid").write_text(f"{proc.pid}\n", encoding="utf-8")
            (evidence / "serial.txt").write_text(f"{SERIAL}\n", encoding="utf-8")
            _wait_boot(proc)
            return invoke()
        except BaseException as exc:
            owner["primary"] = exc
            raise
        finally:
            if log_fh is not None:
                try:
                    log_fh.close()
                except BaseException as exc:
                    owner["cleanup_errors"].append(exc)
            if proc is not None:
                try:
                    _terminate(proc)
                except BaseException as exc:
                    owner["cleanup_errors"].append(exc)
            try:
                _write_after(evidence, before, owner["cleanup_errors"])
            except BaseException as exc:
                owner["cleanup_errors"].append(exc)
