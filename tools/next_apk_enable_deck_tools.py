#!/usr/bin/env python3
"""Enable existing unified deck importer/editor in next test APK, preserving gameplay.

Owner approved home Import new and normal-app Deck Editor. No new importer,
no migration, no auto-replace of unsupported cards, no bypass of Argentum deck
validation. Applies only on the disposable combined source after other merges.
"""
from __future__ import annotations
import argparse
from pathlib import Path

HOME = Path("android/app/src/main/java/com/arcanatable/HomeScreen.kt")
BUILDER = Path("android/app/src/main/java/com/arcanatable/DeckBuilder.kt")
PICKER = Path("android/app/src/main/java/com/arcanatable/TableOverlays.kt")

OLD_HOME = '''                            // Import is parked for now (plumbing kept in onImport): greyed, tap says so.
                            val homeContext = androidx.compose.ui.platform.LocalContext.current
                            OutlinedButton(
                                onClick = { android.widget.Toast.makeText(homeContext, "Not yet available", android.widget.Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("home-import"),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PanelLight.copy(alpha = 0.5f)),
                            ) { Text("Import new", color = Muted.copy(alpha = 0.6f), fontWeight = FontWeight.Bold) }'''
NEW_HOME = '''                            // Existing unified importer: same onImport callback as the deck picker.
                            OutlinedButton(
                                onClick = onImport,
                                enabled = !argentumActive,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("home-import"),
                            ) { Text("Import new", color = SoftWhite, fontWeight = FontWeight.Bold) }'''
OLD_FLAG = '    val enabled: Boolean get() = BuildConfig.DEBUG'
NEW_FLAG = '    val enabled: Boolean get() = true // Owner-approved Deck Editor in next release-look test APK'
OLD_PICKER = '                    Text("Deck builder · developer preview", color = Mint, fontWeight = FontWeight.Bold)'
NEW_PICKER = '                    Text("Deck editor · library", color = Mint, fontWeight = FontWeight.Bold)'


def one_port(content: str, before: str, after: str, label: str) -> str:
    if content.count(after) == 1 and content.count(before) == 0:
        return content
    if content.count(before) != 1 or content.count(after) != 0:
        raise ValueError(f"{label}: expected exact legacy snippet exactly once (fail closed)")
    return content.replace(before, after, 1)


def transform(home: str, builder: str, picker: str) -> tuple[str, str, str]:
    result_home = one_port(home, OLD_HOME, NEW_HOME, "Home Import new")
    result_builder = one_port(builder, OLD_FLAG, NEW_FLAG, "DeckBuilderFlag")
    result_home = one_port(result_home, OLD_PICKER, NEW_PICKER, "DeckPicker deck editor label")
    result_picker = picker
    # Verify this remains the one real deck-import service, not a second parser.
    if 'onImport = { selectedPlayer = localSeat; importError = null; importOpen = true }' not in result_picker:
        raise ValueError("Original Home -> importOpen dispatch missing")
    if 'if (deckBuilderOpen && DeckBuilderFlag.enabled) DeckManagerDialog(' not in result_picker:
        raise ValueError("Existing DeckManagerDialog -> savedDecks editor dispatch missing")
    if 'onDecksChanged = { updated -> savedDecks = updated; saveSavedDecks(context, updated) }' not in result_picker:
        raise ValueError("Deck editor does not persist through shared saved-decks storage")
    if result_home.count('onClick = onImport,') != 1:
        raise ValueError("Home Import new must use the EXISTING importer exactly once")
    if 'enabled = !argentumActive' not in result_home:
        raise ValueError("Cannot open import while current Argentum game is active")
    if 'BuildConfig.DEBUG' in result_builder[result_builder.index('internal object DeckBuilderFlag'):result_builder.index('internal object DeckBuilderFlag') + 150]:
        raise ValueError("Deck editor is still debug-gated")
    return result_home, result_builder, result_picker


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--verify-only", action="store_true")
    p.add_argument("--home", type=Path, default=HOME)
    p.add_argument("--builder", type=Path, default=BUILDER)
    p.add_argument("--picker", type=Path, default=PICKER)
    args = p.parse_args()
    old = (args.home.read_text(), args.builder.read_text(), args.picker.read_text())
    new = transform(*old)
    if args.verify_only and old != new:
        raise SystemExit("FAIL: Import new and Deck Editor not enabled in combined APK source")
    if not args.verify_only:
        for path, before, after in zip((args.home,args.builder,args.picker),old,new):
            if before != after:
                path.write_text(after)
    print("PASS: Home Import new → preexisting importOpen dialog (disabled during game)")
    print("PASS: Deck Editor flag enabled on BOTH test APK variants; existing saved decks")
    print("PASS: import/editor wiring through shared deck library; no duplicate importer")


if __name__ == "__main__":
    main()
