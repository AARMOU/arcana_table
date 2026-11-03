# Patches applied to Argentum for the in-app engine (chunk 3)

Base: wingedsheep/argentum-engine `7a9663d8e4df4ce7db223023ae7714f0028e08a9`.

- `0001-android-regex.patch` — Android's ICU regex engine rejects an unescaped `}` or `]` outside a
  character class and an escaped `\-` inside one. Fixes ManaCost, DraftsimMana, AiResponseParser,
  ChangeWordInTextExecutor and CardFacesProjector. Apply with `git am` on the base commit.

These are kept here (not pushed to Argentum). They could be offered upstream later.
- `0002-engine-trial-build.patch` — compiles every main source set to Java 17 bytecode against the Java 17
  API (`-Xjdk-release=17`, `jvmTarget 17`, `javac --release 17`) so the jars run on Android, whatever JDK
  upstream builds with; JVM-only test sources keep the toolchain's API (folded in from the former 0008).
  The settings live in an Arcana-owned `buildSrc/src/main/kotlin/arcana-android-target.gradle.kts`, applied
  by one line in the plugins block of upstream's `kotlin-jvm.gradle.kts`, so upstream's toolchain bumps and
  test-property edits in that file no longer conflict with it (they did on 2026-11-02: JDK 21 → 25). It also adds a local `:arcana-export` module that writes
  Portal as card data (`por-cards.jsonl`) with a round-trip check. The PC build runs
  `:mtg-sdk:jar :mtg-sdk-tooling:jar :rules-engine:jar :ai:jar :arcana-export:run`.
