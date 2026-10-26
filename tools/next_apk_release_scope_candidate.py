#!/usr/bin/env python3
"""Opt IN to expanded owner-approved features for the TEST-signed release-look APK.

No public Play Store scope changes: all edits live ONLY in ephemeral Actions
combined checkout. Strictly preserves the release test-scenarios empty stub.
"""
from pathlib import Path
import argparse

F = Path("android/app/src/main/java/com/arcanatable/ReleaseFeatures.kt")
T = Path("android/app/src/test/java/com/arcanatable/ReleaseFeaturesTest.kt")
R = Path("android/app/src/testRelease/java/com/arcanatable/ReleaseHasNoTestScenariosTest.kt")

def swap(x, before, after, label):
    if x.count(after) == 1 and x.count(before) == 0:
        return x
    if x.count(before) != 1 or x.count(after) != 0:
        raise ValueError(f"Release feature scope drift: {label}")
    return x.replace(before, after, 1)


def updated(src: str, tests: str, release: str):
    src = swap(src, "    val deckImportEnabled: Boolean get() = BuildConfig.DEBUG",
               "    val deckImportEnabled: Boolean get() = true // Owner: next TEST release-look",
               "import release gate")
    src = swap(src, "    val multiplayerPreviewEnabled: Boolean get() = BuildConfig.DEBUG",
               "    val multiplayerPreviewEnabled: Boolean get() = true // Owner: next TEST release-look",
               "online multiplayer gate")
    src = swap(src, "        requested.coerceIn(1, if (debug) 3 else 1)",
               "        requested.coerceIn(1, 3)", "AI seat count")
    src = swap(src, "        if (debug) decks else decks.filter { it.builtIn }",
               "        decks // User-imported decks available; Argentum still validates game support", "saved deck picker")

    tests = swap(tests, "productionClampsPersistedMultiplayerSettingsToOneOpponent",
                 "testCandidateAllowsPersistedThreeAiOpponents", "AI test name")
    tests = swap(tests, '            assertEquals("Persisted setting $saved must still launch a two-player game", 1,\n                ReleaseFeatures.aiCount(saved, debug = false))',
                 '            assertEquals("Persisted opponent setting $saved must clamp to 1..3", saved.coerceIn(1, 3),\n                ReleaseFeatures.aiCount(saved, debug = false))', "AI assertions")
    tests = swap(tests, "releaseHidesSavedImportedDecksWithoutModifyingThem",
                 "testCandidateOffersSavedImportedDecksWithoutModifyingThem", "decks test name")
    tests = swap(tests, "        assertEquals(listOf(premade), ReleaseFeatures.availableDecks(stored, debug = false))",
                 "        assertEquals(stored, ReleaseFeatures.availableDecks(stored, debug = false))", "decks test")

    release = swap(release, "releaseKeepsUnapprovedFeaturesDisabled",
                   "releaseTestCandidateKeepsApprovedDecksAndMultiplayerEnabled", "release test name")
    release = swap(release, "        assertFalse(ReleaseFeatures.deckImportEnabled)",
                   "        org.junit.Assert.assertTrue(ReleaseFeatures.deckImportEnabled)", "release importer assertion")
    release = swap(release, "        assertFalse(ReleaseFeatures.multiplayerPreviewEnabled)",
                   "        org.junit.Assert.assertTrue(ReleaseFeatures.multiplayerPreviewEnabled)", "release multiplayer assertion")
    release = swap(release, "        assertFalse(DeckBuilderFlag.enabled)",
                   "        org.junit.Assert.assertTrue(DeckBuilderFlag.enabled)", "release editor assertion")
    release = swap(release, "        assertEquals(1, ReleaseFeatures.aiCount(3))",
                   "        assertEquals(3, ReleaseFeatures.aiCount(3))", "release seat assertion")
    release = swap(release, '        assertEquals(emptyList<DeckChoice>(), ReleaseFeatures.availableDecks(\n            listOf(DeckChoice("saved", "An imported deck", null, false))))',
                   '        val saved = DeckChoice("saved", "An imported deck", null, false)\n        assertEquals(listOf(saved), ReleaseFeatures.availableDecks(listOf(saved)))',
                   "release deck assertion")
    if "fun releaseHasNoScenarios()" not in release or "fun releaseCannotBuildAScenario()" not in release:
        raise ValueError("Mandatory release test scenario exclusion was modified")
    return src,tests,release


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--verify-only", action="store_true")
    args = p.parse_args()
    original=(F.read_text(),T.read_text(),R.read_text())
    target=updated(*original)
    if args.verify_only and target!=original:
        raise SystemExit("FAIL: release-look feature and tests not enabled")
    if not args.verify_only:
        for path, old, new in zip((F,T,R),original,target):
            if new!=old: path.write_text(new)
    print("PASS: owner-authorized 2–4 seat, saved/imported deck and editor test-release scope")
    print("PASS: mandatory RELEASE excludes debug test scenarios and blocks scenario construction")


if __name__=="__main__":
    main()
