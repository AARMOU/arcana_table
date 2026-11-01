# Goosegeta recovery manifest — PARKED

Owner decision: October 31, 2026 (“Box it”; “I want all UI corrected”). The owner confirmed no active match needed preservation.

## Completed parking

- Authenticated runtime shutdown: [run 41264632755](https://github.com/AARMOU/arcana_table/actions/runs/41264632755), source `66c546fcc97daa00ed4add68a8cf2399fe8699ce`.
- Isolated `arcana-table-rooms-goosegeta-test`: `GOOSEGETA_OPENAI_ENABLED=false`, `GOOSEGETA_ENABLED=false`; deployed settings version `7a722a26-d120-4c38-b623-386f3e176d04`.
- Shared `arcana-review`: Goosegeta feature flag OFF; settings version `53069c55-1d82-428e-92ea-bffeef58ff9d`.
- Only those three plain-text flags changed. The settings operation inherited every other binding; its readback verified non-Goosegeta bindings, secret names and cron unchanged. Existing code, Workers, Durable Object namespaces, room storage and budget ledgers were retained.
- Health, normal Control login redirect and authentication boundaries passed. No provider request was made by parking.
- Seven experimental Goosegeta workflows disabled. Original rollout jobs also source-disabled. The parking workflow's runtime job was subsequently disabled in source; its archival job does not deploy.
- Existing Project Goose automation has a permanent explicit hold preventing automatic Goosegeta pickup while ordinary card/B141/pipeline work continues.
- [UI PR #727](https://github.com/AARMOU/arcana_table/pull/727) merged as `6ffcbe64ba94e65d4a6cdd98af927025b6402cb6`. [Pages run 41264802884](https://github.com/AARMOU/arcana_table/actions/runs/41264802884) succeeded. Published HTML was independently fetched: Parked present; summon/model controls absent; ordinary check-in retained.
- UI source removes opponent invitation/deck/model/chat/retry/dismiss controls, neutralizes old invitation fragments/cached forms, and changes the engineering agent label to Goose / Project Goose. New Control source returns authenticated HTTP 410 for the retired route. The live backend remains its retained code with its runtime feature flag OFF; the new 410 implementation has not been separately deployed.
- 132 local Control/browser/backend tests passed. All four exact-head PR checks passed (controls, website, coordination guard and observer). No Android build or APK was made.
- Native invite UI removed on #641 in `88c09f933256adc22180703f6c41a13d66d8c2d5` and #700 in `99423571f495eaef142ffcd7365ba30186a8d377`. Installed historical test APKs are unchanged; next normal app integration must carry the cleanup. No new native build result is claimed.
- Drafts #572/#641/#654/#674/#707 closed as parked, with original branches retained. #700 remains available as the shared connection fix; preserve its effective native lifecycle/foreground-resume changes and tests without merging the experimental ancestry wholesale.

## Original source references

All ten dated archive references were read back and matched exactly. They retain original commit ancestry, even though working branches may now include parking edits.

| Reference suffix under `archive/goosegeta/2026-10-31/` | Original commit |
|---|---|
| 572 | `924ea02c928a06545a7a61f29a621560c55fb74c` |
| 641 | `8c5a6acacc7c999887ba2e790da8f2b11990e30d` |
| 642 | `68878593bc1891a685caf4809b1810683b0c8bab` |
| 654 | `1565c5871b1e43fa9309c56530b38b11d8ffda52` |
| 667 | `253238cba7ca7693bb47d38bb67e0f0f5940a9f7` |
| 674 | `1f004f2758bdd476aa7aa3b2dea8505d591740fe` |
| 700 | `e826d62996f0a6574bee838f94d031c444163a5c` |
| 707 | `26c780ab29a75a077d95ddd159e7432fce05d929` |
| deployed | `c03623a4617460847f9a11168877b1ecf9d795ed` |
| main | `8746e8e6f22eae731cf61b4a9f4cc3a08c450046` |

Dependency path: #572 prototype → #641 cloud/native (#667 model preflight merged into this branch) → #674 recovery/model overlay → #700 native handoff fix. Control #642 merged into main; #707 recovery UI remained unmerged. #654 rollout explicitly checked out separately pinned cloud source. Current #674 source `1f004f2f` and last ON-deployed cloud source `c03623a4` share tree `7c866f728c7b4a8c6f9310f44c5df3956a6ba5fd`; persona follow-up `8490020f` was reverted.

## What is in the box

[Source-preservation run 41264848005](https://github.com/AARMOU/arcana_table/actions/runs/41264848005) created a complete Git bundle of the ten original refs and their ancestry. GitHub artifact `13067151292` (`Goosegeta-Source-Preservation.zip`) was downloaded and SHA-256 verified: `cc0f309443a63cf9c685dd2f90f1b35e7ef52d7ef368b51cbe95b35cde93879e`.

The box also contains:
- Original test APK/provenance artifact 13049847087; ZIP SHA-256 `7cb63f23be02d13ea02975d74799ed634f779e496ca998573e850c4d3ff20497`.
- Handoff test APK/provenance artifact 13061211239; ZIP SHA-256 `560715a3f601732b68097501886f0c63b62f0b89846c546a26e6f7b4fb853a4a`.
- Native JUnit artifacts 13048009226 and 13061230283; original digests recorded in the disposition report and final checksum inventory.
- Parking evidence artifact 13066712663 (runtime readback, retained binding names/types, versions and health/auth result) and masked parking job log.
- Original PR metadata and relevant #81 coordination/design/STOP/recovery notes in HISTORICAL-EVIDENCE.json.
- Assessment report, recovery manifest and checksums.

The artifact bytes are preserved as evidence, not newly released APKs. Do not install or rerun them as a current version. No private diagnostics, screenshots, OAuth tokens, provider keys or secret values were exported. Full original Unhinged Intelligence Directive text was not recovered; Claude's historical summary and withdrawal are retained, with that limitation explicit.

## Working components and known failures

Evidence supports host-authoritative seat admission, cloud presence/auth, privacy/replay protections, synthetic bounded rejection recovery, and the owner's physical-Fold report that #700 app switching works. Full real-phone turn progression/completed-game reliability remains unverified. The observed stalled match had 37 moves sent / 36 accepted and a host-rejected status.

The guest has a bounded candidate protocol (maximum 32 choices), unsupported/coupled decisions can pause, and free fallback uses a simple action order. Banter is scripted when model is off. Some transient rejection recovery is bounded and tested synthetically; it is not a complete gameplay solution. Old poisoned sessions lacked recovery fingerprints. Provider quality/cost, persistent game history/rivalry memory and a strong strategic hybrid were never certified. Deck index presence does not certify every card/rules interaction.

The native-AI/GPT persona, information audit, strategic simulation and memory directions were rescinded. Claude's architecture assessment and withdrawal are historical documents, not backlog assignments. Do not silently recreate them.

## Reopen months later

A NEW explicit owner decision is required before any resumed implementation, service enablement, inference, build or release. Generic check-in/“Go” instructions do not reopen this parked lane.

1. Verify SHA256SUMS and unpack Goosegeta-Source-Preservation.zip. Review its REFS.txt and bundle checksum.
2. Import the complete bundle into a fresh repository, for example:
   `git init goosegeta-recovery`
   `git -C goosegeta-recovery fetch /absolute/path/Goosegeta-Original-Source.bundle 'refs/remotes/origin/archive/goosegeta/2026-10-31/*:refs/heads/archive/goosegeta/2026-10-31/*'`
3. Branch from the chosen original checkpoint. Inspect its original AGENTS.md, lockfiles, Node/JDK/Android versions, docs and tests. Source directories: tools/goosegeta; android/cloud-room; native #641/#700 diff; Control; docs/SUMMON-GOOSEGETA.md; docs/GOOSEGETA-PAID-AI-PREFLIGHT.md; tools/goosegeta-rollout and Goosegeta workflows.
4. Reconcile current service versions/config, dependencies, billing and relevant existing tests. Use fixture-only tests before live activity. Do not reset lifetime reservation ledgers, export secrets or replay old ON-setting workflows.
5. Establish any new narrow scope and separate deployment/build/provider spending approval with the owner.

The production room service, ordinary Control/card/billing operations and release baseline were not rolled back to park Goosegeta.
