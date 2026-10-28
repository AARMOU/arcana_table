#!/usr/bin/env python3
"""Run the unregistered candidate without changing assets or runtime jars. Nonzero is expected today."""
from pathlib import Path
import shutil
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
BASE = ROOT / 'android/custom-cards/src'
files = [
    (HERE / 'candidate/HideousTaskmaster.kt', BASE / 'main/kotlin/com/arcanatable/customcards/cards/HideousTaskmaster.kt'),
    (HERE / 'candidate/HideousTaskmasterScenarioTest.kt', BASE / 'test/kotlin/com/arcanatable/customcards/HideousTaskmasterScenarioTest.kt'),
]
for _, target in files:
    if target.exists():
        raise SystemExit(f'Refusing to overwrite {target}')
try:
    for source, target in files:
        shutil.copyfile(source, target)
    # Optional command permits an Android-free JVM harness using these same source directories.
    command = sys.argv[1:] or ['./gradlew', '--no-daemon', '--max-workers=2', ':custom-cards:test', '--tests', 'com.arcanatable.customcards.HideousTaskmasterScenarioTest']
    result = subprocess.run(command, cwd=ROOT / 'android')
finally:
    for _, target in files:
        target.unlink(missing_ok=True)
raise SystemExit(result.returncode)
