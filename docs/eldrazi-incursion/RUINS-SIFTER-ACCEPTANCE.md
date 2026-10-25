# Ruins of Oran-Rief and Sifter of Skulls acceptance

Nine new real-engine cases, eleven focused cases total, and the full **612/612** custom-card suite
pass against the unchanged committed runtime. No card definitions, registry, exports or jars changed.
Oracle was fetched through Cardkit; two check warnings concern omitted reminder text only. Cardkit
has zero errors, docs-check zero errors, 58 Python tests pass with one pre-existing missing-javap skip.

## Ruins of Oran-Rief — five cases total

The original enters-tapped/current-turn counter case is preserved. Four added cases prove:

- Tapped entry prevents both abilities; its later untapped mana ability adds immediate colorless mana
  with no stack, rejects reuse, and actually pays for Sol Ring.
- Colored creatures, colorless noncreatures and creatures not entered this turn are illegal targets;
  rejection does not spend the tap cost or add counters.
- The target need not be controlled by the Ruins player: an opponent's actual newly cast colorless
  creature receives the counter during that opponent's turn. Oracle has no controller restriction.
- An actual newly cast creature is eligible this turn and ineligible on a later turn; failed use
  leaves the untapped land and existing counter unchanged.

## Sifter of Skulls — six cases total

The original friendly nontoken death case is preserved. Five added cases prove:

- Sifter's own death produces no token. Doom Blade really destroys the colorless Sifter.
- An opponent's nontoken death produces no token for either player.
- A friendly creature token's death produces no token.
- Wrath of God killing Sifter with two other friendly nontokens, an opposing creature and a friendly
  token still creates exactly two Scions from last-known battlefield information.
- The created Scion immediately sacrifices for spendable colorless mana, without using the stack,
  permitting an opponent activation or a second sacrifice, or recursively triggering Sifter. Its
  mana actually casts Sol Ring and is consumed.

Source/test/runtime fingerprints and all 100 classifications are in coverage.json. Case names and
JUnit fingerprints are in evidence/readiness-core-scenarios.json. These two reviewed suites move
from missing acceptance coverage to category 1: **32 category 1 / 58 category 2 / 0 category 3 /
2 missing implementations / 8 qualified engine/runtime blockers**. There are still 91 indexed
originals and the same nine absent names. This is an evidence improvement, not 100/100 readiness.

Stack includes #209's isolated offline undo fixture fix. The original CI XML proves a timeout before
PlayLand selection; the exact CI hand was not recorded. A landless hand reaching an unanswered
cleanup decision is a source-supported explanation, not a recovered fact about that CI run. The
fix removes that precondition by guaranteeing an opening land and seeding the engine itself.
