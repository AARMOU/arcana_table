Upstream `16a2f5e184` is 31 commits ahead of the pin `b38db9760c`. A patch no longer merges on its own; see **Handoff** below.

### `0030-bloodthirst-keyword.patch` → `docs/card-sdk-language-reference.md` (content)

```diff
diff --cc docs/card-sdk-language-reference.md
index f6ae06fea,8162703bc..000000000
--- a/docs/card-sdk-language-reference.md
+++ b/docs/card-sdk-language-reference.md
@@@ -15085,16 -15026,23 +15085,36 @@@ Blighted Agent, Ichor Rats, Plague Stin
    abilities (as suspend's countdown and cast do): a vanishing permanent sits where instant-speed
    counter removal can reach it, and it is sacrificed whenever the last counter leaves, not only on
    an upkeep.
++<<<<<<< HEAD
 +- `Sunburst` — **engine-live.** Declare it and nothing else: `keywords(Keyword.SUNBURST)` (Engineered
 +  Explosives). CR 702.44a: as the permanent enters **from the stack**, the engine places one counter
 +  per distinct color of mana spent to cast it (`DistinctColorsManaSpent`; colorless never counts) —
 +  **+1/+1** counters if it enters as a creature, **charge** counters otherwise. "As a creature" and
 +  "still has sunburst" are read off the object as it would exist on the battlefield
 +  (`EntryCharacteristics.projectedAsEntering`, CR 614.12), so an artifact March of the Machines
 +  animates gets +1/+1 counters. Synthesized on the stack-resolution path only, so a sunburst
 +  permanent reanimated or blinked gets none; the counters go through `placeEntryCounters` (Hardened
 +  Scales / Solemnity apply). Don't hand-write an `EntersWithDynamicCounters` for it — it would stack.
 +  Converge (an ability word) is the hand-authored neighbour: `convergeEntersWithCounters()`.
++=======
+ - `Bloodthirst(n)` — **engine-live.** Declare it and nothing else:
+   `keywordAbility(KeywordAbility.bloodthirst(2))`. The engine supplies the replacement effect
+   from [`Bloodthirst`](../mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/Bloodthirst.kt):
+   "if an opponent was dealt damage this turn, it enters with N +1/+1 counters". It runs at the
+   shared entry seam (`EntersWithReplacements.applyGlobal`, so spells, tokens, reanimation and blinks
+   alike), through `placeEntryCounters` (so Hardened Scales applies), with no trigger and no stack.
+   Whether the permanent has bloodthirst is read from its **projected** keywords as it would exist on
+   the battlefield (CR 614.12): the printed half is gated on the bare `BLOODTHIRST` keyword and reads
+   N from the printed `KeywordAbility.Numeric`; a lose-all-abilities effect that applies to it strips
+   both halves. "An opponent" is relative to the entering permanent's controller; only damage counts,
+   not life loss. **Granted bloodthirst** ("Each other colorless creature you control has
+   bloodthirst 2", Twins of Discord) is `GrantKeyword(KeywordAbility.grantedBloodthirst(2),
+   GroupFilter(<filter>, excludeSelf = true))`, the projected `BLOODTHIRST_<n>` keyword carrying
+   its own N. Like renown, grants are **not summed**: projection tallies identical grants
+   (`ProjectedState.grantedKeywordInstances`), each instance adds its own N, and the client shows
+   the keyword as "bloodthirst 2". Do **not** hand-write an `EntersWithCounters`; it would stack
+   with the engine's. Pinned by `BloodthirstKeywordTest`.
++>>>>>>> 94669f9d4 (arcana: 0030-bloodthirst-keyword)
  - `Renown(n)` — **engine-live.** Declare it and nothing else:
    `keywordAbility(KeywordAbility.renown(1))` (Topan Freeblade). The engine supplies the CR 702.112
    ability from [`Renown`](../mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/Renown.kt): a
```

### `0030-bloodthirst-keyword.patch` → `mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/core/Keyword.kt` (content)

```diff
diff --cc mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/core/Keyword.kt
index 24e839bae,1f37b5881..000000000
--- a/mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/core/Keyword.kt
+++ b/mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/core/Keyword.kt
@@@ -840,17 -840,11 +840,25 @@@ enum class Keyword(val displayName: Str
      VANISHING("Vanishing"),
  
      /**
++<<<<<<< HEAD
 +     * Sunburst (CR 702.44). "If this object is entering the battlefield from the stack as a
 +     * creature, it enters with a +1/+1 counter on it for each color of mana spent to cast it.
 +     * Otherwise, it enters with a charge counter on it for each color of mana spent to cast it."
 +     *
 +     * A bare keyword with no DSL helper: the engine synthesizes the entry counters on the
 +     * stack-resolution path only (CR 702.44a, "from the stack"), counting
 +     * [com.wingedsheep.sdk.scripting.values.DynamicAmount.DistinctColorsManaSpent] and choosing the
 +     * counter kind from the object's characteristics as it would exist on the battlefield
 +     * (CR 614.12) — so an artifact March of the Machines animates enters with +1/+1 counters.
 +     */
 +    SUNBURST("Sunburst"),
++=======
+      * Bloodthirst N: "If an opponent was dealt damage this turn, this permanent enters
+      * with N +1/+1 counters on it." Printed via `KeywordAbility.bloodthirst(n)`, granted via
+      * `KeywordAbility.grantedBloodthirst(n)`; see [com.wingedsheep.sdk.scripting.Bloodthirst].
+      */
+     BLOODTHIRST("Bloodthirst"),
++>>>>>>> 94669f9d4 (arcana: 0030-bloodthirst-keyword)
  
      /**
       * Cumulative upkeep [cost] (CR 702.24). Display-only; the `cumulativeUpkeep(cost)` DSL helper
```

### `0030-bloodthirst-keyword.patch` → `web-client/src/types/enums.ts` (content)

```diff
diff --cc web-client/src/types/enums.ts
index 5013cb842,99692da47..000000000
--- a/web-client/src/types/enums.ts
+++ b/web-client/src/types/enums.ts
@@@ -311,7 -311,7 +311,11 @@@ export enum Keyword 
    SOULSHIFT = 'SOULSHIFT',
    FADING = 'FADING',
    VANISHING = 'VANISHING',
++<<<<<<< HEAD
 +  SUNBURST = 'SUNBURST',
++=======
+   BLOODTHIRST = 'BLOODTHIRST',
++>>>>>>> 94669f9d4 (arcana: 0030-bloodthirst-keyword)
    CUMULATIVE_UPKEEP = 'CUMULATIVE_UPKEEP',
    ECHO = 'ECHO',
    SUSPEND = 'SUSPEND',
@@@ -456,7 -456,7 +460,11 @@@ export const KeywordDisplayNames: Recor
    [Keyword.MODULAR]: 'Modular',
    [Keyword.FADING]: 'Fading',
    [Keyword.VANISHING]: 'Vanishing',
++<<<<<<< HEAD
 +  [Keyword.SUNBURST]: 'Sunburst',
++=======
+   [Keyword.BLOODTHIRST]: 'Bloodthirst',
++>>>>>>> 94669f9d4 (arcana: 0030-bloodthirst-keyword)
    [Keyword.CUMULATIVE_UPKEEP]: 'Cumulative upkeep',
    [Keyword.ECHO]: 'Echo',
    [Keyword.SUSPEND]: 'Suspend',
```
