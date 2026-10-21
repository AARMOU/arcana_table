# Patches applied to Argentum for the in-app engine (chunk 3)

Base: wingedsheep/argentum-engine `7a9663d8e4df4ce7db223023ae7714f0028e08a9`.

- `0001-android-regex.patch` — Android's ICU regex engine rejects an unescaped `}` or `]` outside a
  character class and an escaped `\-` inside one. Fixes ManaCost, DraftsimMana, AiResponseParser,
  ChangeWordInTextExecutor and CardFacesProjector. Apply with `git am` on the base commit.

These are kept here (not pushed to Argentum). They could be offered upstream later.
- `0002-engine-trial-build.patch` — compiles every module against the Java 17 API (`-Xjdk-release=17`,
  `jvmTarget 17`) so the jars run on Android, and adds a local `:arcana-export` module that writes
  Portal as card data (`por-cards.jsonl`) with a round-trip check. The PC build runs
  `:mtg-sdk:jar :mtg-sdk-tooling:jar :rules-engine:jar :ai:jar :arcana-export:run`.

- `0003-token-copy-triggered-abilities.patch` — stores token-defined triggered abilities in
  copiable card data, including additional abilities on token copies. Removes the corresponding
  state-only grants so the same trigger is not registered twice. This lets a Jolly Balloon Man
  copy of Smaug create exactly fourteen Treasures when it dies, and preserves that behavior on
  later copies.

## Smaug fix verification

The isolated `gpt/smaug-death-trigger-122` branch uses `smaug-validation.yml` to reproduce
the shipped engine's exact 0-versus-14 Treasure failure, rebuild all four runtime libraries from
the pinned engine base above plus the three patches, reject class files newer than Java 17,
run the complete custom-card suite (including six Smaug/negative-control scenarios), run the
Android unit tests and APK assembly, and run the upstream engine/card scenario suite.
Only after every gate passes does CI commit the tested runtime libraries to that isolated branch.

This is an engine fix against the rolled-back Build 112 source. The validation APK is a test
artifact, not a replacement for later app builds or a Play Store release. Integrate the tested
runtime libraries with the current app source before a release; this patch does not restore
the unrelated UI/deck changes that were removed by the rollback.
