#!/usr/bin/env python3
"""Exercise the real notice publication code in an offline, plugin-free Gradle fixture."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

CASES = ["valid", "tampered", "missing", "extra", "malformed", "missing-index",
         "wrong-schema", "duplicate", "escape", "invalid-hash", "empty",
         "root-symlink", "file-symlink", "late-tampered"]


def jar_members(path):
    with zipfile.ZipFile(path) as archive:
        return {name: hashlib.sha256(archive.read(name)).hexdigest()
                for name in archive.namelist() if not name.endswith("/")}


def publication_code(patch):
    lines = patch.read_text().splitlines()
    # Reconstruct only the added notice section; surrounding Skiko code is not run.
    added = "\n".join(line[1:] for line in lines
                      if line.startswith("+") and not line.startswith("+++"))
    start = added.index("    // Optional explicit notice input")
    end = added.index("    publishing {", start) if "    publishing {" in added[start:] else len(added)
    code = added[start:end]
    # The final patch hunk wires the artifact; the fixture does so separately.
    code = code.split("            noticesJar?.let { artifact(it) }")[0]
    helpers = added[:start]
    return helpers, code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gradle", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--publishing-source", type=Path)
    parser.add_argument("--validation-patch", type=Path)
    parser.add_argument("--case", choices=CASES, default="tampered")
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    patch = Path(__file__).with_name("Skiko-ios-notice-publication.patch")
    helpers, code = publication_code(patch)
    with tempfile.TemporaryDirectory(prefix="odai-notice-publication-") as scratch:
        root = Path(scratch)
        source_hash = None
        validation_hash = None
        if args.validation_patch and not args.publishing_source:
            parser.error("--validation-patch requires --publishing-source")
        if args.publishing_source:
            source = root / "skiko/buildSrc/src/main/kotlin/publishing.kt"
            source.parent.mkdir(parents=True)
            source.write_bytes(args.publishing_source.read_bytes())
            if args.validation_patch:
                validation = args.validation_patch.resolve()
                validation_hash = hashlib.sha256(validation.read_bytes()).hexdigest()
                original = source.read_bytes()
                applied = subprocess.run(["rtk", "proxy", "patch", "--batch", "--forward", "--fuzz=0", "-p1", "-i", str(validation)], cwd=root,
                                         capture_output=True, text=True, check=True)
                (output / "patch-apply.log").write_text(applied.stdout + applied.stderr)
                if source.read_bytes() == original:
                    raise ValueError("Validation patch did not change the intended publishing source")
            text = source.read_text()
            source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
            start = text.index("    // Optional explicit notice input")
            code = text[start:text.index("\n    publishing {", start)]
            helpers = ""
            if "private fun validateThirdPartyNotices(" in text:
                helpers = text[text.index("private fun validateThirdPartyNotices("):
                               text.index("private fun SkikoPublishingContext.configurePublicationDefaults()")]
        notices = root / "notices"
        notices.mkdir()
        data = b"upstream copyright and permission\n"
        (notices / "LICENSE").write_bytes(data)
        index = {"schemaVersion": 1, "files": [{"path": "LICENSE", "sha256": hashlib.sha256(data).hexdigest()}]}
        (notices / "NOTICE-INDEX.json").write_text(json.dumps(index))
        if args.case == "tampered":
            (notices / "LICENSE").write_bytes(b"changed text\n")
        elif args.case == "missing":
            (notices / "LICENSE").unlink()
        elif args.case == "extra":
            (notices / "EXTRA").write_bytes(b"unindexed\n")
        elif args.case == "malformed":
            (notices / "NOTICE-INDEX.json").write_text("{")
        elif args.case == "missing-index":
            (notices / "NOTICE-INDEX.json").unlink()
        elif args.case in ("wrong-schema", "duplicate", "escape", "invalid-hash", "empty"):
            if args.case == "wrong-schema": index["schemaVersion"] = 2
            elif args.case == "duplicate": index["files"] *= 2
            elif args.case == "escape": index["files"][0]["path"] = "../LICENSE"
            elif args.case == "invalid-hash": index["files"][0]["sha256"] = "invalid"
            else: index["files"] = []
            (notices / "NOTICE-INDEX.json").write_text(json.dumps(index))
        elif args.case == "root-symlink":
            real = notices.with_name("original-notices")
            notices.rename(real)
            notices.symlink_to(real, target_is_directory=True)
        elif args.case == "file-symlink":
            external = root / "external-license"
            external.write_bytes(data)
            (notices / "LICENSE").unlink()
            (notices / "LICENSE").symlink_to(external)
        (root / "settings.gradle.kts").write_text('rootProject.name = "notice-fixture"\n')
        build = '''import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
plugins { base; `maven-publish` }
inline fun <reified T : Task> TaskContainer.registerOrGetTask(name: String, noinline configure: T.() -> Unit): TaskProvider<T> =
    if (name in names) named<T>(name).also { it.configure(configure) } else register<T>(name, configure)
tasks.register<Jar>("sourcesJar") { archiveClassifier.set("sources") }
'''
        build += helpers + "\n" + code
        if args.case == "late-tampered":
            build += '\ngradle.taskGraph.whenReady { file("notices/LICENSE").writeText("changed after configuration\\n") }\n'
        build += '''
publishing { publications { create<MavenPublication>("fixture") {
    groupId = "test.odai"; artifactId = "fixture"; version = "1"
    noticesJar?.let { artifact(it) }; artifact(tasks.named("sourcesJar"))
} }; repositories { maven { name = "Local"; url = layout.buildDirectory.dir("repo").get().asFile.toURI() } } }
'''
        (root / "build.gradle.kts").write_text(build)
        command = [str(args.gradle.resolve()), "--offline", "--no-daemon", "--console=plain", "--max-workers=1",
                   "-Pskiko.notices.dir=" + str(notices), "publishFixturePublicationToLocalRepository"]
        (output / "build.gradle.kts").write_text(build)
        (output / "command.json").write_text(json.dumps(command, indent=2) + "\n")
        with (output / "gradle.log").open("x") as log:
            result = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT, env=os.environ.copy())
        jars = list((root / "build/repo").rglob("*.jar"))
        members = {p.name: jar_members(p) for p in jars}
        if args.case == "valid":
            passed = result.returncode == 0 and len(jars) == 2 and all(
                jar.get("META-INF/ondevice-ai-kit/notices/LICENSE") == hashlib.sha256(data).hexdigest()
                for jar in members.values())
        else:
            log = (output / "gradle.log").read_text()
            expected_rejection = "Skiko notice" in log or "skiko.notices.dir must contain" in log
            passed = result.returncode != 0 and not jars and expected_rejection
        receipt = {"case": args.case, "gradleExitCode": result.returncode, "passed": passed,
                   "publishedJars": members, "publicationPatchSha256": hashlib.sha256(patch.read_bytes()).hexdigest(),
                   "publishingSourceSha256": source_hash, "validationPatchSha256": validation_hash,
                   "scope": "Real offline Gradle fixture of the exact added notice block; no Skiko native rebuild, frozen artifact change or remote publication"}
        (output / "result.json").write_text(json.dumps(receipt, indent=2) + "\n")
        print(json.dumps(receipt, indent=2))
        raise SystemExit(0 if passed else 1)


if __name__ == "__main__":
    main()
