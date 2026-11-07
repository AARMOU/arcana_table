# STUDIO-J10 — crowded four-player Table/Player layout fixture

**Status:** test-data only; schema-level generated and validated, **not run in Robolectric or on a Fold**. This is an isolated Studio UI stress fixture, NOT a rules-legal MTG game or a release/engine integration input.

## Files / provenance

- `../argentum/studio-busy-four-player.json`: a self-contained, structurally valid `stateUpdate` JSON payload cloned from the existing recorded `state-main-phase-with-actions.json` fixture; preserves card/state envelope and mandatory properties.
- Its modifications are test-only; synthetic card-instance IDs begin with `studio-`. Actual card names are real Magic card names used to stress text wrapping; detailed oracle/card-type fields for cloned instances are inherited for UI layout, **not to validate card legality**. Art URIs for synthetic cards are null so screenshot results do not depend on remote card art.

## Intentionally crowded dimensions

| Requirement | Fixture |
|---|---|
| Four seats | four distinct `playerId` values |
| One busy battlefield | **18** permanents, including **3** Beast tokens |
| Attached cards | **2** attachments on first creature: Rancor (Aura) and Sword of the Animist (Equipment) |
| Long labels | Asmoranomardicadaistinaculdacar; Bello, Bard of the Brambles; Ink-Eyes, Servant of Oni |
| Life | **108, 143, -9, 126** (negative-life seat remains present on purpose) |
| Stack | **6** distinct instances in Stack zone |
| Graveyards | **58** total across four seats (24 + 21 + 6 + 7) |
| Counters | poison **2, 6, 8, 3** by player |
| Commander damage | human/third-seat `commanderDamage` scenarios of **18/19**, respectively, using consumer fields `commanderId` and `amount`. `StudioBusyFixtureTest` asserts the real app projection preserves both values; execution status is reported separately below. |

Unique-card ownership invariant checked during construction: no duplicate IDs across zone lists; every zone card ID resolves in `state.cards`; four players and six stack objects are present.

## Using the existing fake-server / screen capture

`FakeArgentumServer.fixture(name)` reads under `src/test/resources/argentum/`. In a new **test class**, before calling `UiTestSupport.playWithArgentum`, set:

```kotlin
fake.stateMessage = FakeArgentumServer.fixture("studio-busy-four-player.json")
fake.dealOpeningHand = false
// Launch MainActivity with a known, local test deck/session preference.
// UiTestSupport.playWithArgentum(rule, scenario)
// await Table/Player content; use UiTestSupport.screenshot(activity, "studio-j10-table")
// switch to the local Player tab; capture "studio-j10-player"
```

Follow the server start, seed, launch and settlement pattern in `ui/ArgentumResilienceUiTest.kt` and `ui/StudioCoverageAuditTest.kt`. Prefer `@Config(qualifiers = "w344dp-h800dp-port-xhdpi")` then unfolded `w673dp-h928dp-port-xhdpi` to make line breaks and clipping reproducible. Also assert that total player chips are four, the game remains responsive, and neither target nor required pass/exit is hidden behind a clipping row.

### Important test caveats

1. The fixture preserves a recorded server payload but has **not** passed the app parser or a running UI test. The four-player/stack interpretation, token/attachment rendering and commander-damage shape MUST be verified by the existing Android test runner. Do not treat JSON validity as a successful UI test.
2. Some cloned card template attributes intentionally disagree with their display names (layout stress only); rules/action legality and AI logic cannot be inferred. The action list was cleared to avoid users taking unsupported fake actions.
3. Negative life and large poison counts are deliberate rendering extremes, not a simulated real match outcome; `hasLost=false` is set on the negative-life seat so it stays visible for layout.
4. If a parser silently filters cards or expects a different commander-damage object schema, adjust only this test resource / a separately authorized test, note exactly which field was wrong, and do not weaken production validators.
5. **No screenshot evidence yet.** A device or Robolectric run must create and inspect the Table and Player PNGs before J10 is fully accepted.

## Constraints

Test resource only. No `Studio*.kt`, app runtime, `MainActivity`, hand drawer, engine, JAR, APK, merge, cloud deploy, permission or worker changes. Destination is a **draft** PR targeting `gpt/arcana-integration-next`; Claude's Studio branch remains untouched.

## J10 correction and executable acceptance tests (Elmer follow-through)

The original fixture used `sourceCommanderId` / `damage`, but the app's projection reads
`commanderId` (or `commanderName`) / `amount`. The corrected fixture preserves 18/19 instead
of projecting an empty commander key and zero damage. Production parsing is unchanged.

- `com.arcanatable.StudioBusyFixtureTest`: real `ArgentumTableSnapshot.parse` checks four
  seats, life extremes, local battlefield/token counts, six distinct stack entries, 58
  graveyard cards and both commander-damage values. Also checks zone uniqueness, sizes,
  resolvable cards and attachment references.
- `com.arcanatable.ui.StudioBusyFixtureUiTest`: existing fake-server startup with the fixture,
  all four seat tabs and controller-state assertions, then Table and local Player PNGs at
  cover (344x800dp) and unfolded (673x928dp) qualifiers. Outputs:
  `android/app/build/screenshots/studio-j10-{cover,unfolded}-{table,player}.png`.

Run from `android/`, only after claiming the shared heavy-PC slot:

```powershell
.\gradlew.bat --no-daemon --max-workers=2 :app:testDebugUnitTest --tests com.arcanatable.StudioBusyFixtureTest --tests com.arcanatable.ui.StudioBusyFixtureUiTest
```

**Execution status at preparation:** Android tests and screenshots pending a coordinated
runner slot. These new tests are not yet claimed compiled or passed. The fixture branch is
based on the existing integration line, not Claude's Studio implementation. Successful
execution here proves fixture compatibility and baseline rendering; Studio edit/apply
behavior and physical Fold acceptance require separate execution on the chosen Studio SHA.
PNG existence is not a visual-quality verdict: inspect all four captures before acceptance.
