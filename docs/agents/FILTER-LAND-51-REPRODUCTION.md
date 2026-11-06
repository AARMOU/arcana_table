# Filter-land affordability reproduction (#51)

Crayola, November 6, 2026. Development baseline: `01053b6024f748773e90eb63550094ef65688439`.

`FilterLandAffordabilityTest` uses the actual shipped card library, engine jars,
scenario driver, legal-action enumeration and host-style `LegalActionEnricher`.
Each fresh game is at player one's precombat main with untapped Plains and
Skycloud Expanse, no floating mana, and the selected spell in hand. No card,
registry, runtime or app behavior was changed.

## Observed results

- Voice of Victory ({1}{W}): absent from affordable casts; direct AutoPay rejects
  with `Not enough mana to cast this spell`.
- Deft Duelist ({W}{U}): same affordability and AutoPay failures.
- Explicitly activating Skycloud taps both lands and produces exactly WU;
  Voice of Victory then becomes affordable and casts from that pool, consuming it.
- White Knight (WW) and Lord of Atlantis (UU) are correctly unaffordable;
  rejected casts do not tap either land or put a spell on the stack.
- Adding a third Plains enables White Knight but still cannot pay UU.

The dependent filter-mana path fails both enumeration/affordability and direct
payment. The separate manual-activation success rules out an inability to execute
Skycloud's printed ability. This reproduction does not locate the internal solver
line or establish physical Android UI behavior. It uses the library definitions;
no new card implementation or Oracle edit is proposed.

## Validation

Run from android with the existing JDK21 and Android SDK:

```powershell
.\gradlew.bat --offline --no-daemon --max-workers=2 :custom-cards:test --tests com.arcanatable.customcards.FilterLandAffordabilityTest
```

Seven tests execute: **3 pass, 4 fail** on the baseline. The four failing tests
assert the correct affordable/direct-cast behavior and deliberately remain red;
no skips, inversions or weakened assertions hide the defect. This draft must not
merge until the corresponding engine repair makes all seven pass.

Cardkit Python tests: 67 run, one existing skip, no failures. `cardkit check`:
0 errors / 41 warnings. `docs-check`: 0 errors. `git diff --check`: passes.
The Python suite rewrote verified-support.json line endings on Windows; verified
that its normalized content was unchanged and restored the original exact bytes.
Full Android/card suites and device tests were not run: this is a focused engine
reproduction with a known failing gate, not a fix or release candidate.

## Handoff

Engine repair follows the current Red Bull -> Boneknuckles -> owner routing.
Preserve these positive and negative cases, account for an activation's input
mana and multi-color output, and prove enumeration and payment agree. Do not
patch a glow, pretend an unaffordable cast succeeded, or replace runtime jars
from this test-only task. No merge, APK, release or deployment is authorized.
