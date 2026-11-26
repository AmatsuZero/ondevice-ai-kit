#!/usr/bin/env python3
"""Build one offline, static Apple arm64 WeMM library slice."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys

PINNED_REVISION = "88aaf325a7026ac1127d0897394b0a3d6413dc2a"
ARCHIVES = (
    "libmtmd.a", "libllama.a", "libggml.a", "libggml-cpu.a",
    "libggml-blas.a", "libggml-metal.a", "libggml-base.a",
    "libvendor-hash.a",
)
DEPLOYMENT_TARGETS = {
    "macosx": "13.0",
    "iphoneos": "16.4",
    "iphonesimulator": "16.4",
}
TRIPLES = {
    "macosx": "arm64-apple-macosx13.0",
    "iphoneos": "arm64-apple-ios16.4",
    "iphonesimulator": "arm64-apple-ios16.4-simulator",
}


def run(args, *, cwd=None, capture=False):
    return subprocess.run(
        [str(arg) for arg in args], cwd=cwd, check=True, text=True,
        stdout=subprocess.PIPE if capture else None,
    )


def output(*args):
    return run(args, capture=True).stdout.strip()


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def unique_archive(build_dir, name):
    matches = sorted(
        path for path in build_dir.rglob(name)
        if path.name == name and path.is_file() and not path.is_symlink()
    )
    if len(matches) != 1:
        raise RuntimeError(
            f"expected one regular {name} under {build_dir}, found {len(matches)}"
        )
    return matches[0]


def build(args):
    source = args.source.resolve(strict=True)
    if args.output.exists() or args.output.is_symlink():
        raise RuntimeError(f"output already exists: {args.output}")
    destination = args.output.resolve()
    if not source.is_dir():
        raise RuntimeError(f"source is not a directory: {source}")
    if source == destination or source in destination.parents:
        raise RuntimeError("output must not be inside the source checkout")
    git_root = Path(output("git", "-C", source, "rev-parse", "--show-toplevel"))
    if git_root.resolve() != source:
        raise RuntimeError(f"source must be a Git checkout root: {source}")

    project = Path(__file__).resolve().parent.parent / "demo" / "iosApp" / "WeMMNative"
    patch = project / "mtmd-projector.patch"
    cpp = project / "WeMMNative.cpp"
    header = project / "WeMMNative.h"
    for path in (patch, cpp, header):
        if not path.is_file():
            raise RuntimeError(f"required project file is missing: {path}")

    sdk = args.sdk
    deployment = DEPLOYMENT_TARGETS[sdk]
    sdk_path = output("xcrun", "--sdk", sdk, "--show-sdk-path")
    sdk_version = output("xcrun", "--sdk", sdk, "--show-sdk-version")
    destination.mkdir(parents=True)
    staged_patch = destination / patch.name
    shutil.copy2(patch, staged_patch)
    checkout = destination / "build-source"
    run(("git", "clone", "--no-hardlinks", "--local", "--no-checkout", source, checkout))
    run(("git", "-C", checkout, "checkout", "--detach", PINNED_REVISION))
    revision = output("git", "-C", checkout, "rev-parse", "HEAD")
    if revision != PINNED_REVISION:
        raise RuntimeError(f"checkout revision mismatch: {revision}")
    run(("git", "-C", checkout, "apply", "--check", staged_patch))
    run(("git", "-C", checkout, "apply", staged_patch))

    product_sources = destination / "product-sources"
    product_sources.mkdir()
    staged_cpp = product_sources / cpp.name
    staged_header = product_sources / header.name
    shutil.copy2(cpp, staged_cpp)
    shutil.copy2(header, staged_header)
    include_dir = destination / "include"
    include_dir.mkdir()
    shutil.copy2(staged_header, include_dir / header.name)
    (include_dir / "module.modulemap").write_text(
        'module ODAIWeMMNative {\n'
        '    header "WeMMNative.h"\n'
        '    export *\n'
        '}\n', encoding="utf-8"
    )
    shutil.copy2(checkout / "LICENSE", destination / "NOTICE")

    build_dir = destination / "cmake-build"
    cmake_args = [
        args.cmake, "-S", checkout, "-B", build_dir, "-G", "Unix Makefiles",
        "-DCMAKE_BUILD_TYPE=Release",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        f"-DCMAKE_OSX_SYSROOT={sdk_path}",
        f"-DCMAKE_OSX_DEPLOYMENT_TARGET={deployment}",
        "-DBUILD_SHARED_LIBS=OFF", "-DGGML_BACKEND_DL=OFF",
        "-DGGML_NATIVE=OFF", "-DGGML_BLAS=ON", "-DGGML_METAL=ON",
        "-DGGML_METAL_EMBED_LIBRARY=ON", "-DGGML_OPENMP=OFF",
        "-DGGML_CPU_KLEIDIAI=OFF", "-DLLAMA_BUILD_COMMON=OFF",
        "-DLLAMA_BUILD_APP=OFF", "-DLLAMA_BUILD_TOOLS=OFF",
        "-DLLAMA_BUILD_SERVER=OFF", "-DLLAMA_BUILD_EXAMPLES=OFF",
        "-DLLAMA_BUILD_TESTS=OFF", "-DLLAMA_BUILD_UI=OFF",
        "-DLLAMA_BUILD_MTMD=ON", "-DMTMD_VIDEO=OFF",
    ]
    if sdk != "macosx":
        cmake_args.append("-DCMAKE_SYSTEM_NAME=iOS")
    run(cmake_args)
    run((args.cmake, "--build", build_dir, "--target", "mtmd", "vendor-hash",
         "--parallel", args.jobs))

    libraries = [unique_archive(build_dir, name) for name in ARCHIVES]
    obj = destination / "WeMMNative.o"
    run(("xcrun", "--sdk", sdk, "clang++", "-target", TRIPLES[sdk],
         "-isysroot", sdk_path, "-O3", "-std=c++17", "-c", staged_cpp,
         "-o", obj, "-I", checkout / "include", "-I", checkout / "ggml" / "include",
         "-I", checkout / "tools" / "mtmd", "-I", checkout / "vendor"))
    archive = destination / "libODAIWeMMNative.a"
    run(("xcrun", "--sdk", sdk, "libtool", "-static", "-o", archive,
         obj, *libraries))

    receipt = {
        "revision": PINNED_REVISION,
        "sdk": sdk,
        "sdk_version": sdk_version,
        "deployment_target": deployment,
        "archive": {"file": archive.name, "sha256": sha256(archive)},
        "sources": {
            patch.name: sha256(staged_patch),
            cpp.name: sha256(staged_cpp),
            header.name: sha256(staged_header),
        },
    }
    (destination / "Receipt.json").write_text(
        json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path, help="local Git checkout")
    parser.add_argument("--sdk", required=True, choices=tuple(DEPLOYMENT_TARGETS))
    parser.add_argument("--output", required=True, type=Path, help="new output directory")
    parser.add_argument("--jobs", type=int, default=2)
    parser.add_argument("--cmake", default="cmake", help="CMake executable")
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error("--jobs must be at least 1")
    try:
        build(args)
    except (OSError, RuntimeError, subprocess.CalledProcessError) as exc:
        print(f"build failed: {exc}", file=sys.stderr)
        print("Any created output is retained for inspection.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
