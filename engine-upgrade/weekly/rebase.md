A patch does not apply even on the pinned engine `b38db9760c`: it was made on a different engine (often a numbered build's). Re-fit it to the pin first, then the upgrade can run; see **Handoff** below.

### `0038-chosen-color-land-mana-bonus.patch` → `docs/sdk-index.md` (does_not_apply_on_pin)

```diff
error: patch failed: docs/sdk-index.md:1831
error: docs/sdk-index.md: patch does not apply
```
