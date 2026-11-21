#!/usr/bin/env python3
"""Check the actual unsigned paired runner package without using a device."""
import hashlib
import json
from pathlib import Path
import plistlib
import shlex
import subprocess
import wave

PROBE = Path(__file__).resolve().parent
ROOT = PROBE.parents[1]
TASK = ROOT / ".omx/artifacts/w3-punctuation-paired6-preparation-20261116"
APP = TASK / "DerivedData/Build/Products/Debug-iphoneos/W3PunctuationPaired6.app"
checks = 0


def check(condition, label):
    global checks
    if not condition:
        raise AssertionError(label)
    checks += 1


def sha(data):
    return hashlib.sha256(data).hexdigest()


def command(*args):
    return subprocess.check_output(["rtk", "proxy", *map(str, args)], text=True)


contract = json.loads((PROBE / "execution-contract.json").read_bytes())
identity_bytes = (PROBE / "source-identity-manifest.json").read_bytes()
identity = json.loads(identity_bytes)
check(identity["runId"] == contract["runId"], "new paired run identity")
check(contract["selectedInputs"] == 6 and contract["proposedRequests"] == 12, "six-pair boundary")
check(contract["pairOrder"] == ["AB", "BA", "AB", "BA", "AB", "BA"], "counterbalance fixed before execution")
check(contract["executionAuthorized"] is False, "preparation grants no recognition authorization")
check(sha((PROBE / "input-selection.json").read_bytes()) == contract["inputSelectionSHA256"], "frozen selection")
for name in ("scorer", "classification"):
    policy = contract["interpretation"]
    check(sha((ROOT / policy[name + "Path"]).read_bytes()) == policy[name + "SHA256"], "original metric/classification unchanged")

binding = json.loads((PROBE / "runtime-source-binding.json").read_bytes())
check(len(binding["inputs"]) == 14, "fourteen actual runtime copies")
for row in binding["inputs"]:
    check(sha((ROOT / row["source"]).read_bytes()) == row["sourceSHA256"], "production input preserved")
    check(sha((TASK / row["destination"]).read_bytes()) == row["preparedSHA256"], "runtime copy pinned")
insertion = b"\n        if #available(iOS 16.0, macOS 13.0, *) {\n            request.addsPunctuation = true\n        }"
arm_a = TASK / "RuntimeSources/PunctuationArmA"
arm_b = TASK / "RuntimeSources/PunctuationArmB"
for a in sorted(arm_a.glob("*.swift")):
    b = (arm_b / a.name).read_bytes()
    if a.name == "AppleSpeechRecognizerBackend.swift":
        check(b.count(insertion) == 1, "exactly one guarded B assignment")
        check(b.replace(insertion, b"", 1) == a.read_bytes(), "inverse B restores A bytes")
    else:
        check(a.read_bytes() == b, "other arm source bytes identical")
    check(a.read_bytes() == (ROOT / "apple/Sources/ODAIKitAppleSpeech" / a.name).read_bytes(), "A preserves production source")

check(len(identity["selectedSources"]) == 18, "seventeen Swift inputs plus project configuration")
for row in identity["selectedSources"]:
    check(sha((ROOT / row["path"]).read_bytes()) == row["sha256"], "selected source/config pin")
check((APP / "source-identity-manifest.json").read_bytes() == identity_bytes, "packaged source identity")
manifest_bytes = (TASK / "AudioFixtures/manifest.json").read_bytes()
manifest = json.loads(manifest_bytes)
check(sha(manifest_bytes) == contract["manifestSHA256"] == identity["manifestSHA256"], "fixture manifest pin")
check((APP / "AudioFixtures/manifest.json").read_bytes() == manifest_bytes, "packaged fixture manifest")
baseline = json.loads((ROOT / ".omx/artifacts/w3-apple65-probe-preparation-20261116/AudioFixtures/manifest.json").read_bytes())
check(manifest["cases"] == baseline["cases"], "all sixty-five fixture identities unchanged")
check(len(list((APP / "AudioFixtures/audio").glob("*.wav"))) == 65, "all sixty-five integrity resources packaged")
for clip in manifest["cases"]:
    path = APP / "AudioFixtures" / clip["wavPath"]
    check(sha(path.read_bytes()) == clip["wavSHA256"], "actual WAV pin")
    with wave.open(str(path), "rb") as stream:
        check((stream.getnchannels(), stream.getsampwidth(), stream.getframerate(), stream.getnframes()) == (1, 2, 16000, clip["samples"]), "independent WAV format")
        check(sha(stream.readframes(stream.getnframes())) == clip["pcmSHA256"], "actual PCM pin")

filelists = sorted((TASK / "DerivedData/Build/Intermediates.noindex").rglob("*.SwiftFileList"))
check(len(filelists) == 5, "five real compiler FileLists")
compiled = [Path(path).resolve() for file in filelists for path in shlex.split(file.read_text())]
selected = {(ROOT / row["path"]).resolve() for row in identity["selectedSources"] if row["path"].endswith(".swift")}
check(len(compiled) == len(selected) == 17 and set(compiled) == selected, "seventeen actual Swift inputs exactly match selected sources")
check(not any("Checks" in path.name for path in compiled), "host tests excluded from App")
frameworks = sorted(path.name for path in (APP / "Frameworks").iterdir())
check(frameworks == ["ODAIKitCore.framework", "ODAIKitSpeech.framework", "PunctuationArmA.framework", "PunctuationArmB.framework"], "exact four embedded dynamic frameworks")
binary_identities = {}
for name in ("ODAIKitCore", "ODAIKitSpeech", "PunctuationArmA", "PunctuationArmB"):
    binary = APP / "Frameworks" / (name + ".framework") / name
    check(binary.read_bytes() == (TASK / "DerivedData/Build/Products/Debug-iphoneos" / (name + ".framework") / name).read_bytes(), "embedded framework matches actual built framework")
    check("DYLIB" in command("otool", "-hv", binary), "framework really is dynamic")
    links = command("otool", "-L", binary)
    if name.startswith("PunctuationArm"):
        check("@rpath/ODAIKitCore.framework/ODAIKitCore" in links and "@rpath/ODAIKitSpeech.framework/ODAIKitSpeech" in links, "arms use shared Core/Speech framework identities")
        selectors = command("strings", "-a", binary)
        check(("setAddsPunctuation:" in selectors) == (name == "PunctuationArmB"), "setter is present only in B binary; not runtime-effect proof")
    binary_identities[name] = dict(path=str(binary.relative_to(ROOT)), bytes=binary.stat().st_size,
                                   sha256=sha(binary.read_bytes()), uuid=command("dwarfdump", "--uuid", binary).strip())
check(binary_identities["PunctuationArmA"]["sha256"] != binary_identities["PunctuationArmB"]["sha256"], "separate arm binary identities")
links = command("otool", "-L", APP / "W3PunctuationPaired6.debug.dylib")
for arm in ("PunctuationArmA", "PunctuationArmB"):
    check("@rpath/" + arm + ".framework/" + arm in links, "actual App links arm " + arm)
info = plistlib.loads((APP / "Info.plist").read_bytes())
check(info["CFBundleIdentifier"] == "io.ondeviceai.probe.w3punctpair20261116", "new bundle identity")
check("NSSpeechRecognitionUsageDescription" in info and "NSMicrophoneUsageDescription" not in info, "Speech-only App usage key")
sign = subprocess.run(["rtk", "proxy", "codesign", "--display", "--verbose=4", str(APP)], text=True, capture_output=True)
check(sign.returncode != 0 and "not signed at all" in sign.stderr, "App remains unsigned")

source = (ROOT / "probes/ios-apple65-file/W3Apple65App.swift").read_text()
for patch in json.loads((PROBE / "app-integration-substitutions.json").read_bytes()):
    check(source.count(patch["old"]) == patch["count"], "reviewed exact App substitution count")
    source = source.replace(patch["old"], patch["new"])
check(source == (PROBE / "W3Apple65App.swift").read_text(), "App differs only by reviewed integration substitutions")
for name in ("DriverHost.swift", "Apple65UITests.swift", "project.yml"):
    source = (ROOT / "probes/ios-apple65-file/ui-driver" / name).read_text()
    for patch in json.loads((PROBE / "ui-integration-substitutions.json").read_bytes()):
        if patch["file"] == name:
            check(source.count(patch["old"]) == patch["count"], "exact UI substitution count")
            source = source.replace(patch["old"], patch["new"])
    check(source == (PROBE / "ui-driver" / name).read_text(), "UI changes identifiers only")
ui = (PROBE / "ui-driver/Apple65UITests.swift").read_text()
check(ui.count("run.tap()") == 1 and "app.launch()" not in ui, "one Run site with activate preserved")
check(contract["runId"] in ui and "ODAI_APPLE65_RUN_ID" in ui, "opt-in UI execution guard")
check(len(list((TASK / "DriverDerivedData/Build/Products").glob("*.xctestrun"))) == 1, "one driver build-for-testing recipe")
for name in ("build", "ui-build", "core-checks", "plan-checks"):
    check(json.loads((TASK / (name + ".json")).read_bytes())["exitCode"] == 0, "actual success receipt: " + name)
check(sum(line.startswith("PASS: ") for line in (TASK / "core-checks.stdout").read_text().splitlines()) == 506, "five hundred six inherited checks")
check(sum(line.startswith("PASS: ") for line in (TASK / "plan-checks.stdout").read_text().splitlines()) == 37, "thirty-seven actual plan checks")
check(json.loads((TASK / "plan-red.json").read_bytes())["exitCode"] != 0, "original missing implementation failed first")

files = [dict(path=str(path.relative_to(APP)), bytes=path.stat().st_size, sha256=sha(path.read_bytes()))
         for path in sorted(APP.rglob("*")) if path.is_file()]
receipt = dict(status="built-verified-not-authorized-not-executed", parentChecks=checks,
               runId=contract["runId"], selectedInputs=6, proposedRequests=12, fixtureIntegrityInputs=65,
               sourceIdentitySHA256=sha(identity_bytes), manifestSHA256=sha(manifest_bytes),
               compiledSwiftInputs=17, frameworkBinaryIdentities=binary_identities,
               swiftFileLists=[dict(path=str(path.relative_to(ROOT)), sha256=sha(path.read_bytes()), sources=shlex.split(path.read_text())) for path in filelists],
               actualAppFiles=files, coreChecks=506, planChecks=37, appUnsigned=True,
               executionAuthorized=False, newRecognitionRequests=0, newDeviceRuns=0,
               independentFinalReview=False, R4Accepted=False, R5Accepted=False, P4Accepted=False, W3Complete=False)
(TASK / "package-acceptance.json").write_text(json.dumps(receipt, indent=2) + "\n")
print(json.dumps({key: value for key, value in receipt.items() if key not in ("swiftFileLists", "actualAppFiles", "frameworkBinaryIdentities")}))
