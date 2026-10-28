#!/usr/bin/env python3
"""Install the rebuilt engine jars into THIS CI workspace only (never committed), print their sha256,
check Java 17 bytecode, prove the six Build 134 symbols are inside, and refresh the card corpus."""
import hashlib
import json
import shutil
import struct
import zipfile
from pathlib import Path

libs = Path("android/app/libs")
assets = Path("android/app/src/main/assets/argentum")
modules = {"mtg-sdk": "com/wingedsheep/sdk/model/CardDefinition.class",
           "mtg-sdk-tooling": "com/wingedsheep/sdk/tooling/CardLoader.class",
           "rules-engine": "com/wingedsheep/engine/core/GameAction.class",
           "ai": "com/wingedsheep/ai/engine/AIPlayer.class"}
for module, ident in modules.items():
    old = libs / f"argentum-{module}.jar"
    before = hashlib.sha256(old.read_bytes()).hexdigest()
    jars = [p for p in Path(f"argentum-engine/{module}/build/libs").glob("*.jar")
            if "sources" not in p.name and "javadoc" not in p.name]
    jars = [p for p in jars if ident in zipfile.ZipFile(p).namelist()]
    assert len(jars) == 1, (module, jars)
    shutil.copyfile(jars[0], old)
    with zipfile.ZipFile(old) as z:
        v = max(struct.unpack(">H", z.read(n)[6:8])[0] for n in z.namelist() if n.endswith(".class"))
    assert v <= 61, (module, v)
    print(f"jar {old.name} sha256={hashlib.sha256(old.read_bytes()).hexdigest()} (committed {before}) bytecode<={v}")

# The symbols must be compiled into the SDK jar, not merely present in source.
with zipfile.ZipFile(libs / "argentum-mtg-sdk.jar") as z:
    names = z.namelist()
    blob = b"".join(z.read(n) for n in names if n.endswith(".class"))
for symbol in ("StackObjectKind", "CopyControlledStackObjects", "DevoidSpellsOnly",
               "ColorlessSubtypeSpellsOrAbilitiesOnly", "maxManaValue", "duringYourTurnOnly"):
    in_names = any(symbol in n for n in names)
    in_bytes = symbol.encode() in blob
    assert in_names or in_bytes, f"{symbol} not compiled into argentum-mtg-sdk.jar"
    print(f"symbol {symbol}: {'class' if in_names else 'member'} present in argentum-mtg-sdk.jar")

out = Path("argentum-engine/arcana-export-out")
for name in ("all-cards.jsonl", "all-cards.index.tsv", "por-cards.jsonl"):
    old = (assets / name).read_text().splitlines()
    new = (out / name).read_text().splitlines()
    changed = [i for i, (a, b) in enumerate(zip(old, new)) if a != b]
    names = [json.loads(new[i])["name"] for i in changed[:20]] if name.endswith(".jsonl") else []
    print(f"corpus {name}: committed={len(old)} rebuilt={len(new)} changed_lines={len(changed)} {names}")
    shutil.copyfile(out / name, assets / name)
