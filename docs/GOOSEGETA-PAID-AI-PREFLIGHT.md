# Goosegeta paid-AI preflight — isolated preparation, NOT DEPLOYED

This supplementary draft targets the existing Goosegeta cloud/native draft #641. It must not be merged independently into B140 or deployed while #641 is being reviewed. Owner approved *engineering preparation* and an initial **up-to-$5 prepaid API purchase** on 2026-10-31, and later confirmed the credits were loaded. **Separate explicit approval is still required for any Cloudflare deployment, new live API call, or release.** Do not launch a paid AI coding worker.

## Candidate for the first small-budget evaluation

- Proposed initial model: `gpt-6-luna`, Responses API with strict `candidateId` enum and `reasoning.effort=none`. The model is **not** this ChatGPT conversation and cannot see its memory.
- Official standard per-million-token pricing checked 2026-10-31: input $0.10, output $0.50. This is an estimate basis, **not** live-model proof or a dollar-denominated in-code spending cap.
- Tentative defensive runtime ceilings: `GOOSEGETA_GAME_TOKENS=150000`, `GOOSEGETA_DAILY_TOKENS=200000`. These are conservative *reservation units*, **not** measured model tokens, and may end a game early. Review actual usage before changing them.
- Keep `GOOSEGETA_OPENAI_ENABLED=false` and do not enter a real API key in any branch, PR, issue, chat, APK or browser.
- `OPENAI_API_KEY` must eventually be stored **only** as a server-side Cloudflare Worker secret bound to the isolated test service. Do not place it in GitHub repository files or the Android client.
- The latest inspected owner screenshot showed the org limit **$120/month, hard enforcement OFF**. A credit purchase does not change that. Verify and set **$5/month with hard enforcement ON** before any live API call. Keep prepaid auto-reload **OFF**; reserve a separate Goosegeta project/key with the smallest suitable permissions. Billing enforcement has reporting delays and cannot guarantee a strict zero overrun.

## Test/approval gates

1. Run this branch's *local-fixture-only* model tests: `node --test tools/goosegeta/session.test.mjs tools/goosegeta/paid-model-preflight.test.mjs`. No live provider requests; fixture-only mode.
2. Run existing workerd/SQLite GooseBudget tests for account-shared daily reservations, circuit breaker, concurrent requests, invalid action IDs, cancellation, no data leakage and replay.
3. Compare real host-accepted actions and seeded gameplay quality against the deterministic baseline using the observed Rocco vs Goosegeta test and fixed test scenarios. Unsupported choices must visibly pause, never fabricate moves.
4. After separate approval and a verified $5 enforced org hard cap, securely configure the *isolated* Cloudflare test Worker model and secret, **not production**. Check the deployed flags/permissions before making **one** low-token live model call, then inspect actual OpenAI usage and logs for success/cost/rejection. Keep the owner informed of measured spending and leave automatic recharges disabled.
5. Only after green live checks, repeat a real Android-hosted Commander game and validate host receipts, action legality, chat, mute/dismiss, and reconnect. Preserve original #641 owner/build/artifact gates and release restrictions.

This draft contains no deployment workflow, no API keys, no GitHub billing changes, and no live request.
