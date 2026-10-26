# Arcana AI — Milestone A evidence and acceptance gates

**Issue:** #81. **Branch:** `gpt/ai-infrastructure-replay-20261025` (from `gpt/arcana-integration-next`).  
**Scope:** Infrastructure and tactical baseline ONLY. Do not modify shipped AI policy, engine jars, patches, signing, release builds or worker dispatch. No merge, APK, or release without owner approval.

## Status and interpretation

- **Implemented in this draft:** imported existing opt-in 2p/4p AI benchmark from PR #238, with SHA-256 fingerprints of **each submitted action in execution order**; an opt-in repeatability assertion replays identical mode/seed/decks/profile seats in the same JVM. The published JSON contains only fingerprints, not raw hidden actions.
- **Not yet implemented:** fixed-work AI search mode, cross-machine deterministic proof, owner-playtest combat traces, and dedicated engine-backed tactical test suite. These must not be presented as completed.
- **Not concluded:** whether seed 8073 nondeterminism comes from wall-clock cutoffs. Evidence to date is consistent with several explanations. The action fingerprint captures differences; it does **not** eliminate causes.
- **Existing code fact:** `OfflineAiPolicy` uses `System.nanoTime()`, with normal 250 ms, combat 600 ms, and crowded board 150 ms plus a shared 600 ms host burst. Independent test runs may traverse different branches. Fixing that for benchmarks might require **Argentum AI source-level support**; no change to jar or runtime is authorized by this PR.
- **Existing host fact:** `LocalArgentumHost.driveBurst` attempts the chosen action then unconditionally tries a hand-built `safePass` if processing rejects it; `apply` drops `result.error`. Unchanged here.

## Reproduction and gates

Current focused CI:
```bash
cd android
ARCANA_AI_BENCHMARK=1 ARCANA_AI_REPEAT_REPLAY=1 \
ARCANA_AI_GAME_MODE=2 ARCANA_AI_GAMES_PER_MODE=2 \
ARCANA_AI_PAIR_START=80 ARCANA_AI_MAX_ACTIONS=6000 \
ARCANA_AI_MAX_GAME_MS=45000 \
./gradlew --no-daemon --max-workers=2 :app:testDebugUnitTest \
  --tests 'com.arcanatable.AiStrengthBenchmarkTest'
```

The test executes the **same seed 8073 / pair 80** twice in each swapped profile-seat arrangement, hashing turn/step/actor/submitted action before every engine process call. On differing hashes, counts, or statuses it fails with `[ai-replay]` diagnostic evidence.

The run records JSON fingerprints for external CI-versus-local comparison, but until independent runner hashes match, cross-machine determinism remains unverified. Do not record “fixed” based on nonreproduction.

## Subsequent milestone A gates (not part of the current implementation)

1. If hashes differ, isolate the earliest divergent submitted action. Investigate ordering, randomness, and decision-budget cutoff. Do not assume the cutoff is the root cause. Introduce an actual fixed **engine work-unit** search budget only after verifying the AI implementation supports it; keep shipped Android wall-clock policy unchanged.
2. Log *public* AI decisions on the device at attack, block and relevant instant windows: turn/step, player life, commander damage by each commander, card names, visible P/T and keywords, selected action, rejected engine error, and score/candidates **only when exposed by AIPlayer**. Do not include hidden opposing cards, library order or arbitrary private state. Bound the event count and overhead. No log recording has yet been added to the host.
3. Test targeted, real-engine situations with known combat outcomes and explicit legal alternatives; distinguish *Argentum resolution correctness* from *AIPlayer action choice*. First strike and double strike; attacker preservation against commander damage; defensive spell timing. Count an inconclusive/nonunique choice separately from fail.
4. **Correct sample design:** 21+ combat damage from the same commander is lethal, so 16+4 is **not** lethal; test 17+4 or 16+5. *Dawn's Truce* grants hexproof and optional indestructible to permanents; it does **not** prevent unblocked player combat damage. Make its defense scenario about relevant target removal or destruction, not incoming player combat damage.
5. Once the tactical baseline and fixes exist, run a predeclared sufficient paired-game evaluation; report candidate decisive win fraction, Wilson confidence interval **and paired dependence / seed effects**, error/incomplete counts, latency and budget overhead. The handoff's suggested 200 pairs is a useful initial scale but is **not** by itself proof of non-inferiority or adequate power for a desired effect.

## Review rules

Keep this PR DRAFT. Milestone A is **not review-complete** until fixed budget/search mode is validated, deterministic cross-run action hashes match, privacy-bounded combat logs appear in owner diagnostics, and engine-backed scenario baseline has a measured pass rate. Release-branch modifications and promotion require explicit owner approval.