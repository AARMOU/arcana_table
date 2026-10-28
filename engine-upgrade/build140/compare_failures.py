#!/usr/bin/env python3
"""compare_failures.py CANDIDATE BASELINE  Acceptance against unmodified B140: the candidate may not
fail any test that B140 itself passes. Tests B140 already fails are reported, never hidden."""
import os
import sys
from pathlib import Path

cand = set(Path(sys.argv[1]).read_text().split())
base = set(Path(sys.argv[2]).read_text().split())
new, fixed, still = sorted(cand - base), sorted(base - cand), sorted(cand & base)
print(f"::{'error' if new else 'notice'} title=vs B140::new failures={len(new)} fixed={len(fixed)} still failing on B140 too={len(still)}")
for t in new[:15]:
    print(f"::error title=NEW failure::{t}")
for t in fixed[:15]:
    print(f"::notice title=FIXED vs B140::{t}")
for t in still[:30]:
    print(f"pre-existing on B140: {t}")
with open(os.environ.get("GITHUB_OUTPUT", os.devnull), "a") as f:
    f.write(f"no_new_failures={'true' if not new else 'false'}\n")
