# Studio coverage — out-of-game screens

Owner decision D33 (Nov 6, 2026): Arcana Studio edits **in-game screens only** (Table, Player, Hand, Stack, Zones,
Log, Combat). Home, Decks, Setup, Lobby and Settings are **not editable** and are never affected by palettes, density
or profiles; earlier edits on them are cleared when Studio opens (`StudioControlsRuntime.withoutExcluded`).

Covered by `StudioEditorRealAppUiTest.settingsReplacesAdjustUiAndIsNeverEditedOrRecoloured`,
`editsLeftOnExcludedScreensAreDroppedWhenStudioOpens` and
`ArgentumResilienceUiTest.studioExcludesHomeAndSettingsAcrossActualActivityRecreation`.
Outside a game Studio shows a practice-game card and the Hand screen (`homeOpensStudioWhichOffersPracticeAndTheHand`).
