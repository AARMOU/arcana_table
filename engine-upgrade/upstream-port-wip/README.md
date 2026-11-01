# Upstream engine catch-up (2026-11-01): complete

B141 runs upstream Argentum at `engine-upgrade/audit_patches.py` `TARGET`, with every `MANDATORY` patch in
`argentum-patches/` (0001–0021). That is the same series the Monday weekly carries on int-base (merged in #740).
`engine-upgrade/forward-ports/` is gone: the forward ports were only needed for the old `61e71e58` pin.

- Build, validate and stage: `.github/workflows/engine-port-validate.yml` (`stage=true` commits).
- Validation, run 41284786258: 9,224/9,224 engine tests, 800/800 custom-card tests after reconcile, and 441/441
  app tests.
- Conflict resolutions (B140 behaviour on upstream code; upstream-native where upstream already has it) are listed in
  #81 comment 6555198691 and visible in the patches themselves.
- Primal Amulet moved to `ManaSpellRider.TriggersWhenSpent` (upstream #3746). The deck-builder catalog honours
  `custom-overrides.txt` (from int-base).
- `.github/workflows/b141-engine-runtime.yml` (B140 engine + backports) is retired: manual runs only, staging disabled.
