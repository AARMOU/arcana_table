#!/usr/bin/env python3
"""Read-only #81 comment deltas without sending the full history into agent context.

Run from anywhere: python3 scripts/issue81_delta.py [--after-id N] [--full]
Requires only Python stdlib; GH_TOKEN or GITHUB_TOKEN is optional for the public repo.
"""
import argparse
import json
import os
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

REPO = "AARMOU/arcana_table"
STATUS = Path(__file__).resolve().parents[1] / "docs/coordination/CURRENT-STATUS.md"
API = f"https://api.github.com/repos/{REPO}"


def read_marker():
    match = re.search(r"^issue81_last_comment_id:\s*(\d+)\s*$", STATUS.read_text(), re.M)
    if not match:
        raise ValueError(f"Missing issue81_last_comment_id in {STATUS}")
    return int(match.group(1))


def get_json(url):
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "arcana-issue81-delta-read-only",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    token = os.getenv("GH_TOKEN") or os.getenv("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    with urlopen(Request(url, headers=headers), timeout=20) as response:
        return json.load(response)


def collect_comments(after_id):
    # Fetch the marker's authoritative timestamp so we do not need to refetch
    # the full historical thread. Overlap one minute for timestamp precision.
    marker = get_json(f"{API}/issues/comments/{after_id}")
    if marker.get("id") != after_id:
        raise ValueError("Baseline comment ID was not confirmed by GitHub")
    when = datetime.fromisoformat(marker["created_at"].replace("Z", "+00:00"))
    since = (when - timedelta(minutes=1)).astimezone(timezone.utc)
    since_iso = since.isoformat(timespec="seconds").replace("+00:00", "Z")

    fresh = []
    for page in range(1, 51):
        qs = urlencode({"per_page": 100, "page": page, "since": since_iso})
        batch = get_json(f"{API}/issues/81/comments?{qs}")
        if not isinstance(batch, list):
            raise ValueError("GitHub returned a non-list comment collection")
        fresh.extend(item for item in batch if int(item["id"]) > after_id)
        if len(batch) < 100:
            return sorted(fresh, key=lambda item: item["id"])
    raise RuntimeError("Reached 50 pages; refusing to claim a complete delta")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--after-id", type=int, help="override snapshot marker for this run")
    parser.add_argument("--full", action="store_true", help="show full new comment bodies")
    parser.add_argument("--limit", type=int, default=12, help="maximum comments per run (default: 12)")
    args = parser.parse_args()
    if args.limit < 1:
        parser.error("--limit must be at least 1")

    after_id = args.after_id if args.after_id is not None else read_marker()
    if after_id < 1:
        parser.error("--after-id must be positive")
    comments = collect_comments(after_id)
    print(f"Issue #81: {len(comments)} newer comment(s) after {after_id}.")
    for item in comments[:args.limit]:
        body = item.get("body") or ""
        heading = re.search(r"^#{1,4}\s+(.+)$", body, re.M)
        title = heading.group(1) if heading else "(no heading)"
        url = item.get("html_url") or f"https://github.com/{REPO}/issues/81"
        print(f"\n--- #{item['id']} | {item.get('created_at', '?')} | {title} ---")
        print(url)
        if args.full:
            print(body.strip())
        else:
            preview = re.sub(r"\s+", " ", body).strip()
            print(preview[:650] + (" ... [TRUNCATED; use --full]" if len(preview) > 650 else ""))
    if len(comments) > args.limit:
        last_printed = comments[args.limit - 1]["id"]
        print(f"\n{len(comments) - args.limit} more comment(s) NOT SHOWN. Continue with --after-id {last_printed}.")
    if not args.full and comments:
        print("\nSummaries are for triage only. Read relevant full comments and verify live evidence before action.")
    # Never write state: advancing the canonical marker requires a reviewed snapshot update.


if __name__ == "__main__":
    try:
        main()
    except (OSError, HTTPError, URLError, ValueError, KeyError, RuntimeError) as exc:
        print(f"Issue #81 delta failed (no state was changed): {exc}", file=sys.stderr)
        sys.exit(1)
