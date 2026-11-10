# GooseClippy implementation handoff (Nov 10, 2026)

**Ownership:** Goose owns the assistant; Claude owns the Windows XP desktop and game layout.

## What is implemented on this branch

- `GooseClippyLines.kt`: 228 unique lines (19 observed game event categories), complete shuffle-bag rotation.
- `GooseClippyFeature.kt`: persistent independent ON/OFF setting; fully hidden/silent when OFF; Studio control; transparent animated GIF presentation; draggable floating assistant and XP-ish speech bubble; real game observation adapter; no rules actions.
- `GooseClippyLinesTest.kt` and `GooseClippyPreferencesTest.kt`: recurrence, event grounding and persistence checks.
- One small insertion into `StudioEditorPanels.kt` under **Layout → Presets**. No Forge, XP layout or card rendering changes.

## Approved visual assets — required for the completed skin

These are *not yet committed as binary files on this branch*; import the owner's `gooseclippy-android-assets.zip` into `android/`. It contains:

- `app/src/main/assets/gooseclippy_idle.gif`: 240×360 transparent GIF, stationary body, blinking eyes, 5 frames, 119,843 bytes, SHA256 `16dde375e89d564b90b160cb460ee42b69dfd2763caaff6d91ce0e3ca8979e92`
- `app/src/main/assets/gooseclippy_react.gif`: 240×360 transparent GIF, original animated reaction, 12 frames, 170,569 bytes, SHA256 `40e4833fdb5b69ce1184859a0f14e176571f22cc25cbdf00f2cec9f771c6d959`

**Do not substitute** a newly drawn paperclip or change Magic card art. If either asset is missing, a visible red diagnostic placeholder appears rather than silently substituting different artwork.

The assistant uses Android's native `ImageDecoder` on API 28+; older devices use a static frame of the supplied GIF.

## One-call integration in Claude's actual XP desktop

Inside the final XP composable's root `Box` (after game windows, before modal dialogs so dialogs remain accessible):

```kotlin
GooseClippyAssistant(observation = tableController.gooseClippyObservation())
```

This overlay is independently enabled or disabled via `GooseClippyPreferences` and **must not** modify the XP layout or Forge rules. If the XP screen is not active, omit the overlay; the preference is still remembered.

Add `GooseClippySettingsToggle()` to the XP Start menu's Options panel, or invoke it wherever other user toggles appear. That is the **same** stored switch as Studio Layout → Presets. Close (×) turns GooseClippy OFF until the user explicitly switches it on again.

The overlay is draggable and stores independent positions for cover and unfolded widths in dp.

## Acceptance gates

1. Import both exact GIF files and verify transparent backgrounds and actual runtime animation.
2. Compile and run the Android unit tests on the exact integration commit.
3. On Fold cover/unfolded layouts, test ON/OFF in Studio and XP; restart app and verify it persists.
4. Confirm hiding GooseClippy stops sprite rendering and quip updates without changing the selected preset.
5. Confirm each observed event triggers a relevant line, 12 per category before repeats, and tap triggers GENERAL lines.
6. Ensure mandatory decisions/combat/card taps remain accessible; assistant must never modify a card image, game engine or control callback.
7. Do not merge or release without the owner's approval.
