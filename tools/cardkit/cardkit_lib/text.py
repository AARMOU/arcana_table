"""Oracle-text normalization so the same rules sentence on two cards compares equal."""
from __future__ import annotations

import re

_REMINDER = re.compile(r"\s*\([^()]*\)")
_SELF_REFS = re.compile(
    r"\bthis (creature|card|artifact|enchantment|land|equipment|vehicle|spell|permanent|aura|"
    r"planeswalker|battle|saga|token|class|case|siege)\b"
)
_NUMBER_WORDS = (
    "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen "
    "sixteen seventeen eighteen nineteen twenty"
).split()
_NUMBER_WORD = re.compile(r"\b(" + "|".join(_NUMBER_WORDS) + r")\b")
_TOKEN = re.compile(r"\{[^}]+\}|[+\-]?n/[+\-]?n|[a-z~'+\-/]+|:|n")
# "Flavor Name — rules" (ability words, Warhammer weapon names). Not keyword costs like "Flashback—{1}".
_ABILITY_WORD = re.compile(r"^([^{}:—\n]{1,40}?) — (?=\S)")


def split_lines(oracle: str) -> list[str]:
    """Oracle text -> ability lines, with reminder text removed and empties dropped."""
    lines = []
    for raw in (oracle or "").replace("\r", "").split("\n"):
        line = _REMINDER.sub("", raw).strip()
        if line:
            lines.append(line)
    return lines


def strip_ability_word(line: str) -> str:
    m = _ABILITY_WORD.match(line)
    if m and len(m.group(1).split()) <= 6:
        return line[m.end():]
    return line


def normalize_line(line: str, card_name: str = "") -> str:
    s = strip_ability_word(line.strip())
    s = s.replace("’", "'").replace("−", "-")
    names = []
    if card_name:
        for face in card_name.split(" // "):
            names.append(face)
            if "," in face:
                names.append(face.split(",")[0])
    for n in sorted(names, key=len, reverse=True):
        if n:
            s = re.sub(re.escape(n.replace("’", "'")), "~", s, flags=re.I)
    s = s.casefold()
    s = _SELF_REFS.sub("~", s)
    s = re.sub(r"\{(\d+)\}", "{n}", s)
    s = re.sub(r"\{(?:[wubrg]|[wubrg2]/[wubrgp]|[wubrg]/[wubrg]/p)\}", "{m}", s)  # any coloured pip
    s = re.sub(r"\d+", "n", s)
    s = _NUMBER_WORD.sub("n", s)
    s = s.replace("—", " - ")
    return s


def tokens(normalized: str) -> list[str]:
    return _TOKEN.findall(normalized)


def is_keyword_line(line: str, keywords: list[str]) -> bool:
    """True if [line] is only a comma-separated list of [keywords] (with simple parameters)."""
    if not keywords:
        return False
    kws = {k.casefold() for k in keywords}
    parts = [p.strip().casefold() for p in re.split(r",|;", _REMINDER.sub("", line)) if p.strip()]
    if not parts:
        return False
    for part in parts:
        head = re.split(r"\s+(?=[{\d]|from\b|x\b)|—", part)[0].strip()
        if head not in kws and part not in kws:
            return False
    return True


_TRIGGER_HEAD = re.compile(r"^((?:when|whenever|at the beginning of|at)\b[^,]*,)\s*(.+)$")


def clauses(normalized: str) -> list[str]:
    """A normalized line split into sentences, trigger conditions and costs (for combination matching)."""
    out: list[str] = []
    for sent in _sentences(normalized):
        sent = sent.strip()
        if not sent:
            continue
        if '"' in sent:  # a granted ability in quotes is one unit; never match its pieces separately
            out.append(sent)
            continue
        if ":" in sent and not sent.startswith(("when", "whenever", "at ")) and sent.index(":") < 60:
            cost, _, eff = sent.partition(":")
            out += [cost.strip() + ":", eff.strip()]
            continue
        m = _TRIGGER_HEAD.match(sent)
        if m:
            out += [m.group(1).strip(), m.group(2).strip()]
        else:
            out.append(sent)
    return [c for c in out if c]


def _sentences(s: str) -> list[str]:
    """Splits at ". " outside quoted text."""
    out, start, quoted = [], 0, False
    for i, ch in enumerate(s):
        if ch == '"':
            quoted = not quoted
        elif ch == "." and not quoted and i + 1 < len(s) and s[i + 1] == " ":
            out.append(s[start:i + 1])
            start = i + 2
    out.append(s[start:])
    return [x for x in out if x.strip()]


def ability_words(oracle: str) -> set[str]:
    """Names printed as "Name — rules" (ability words, flavor-named abilities)."""
    found = set()
    for line in split_lines(oracle):
        m = _ABILITY_WORD.match(line)
        if m:
            found.add(m.group(1).strip().casefold())
    return found
