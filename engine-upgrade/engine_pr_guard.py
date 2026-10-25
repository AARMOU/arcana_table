#!/usr/bin/env python3
"""Read-only engine-PR drift and split-landing guard.

Fetches PR *data* through the GitHub REST API. Never checks out or executes PR
code, uses write-capable tokens, publishes or merges anything. All conclusions
are from exact immutable head SHAs; a newer upstream is a risk notification,
not proof that a source patch fails to rebase.
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

PIN_RE = re.compile(r'^TARGET = "([0-9a-f]{40})"$', re.M)
MANDATORY_RE = re.compile(r'^MANDATORY\s*=\s*\[(.*?)^\]', re.M | re.S)
PATCH_RE = re.compile(r'^argentum-patches/([0-9]{4}-.+\.patch)$')
SHA_RE = re.compile(r'^[0-9a-f]{40}$')
REPO_RE = re.compile(r'^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')
ENGINE_LOCATIONS = (
    "argentum-patches/", "engine-upgrade/forward-ports/",
    "engine-upgrade/rerere/", "android/app/libs/argentum-",
    "engine-upgrade/ulalek/", "engine-upgrade/eldrazi-temple/",
    "engine-upgrade/eldrazi-combined/", "engine-upgrade/corrupted-crossroads/",
)
ENGINE_FILES = {
    "engine-upgrade/audit_patches.py", "engine-upgrade/rebase-rules.json",
    "engine-upgrade/preview-config.json",
}
EXP_PATCH = "0010-enumeration-absence-index.patch"


def is_engine_file(path: str) -> bool:
    if path in ENGINE_FILES:
        return True
    if path.startswith("android/app/libs/argentum-"):
        return path.endswith(".jar")
    if path.startswith(("engine-upgrade/ulalek/", "engine-upgrade/eldrazi-temple/",
                        "engine-upgrade/eldrazi-combined/",
                        "engine-upgrade/corrupted-crossroads/")):
        return path.endswith(".patch") or "/engine-tests/" in path
    return any(path.startswith(p) for p in ENGINE_LOCATIONS[:3])


def read_pin(text: str) -> str:
    pins = PIN_RE.findall(text)
    if len(pins) != 1:
        raise ValueError("Expected exactly one exact 40-character TARGET in audit_patches.py")
    return pins[0]


def read_mandatory(text: str) -> set[str]:
    match = MANDATORY_RE.search(text)
    if not match:
        raise ValueError("Missing MANDATORY patch list in audit_patches.py")
    return set(re.findall(r'"([0-9]{4}-[^"]+\.patch)"', match.group(1)))


def evaluate(pr: dict, files: list[dict], head_audit: str | None,
             dev_pin: str, upstream_sha: str, now: dt.datetime) -> dict | None:
    engine_paths = sorted({f["filename"] for f in files if is_engine_file(f["filename"])})
    if not engine_paths:
        return None
    number = pr["number"]
    head_sha = pr["head"]["sha"]
    result = {
        "pr": number, "title": pr["title"], "url": pr["html_url"],
        "head_sha": head_sha, "dev_pin": dev_pin, "upstream_sha": upstream_sha,
        "engine_paths": engine_paths, "errors": [], "warnings": [], "advice": [],
    }
    if head_audit is None:
        result["errors"].append("Engine PR head has no readable audit_patches.py")
        result["advice"].append("Restore a full exact-pin engine audit in this PR.")
        return result
    try:
        head_pin = read_pin(head_audit)
        mandatory = read_mandatory(head_audit)
    except ValueError as exc:
        result["errors"].append(str(exc))
        return result
    result["head_pin"] = head_pin
    if head_pin != dev_pin:
        result["errors"].append("STALE_ENGINE_PIN: PR pin differs from current development")
        result["advice"].append(
            "Rebase to development's engine pin and validate the complete mandatory patch series.")
    if upstream_sha != head_pin:
        result["warnings"].append(
            "UPSTREAM_DRIFT: newest Argentum main differs from the PR pin; rebase may be required")
        result["advice"].append("Use weekly.py rebase/finish against the exact upstream SHA; do not assume patch applicability.")
    added_patches = [
        PATCH_RE.match(f["filename"]).group(1)
        for f in files
        if PATCH_RE.match(f["filename"]) and f.get("status") in ("added", "modified", "renamed")
    ]
    for patch in added_patches:
        if patch not in mandatory and patch != EXP_PATCH:
            result["errors"].append("UNTRACKED_ENGINE_PATCH: " + patch + " absent from MANDATORY")
            result["advice"].append("Integrate the patch into audit_patches.py MANDATORY before treating it as a ship-ready engine change.")
    if added_patches:
        result["warnings"].append("SPLIT_LANDING: engine patch edits are still isolated on this PR")
        result["advice"].append(
            "Prepare a separately tested engine-only PR into development; land cards/UI after engine review.")
        created = dt.datetime.fromisoformat(pr["created_at"].replace("Z", "+00:00"))
        age = (now - created).total_seconds() / 86400
        if age >= 2:
            result["warnings"].append(f"LONG_LIVED_ENGINE_PATCH: {age:.1f} days since PR creation")
            result["advice"].append("Split out and land reviewed engine changes instead of waiting for unrelated card/UI work.")
    # A weekly warning is not a failing source test; no upstream code has been built here.
    return result


class Api:
    def __init__(self, token: str):
        if not token:
            raise ValueError("GITHUB_TOKEN required: GitHub PR metadata is rate limited anonymously")
        self.token = token

    def get(self, path: str) -> object:
        req = urllib.request.Request(
            "https://api.github.com" + path,
            headers={"Authorization": "Bearer " + self.token,
                     "Accept": "application/vnd.github+json",
                     "X-GitHub-Api-Version": "2022-11-28",
                     "User-Agent": "arcana-readonly-engine-pr-guard"})
        with urllib.request.urlopen(req, timeout=20) as response:
            return json.load(response)

    def pages(self, path: str) -> list:
        items = []
        for page in range(1, 21):
            join = "&" if "?" in path else "?"
            batch = self.get(path + join + f"per_page=100&page={page}")
            if not isinstance(batch, list):
                raise ValueError("Expected list response while paging GitHub REST")
            items.extend(batch)
            if len(batch) < 100:
                break
        else:
            raise ValueError("Pagination limit reached; refusing partial coverage claim")
        return items

    def content(self, repo: str, path: str, sha: str) -> str:
        if not REPO_RE.fullmatch(repo) or not SHA_RE.fullmatch(sha):
            raise ValueError("Invalid exact repo or commit SHA")
        qpath = urllib.parse.quote(path, safe="/")
        data = self.get(f"/repos/{repo}/contents/{qpath}?ref={sha}")
        if data.get("encoding") != "base64":
            raise ValueError(f"Cannot inspect {path} at {sha}: not base64 file contents")
        return base64.b64decode(data["content"], validate=False).decode("utf-8")


def latest_upstream() -> str:
    done = subprocess.run(
        ["git", "ls-remote", "https://github.com/wingedsheep/argentum-engine.git",
         "refs/heads/main"], capture_output=True, text=True, timeout=30, check=True)
    sha = done.stdout.partition("\t")[0]
    if not SHA_RE.fullmatch(sha):
        raise ValueError("Unable to resolve exact upstream Argentum main SHA")
    return sha


def scan(api: Api, repo: str, dev: str, number: int | None,
         upstream_sha: str, now: dt.datetime) -> list[dict]:
    if not REPO_RE.fullmatch(repo):
        raise ValueError("Invalid GITHUB_REPOSITORY")
    dev_meta = api.get(f"/repos/{repo}/branches/{urllib.parse.quote(dev, safe='')}")
    dev_sha = dev_meta["commit"]["sha"]
    if not SHA_RE.fullmatch(dev_sha):
        raise ValueError("Development branch did not resolve to an exact SHA")
    dev_pin = read_pin(api.content(repo, "engine-upgrade/audit_patches.py", dev_sha))
    if number:
        prs = [api.get(f"/repos/{repo}/pulls/{number}")]
    else:
        prs = api.pages(f"/repos/{repo}/pulls?state=open")
    results = []
    for pr in prs:
        if pr.get("state") != "open":
            continue
        files = api.pages(f"/repos/{repo}/pulls/{pr['number']}/files")
        if not any(is_engine_file(f["filename"]) for f in files):
            continue
        head = pr["head"]
        head_repo = (head.get("repo") or {}).get("full_name") or repo
        audit = None
        try:
            audit = api.content(head_repo, "engine-upgrade/audit_patches.py", head["sha"])
        except (urllib.error.HTTPError, ValueError, KeyError):
            pass
        result = evaluate(pr, files, audit, dev_pin, upstream_sha, now)
        if result:
            result["dev_sha"] = dev_sha
            results.append(result)
    return results


def report(rows: list[dict], *, dev: str) -> str:
    out = [f"## Read-only Argentum engine PR guard ({dev})", "",
           "An upstream-drift warning is **not** a compilation or rules-test verdict.",
           "The patch series and every card scenario still require exact-target verification.", "",
           "| PR | Development pin | PR pin | Findings |",
           "|---|---|---|---|"]
    for r in rows:
        findings = r["errors"] + r["warnings"]
        out.append(
            f"| [#{r['pr']}]({r['url']}) | \`{r['dev_pin'][:10]}\` | "
            f"\`{r.get('head_pin', '?')[:10]}\` | " + "; ".join(findings).replace("|", "/") + " |")
        for item in dict.fromkeys(r["advice"]):
            out.append(f"  - #{r['pr']}: {item}")
    if not rows:
        out.append("| — | — | — | No open PR changing engine patches/pins/runtime |")
    return "\n".join(out) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", "AARMOU/arcana_table"))
    parser.add_argument("--development", default="gpt/arcana-integration-next")
    parser.add_argument("--pr", type=int)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--upstream-sha")  # tests may pass exact SHA without network resolution
    args = parser.parse_args()
    if args.pr is not None and args.pr < 1:
        parser.error("--pr must be positive")
    upstream = args.upstream_sha or latest_upstream()
    if not SHA_RE.fullmatch(upstream):
        parser.error("--upstream-sha requires exactly 40 lowercase hex characters")
    try:
        results = scan(Api(os.getenv("GITHUB_TOKEN", "")), args.repo, args.development, args.pr,
                       upstream, dt.datetime.now(dt.timezone.utc))
    except (ValueError, urllib.error.HTTPError, urllib.error.URLError,
            subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
        print(f"::error title=Engine PR guard could not verify inputs::{exc}", file=sys.stderr)
        return 2  # fail closed instead of reporting that no PRs need work
    doc = report(results, dev=args.development)
    print(doc, end="")
    summary = os.getenv("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as file:
            file.write(doc)
    if args.output:
        args.output.write_text(json.dumps({"upstream_sha": upstream, "results": results}, indent=2) + "\n")
    count = sum(bool(r["errors"]) for r in results)
    if count:
        print(f"::error title=Engine PR guard::{count} engine PR(s) need fixes before integration",
              file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
