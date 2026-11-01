# Vendored Argentum SDK reference

Copied unmodified from [`wingedsheep/argentum-engine`](https://github.com/wingedsheep/argentum-engine) at
commit `c1c04ca3265c61df913af6db65460ad4ce409614` — the commit Arcana's engine jars are built from. The
patches in `argentum-patches/` adjust some engine and AI behaviour but add no card-DSL vocabulary.
Licensed MIT; see [`LICENSE`](LICENSE).

| File | Upstream path | Use it for |
|---|---|---|
| `card-sdk-language-reference.md` | `docs/card-sdk-language-reference.md` | What every effect, trigger, condition, filter, cost, keyword and amount means |
| `sdk-index.md` | `docs/sdk-index.md` | Every sealed SDK type with its fields (generated from code upstream) |
| `add-card-examples.md` | `.agents/skills/add-card/examples.md` | More card-definition shapes |

Caveats:

- Internal links in these files point at upstream paths and may not resolve here.
- Test templates in `add-card-examples.md` use upstream's harness. In Arcana use `customScenario(...)` and
  the helpers in `android/custom-cards/src/test/kotlin/com/arcanatable/customcards/support/` instead
  (see [`../CARD-PATTERNS.md`](../CARD-PATTERNS.md) §8).
- The authority for "does this exist in the app's engine" is [`../generated/sdk-api.md`](../generated/sdk-api.md),
  generated from the committed jar. Re-vendor these files whenever the pinned commit changes.
