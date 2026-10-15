# Arcana Table game-assistance overhaul

## Product goal

Arcana Table remains a freeform Magic table. Players may take actions without
the app authorizing or forcing them. The app observes recorded actions, uses
card data and game context to recognize likely rules events, and offers the
next useful step. Supported actions may be automated when their meaning is
unambiguous. Choices, targets, costs, hidden information, and unsupported rules
stay under player control.

The long-term target is to offer useful assistance for at least 90% of relevant
game actions. That target must be measured against a real action/card corpus;
it must not be inferred from the fraction of Oracle sentences a text heuristic
can classify. A recognized sentence is not proof that the complete card or
game state was understood.

## Current implementation gaps

The current resolver handles only a small set of fixed, unconditional effects:
gain life, lose life, draw cards, and create simple tokens. Trigger recognition
now covers common casts, draws, entries, deaths, sacrifices, attacks, blocks,
damage, life changes, turn steps, taps, counters, and discards. Two named-card
branches remain for Rhystic Study and Smothering Tithe. The recognizer still
misses compound or highly qualified patterns, and event detection is uneven
because card changes are performed directly by many UI callbacks.

Target picking currently estimates a target count from the word “target” and
shows broad object candidates. It does not evaluate full target legality,
restrictions, protection, zones allowed by the effect, or whether a target is
still legal on resolution. The app must describe this as a selection aid, not
as a comprehensive rules judge.

The priority pass cycle now records which player added the latest item. After
that player passes, priority visits each other eligible player once. If nobody
adds a response, returning to that player resolves the top item. A response
restarts the cycle from its controller. Online snapshots preserve the origin.

## Design direction

### 1. Observe actions through typed events

Every state-changing action should emit a normalized event after it succeeds.
Examples include spell cast, ability activated, card entered, card left, died,
sacrificed, card drawn, card discarded, permanent tapped or untapped, life
gained or lost, damage dealt, counter added or removed, attack declared, block
declared, phase begun, and turn begun or ended. Events should include the
acting player, affected object, source object, zone transition, amount, and
phase where known.

Events are facts about actions recorded by the app. They must not claim that a
player paid a cost, selected a legal target, or performed an unrecorded action.
Manual controls should emit the same event types as automated paths.

### 2. Parse rules text into reviewable assistance

Card text should be split into clauses and classified into one of these
outcomes:

| Outcome | App behavior |
| --- | --- |
| Fully supported, fixed effect | Explain it, request confirmation where appropriate, then apply it atomically. |
| Supported event, player choice required | Identify the trigger and open a choice prompt with the relevant card text. |
| Targeted effect | Offer candidates narrowed by known characteristics; let players confirm legality and choose. Recheck selected objects when resolving. |
| Partially understood or conditional effect | Do not apply part of the effect. Show the whole relevant text and let players resolve it manually. |
| Unknown or missing card data | Say what data is missing and preserve manual play. |

The parser should recognize trigger timing and event independently from effect
resolution. This lets a card trigger on an event even when its resulting effect
still needs a player decision.

### 3. Make effects transactional

An effect with multiple clauses must not apply its easy clauses and silently
skip the rest. The current all-or-manual behavior for unsupported spell text is
the right safety rule. Future effect plans should validate all prerequisites,
targets, and choices before committing any state changes. If required context
is missing, keep the state unchanged and ask the player.

### 4. Preserve sandbox authority

Assistance is advisory. Players can carry out unsupported effects manually,
correct a recognized state, or ignore a suggestion. The app should log what it
automated, what it inferred, what the player confirmed, and what remains
manual. It should not silently change card zones, targets, life, or counters
based only on uncertain text parsing.

## Coverage areas to build out

1. **Action event coverage:** centralize state mutations so casts, resolution,
   zone changes, sacrifices, token entry, combat, counters, life, mana, turn
   steps, and player elimination all publish consistent events.
2. **Triggered abilities:** add recognition for phase triggers, landfall,
   enters/leaves, death, sacrifice, attack, block, damage, draw, discard, life
   change, tap/untap, and counter events. Distinguish actor and object filters.
3. **Resolution assistance:** broaden fixed effects for life, draw, token
   creation, counters, tap/untap, zone movement, scry/mill, and damage only
   where the full effect is deterministic. Keep modes, replacement effects,
   choices, dynamic values, and conditional costs in the prompt flow.
4. **Targeting:** parse target object/player/zone categories, minimum and
   maximum counts, and repeated target groups. Display candidates with card
   type, controller, zone, and relevant text. Add legality hints without
   claiming full judge-level legality until restrictions are modeled.
5. **Choices and costs:** offer explicit interfaces for modes, X values, payment
   options, creature types, token characteristics, divisions, sacrifices,
   discards, and optional effects. Record the player's choices in the stack and
   log.
6. **Continuous/replacement effects:** first identify and surface relevant
   effects; automate them only after dependency and timestamp rules are modeled.
7. **Diagnostics and measurement:** keep a corpus of common card text and
   action sequences; report recognition, correct-prompt, false-positive,
   manual-fallback, and state-mismatch rates separately. The 90% goal is met
   only when evaluated on this corpus and real play feedback.

## Work started on this branch

The `experimental/assistance-overhaul` branch is based on
`experimental/targeting` Build 81. The initial slice expands trigger-text
recognition to land/permanent entry, death, leaving the battlefield, attack,
block, combat damage, generic damage, life gain/loss, tap, discard, sacrifice,
counter placement, and turn phases. Existing action paths now emit or evaluate
many of these events, including spell cast, draw, permanent entry, manual life
change, tap, discard, sacrifice, phase start, attacker/blocker declarations,
counter placement, and combat damage. Target pickers narrow visible choices to
likely object classes when Oracle text makes that clear, with an explicit
legality warning. Simple generated tokens preserve parsed colors,
power/toughness, and common keyword data. Unknown or targeted spell effects are
queued into a visible manual-resolution prompt with their card text.

This slice is a foundation, not the 90% completion claim. Event paths such as
sacrifice costs, replacement effects, stack-aware trigger ordering, effects
that change other permanents or zones, and comprehensive target legality still
need dedicated implementation and regression coverage. The priority cycle has
focused unit coverage and passed the Build 82 Android CI test run.

## Offline recognition benchmark

`app/src/test/resources/assistance-oracle-benchmark.json` stores small,
representative Oracle-text excerpts retrieved from Scryfall's Card Collection
API. `AssistanceBenchmarkTest` reports separate coverage for trigger-event
classification, target-category hints, and automatic fixed-effect parsing, and
lists expected manual fallbacks. The current fixture includes one deliberate
trigger miss (Tectonic Giant's compound attack-or-target condition) so growth in
coverage is visible. Target hints are not legality checks: for example,
`target nonland permanent` still includes lands in the candidate category set.

This corpus is a regression benchmark, not a statistically representative
estimate of all game actions. Its percentages must not be described as proof of
the 90% product goal. Expand it with labeled action sequences and player
feedback before making that claim.
