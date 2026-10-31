import test from "node:test";
import assert from "node:assert/strict";
import { loadDecks, runtimeCardIndex, validateDeck, selectAction, chooseBottomCards } from "./logic.mjs";

test("both original Goosegeta Commander decks contain 100 indexed cards", () => {
  const decks = loadDecks();
  const index = runtimeCardIndex();
  assert.equal(decks.length, 2);
  for (const deck of decks) {
    const result = validateDeck(deck, index);
    assert.equal(result.cards.length, 99);
    assert.ok(index.has(result.commander.toLowerCase()));
    assert.ok(!result.cards.includes(result.commander));
  }
});

test("refuse cards missing from the installed runtime and illegal duplicates", () => {
  const deck = loadDecks()[0];
  const index = runtimeCardIndex();
  const wrong = structuredClone(deck);
  wrong.cards["Imaginary Goose of Infinite Glue"] = 1;
  wrong.cards["Hare Apparent"] -= 1;
  assert.throws(() => validateDeck(wrong, index), /Not in the installed/);
  const duplicate = structuredClone(deck);
  duplicate.cards["Academy Manufactor"] = 2;
  duplicate.cards["Hare Apparent"] -= 1;
  assert.throws(() => validateDeck(duplicate, index), /Unsupported duplicate/);
});

const state = (actions, extra = {}) => ({
  type: "stateUpdate",
  interactive: true,
  interactionEpoch: "offline-12",
  legalActions: actions.map(action => ({ action, isAffordable: true })),
  pendingDecision: null,
  ...extra,
});

test("prioritize legal land over a simple spell, then pass", () => {
  const pass = { type: "PassPriority", playerId: "offline-ai" };
  const spell = { type: "CastSpell", playerId: "offline-ai", cardId: "a" };
  const land = { type: "PlayLand", playerId: "offline-ai", cardId: "b" };
  assert.equal(selectAction(state([pass, spell, land])).action.type, "PlayLand");
  assert.equal(selectAction(state([pass, spell])).action.type, "CastSpell");
  assert.equal(selectAction(state([pass])).action.type, "PassPriority");
});

test("no off-turn cheating or invented payment/targets", () => {
  assert.equal(selectAction(state([{ type: "PassPriority" }], { interactive: false })), null);
  assert.equal(selectAction(state([{ type: "CastSpell" }], { pendingDecision: { type: "ChooseTargets" } })), null);
  const unsafe = state([{ type: "CastSpell", cardId: "x" }, { type: "PassPriority" }]);
  unsafe.legalActions[0].requiresTargets = true;
  assert.equal(selectAction(unsafe).action.type, "PassPriority");
  unsafe.legalActions[0].requiresTargets = false;
  unsafe.legalActions[0].hasXCost = true;
  assert.equal(selectAction(unsafe).action.type, "PassPriority");
});

test("play-card decision may proceed only with offered PlayLand/CastSpell", () => {
  const offered = state([{ type: "PlayLand", cardId: "x" }], { pendingDecision: { type: "PlayCardDecision", id: "choice-1", playerId: "offline-ai" } });
  assert.equal(selectAction(offered).action.type, "PlayLand");
});

test("declare only engine-supplied attack targets; refuse missing mandatory targets", () => {
  const info = { action: { type: "DeclareAttackers", playerId: "offline-ai" }, validAttackers: ["a"], attackTargetsByAttacker: { a: ["opponent"] }, mandatoryAttackers: ["a"] };
  const outcome = selectAction(state([], { legalActions: [info] }));
  assert.deepEqual(outcome.action.attackers, { a: "opponent" });
  info.attackTargetsByAttacker = {};
  assert.equal(selectAction(state([], { legalActions: [info] })), null);
});

test("opening-hand choice only selects real owned card IDs", () => {
  assert.deepEqual(chooseBottomCards({ cardsToPutOnBottom: 2, hand: ["x", "y", "z"], cards: { x: { typeLine: "Land" }, y: { typeLine: "Creature" }, z: { typeLine: "Artifact" } } }), ["x", "z"]);
  assert.equal(chooseBottomCards({ cardsToPutOnBottom: 3, hand: ["x"], cards: { x: {} } }), null);
});
