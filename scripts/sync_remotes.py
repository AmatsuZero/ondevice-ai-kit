#!/usr/bin/env python3
"""Fast-forward an explicit ref to origin then jihu; never force or mirror refs."""
import argparse
import subprocess
import sys

def git(*args, check=True):
    result = subprocess.run(["git", *args], text=True, capture_output=True)
    if check and result.returncode:
        raise RuntimeError(result.stderr.strip() or "git failed")
    return result

def remote_sha(remote, ref):
    result = git("ls-remote", "--exit-code", remote, ref, check=False)
    if result.returncode == 2:
        return None
    if result.returncode:
        raise RuntimeError(f"cannot read {remote}: {result.stderr.strip()}")
    lines = [line.split() for line in result.stdout.splitlines()]
    matches = [sha for sha, name in lines if name == ref]
    if len(matches) != 1:
        raise RuntimeError(f"ambiguous remote ref {remote} {ref}")
    return matches[0]

def synchronize(name, tag=False):
    prefix = "refs/tags/" if tag else "refs/heads/"
    ref = prefix + name
    git("check-ref-format", ref)
    frozen = git("rev-parse", "--verify", ref).stdout.strip()
    for remote in ("origin", "jihu"):
        existing = remote_sha(remote, ref)
        if existing is None or existing == frozen:
            continue
        if tag:
            raise RuntimeError(f"immutable tag differs on {remote}")
        git("fetch", "--no-tags", remote, ref)
        if git("merge-base", "--is-ancestor", existing, frozen, check=False).returncode:
            raise RuntimeError(f"diverged or ahead: {remote} {ref}; preserved both refs")
    git("push", "origin", f"{frozen}:{ref}")
    if remote_sha("origin", ref) != frozen:
        raise RuntimeError("primary changed during sync; mirror untouched")
    try:
        git("push", "jihu", f"{frozen}:{ref}")
        if any(remote_sha(remote, ref) != frozen for remote in ("origin", "jihu")):
            raise RuntimeError("fresh remote SHA equality failed")
    except RuntimeError as error:
        raise RuntimeError(f"mirror pending retry; primary frozen SHA {frozen}: {error}") from error
    print(f"synced {ref} {frozen}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("name")
    parser.add_argument("--tag", action="store_true")
    args = parser.parse_args()
    try:
        synchronize(args.name, args.tag)
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
