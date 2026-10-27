#!/usr/bin/env python3
"""Inject the isolated primitive test, run it, preserve its failure, and clean up."""
from pathlib import Path
import shutil, subprocess, sys
here=Path(__file__).resolve().parent
root=here.parents[2]
target=root/'android/custom-cards/src/test/kotlin/com/arcanatable/customcards/BenthicCollectionStatProbeTest.kt'
if target.exists():raise SystemExit(f'Refusing to overwrite {target}')
try:
    shutil.copyfile(here/'BenthicCollectionStatProbeTest.kt',target)
    cmd=sys.argv[1:] or ['./gradlew','--no-daemon','--max-workers=2',':custom-cards:test','--tests','com.arcanatable.customcards.BenthicCollectionStatProbeTest']
    result=subprocess.run(cmd,cwd=root/'android')
finally:
    target.unlink(missing_ok=True)
raise SystemExit(result.returncode)
