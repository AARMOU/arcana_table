"""Where things live in the Arcana repository, and readers for the card sources, registry and assets."""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
TOOL_DIR = ROOT / "tools" / "cardkit"
DATA_DIR = TOOL_DIR / "data"
ANDROID = ROOT / "android"
MODULE = ANDROID / "custom-cards"
PKG = "com/arcanatable/customcards"
CARDS_DIR = MODULE / "src/main/kotlin" / PKG / "cards"
TESTS_DIR = MODULE / "src/test/kotlin" / PKG
REGISTRY = MODULE / "src/main/kotlin" / PKG / "CustomCards.kt"
ASSETS = ANDROID / "app/src/main/assets/argentum"
LIBS = ANDROID / "app/libs"
DOCS = ROOT / "docs" / "agents"
GENERATED = DOCS / "generated"

SCAFFOLD_MARK = "CARDKIT-SCAFFOLD"
TODO_MARK = "CARDKIT-TODO"

UPSTREAM_REPO = "wingedsheep/argentum-engine"
UPSTREAM_COMMIT = "c1c04ca3265c61df913af6db65460ad4ce409614"


def rel(path: Path) -> str:
    """Repository-relative path with forward slashes."""
    try:
        return path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def upstream_url(path: str) -> str:
    return f"https://github.com/{UPSTREAM_REPO}/blob/{UPSTREAM_COMMIT}/{path}"


# ---------------------------------------------------------------- Kotlin string literals

_ESCAPES = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\", "$": "$", "'": "'", "b": "\b"}


def read_string_expr(src: str, pos: int) -> tuple[str | None, int]:
    """Reads a Kotlin string expression ("a" + "b", or a raw triple-quoted string) at [pos].

    Returns (value, end) or (None, pos) when there is no string literal there."""
    out: list[str] = []
    i = pos
    n = len(src)
    while True:
        while i < n and src[i] in " \t\r\n":
            i += 1
        if src.startswith('"""', i):
            end = src.find('"""', i + 3)
            if end < 0:
                return None, pos
            out.append(src[i + 3:end])
            i = end + 3
        elif i < n and src[i] == '"':
            i += 1
            buf: list[str] = []
            while i < n and src[i] != '"':
                c = src[i]
                if c == "\\" and i + 1 < n:
                    nxt = src[i + 1]
                    if nxt == "u" and i + 5 < n:
                        buf.append(chr(int(src[i + 2:i + 6], 16)))
                        i += 6
                        continue
                    buf.append(_ESCAPES.get(nxt, nxt))
                    i += 2
                    continue
                buf.append(c)
                i += 1
            if i >= n:
                return None, pos
            out.append("".join(buf))
            i += 1
        else:
            break
        j = i
        while j < n and src[j] in " \t\r\n":
            j += 1
        if j < n and src[j] == "+":
            i = j + 1
            continue
        break
    if not out:
        return None, pos
    return "".join(out), i


def kotlin_string(text: str) -> str:
    """A Kotlin string literal (one line, escaped) for [text]."""
    esc = text.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\n", "\\n")
    return f'"{esc}"'


# ---------------------------------------------------------------- card sources

_CARD_DECL = re.compile(r'^val\s+(\w+)\s*=\s*card\(\s*"((?:[^"\\]|\\.)*)"\s*\)', re.M)
_PROP = re.compile(r'^\s{4}(manaCost|colorIdentity|typeLine|oracleText)\s*=\s*', re.M)
_INT_PROP = re.compile(r'^\s{4}(power|toughness|startingLoyalty)\s*=\s*(-?\d+)\s*$', re.M)


@dataclass
class CardSource:
    val: str
    name: str
    path: Path
    text: str
    props: dict = field(default_factory=dict)

    @property
    def is_scaffold(self) -> bool:
        return SCAFFOLD_MARK in self.text or TODO_MARK in self.text


_ANY_CARD = re.compile(r'^(?:private\s+)?val\s+(\w+)\s*=\s*card\(\s*"((?:[^"\\]|\\.)*)"\s*\)', re.M)
_COMPOSED = re.compile(r'^val\s+(\w+)\s*(?::\s*CardDefinition\s*)?=\s*CardDefinition\.\w+\(', re.M)


def parse_card_file(path: Path) -> list[CardSource]:
    text = path.read_text(encoding="utf-8")
    found = []
    decls = list(_CARD_DECL.finditer(text))
    composed = list(_COMPOSED.finditer(text))
    if composed and not decls:
        # Multi-face card: public val composed from private face definitions; the first face names it.
        face = _ANY_CARD.search(text)
        if face:
            face_cards = parse_card_file_faces(path, text)
            props = face_cards[0].props if face_cards else {}
            name, _ = read_string_expr(face.group(0), face.group(0).index('"'))
            return [CardSource(m.group(1), name or face.group(2), path, text, props) for m in composed[:1]]
    return _parse_decls(path, text, decls)


def parse_card_file_faces(path: Path, text: str) -> list[CardSource]:
    return _parse_decls(path, text, list(_ANY_CARD.finditer(text)))


def _parse_decls(path: Path, text: str, decls: list) -> list[CardSource]:
    found = []
    for k, m in enumerate(decls):
        body_end = decls[k + 1].start() if k + 1 < len(decls) else len(text)
        body = text[m.end():body_end]
        props: dict = {}
        for pm in _PROP.finditer(body):
            value, _ = read_string_expr(body, pm.end())
            if value is not None and pm.group(1) not in props:
                props[pm.group(1)] = value
        for im in _INT_PROP.finditer(body):
            props.setdefault(im.group(1), int(im.group(2)))
        name, _ = read_string_expr(m.group(0), m.group(0).index('"'))
        found.append(CardSource(m.group(1), name or m.group(2), path, text, props))
    return found


@lru_cache(maxsize=1)
def card_sources() -> tuple[CardSource, ...]:
    out: list[CardSource] = []
    for path in sorted(CARDS_DIR.glob("*.kt")):
        out.extend(parse_card_file(path))
    return tuple(out)


# ---------------------------------------------------------------- registry

_CONST = re.compile(r'const\s+val\s+(\w+)\s*=\s*"((?:[^"\\]|\\.)*)"')
_ENTRY = re.compile(r'Entry\(\s*(\w+)\s*,\s*"([^"]*)"\s*,\s*("(?:[^"\\]|\\.)*"|\w+)\s*\)')


@dataclass
class RegistryEntry:
    val: str
    set_code: str
    deck: str


@lru_cache(maxsize=1)
def registry_entries() -> tuple[RegistryEntry, ...]:
    text = REGISTRY.read_text(encoding="utf-8")
    consts = {m.group(1): m.group(2) for m in _CONST.finditer(text)}
    start = text.find("val entries")
    out = []
    for m in _ENTRY.finditer(text, start if start >= 0 else 0):
        deck = m.group(3)
        deck = deck[1:-1] if deck.startswith('"') else consts.get(deck, deck)
        out.append(RegistryEntry(m.group(1), m.group(2), deck))
    return tuple(out)


# ---------------------------------------------------------------- tests

@lru_cache(maxsize=1)
def test_files() -> tuple[Path, ...]:
    return tuple(sorted(TESTS_DIR.rglob("*.kt")))


@lru_cache(maxsize=1)
def tests_by_val() -> dict[str, list[Path]]:
    """Card val -> test files that import or name it (support helpers excluded)."""
    vals = {c.val for c in card_sources()}
    found: dict[str, list[Path]] = {}
    import_re = re.compile(r'import\s+com\.arcanatable\.customcards\.cards\.(\w+|\*)')
    for path in test_files():
        if "/support/" in path.as_posix():
            continue
        text = path.read_text(encoding="utf-8")
        imported = set(import_re.findall(text))
        if "*" in imported:
            imported |= {v for v in vals if re.search(rf"\b{v}\b", text)}
        stem = path.stem.removesuffix("ScenarioTest").removesuffix("Test")
        if stem in vals:
            imported.add(stem)
        for v in imported & vals:
            found.setdefault(v, []).append(path)
    return found


def own_test(val: str) -> Path | None:
    p = TESTS_DIR / f"{val}ScenarioTest.kt"
    return p if p.exists() else None


# ---------------------------------------------------------------- Argentum library assets

def normalize_name(name: str) -> str:
    return (name.replace("\u2019", "'").replace("\u2018", "'").replace("\u2014", "-")
            .replace("\u2013", "-").strip().casefold())


@lru_cache(maxsize=1)
def library_index() -> dict[str, str]:
    """Normalized library name -> kind (card, basic, token)."""
    out = {}
    path = ASSETS / "all-cards.index.tsv"
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            name, _, kind = line.partition("\t")
            out[normalize_name(name)] = kind or "card"
            if " // " in name:
                out.setdefault(normalize_name(name.split(" // ")[0]), kind or "card")
    return out


@lru_cache(maxsize=1)
def library_cards() -> tuple[dict, ...]:
    """Argentum library cards (name, oracleText, keywords, typeLine), from the app's assets."""
    out = []
    with (ASSETS / "all-cards.jsonl").open(encoding="utf-8") as fh:
        for line in fh:
            if not line.strip():
                continue
            d = json.loads(line)
            out.append({
                "name": d.get("name", ""),
                "oracleText": d.get("oracleText") or "",
                "typeLine": d.get("typeLine") or "",
                "manaCost": d.get("manaCost") or "",
            })
    return tuple(out)


@lru_cache(maxsize=1)
def custom_index_names() -> set[str]:
    path = ASSETS / "custom-cards.index.tsv"
    if not path.exists():
        return set()
    return {normalize_name(l.split("\t")[0]) for l in path.read_text(encoding="utf-8").splitlines() if l.strip()}


@lru_cache(maxsize=1)
def upstream_sources() -> dict[str, str]:
    """Normalized card name -> upstream source path at the pinned commit."""
    import gzip
    path = DATA_DIR / "upstream-card-sources.tsv.gz"
    if not path.exists():
        return {}
    out = {}
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        for line in fh:
            name, _, src = line.rstrip("\n").partition("\t")
            if name:
                out[normalize_name(name)] = src
    return out


def val_name_for(card_name: str) -> str:
    """The Kotlin val / file stem the repo uses for a card name: PascalCase of its words."""
    base = card_name.split(" // ")[0]
    base = (base.replace("\u2019", "'").replace("'", "").replace("\u2014", " ")
            .replace("-", " ").replace(",", " ").replace(".", " "))
    import unicodedata
    base = unicodedata.normalize("NFKD", base).encode("ascii", "ignore").decode()
    words = re.findall(r"[A-Za-z0-9]+", base)
    out = "".join(w[:1].upper() + w[1:] for w in words)
    if out and out[0].isdigit():
        out = "Card" + out
    return out
