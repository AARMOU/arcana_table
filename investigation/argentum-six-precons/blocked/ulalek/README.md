# Ulalek: highest-priority blocked card

The exact M3C #4 Oracle definition is retained here, outside the live registry and all active
source sets. It is not counted as implemented, exported or playable.

Required before registration:

1. Complete full regression validation of staged engine patch `0004-stack-object-copying.patch` against the pinned engine,
   including batch order, source identity, retargeting, other Ulalek triggers, modes/X,
   permanent-copy tokens, serialization and the full existing copy regressions.
2. Implement and test true `{C/W}` / `{C/U}` / `{C/B}` / `{C/R}` / `{C/G}` symbols. The pinned
   `ManaCost.parse` tries to parse both halves as `Color` and rejects `C`. Colored mana of an
   unrelated color cannot pay a colorless half. Do not rewrite Ulalek to cost five generic mana.
3. Rebuild and validate every SDK consumer (SDK, rules, AI, native card corpus and tooling) for
   the Java-17 Android runtime, preserving
   all existing Arcana patches. Stage-only JVM tests are not Android integration evidence.
4. Move this exact definition into the custom-card source set, add card-specific end-to-end
   scenarios, register it, run the full custom-card regression/export/audit, and then update counts.

Oracle source: Scryfall exact-name API, `set=m3c`, fetched October 22, 2026, checked against the
Wizards Modern Horizons 3 release notes. The scenario-only fixtures in the engine tests are
synthetic mechanic probes, never substitute playable card definitions.

`ArcanaUlalekManaProbe.java` was executed against both the newly compiled source SDK and the
actual pinned `android/app/libs/argentum-mtg-sdk.jar` on Java 17. `{C}{C}` parsed correctly;
all five `{C/color}` symbols threw `IllegalArgumentException: Unknown hybrid mana symbol`.
The probe completed with exit code 0 because it deliberately verifies this blocker. It is not
a passing card test and is not evidence of colorless-hybrid support.

Targeted local validation now passed 39 distinct tests, including all 14 batch-copy scenarios.
See `../../validation/2026-10-22-stack-copy.json`. Full-suite CI and Android integration remain gates.
