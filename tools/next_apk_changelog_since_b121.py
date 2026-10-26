#!/usr/bin/env python3
"""Stage the verified Build 122–134 changelog in Arcana's existing What's new UI.

This is a deterministic runner-only source port, invoked AFTER integrating the
latest game/menu changes. Leaves Claude's lobby and the working release branch
untouched. Next test build features are separated from the earlier verified Build 134 preview.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

UI = Path("android/app/src/main/java/com/arcanatable/TableDialogs.kt")
INSERT_BEFORE = '                Text("BUILD 123 · LARGE END-STEP AI FIX", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)'
ARCHIVE_BEFORE = '                Text("BUILDS 105–106 · NEW CARDS AND DECKS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)'
BEGIN = '                // BEGIN changelog-since-build121 — generated next-APK release notes'
END = '                // END changelog-since-build121'
ARCHIVE_MARKER = '                Text("EARLIER HISTORY · BUILDS 105 AND BEFORE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)'

# Cumulative changes grouped by verified development milestone; a build number
# is NOT evidence of physical-device acceptance or publication to Google Play.
ENTRIES: list[tuple[str, list[str]]] = [
    ("BUILD 134 · CURRENT NO-GOOSE TEST PREVIEW", [
        "The original Arcana launcher icon is restored. The oversized developer goose is removed from the home screen; debug test scenarios remain available in the debug APK.",
        "A coherent source-built Argentum engine, Ulalek/stack-copying/restricted-mana tests and 94 indexed original Eldrazi Incursion slots are included. Indexed cards are not all independently gameplay-certified.",
        "Offline AI has Easy, Normal, Hard and Brutal options with bounded searches, persistence and recovery from rejected actions. Harder levels still need device and broad win-rate testing.",
        "The 2–4-player online/Wi-Fi candidate uses an authoritative host, per-seat private game state, human/AI seat selection and reconnect handling. Cross-network gameplay on real phones still needs acceptance testing.",
        "The interactive tutorial has 20 chapters, 160 steps, a 31-term glossary and four offline engine-backed lessons. The Table-view HAND step-two fix is prepared for the next test candidate, NOT in the previous no-goose Build 134 APK.",
        "Help & Diagnostics has the Engine running notice toggle, an original-logo check and cleaner menu controls. Card/priority/deck and multiplayer fixes were regression-tested in CI.",
        "Build 134 here means the validated TEST preview, not a Play Store release; import and deck-edit features are not enabled in the production-style variant."
    ]),
    ("NEXT TEST APK · LOBBY, TUTORIAL AND DECK TOOLS", [
        "Full-screen multiplayer lobby: commander portraits, room-code copy/share, READY UP, 2–4 seats, host AI/friend switching, player removal and Start only when guests are ready. Cloudflare Worker is updated; on-phone lobby testing is still pending.",
        "Your First Turn tutorial: the Table-view HAND button advances step two to Play a land, matching Player view. Full Fold and real-engine lesson acceptance remain pending.",
        "Import new now opens the existing unified deck importer; the Deck Editor is enabled on the next test candidate using the same saved-deck library, with search, editing and replacement explanations. Both require Android device validation before release."
    ]),
    ("BUILD 133 · ELDRAZI AND ENGINE CANDIDATE", [
        "Combined newer Argentum source and Eldrazi Incursion's 94th original indexed slot (Azlask) with real-card scenarios and strict terminal AI/deck testing.",
        "Build 133 attempts exposed a shock-fetch/Android unit-test regression; fixes and passing exact-source tests were required before any subsequent preview. A test attempt is not a released upgrade."
    ]),
    ("BUILD 132 · ENGINE, DECK AND ANDROID INTEGRATION", [
        "Prepared 93 original Eldrazi Incursion slots, updated Argentum runtime patches and Ulalek stack-copying, Eldrazi Temple and Corrupted Crossroads scenarios.",
        "The existing developer-only deck builder searches the packaged playable card library offline, validates Commander lists, supports undo/redo and suggests replacements without silently changing a deck.",
        "Fixed Android-incompatible regular expressions that crashed the deck editor and added automatic regex guards. The original Build 132 combined validation did not establish a releasable APK."
    ]),
    ("BUILD 131 · ARGENTUM AND PRECON INTEGRATION", [
        "Upgraded the on-device Argentum engine, rebuilt coherent runtime JARs and card assets, and established repeatable hash-checked runtime packaging.",
        "Expanded official Warhammer and Eldrazi precon work and custom-card testing; not every card has complete rules support.",
        "Improved game-result diagnostics and rules-regression coverage. Priority stops, token-heavy performance and more rules cases remained follow-up work."
    ]),
    ("BUILDS 124–130 · GAMEPLAY AND DEBUG EVOLUTION", [
        "Iterative fixes and investigations covered Treasure mana, legal cast/payment feedback, complex token/trigger performance, opening-hand overlays, and Lunar Whale, Iron Spider and Myriad rules.",
        "Improved source-built engine integration, diagnostics and repeatable card/AI scenario testing. Several items remained open; inclusion here does not claim all bugs are fixed.",
        "Further official precon and custom-card implementations were staged across development branches rather than shipped uniformly in every intermediate APK."
    ]),
]

# The older two sections, Build 123 and Build 122, are kept unchanged and appear
# immediately below these entries. Build 121 itself is a baseline, not an upgrade.

def compose_entries() -> str:
    lines = [
        BEGIN,
        '                Text("UPGRADES SINCE BUILD 121 · VERIFIED VS. PREPARED", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)',
        '                Text("Builds 122–134 include historical previews and development milestones. The next test APK entries are candidate features, not a Play Store release. Passing compilation is not real-device acceptance.", color = SoftWhite, fontSize = 12.sp)',
    ]
    for title, bullets in ENTRIES:
        lines.append('                Text(' + json.dumps(title, ensure_ascii=False) + ', color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)')
        lines.append('                Text(' + json.dumps("\n".join("• " + item for item in bullets), ensure_ascii=False) + ', color = SoftWhite, fontSize = 12.sp)')
    lines += [END]
    return "\n".join(lines) + "\n"


def patched(source: str) -> str:
    if source.count(BEGIN) == 1 and source.count(END) == 1 and source.count(ARCHIVE_MARKER) == 1:
        validate(source)
        return source
    if any(marker in source for marker in (BEGIN, END, ARCHIVE_MARKER)):
        raise ValueError("Partial changelog port; refusing to duplicate or overwrite")
    if source.count(INSERT_BEFORE) != 1 or source.count(ARCHIVE_BEFORE) != 1:
        raise ValueError("Existing What's new Build 123 or pre-121 archive changed; review source")
    if 'title = { Text("What\'s new · Build ${BuildConfig.VERSION_CODE}") }' not in source:
        # VERSION_CODE uses a Kotlin interpolation, never pin the menu to 134.
        if 'title = { Text("What\'s new · Build ${BuildConfig.VERSION_CODE}") }'.replace("\\$", "$") not in source:
            raise ValueError("Dynamic version title changed; review source")
    out = source.replace(INSERT_BEFORE, compose_entries() + INSERT_BEFORE, 1)
    out = out.replace(ARCHIVE_BEFORE, ARCHIVE_MARKER + "\n" + ARCHIVE_BEFORE, 1)
    validate(out)
    return out


def validate(source: str) -> None:
    assert source.count(BEGIN) == source.count(END) == source.count(ARCHIVE_MARKER) == 1
    assert source.count(INSERT_BEFORE) == source.count(ARCHIVE_BEFORE) == 1
    assert source.index(BEGIN) < source.index(INSERT_BEFORE) < source.index(ARCHIVE_MARKER)
    for title, _ in ENTRIES:
        assert source.count(json.dumps(title, ensure_ascii=False)) == 1, title
    assert 'BUILD 122 · FIXES AND QUICK UI TIPS' in source
    assert 'BUILD 123 · LARGE END-STEP AI FIX' in source
    assert 'What\'s new · Build ${BuildConfig.VERSION_CODE}' in source


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=UI)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    before = args.source.read_text()
    after = patched(before)
    if args.verify_only and after != before:
        raise SystemExit("FAIL: Build 121+ What's new changelog not yet injected")
    if not args.verify_only and after != before:
        args.source.write_text(after)
    validate(after)
    print("PASS: What's new lists Build 122 through Build 134, plus clearly marked next APK")
    print("PASS: existing pre-121 history retained; Build 122/123 original wording preserved")


if __name__ == "__main__":
    main()
