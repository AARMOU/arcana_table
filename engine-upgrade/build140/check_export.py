#!/usr/bin/env python3
"""Gate 2: after `:custom-cards:exportCards`, the only change to the committed custom-card data may be
the two new Eldrazi lands. Anything else (a changed or removed card) fails, so the rebuilt SDK cannot
silently alter other cards. Run from the repository root."""
import json
import subprocess
import sys

EXPECTED = {"Eldrazi Temple", "Corrupted Crossroads"}
ok = True
for path, name_of in (("android/app/src/main/assets/argentum/custom-cards.jsonl", lambda l: json.loads(l)["name"]),
                      ("android/app/src/main/assets/argentum/custom-cards.index.tsv", lambda l: l.split("\t")[0])):
    diff = subprocess.run(["git", "diff", "-U0", "--", path], capture_output=True, text=True, check=True).stdout
    added = [l[1:] for l in diff.splitlines() if l.startswith("+") and not l.startswith("+++")]
    removed = [l[1:] for l in diff.splitlines() if l.startswith("-") and not l.startswith("---")]
    names = {name_of(l) for l in added}
    print(f"{path}: +{len(added)} -{len(removed)} added={sorted(names)}")
    if removed or names != EXPECTED or len(added) != len(EXPECTED):
        ok = False
        print(f"::error title=gate2-export::{path}: expected exactly {sorted(EXPECTED)} added and nothing else; "
              f"got added={sorted(names)[:10]} removed={len(removed)}")
print(f"::{'notice' if ok else 'error'} title=gate2-export::custom-card data change {'is exactly the two Eldrazi lands' if ok else 'is unexpected'}")
sys.exit(0 if ok else 1)
