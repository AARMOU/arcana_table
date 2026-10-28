#!/usr/bin/env python3
"""Review or locally apply the exact Build 134 integration source artifact.

This tool never commits, pushes, merges, builds, or changes runtime JARs.
Download build134-integrated-source.patch from Actions run 40678286737 first.
"""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import sys

BASE_SHA = "77bccdcc0b7445b8888984c5ed194fbfb80f99ee"
PATCH_SHA256 = "071220efb31c70a28ac626dffc52e5e2ac1268ba3a84e2da48406167049a7740"
PATCH_BYTES = 5046009


def verify_patch(data: bytes, expected_sha: str = PATCH_SHA256) -> None:
    if hashlib.sha256(data).hexdigest() != expected_sha:
        raise ValueError("source patch SHA256 mismatch: refuse unverified artifact")
    if not data.startswith(b"diff --git a/"):
        raise ValueError("not a Git source patch")
    paths = re.findall(rb"^diff --git a/([^\n]+?) b/([^\n]+)$", data, re.MULTILINE)
    if not paths or any(a != b for a, b in paths):
        raise ValueError("unexpected patch path mapping")
    if any(a.startswith(b"/") or b".." in a.split(b"/") or b"\\" in a for a, _ in paths):
        raise ValueError("unsafe absolute, parent-traversal, or backslash patch path")
    if any(a.startswith(b"android/app/libs/") and a.endswith(b".jar") for a, _ in paths):
        raise ValueError("generated Argentum JAR must not be part of source patch")
    if any(b"Build134ReleaseScopeTest.kt" in a for a, _ in paths):
        raise ValueError("ephemeral release-scope fixture must not enter source")


def git(*args: str, cwd: Path) -> str:
    result = subprocess.run(["git", *args], cwd=cwd, text=True,
                            capture_output=True, check=True)
    return result.stdout.strip()


def verify_checkout(repo: Path, expected_head: str = BASE_SHA) -> None:
    if git("rev-parse", "HEAD", cwd=repo) != expected_head:
        raise ValueError("checkout is not the exact validated source base")
    if git("status", "--porcelain", cwd=repo):
        raise ValueError("working tree must be clean before applying source patch")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("patch", type=Path, help="extracted source patch from CI artifact")
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--apply", action="store_true",
                        help="explicitly apply after all checks; default is read-only")
    args = parser.parse_args()
    repo = args.repo.resolve()
    try:
        data = args.patch.read_bytes()
        if len(data) != PATCH_BYTES:
            raise ValueError("unexpected source patch byte count")
        verify_patch(data)
        verify_checkout(repo)
        subprocess.run(["git", "apply", "--check", str(args.patch.resolve())],
                       cwd=repo, check=True)
        if args.apply:
            subprocess.run(["git", "apply", str(args.patch.resolve())],
                           cwd=repo, check=True)
            print("Applied verified source patch locally; no commit or merge performed.")
        else:
            print("PASS: exact Build 134 patch verified and applies cleanly; no files changed.")
        print("Runtime Argentum JARs are intentionally excluded; rebuild them from the pinned engine source before release validation.")
        return 0
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        print(f"REFUSED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
