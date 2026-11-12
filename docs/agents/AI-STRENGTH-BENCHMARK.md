# AI Strength Benchmark (opt-in, headless)

**Owner goal:** harder offline AI without slowing gameplay. This lane is instrumentation only; it changes no shipped game behavior, Argentum runtime or AI decision budget. Coordination: [issue #81](https://github.com/AARMOU/arcana_table/issues/81).

## Run

From `android/`:

```bash
# Normal CI runs a tiny statistical arithmetic test and does not play a benchmark.
./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.arcanatable.AiStrengthBenchmarkTest'

# Fast development smoke: four games total (2 in each format), no statistical claim.
ARCANA_AI_BENCHMARK=1 ARCANA_AI_GAMES_PER_MODE=2 \
  ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.arcanatable.AiStrengthBenchmarkTest'

# Full control baseline: 200 games in each format, 400 total.
ARCANA_AI_BENCHMARK=1 ARCANA_AI_GAMES_PER_MODE=200 \
  ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.arcanatable.AiStrengthBenchmarkTest'
```

Output (when explicitly enabled): `android/app/build/reports/ai-strength/comparison.json` and `comparison.md`. Override with `ARCANA_AI_REPORT_DIR` if needed. Optional per-game safety caps: `ARCANA_AI_MAX_GAME_MS` (default 120000 ms), `ARCANA_AI_MAX_ACTIONS` (4000). Turn cap: 60. A single unresponsive engine call **cannot be interrupted** by these outer-loop caps; the underlying Argentum cooperative deadline patches remain essential.

## Dataset and fairness

- Loads fully supported 100-card bundled Commander decks from `commander-decks.json`, plus fully resolved original lists from `investigation/argentum-six-precons/deck-manifest.json`. Unsupported/partial decks are excluded with reasons. **No silent land substitutions.**
- Paired comparisons: same deck order, same Argentum `GameConfig.seed`, same starting seat, alternating profile assignment. 1v1: one candidate versus one baseline; 4-player: two candidate seats versus two baseline seats.
- Baseline is exactly `OfflineAiPolicy.profile` (`arcana-mobile-b123`). The `arcana-ai-candidate-control` is currently a byte-for-byte-equivalent behavior copy with only its ID changed. A 50% result is expected in the limit; **it is not a stronger AI yet**.
- Uses real Argentum `GameInitializer`, `AIPlayer`, `ActionProcessor` and bundled cards, with no stubbed rules.
- Engine game-over draws, action/turn/time caps, missing actors, and illegal AI actions have separate outcome categories. The win rate and Wilson 95% interval use *decisive games only*, with denominator disclosed. Many undecided games invalidate strength claims even if the decisive win rate looks good.

## Timing and diagnostic interpretation

- Per AI call, record nanoseconds, side, current game turn, and observed policy budget tier; report p50/p95/p99/max by side, format and tier, plus AI ms per reported game turn, wall time, and decision counts. The full decision series is in JSON.
- Measurement is **headless JVM**, not Android: no animations, Compose render, input latency, phone thermals, or game-host message pacing.
- Instrumentation currently scopes a 600-ms `withThinkingBurst` around **each decision**. The production host shares that budget across multiple consecutive decisions in a drive burst. This simulator cannot certify end-to-end real-host latency.
- Exact invalid-action counts and observational priority passes are available. **Do not label every pass a bad play.** No trustworthy counters yet for poor trades, pointless blocks, self-removal, passing with castable mana, or land discard. Implement those later with actual game-state proof; never output misleading zeros.
- Benchmark wall time includes engine action processing. Choice samples exclude processor time and include lightweight tier observation. Sample warmup, JVM scheduling, thermal and workload variance before deciding on p95/max regressions.

## Ship gates (none satisfied by this first harness)

1. Fix compile/smoke errors, run and attach a 200+ game baseline **per mode**, with both JSON and Markdown reports.
2. Change candidate policy *one variable at a time*, keep 600-ms burst cap, crowded-board 150-ms fallback and engine deadline safeguards; show 95% Wilson lower bound above 50% in both 1v1 and four-player while controlling for draws/errors.
3. Add verified dumb-play regression heuristics and compare them; no false labels from speculative telemetry.
4. Repeat a defensible before/after latency comparison under matched device/runner conditions, including `OfflineAiLargeBoardTest`; require no p95/max, AI-ms/turn or game-wall regression.
5. Full unit suites, lint, regex compatibility scanner and debug assembly; review and user Fold gameplay feel-test.
6. Independent reviewer + owner merge. Nothing here authorizes a runtime swap, worker dispatch, release, signing or publishing.

**Status:** phase-one harness source provided; baseline metrics are **not yet measured**. This document is not a green CI or APK claim.
