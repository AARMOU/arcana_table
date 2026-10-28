#!/usr/bin/env python3
"""Count JUnit XML results: count_junit.py LABEL GLOB [CLASS_REGEX]. Prints totals and failed cases."""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

label, pattern = sys.argv[1], sys.argv[2]
only = re.compile(sys.argv[3]) if len(sys.argv) > 3 else None
c = dict(tests=0, failures=0, errors=0, skipped=0)
failed, files = [], 0
for x in sorted(Path(".").glob(pattern)):
    if only and not only.search(x.name):
        continue
    files += 1
    root = ET.parse(x).getroot()
    for k in c:
        c[k] += int(root.get(k, "0"))
    for tc in root.iter("testcase"):
        if tc.find("failure") is not None or tc.find("error") is not None:
            failed.append(f"{tc.get('classname')}.{tc.get('name')}")
print(f"{label}: files={files} {c}")
for f in failed[:25]:
    print("  FAILED", f)
