# Benthic Anomaly: chosen-collection stat aggregation probe

This is **not an implementation of Benthic Anomaly**. No card is registered or exported.
The cached Oracle and [official MH3 release notes](https://magic.wizards.com/en/news/feature/modern-horizons-3-release-notes)
require a copy with power/toughness equal to the totals of exactly the creatures chosen from
opponents, replacing colors/types/subtypes while retaining supertypes and copied abilities.

SDK inspection found `forEachPlayerCollecting`, mandatory `chooseExactly` with
`Chooser.SourceController`, and `CreateTokenCopyOfTarget` with dynamic base-stat/type/color
exceptions. The unresolved part is a total of the chosen collection's power and toughness.
`DynamicAmounts.battlefield(...).sumPower()` totals a battlefield filter; it does not accept
an arbitrary stored collection. `manaValueSumOf(collection)` exists, but no corresponding
power/toughness collection aggregate is exposed by the pinned documented SDK.

## Executed primitive regression

Two real ActionProcessor scenarios round-trip an unregistered probe definition through card JSON:

1. Directly storing the first collected creature's projected power and reading it later works: 2.
2. Starting a numeric accumulator at zero, visiting creatures with 2 and 3 power via
   `Effects.ForEachInCollection`, and updating it with `Effects.StoreNumber` returns **0**, not 5.

**Observed: 2 executed / 1 passed / 1 failed, no skips.** `result.xml` preserves the assertion.
The mana payoff is only an observable probe for the computed number; it is not proposed Benthic
Oracle behavior. The failure also applies to the analogous toughness accumulator.

Pinned upstream `61e71e586b100faa661f86cac827ac3c3bbb5a78`,
`rules-engine/.../effects/composite/ForEachExecutor.kt:processItems`, carries collected collections
between/after iterations but does not merge/return `updatedStoredNumbers` from the body.
`StoreNumberExecutor` correctly returns those numeric outputs, and `CompositeEffectExecutor`
can consume them outside the loop (the positive control).

```sh
python3 investigation/eldrazi-incursion/benthic-anomaly/run-probe.py
```

The runner temporarily injects only the test source, refuses overwrites, returns its nonzero
result and removes the test afterward. It changes no runtime jars, registry, exports or workflows.
An optional command supports a standalone JVM harness using the same source directories.

## Next exact path

Prefer an explicit SDK aggregate over a stored collection (projected power/toughness, with negative
power and non-targeted selection semantics). Alternatively, specify and test numeric output
propagation through loops without breaking their deliberately isolated collection scopes.
Then implement Benthic with mandatory per-opponent non-targeted choices, no-creature opponents,
source-countered independence, copy exceptions, ETB/characteristic-defining abilities, and stable
copyable stat totals. This probe demonstrates the attempted composition fails; it does not prove
that every possible construction or an engine extension is impossible. Do not cap the sum at
three opponents or substitute total power of all opponents' creatures.
