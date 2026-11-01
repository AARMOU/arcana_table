#!/usr/bin/env bash
# B141 engine source = the exact B140 engine + targeted upstream backports (owner 2026-10-31: no hours-long full
# upgrade for B141; reuse work already done; minimal engine changes).
#
#   wingedsheep/argentum-engine@61e71e5 (checked out at ./argentum-engine)
#   + everything engine-upgrade/build140/apply_engine.sh applies (the 19 B140 patches, specs, symbol checks)
#   + engine-upgrade/b141/patches/*.patch, in order:
#       0001 Encore keyword (upstream e76eaef4a, #3829), needed by approved #662 Phyrexian Triniform.
#            Engine/SDK code applies unchanged; docs/sdk-index.md keeps B140's MarkExileOnDeathEffect line;
#            the CMR snapshot adds only Impulsive Pilferer.
# Fails on any patch that does not apply exactly. Appends to engine-source.txt (provenance).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
bash engine-upgrade/build140/apply_engine.sh
for p in engine-upgrade/b141/patches/*.patch; do
  git -C argentum-engine apply --check "$ROOT/$p"
  git -C argentum-engine apply "$ROOT/$p"
done
grep -rqw --include=*.kt "encore" argentum-engine/mtg-sdk || { echo "missing engine symbol: encore" >&2; exit 1; }
{
  echo "B141 backports:"
  for p in engine-upgrade/b141/patches/*.patch; do echo "sha256 $(sha256sum "$p")"; done
} | tee -a engine-source.txt
