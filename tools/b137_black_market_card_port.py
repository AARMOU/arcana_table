#!/usr/bin/env python3
"""Temporary, reviewable B137 source port for Black Market Connections.

The pinned engine/card branch contained this custom card, but the Build136
base omitted it; Claude's deck-library regression correctly detected the
loss after full source integration. Run AFTER fast_apk_snapshot.py source and
BEFORE card asset export on a disposable CI runner only.

No branch merges, APKs, signing, or external deployment.
"""
from pathlib import Path
import subprocess

REVIEWED_SHA = "5a64b631b9d7185c7fb4102a58afa9aa237746c0"
FILES = {
    "android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/BlackMarketConnections.kt":
        "cf8d08d8308349ee075c33adbce024dd02020427",
}
REGISTRY = Path("android/custom-cards/src/main/kotlin/com/arcanatable/customcards/CustomCards.kt")
ANCHOR = '        Entry(SpringleafParade, "ECC", HAZEL),'
ADD = '        Entry(BlackMarketConnections, "MSC", HAZEL),'

def run(*cmd: str) -> str:
    return subprocess.check_output(cmd, text=True).strip()

def main() -> None:
    run("git", "fetch", "--no-tags", "origin", REVIEWED_SHA)
    assert run("git", "rev-parse", REVIEWED_SHA) == REVIEWED_SHA
    for path, blob_sha in FILES.items():
        target = Path(path)
        assert not target.exists(), f"unexpected already existing source: {path}"
        data = subprocess.check_output(["git", "show", f"{REVIEWED_SHA}:{path}"])
        actual = subprocess.check_output(["git", "hash-object", "--stdin"], input=data).decode().strip()
        assert actual == blob_sha, (path, actual)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        print("RESTORED exact reviewed card/scenario", path, blob_sha)
    # B137 already has a regression exercising the authoritative upstream
    # Argentum card. Do NOT replace that test with the older custom scenario.
    existing_scenario = Path("android/custom-cards/src/test/kotlin/com/arcanatable/customcards/BlackMarketConnectionsScenarioTest.kt")
    assert existing_scenario.is_file(), "Upstream engine scenario missing"
    assert run("git", "hash-object", str(existing_scenario)) == "d3889e0c8e30b5d24922f67c211e819939d61343", "Upstream engine scenario changed; review required"
    assert 'CardPool.driver("Black Market Connections")' in existing_scenario.read_text()
    print("PASS: retained existing upstream Argentum rules scenario without modification")
    raw = REGISTRY.read_text()
    assert raw.count(ANCHOR) == 1, "registry insertion anchor has changed"
    assert ADD not in raw, "duplicate Black Market Connections registration"
    REGISTRY.write_text(raw.replace(ANCHOR, ANCHOR + "\n" + ADD, 1))
    assert REGISTRY.read_text().count(ADD) == 1
    print("PASS: Black Market Connections registered from reviewed source; no APK")

if __name__ == "__main__":
    main()
