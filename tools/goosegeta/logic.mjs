import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const BASIC = new Set(["Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes"]);
const SPECIAL_SINGLETON_EXCEPTION = new Set(["Hare Apparent"]);

export function decksFile() {
  return resolve(HERE, "decks.json");
}

export function loadDecks(file = decksFile()) {
  const payload = JSON.parse(readFileSync(file, "utf8"));
  if (payload.schema !== 1 || !Array.isArray(payload.decks)) throw new Error("Unknown deck manifest");
  return payload.decks;
}

export function runtimeCardIndex(paths = [
  resolve(HERE, "../../android/app/src/main/assets/argentum/all-cards.index.tsv"),
  resolve(HERE, "../../android/app/src/main/assets/argentum/custom-cards.index.tsv"),
]) {
  const cards = new Set();
  for (const path of paths) {
    const lines = readFileSync(path, "utf8").split(/\r?\n/);
    for (const line of lines) {
      const [name, kind = "card"] = line.split("\t");
      if (name && kind !== "token") cards.add(name.trim().toLowerCase());
    }
  }
  return cards;
}

export function validateDeck(deck, index) {
  if (!deck || typeof deck.commander !== "string" || !deck.cards || typeof deck.cards !== "object") throw new Error("Invalid deck data");
  const names = Object.entries(deck.cards);
  const count = names.reduce((sum, [_, quantity]) => sum + quantity, 0);
  if (count !== 100) throw new Error("Commander deck must contain exactly 100 cards; got " + count);
  if (deck.cards[deck.commander] !== 1) throw new Error("Commander must appear exactly once");
  for (const [name, quantity] of names) {
    if (!Number.isInteger(quantity) || quantity < 1) throw new Error("Invalid card count: " + name);
    if (quantity > 1 && !BASIC.has(name) && !SPECIAL_SINGLETON_EXCEPTION.has(name)) throw new Error("Unsupported duplicate: " + name);
    if (!index.has(name.toLowerCase())) throw new Error("Not in the installed Argentum card index: " + name);
  }
  // This verifies runtime card PRESENCE, not color identity or exact rules behavior.
  return { commander: deck.commander, cards: names.flatMap(([name, quantity]) => Array(quantity).fill(name)).filter(name => name !== deck.commander) };
}

function list(value) {
  return Array.isArray(value) ? value.filter(x => typeof x === "string" && x.length > 0) : [];
}

function safeOption(info) {
  if (!info || !info.action || info.isAffordable === false || info.isManaAbility === true) return false;
  if (info.requiresTargets || info.hasXCost || info.requiresChoices || info.requiresManaColorChoice) return false;
  if (Array.isArray(info.targetRequirements) && info.targetRequirements.length) return false;
  return true;
}

function attackAction(info) {
  const action = structuredClone(info.action);
  const valid = list(info.validAttackers);
  const mandatory = new Set(list(info.mandatoryAttackers));
  const byAttacker = info.attackTargetsByAttacker ?? {};
  const generic = list(info.validAttackTargets);
  const attackers = {};
  for (const id of valid) {
    const targets = list(byAttacker[id]).length ? list(byAttacker[id]) : generic;
    if (!targets.length) {
      if (mandatory.has(id)) return null;
      continue;
    }
    // Do not send a fabricated target. Use only an engine-supplied legal target.
    if (mandatory.has(id) || Object.keys(attackers).length < 2) attackers[id] = targets[0];
  }
  for (const id of mandatory) if (!attackers[id]) return null;
  action.attackers = attackers;
  return action;
}

function blockAction(info, state) {
  const action = structuredClone(info.action);
  const legal = list(info.validBlockers);
  const mandatory = info.mandatoryBlockerAssignments ?? {};
  const cards = state?.cards ?? {};
  const attackers = Object.entries(cards).filter(([_, card]) => card && card.isAttacking === true).map(([id]) => id);
  const selected = {};
  for (const id of legal) {
    const required = list(mandatory[id]);
    const target = required.find(x => attackers.includes(x)) ?? attackers[0];
    if (target) selected[id] = [target];
    else if (required.length) return null;
  }
  action.blockers = selected;
  return action;
}

export function selectAction(message) {
  if (message?.type !== "stateUpdate" || message.interactive !== true) return null;
  if (message.pendingDecision != null && message.pendingDecision.type !== "PlayCardDecision") return null;
  const actions = Array.isArray(message.legalActions) ? message.legalActions : [];
  const matching = type => actions.filter(info => info?.action?.type === type);
  const land = matching("PlayLand").find(safeOption);
  if (land) return { action: structuredClone(land.action), info: land, reason: "play a legal land" };
  const spells = matching("CastSpell").filter(safeOption);
  if (spells.length) {
    const stateCards = message.state?.cards ?? {};
    spells.sort((a, b) => {
      const av = Number(stateCards[a.action.cardId]?.manaValue ?? 0);
      const bv = Number(stateCards[b.action.cardId]?.manaValue ?? 0);
      return bv - av;
    });
    return { action: structuredClone(spells[0].action), info: spells[0], reason: "cast a legal no-target spell" };
  }
  const attackers = matching("DeclareAttackers")[0];
  if (attackers) {
    const action = attackAction(attackers);
    if (action) return { action, info: attackers, reason: "declare legal attackers" };
    return null;
  }
  const blockers = matching("DeclareBlockers")[0];
  if (blockers) {
    const action = blockAction(blockers, message.state);
    if (action) return { action, info: blockers, reason: "declare legal blockers" };
    return null;
  }
  const pass = matching("PassPriority")[0];
  if (pass) return { action: structuredClone(pass.action), info: pass, reason: "pass priority" };
  return null;
}

export function chooseBottomCards(message) {
  const count = Number(message?.cardsToPutOnBottom ?? 0);
  const hand = list(message?.hand);
  if (!Number.isInteger(count) || count < 0 || count > hand.length || !message?.cards) return null;
  const sorted = [...hand].sort((a, b) => {
    const first = message.cards[a] ?? {};
    const second = message.cards[b] ?? {};
    const rank = card => /land/i.test(card.typeLine ?? "") ? 0 : /Creature/i.test(card.typeLine ?? "") ? 2 : 1;
    return rank(first) - rank(second);
  });
  return sorted.slice(0, count);
}
