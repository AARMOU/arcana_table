#!/usr/bin/env bash
# B140 engine source (Claude lane, #81): Eldrazi Temple + Corrupted Crossroads.
#
# Produces the exact engine source the B140 custom cards compile against:
#   wingedsheep/argentum-engine@61e71e5 (checked out at ./argentum-engine)
#   + mandatory Arcana series argentum-patches/0001-0008, 0011-0016 (engine-upgrade/audit_patches.py)
#   + Ulalek stage confirmation and its engine specs (engine-upgrade/ulalek/apply_patches.py)
#   + engine-upgrade/eldrazi-temple/patches/0001  (ColorlessSubtypeSpellsOrAbilitiesOnly)
#   + engine-upgrade/eldrazi-combined/patches/0002 (DevoidSpellsOnly; applies after Temple)
#   + engine-upgrade/build140/patches/0001 (DevoidSpellsOnly SDK baseline + sdk-index; no rules change)
#   + engine-upgrade/build140/patches/0002 (Temple: battlefield ability sources use current subtypes)
#   + the Temple and Corrupted Crossroads engine specs.
# Fails on any patch that does not apply exactly. Writes engine-source.txt (provenance).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
EXPECTED=61e71e586b100faa661f86cac827ac3c3bbb5a78
test "$(git -C argentum-engine rev-parse HEAD)" = "$EXPECTED" || { echo "argentum-engine is not at $EXPECTED" >&2; exit 1; }

python3 engine-upgrade/audit_patches.py
python3 engine-upgrade/ulalek/apply_patches.py

ELDRAZI=(
  engine-upgrade/eldrazi-temple/patches/0001-colorless-eldrazi-source-mana.patch
  engine-upgrade/eldrazi-combined/patches/0002-devoid-after-temple.patch
  # Engine bookkeeping 0002 omitted: DevoidSpellsOnly's SDK surface-baseline line and sdk-index entry.
  engine-upgrade/build140/patches/0001-devoid-sdk-surface.patch
  # Temple 0001 read printed+projected subtypes for ability sources, so a former Eldrazi kept paying.
  engine-upgrade/build140/patches/0002-temple-projected-source-subtypes.patch
)
for p in "${ELDRAZI[@]}"; do
  git -C argentum-engine apply --check "$ROOT/$p"
  git -C argentum-engine apply "$ROOT/$p"
done

DEST=argentum-engine/rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios
SPECS=(engine-upgrade/eldrazi-temple/engine-tests/*.kt engine-upgrade/corrupted-crossroads/engine-tests/*.kt)
for spec in "${SPECS[@]}"; do
  target="$DEST/$(basename "$spec")"
  if [ -e "$target" ]; then echo "$(basename "$spec") would overwrite an upstream test" >&2; exit 1; fi
  cp "$spec" "$target"
done

# every symbol the B140 custom cards need must now exist in engine source.
missing=0
for s in maxManaValue duringYourTurnOnly DevoidSpellsOnly ColorlessSubtypeSpellsOrAbilitiesOnly StackObjectKind CopyControlledStackObjects; do
  if ! grep -rqw --include=*.kt "$s" argentum-engine/mtg-sdk argentum-engine/rules-engine; then
    echo "missing engine symbol: $s" >&2; missing=1
  fi
done
[ "$missing" = 0 ]

{
  echo "upstream wingedsheep/argentum-engine@$EXPECTED"
  python3 - <<'PY'
import json
r = json.load(open("engine-upgrade/patch-report.json"))
print("mandatory series:", r["status"])
for p in r["patches"]:
    print(f"  {p['patch']}: {p['status']}")
PY
  for p in argentum-patches/*.patch "${ELDRAZI[@]}"; do echo "sha256 $(sha256sum "$p")"; done
  echo "engine specs added: $(for s in "${SPECS[@]}"; do basename "$s"; done | tr '\n' ' ')"
  echo "all six required engine symbols present"
} | tee engine-source.txt
