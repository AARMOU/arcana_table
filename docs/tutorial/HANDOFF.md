# Arcana Table — Tutorial Handoff (Claude → GPT)

**Status:** Design approved by the owner as a starting point. Implementation has not started.
The tutorial was parked as a side project on 2026-10-23; this handoff gives GPT everything needed to build it.
**Coordination:** Issue #81. **Base branch:** `gpt/arcana-integration-next`. **Authorization:** the owner approves every merge, APK and release.

## Files in this folder

| File | What it is | How to use it |
|---|---|---|
| `tutorial-prototype.html` | Clickable HTML/JS prototype of both tracks, plus the design notes. Open it in a browser. | Source for the **flow, step wording, step schema and controller behavior**. Its phone screens are stand-ins, not the real UI. |
| `screen-callouts.html` | Real Build 112 screenshots (5 screens) with numbered gold boxes showing what the tutorial spotlights. | Source of truth for **which real controls to target and in what order**. |
| `HANDOFF.md` | This spec. | Read first. |

**Important:** where the prototype and the callouts disagree, the callouts (real app) win. The prototype's home screen (Continue / Play / Decks / Online / Tutorials tiles, a side drawer with Saved games / Priority & stops / Settings / Help) was invented before the real screens were checked. The real home screen has **Your deck, Change deck / Import new, Play, Settings**. Rewrite the prototype's chapter text against the real screens; keep its structure and mechanics.

---

## 1. Design

Two tracks sharing one engine.

**Quick Start** (~3 min, offered once on first launch). One scripted turn in a sandbox game against an opponent that only passes: play a land, tap it for mana, cast a creature, press and hold to read a card, pass priority. Every action step waits for the player to actually do it.

**Field Guide** (short independent chapters, opened from a Tutorials entry). Covers every control on the real screens. Chapters tick off when finished and can be replayed.

**Principles**

- **Coach marks on the real UI.** A spotlight dims everything except the control being taught. No separate slideshow screens.
- **Learn by doing.** Action steps advance only when the player performs the action. Tapping elsewhere shakes the tip instead of breaking the lesson.
- **One idea per step.** A title plus at most two sentences.
- **Always escapable.** Skip on every step; progress saved per chapter; replay any time.
- **Deterministic sandbox.** Fixed seed, fixed hands, passive opponent, real Argentum rules.

## 2. Step data (schema)

Steps are content, not code, so chapters can be written and reviewed without touching the engine. Store them as JSON under `android/app/src/main/assets/tutorials/` (`quick-start/first-turn.json`, `field-guide/<chapter>.json`).

```json
{
  "id": "play-land",
  "screen": "table",
  "target": "hand-handle",
  "advance": "tap",
  "title": "Play a land",
  "body": "Lands make mana, and you can play one per turn. Tap a land to put it onto the battlefield.",
  "hint": "Tap the Forest"
}
```

- `advance`: `next` (Next button), `tap`, `hold` (press and hold), or an engine event: `event:landPlayed`, `event:manaAdded`, `event:spellCast`, `event:priorityPassed`.
- `target`: a tutorial target id (section 4). `null` = centered tip with no spotlight.
- Chapter file: `{ "id", "title", "minutes", "steps": [...] }`.

## 3. Content

### Quick Start — "Your first turn" (11 steps, from the prototype; retarget to real controls)

1. Welcome (next) — "We'll play one turn together. About three minutes; skip any time."
2. Your hand (next) — target: hand handle (callouts screen 4, #8).
3. Play a land (tap / `event:landPlayed`).
4. Tap for mana (tap / `event:manaAdded`).
5. Your mana pool (next) — target: mana summary (`argentum-mana-summary`).
6. Cast a creature (tap / `event:spellCast`) — 1-mana creature in the fixed hand.
7. Read any card (hold) — opens card zoom (`card-zoom`).
8. Summoning sickness (tap Close).
9. Pass priority (tap / `event:priorityPassed`) — target: Pass in quick actions (screen 4, #5).
10. Your opponent (next) — target: opponent player tab (screen 4, #4).
11. That's a turn (next) — points to the Field Guide.

### Field Guide — chapters (rebuilt against the real screens)

| Chapter | Real screen | Targets, in order (callouts numbering) |
|---|---|---|
| Home | Home (screen 1) | 1 Your deck · 2 Change deck / Import new · 3 Play · 4 Settings |
| Starting a game | Game setup (2) | 1 Offline vs AI · 2 Player count (2–4) · 3 First AI's deck · 4 Play with a friend |
| Opening hand | Mulligan (3) | 1 Play/draw label · 2 Seven cards, Keep Hand / Mulligan |
| Reading the table | Game table (4) | 1 Phase bar · 2 Turn and timer · 4 Player tabs · 6 Commander · 7 Opponent played · 8 Hand handle |
| Taking actions | Game table (4) + hand open (8) | 5 Quick actions (Pass, graveyard, exile, stack, log) · card actions dialog · hold to read |
| Stack & priority | Stack dialog (9) | What's on the stack, passing, responding |
| Game menu & tools | Game menu (6), Game tools (7) | Decks, Game tools (add AI, token, mana pool, search library, stack & priority, log, MTG rules), Room & game, What's new, Options |
| Settings | Settings (5) | 1 Card size · 2 Adjust UI layout · 3 Gameplay · 4 Notifications · 5 Help & diagnostics · 6 Bottom bar |
| Decks | Saved decks (10) | Saved list, selecting, importing |

Screens 6–10 have no screenshots yet. Before writing those chapters, capture them from a Build 131 phone (game menu ⋮, game tools section, hand open with card actions, stack/priority dialog, saved decks) and add callouts the same way. The owner said the UI has not changed much since Build 112, so screens 1–5 can be used as-is but should be spot-checked.

## 4. Android implementation

1. **Target registry.** `Modifier.tutorialTarget("id")` records each control's bounds (via `onGloballyPositioned`/`boundsInRoot`). Reuse existing test tags as ids where they exist: `phase-tracker`, `argentum-mana-summary`, `commander-chip`, `hand-grid`, `argentum-stack`, `argentum-feed`, `card-zoom`, `opening-hand-card`, `battlefield-row`. Add ids for controls that have none (Play, Change deck, Settings, Pass, player tabs, game menu ⋮, hand handle).
2. **`TutorialController`.** Small state machine (current chapter, step index). Advances on `next`, on a tap/hold of the current target, or on matching Argentum events from the offline host's result events.
3. **`SpotlightOverlay`.** Full-screen Compose layer: dim layer with a rounded cutout around the target, the tip card (progress, title, body, Next or "do this" hint, Skip), and input swallowing outside the target. Respect reduced motion. Position the tip above or below the target so it never covers it.
4. **Sandbox game.** Fixed seed, fixed opening hand, opponent that only passes priority, mulligans skipped, runs on the real offline engine. Must not touch the player's saved game or checkpoint.
5. **Progress.** Completed chapter ids in DataStore. First launch offers Quick Start once; a Tutorials entry (in Settings → Help, or the game menu) opens the hub.
6. **Legacy guide.** Build notes mention an older "visual guide with screen highlights and red arrows"; its menu entry no longer appears in current source. Do not revive it; the new tutorial replaces it.

**Build order:** (1) registry + overlay + controller + Quick Start script; (2) sandbox game; (3) Field Guide hub and chapters one at a time, each checked against the real screen; (4) optional later: "?" hints that open the matching chapter.

## 5. Constraints

- Portrait-first; must work on Samsung Fold (folded and unfolded). 44dp minimum touch targets.
- Android regex rules in `AGENTS.md` apply to any new parsing code.
- No engine jar changes. No APK builds, releases or merges without owner approval.
- Don't change existing gameplay behavior; the overlay must be inert when no tutorial is running.

## 6. Done when (first milestone)

- Quick Start runs end to end on a debug build in the sandbox, each gated step advances only on the real action, Skip works at every step, and the player's saved game is untouched.
- Unit tests for the controller (advance rules, skip, completion persistence) and a Compose UI test that a gated step ignores taps outside the target.
- Draft PR on a `gpt/` branch with screenshots or a screen recording from a phone, posted once to #81 when ready for review.
