Upstream `61e71e586b` → `c49672e9b4` (96 upstream commits).

| Check | Result |
|---|---|
| Patches rebased and pass the strict audit | ✅ |
| Every premade-deck card resolves | ✅ |
| Custom cards compile and pass scenario tests on the new engine | ✅ |
| App test suite | ✅ |
| Test APK builds | ✅ |

Tests: 709 run, 0 failed, 4 skipped.

Patches: 8 refreshed.

### Imported cards

Library 16694 → 17035 cards (+341 / −0). 232 custom cards, 13 premade decks checked.

✅ Every premade-deck card that worked before still resolves.

Still swapped (unchanged): Goblin Welder, Ratchet, Field Medic // Ratchet, Rescue Racer

**Custom cards upstream now implements** (the app switches to Argentum's version; retire the custom copy in a follow-up):

- none

**Deck cards whose upstream rules changed** (worth a phone check):

- Firebender Ascension — Avatar Aang (EDHREC average)
- Game Trail — Animated Army, Bello (EDHREC average), Velociramptor Beefcake
- Luminous Broodmoth — Tokens Win 9/26 (Rocco)
- Multiversal Passage — Tokens Win 9/26 (Rocco), Sliver Overlord
- Necroblossom Snarl — Hazel Squirrels


Not run here: upstream's own rules/card/AI suites (upstream CI covers them) and the device checks in `engine-upgrade/UPGRADE-TEMPLATE.md`. Debug APK: see the run artifacts.

Run: https://github.com/AARMOU/arcana_table/actions/runs/40164017388

Owner approves the merge. Engine conflicts and failing engine-facing tests go to Claude's lane; card regressions to GPT (issue #81).
