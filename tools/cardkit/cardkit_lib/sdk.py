"""An index of the Argentum card-DSL entry points that exist in the committed SDK jar.

Generated with `javap` from android/app/libs/argentum-mtg-sdk.jar, so it is exactly what the app compiles
against (pinned upstream commit + Arcana patches). Agents grep it before writing a call: a facade member
that is not listed here does not exist. Regenerate after an engine jar update:

    tools/cardkit/cardkit sdk-index          # rewrites docs/agents/generated/sdk-api.{md,json}
    tools/cardkit/cardkit sdk-index --check  # CI: fails if the committed index is stale
"""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import zipfile
from functools import lru_cache

from . import repo

JAR = repo.LIBS / "argentum-mtg-sdk.jar"
OUT_MD = repo.GENERATED / "sdk-api.md"
OUT_JSON = repo.GENERATED / "sdk-api.json"

# Facades and builders a card file touches directly. Everything under sdk/dsl is included too.
EXTRA_CLASSES = [
    "com.wingedsheep.sdk.scripting.KeywordAbility$Companion",
    "com.wingedsheep.sdk.scripting.GameObjectFilter$Companion",
    "com.wingedsheep.sdk.scripting.GameObjectFilter",
    "com.wingedsheep.sdk.scripting.filters.unified.TargetFilter$Companion",
    "com.wingedsheep.sdk.scripting.filters.unified.GroupFilter$Companion",
    "com.wingedsheep.sdk.core.CounterType",
    "com.wingedsheep.sdk.core.CounterType$Companion",
]
ENUMS = [
    "com.wingedsheep.sdk.core.Keyword",
    "com.wingedsheep.sdk.core.Step",
    "com.wingedsheep.sdk.core.Zone",
    "com.wingedsheep.sdk.core.Color",
    "com.wingedsheep.sdk.model.Rarity",
]
_SKIP_SUFFIX = ("$WhenMappings", "$DefaultImpls")
_METHOD = re.compile(r"^\s*public (?:static )?(?:final )?(?:abstract )?([\w.$<>\[\], ?]+?) ([\w$-]+)\((.*)\);$")


def _simple(t: str) -> str:
    t = t.strip()
    t = re.sub(r"[\w.]+\.(\w+)", r"\1", t)
    t = t.replace("$", ".")
    return {"int": "Int", "boolean": "Boolean", "long": "Long", "double": "Double", "float": "Float",
            "void": "Unit", "String": "String", "Object": "Any"}.get(t, t)


def _javap(classes: list[str]) -> str:
    javap = shutil.which("javap")
    if not javap:
        raise SystemExit("javap not found: install a JDK (17+) to regenerate the SDK index")
    out = []
    for i in range(0, len(classes), 40):
        res = subprocess.run([javap, "-p", "-cp", str(JAR), *classes[i:i + 40]],
                             capture_output=True, text=True, check=True)
        out.append(res.stdout)
    return "\n".join(out)


def _dsl_classes() -> list[str]:
    with zipfile.ZipFile(JAR) as z:
        names = [n for n in z.namelist() if n.startswith("com/wingedsheep/sdk/dsl/") and n.endswith(".class")]
    classes = sorted(n[:-6].replace("/", ".") for n in names)
    return [c for c in classes if not c.endswith(_SKIP_SUFFIX) and not re.search(r"\$\d", c)]


def _fields(text: str) -> dict[str, dict[str, str]]:
    """Outer class (companion folded in) -> lower-cased field name -> declared field name."""
    out: dict[str, dict[str, str]] = {}
    current = None
    for line in text.splitlines():
        m = re.match(r"^(?:public |private |protected )?(?:final |abstract )*(?:class|interface|enum) ([\w.$]+)", line)
        if m:
            current = m.group(1).replace("$Companion", "")
            continue
        fm = re.match(r"^\s*(?:private|public|protected)? ?(?:static )?(?:final )?[\w.$<>\[\], ?]+ ([A-Za-z_][\w]*);$", line)
        if current and fm and fm.group(1) not in ("INSTANCE", "Companion"):
            out.setdefault(current, {}).setdefault(fm.group(1).casefold(), fm.group(1))
    return out


def build() -> dict:
    classes = _dsl_classes() + EXTRA_CLASSES
    text = _javap(classes + ENUMS)
    fields = _fields(text)
    result: dict = {"jar": repo.rel(JAR), "classes": {}, "enums": {}}
    current = None
    is_enum = False
    for line in text.splitlines():
        m = re.match(r"^(?:public |private |protected )?(?:final |abstract )*(?:class|interface|enum) ([\w.$]+)", line)
        if m:
            fq = m.group(1)
            is_enum = " enum " in f" {line} " or "extends java.lang.Enum" in line
            current = fq
            if is_enum:
                result["enums"][fq.rsplit(".", 1)[-1]] = []
            else:
                result["classes"][fq] = {}
            continue
        if current is None:
            continue
        if is_enum:
            em = re.match(r"^\s*public static final [\w.$]+ ([A-Z][A-Z0-9_]*);$", line)
            if em:
                result["enums"][current.rsplit(".", 1)[-1]].append(em.group(1))
            continue
        mm = _METHOD.match(line)
        if not mm:
            continue
        ret, name, params = mm.groups()
        if "$default" in name or name.startswith(("component", "access$")) or name in (
                "equals", "hashCode", "toString", "copy", "<init>"):
            continue
        name = re.sub(r"-[A-Za-z0-9_]{6,8}$", "", name)  # Kotlin value-class mangling
        plist = [] if not params.strip() else [_simple(p) for p in re.split(r",(?![^<]*>)", params)]
        prop = None
        if re.match(r"^(get|is)[A-Z]", name) and not plist:
            raw = name[3:] if name.startswith("get") else name[2:]
            known = fields.get(current.replace("$Companion", ""), {})
            prop = known.get(raw.casefold()) or (raw if raw[:2].isupper() else raw[:1].lower() + raw[1:])
        if re.match(r"^set[A-Z]", name) and len(plist) == 1:
            continue  # builder property setter; the getter already lists it
        members = result["classes"][current]
        sig = f"{prop}: {_simple(ret)}" if prop else f"{name}({', '.join(plist)}): {_simple(ret)}"
        members.setdefault(prop or name, [])
        if sig not in members[prop or name]:
            members[prop or name].append(sig)
    has_defaults = set(re.findall(r" ([\w-]+)\$default\(", text))
    result["withDefaults"] = sorted({re.sub(r"-[A-Za-z0-9_]{6,8}$", "", n) for n in has_defaults})
    for cls in list(result["classes"]):
        if not result["classes"][cls]:
            del result["classes"][cls]
    return result


def _display(fq: str) -> str:
    short = fq.replace("com.wingedsheep.sdk.", "")
    return short.replace("$Companion", "").replace("$", ".")


def render_md(data: dict) -> str:
    defaults = set(data["withDefaults"])
    lines = [
        "# Argentum SDK API index (generated)",
        "",
        f"<!-- Generated by `tools/cardkit/cardkit sdk-index` from `{data['jar']}`. Do not edit by hand. -->",
        "",
        "Every public facade/builder member in the committed SDK jar. **If a member is not listed here, it",
        "does not exist in the engine the app ships — do not call it.** Parameter names are not in the",
        "bytecode; read real usages via `docs/agents/generated/pattern-index.md`, the vendored",
        "`docs/agents/argentum-upstream/card-sdk-language-reference.md`, or the pinned upstream source.",
        "`*` after a member = it has default arguments (you may omit trailing parameters).",
        "",
        "Kotlin properties appear as `name: Type` (`Triggers.self`, `Costs.Tap`).",
        "",
        "## Enums",
        "",
    ]
    for enum, values in sorted(data["enums"].items()):
        lines.append(f"- **{enum}**: " + ", ".join(f"`{v}`" for v in values))
    lines.append("")
    for fq in sorted(data["classes"], key=_display):
        members = data["classes"][fq]
        lines += [f"## {_display(fq)}", ""]
        for name in sorted(members, key=str.casefold):
            star = "*" if name in defaults else ""
            for sig in members[name]:
                lines.append(f"- `{sig}`{star}")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def write(check: bool = False) -> bool:
    data = build()
    md = render_md(data)
    js = json.dumps(data, indent=1, sort_keys=True) + "\n"
    if check:
        ok = OUT_MD.exists() and OUT_JSON.exists() and OUT_MD.read_text(encoding="utf-8") == md \
            and OUT_JSON.read_text(encoding="utf-8") == js
        return ok
    repo.GENERATED.mkdir(parents=True, exist_ok=True)
    OUT_MD.write_text(md, encoding="utf-8")
    OUT_JSON.write_text(js, encoding="utf-8")
    return True


@lru_cache(maxsize=1)
def committed() -> dict:
    """The committed index (no JDK needed)."""
    if not OUT_JSON.exists():
        return {"classes": {}, "enums": {}, "withDefaults": []}
    return json.loads(OUT_JSON.read_text(encoding="utf-8"))


def keyword_enum() -> set[str]:
    return set(committed().get("enums", {}).get("Keyword", []))


def member_exists(facade: str, member: str) -> bool:
    """True if `Facade.member` exists (e.g. Effects.DrawCards, Triggers.self, Costs.Tap)."""
    classes = committed().get("classes", {})
    for fq, members in classes.items():
        short = _display(fq)
        if short.split(".")[-1] == facade or short == facade:
            if member in members:
                return True
        if short.endswith(f"{facade}.{member}"):  # nested object, e.g. Costs.additional
            return True
    return False


def vocabulary() -> set[str]:
    """Lower-cased, separator-free names of every SDK keyword, helper and facade member."""
    vocab = set()
    data = committed()
    for v in data.get("enums", {}).get("Keyword", []):
        vocab.add(v.replace("_", "").casefold())
    for fq, members in data.get("classes", {}).items():
        for m in members:
            vocab.add(re.sub(r"[^a-z0-9]", "", m.casefold()))
    return vocab
