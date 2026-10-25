#!/usr/bin/env python3
"""Read-only source rebase preflight for engine-changing pull requests.

Reuses the *trusted* engine-upgrade/weekly.py rebase machinery with PR patches
treated as untrusted data. No PR Python/build scripts are executed; only exact
GitHub file blobs are copied to a temporary checkout. No publishing or APKs.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
from pathlib import Path

from engine_pr_guard import Api, MANDATORY_RE, PIN_RE, SHA_RE, REPO_RE, read_pin, read_mandatory

ROOT = Path(__file__).resolve().parent.parent
SOURCE_PATCH = re.compile(r"^argentum-patches/[0-9]{4}-.+\.patch$")
EXTRA_PATCH = re.compile(r"^engine-upgrade/.+/patches/[0-9]{4}-.+\.patch$")
ENGINE_REPO = "https://github.com/wingedsheep/argentum-engine.git"


def is_patch_candidate(row: dict) -> bool:
    return any(SOURCE_PATCH.fullmatch(f) or EXTRA_PATCH.fullmatch(f)
               for f in row["engine_paths"])


def candidates(rows: list[dict], limit: int) -> list[dict]:
    relevant = [r for r in rows if is_patch_candidate(r) and "head_pin" in r]
    if len(relevant) > limit:
        raise ValueError(f"{len(relevant)} source-patch PRs exceed --limit {limit}; refusing partial probe")
    return relevant


def trusted_audit(source: str, head_pin: str, mandatory: set[str]) -> str:
    if not SHA_RE.fullmatch(head_pin):
        raise ValueError("Missing full source pin")
    if not MANDATORY_RE.search(source) or not PIN_RE.search(source):
        raise ValueError("Trusted baseline has no exact pin / mandatory list")
    original = read_mandatory(source)
    if any(not re.fullmatch(r"[0-9]{4}-[A-Za-z0-9_-]+\.patch", name) for name in mandatory):
        raise ValueError("Unsafe mandatory patch basename from PR data")
    if not mandatory:
        raise ValueError("Refusing empty mandatory patch series")
    # Preserve order from the PR's audited list, never sort into an unreviewed order.
    raise NotImplementedError("Call materialize_audit to preserve original mandatory ordering")


def materialize_audit(source: str, pr_audit: str) -> str:
    pin = read_pin(pr_audit)
    block = MANDATORY_RE.search(pr_audit)
    if not block or not PIN_RE.search(source) or not MANDATORY_RE.search(source):
        raise ValueError("Missing mandatory source list or trusted baseline audit")
    entries = re.findall(r'"([0-9]{4}-[^"]+\.patch)"', block.group(1))
    if not entries or len(entries) != len(set(entries)):
        raise ValueError("Duplicate or absent mandatory patches")
    if any(not re.fullmatch(r"[0-9]{4}-[A-Za-z0-9_.-]+\.patch", x) or "/" in x or ".." in x
           for x in entries):
        raise ValueError("Unsafe mandatory patch path")
    # Keep trusted code and update only its two strictly parsed constant values.
    updated = PIN_RE.sub(lambda _: f'TARGET = "{pin}"', source)
    listing = "".join(f'    "{item}",\n' for item in entries)
    return MANDATORY_RE.sub(lambda _: "MANDATORY = [\n" + listing + "]", updated)


def safe_env() -> dict:
    env = {k: v for k, v in os.environ.items()
           if not any(s in k.upper() for s in ("TOKEN", "SECRET", "PASSWORD", "CREDENTIAL", "KEY"))
           and not k.startswith(("ACTIONS_", "GITHUB_"))}
    env["GIT_TERMINAL_PROMPT"] = "0"
    env["PYTHONDONTWRITEBYTECODE"] = "1"
    return env


def command(args: list[str], *, cwd: Path, timeout: int = 180) -> subprocess.CompletedProcess:
    return subprocess.run(args, cwd=cwd, text=True, capture_output=True,
                          env=safe_env(), timeout=timeout, check=False)


def head_file(api: Api, head_repo: str, sha: str, path: str) -> str | None:
    try:
        return api.content(head_repo, path, sha)
    except urllib.error.HTTPError as ex:
        if ex.code == 404:
            return None
        raise


def stage_pr_patch_data(api: Api, row: dict, target: Path, repo: str) -> list[str]:
    head_sha = row["head_sha"]
    pr = api.get(f"/repos/{repo}/pulls/{row['pr']}")
    if pr["head"]["sha"] != head_sha:
        raise ValueError(f"PR #{row['pr']} head moved; refuse stale report")
    head_repo = (pr["head"].get("repo") or {}).get("full_name") or repo
    if not REPO_RE.fullmatch(head_repo) or not SHA_RE.fullmatch(head_sha):
        raise ValueError("Unexpected head repository or SHA")
    source_audit = (target / "engine-upgrade/audit_patches.py").read_text()
    pr_audit = head_file(api, head_repo, head_sha, "engine-upgrade/audit_patches.py")
    if pr_audit is None:
        raise ValueError(f"PR #{row['pr']} lacks source pin audit")
    if read_pin(pr_audit) != row["head_pin"]:
        raise ValueError(f"PR #{row['pr']} pin changed between inventory and source probe")
    (target / "engine-upgrade/audit_patches.py").write_text(
        materialize_audit(source_audit, pr_audit))
    mandatory = list(re.findall(r'"([0-9]{4}-.+\.patch)"', MANDATORY_RE.search(pr_audit).group(1)))
    for name in mandatory:
        # Forward-ports override the baseline patch, just as weekly.py patch_file() specifies.
        forward = head_file(api, head_repo, head_sha, "engine-upgrade/forward-ports/" + name)
        patch = head_file(api, head_repo, head_sha, "argentum-patches/" + name)
        if forward is None and patch is None:
            raise ValueError("Missing mandatory source blob: " + name)
        if patch is not None:
            (target / "argentum-patches" / name).write_text(patch)
        if forward is not None:
            path = target / "engine-upgrade/forward-ports" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(forward)
        else:
            (target / "engine-upgrade/forward-ports" / name).unlink(missing_ok=True)
    # Only record these optional data files; never execute untrusted PR code.
    config = head_file(api, head_repo, head_sha, "engine-upgrade/rebase-rules.json")
    if config is not None:
        data = json.loads(config)
        if not isinstance(data, dict):
            raise ValueError("Untrusted rebase-rules JSON is not an object")
        (target / "engine-upgrade/rebase-rules.json").write_text(config)
    for file in api.pages(f"/repos/{repo}/pulls/{row['pr']}/files"):
        path = file["filename"]
        if not path.startswith("engine-upgrade/rerere/") or file.get("status") == "removed":
            continue
        if not re.fullmatch(r"engine-upgrade/rerere/[0-9a-f]{40}/(preimage|postimage|thisimage)", path):
            raise ValueError("Unexpected rr-cache path: " + path)
        entry = head_file(api, head_repo, head_sha, path)
        if entry is None:
            raise ValueError("Missing recorded rerere entry: " + path)
        dst = target / path
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(entry)
    return mandatory


def source_rebase(api: Api, row: dict, repo: str, upstream: str) -> dict:
    result = {"pr": row["pr"], "head_sha": row["head_sha"],
              "head_pin": row["head_pin"], "upstream_sha": upstream,
              "status": "pending", "conflicts": [], "diagnostic": ""}
    with tempfile.TemporaryDirectory(prefix="arcana-pr-rebase-") as folder:
        base = Path(folder)
        source = base / "arcana"
        source.mkdir()
        shutil.copytree(ROOT / "engine-upgrade", source / "engine-upgrade",
                        ignore=shutil.ignore_patterns("weekly", "__pycache__", "*.pyc"))
        shutil.copytree(ROOT / "argentum-patches", source / "argentum-patches")
        mandatory = stage_pr_patch_data(api, row, source, repo)
        result["mandatory"] = mandatory
        engine = base / "argentum-engine"
        clone = command(["git", "clone", "--quiet", "--filter=blob:none",
                         ENGINE_REPO, str(engine)], cwd=base)
        if clone.returncode:
            raise ValueError("Upstream clone failed: " + clone.stderr[-1200:])
        for commit in (row["head_pin"], upstream):
            available = command(["git", "-C", str(engine), "cat-file", "-e",
                                 commit + "^{commit}"], cwd=source, timeout=20)
            if available.returncode:
                fetched = command(["git", "-C", str(engine), "fetch", "--quiet",
                                   "origin", commit], cwd=source)
                if fetched.returncode:
                    raise ValueError("Cannot fetch exact upstream commit " + commit[:12])
        response = command(["python3", "engine-upgrade/weekly.py", "rebase",
                            str(engine), upstream], cwd=source, timeout=180)
        report_file = source / "engine-upgrade/weekly/rebase.json"
        if report_file.is_file():
            report = json.loads(report_file.read_text())
            result["status"] = report.get("status", "unknown")
            result["conflicts"] = [
                {"patch": x.get("patch"), "file": x.get("file"), "kind": x.get("kind")}
                for x in report.get("conflicts", [])
            ]
        else:
            result["status"] = "probe_failed"
        if response.returncode not in (0, 2) or result["status"] not in ("rebased", "conflict"):
            result["status"] = "probe_failed"
        result["diagnostic"] = (response.stdout + "\n" + response.stderr)[-2600:]
        # This is only source rebase verification, never evidence of a successful JAR build.
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", "AARMOU/arcana_table"))
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--limit", type=int, default=12)
    args = parser.parse_args()
    if not REPO_RE.fullmatch(args.repo) or args.limit < 1 or args.limit > 12:
        parser.error("invalid repository or per-run PR cap")
    source = json.loads(args.report.read_text())
    upstream = source["upstream_sha"]
    if not SHA_RE.fullmatch(upstream):
        parser.error("missing exact upstream SHA")
    selected = candidates(source["results"], args.limit)
    api = Api(os.getenv("GITHUB_TOKEN", ""))
    data = []
    for row in selected:
        try:
            r = source_rebase(api, row, args.repo, upstream)
        except (ValueError, subprocess.TimeoutExpired, urllib.error.HTTPError,
                urllib.error.URLError, OSError) as exc:
            r = {"pr": row["pr"], "head_sha": row["head_sha"],
                 "upstream_sha": upstream, "status": "probe_failed",
                 "diagnostic": str(exc)[:1500], "conflicts": []}
        data.append(r)
    markdown = [
        "## Read-only source rebase of open engine patch PRs", "",
        f"Exact upstream: \`{upstream}\`", "",
        "| PR | Source rebase | First conflicts |",
        "|---|---|---|",
    ]
    for r in data:
        conflicts = ", ".join(f"\`{x['patch']}\` in \`{x['file']}\`"
                              for x in r["conflicts"][:4]) or r.get("diagnostic", "")[:180].replace("|", "/")
        markdown.append(f"| #{r['pr']} | {r['status']} | {conflicts} |")
    markdown += ["", "A successful source rebase does NOT certify engine compile, rules or Android tests."]
    message = "\n".join(markdown) + "\n"
    print(message)
    if os.getenv("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as stream:
            stream.write(message)
    if args.output:
        args.output.write_text(json.dumps({"results": data, "upstream_sha": upstream}, indent=2) + "\n")
    return 1 if any(x["status"] != "rebased" for x in data) else 0


if __name__ == "__main__":
    sys.exit(main())
