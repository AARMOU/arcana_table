import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
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

export { validateDeck, selectAction, chooseBottomCards } from "./game-core.mjs";
