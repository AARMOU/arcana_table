# Rules reliability — Build 131 investigation

Tracking issue: #51
Base branch: `gpt/b131-combined-integration-20261022`
Working branch: `gpt/rules-reliability-b131-20261022`

## Safety and verification gate
This branch is isolated from main. No source fixes, merges, releases, signing changes, or APK publication are implied by this investigation document. Pin exact engine and app commits before modifying implementation. Do not call tests passing unless command outputs or CI evidence confirm them.

## Regression matrix
| Area | Reproduce | Assertions | State |
| --- | --- | --- | --- |
| Iron Spider counters | Ability targets another eligible creature | correct legal targets, counter removal, cost, resulting counters | Not run |
| Lunar Whale crew | Activate crew 1 with eligible creature | tapping, crew legality, vehicle creature status, summoning sickness, attack eligibility | Not run |
| Lunar Whale library | Inspect and play top card | owner-only visibility, timing, land-drop/cast permissions, payments | Not run |
| Treasure mana | Sacrifice Treasure for mana | Treasure leaves battlefield, selected mana added, legal cast consumes it | Not run |
| Playable badge mismatch | Card marked playable but payment fails | UI agrees with engine's payment feasibility and restrictions | Not run |
| Myriad | Attack with myriad creature | triggers, correct opponents/tokens, exile timing, no illegal duplicates | Not run |
| Mulligan overlay | Long-press opening hand then keep/mulligan | preview above modal and dismissed on transition | Not run |

## First implementation pass
1. Identify the existing test harness and pinned Argentum dependency.
2. Run baseline tests and capture actual results.
3. Add reproducing tests for shared action-payment and target selection pipelines.
4. Correct the smallest common cause and retest all affected cards.
5. Run full regression checks and review Build 131 compatibility before proposing integration.

## Reporting
For every case record: source commit, test command, observed failure, change commit, passing test evidence, and remaining device-only checks.
