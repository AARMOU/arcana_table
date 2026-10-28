#!/usr/bin/env python3
"""failed_tests.py OUT GLOB...  Write the sorted ids (class.name) of every failed/errored JUnit case."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

out, failed = sys.argv[1], set()
for pattern in sys.argv[2:]:
    for x in Path(".").glob(pattern):
        for tc in ET.parse(x).getroot().iter("testcase"):
            if tc.find("failure") is not None or tc.find("error") is not None:
                failed.add(f"{tc.get('classname')}.{tc.get('name')}")
Path(out).write_text("".join(f"{f}\n" for f in sorted(failed)))
print(f"{len(failed)} failed test(s) written to {out}")
