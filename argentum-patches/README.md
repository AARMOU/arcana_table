# Patches applied to Argentum for the in-app engine (chunk 3)

Base: wingedsheep/argentum-engine `7a9663d8e4df4ce7db223023ae7714f0028e08a9`.

- `0001-android-regex.patch` — Android's ICU regex engine rejects an unescaped `}` or `]` outside a
  character class and an escaped `\-` inside one. Fixes ManaCost, DraftsimMana, AiResponseParser,
  ChangeWordInTextExecutor and CardFacesProjector. Apply with `git am` on the base commit.

These are kept here (not pushed to Argentum). They could be offered upstream later.
