# Arcana Table — pre-cleanup safety snapshot

> GitHub-native backup references; source branches remain unmodified. This is not an offsite Git mirror or backup of uncommitted code.

- Snapshot date: 2026-10-22
- Repository: AARMOU/arcana_table
- Original branches recorded: 74
- Original default branch: main
- Backup prefix: `backup/precleanup-20261022/`
- Snapshot method: backup branch from each recorded exact commit SHA, then read-back verification.
- NEVER merge, rebase, delete, overwrite or force-push original work under this operation.
- PR activity and CI may continue after capture; the backup is an as-of snapshot.
- Backup refs themselves are not branch-protected or offsite; protect/archive them separately before any future destructive cleanup.

## Original branch heads (immutable snapshot targets)

| Original branch | Exact original commit | Backup reference |
|---|---|---|
| `experimental/arcana-argentum` | `367898e29e297adda47ff15b635d6ce25e04bffc` | `backup/precleanup-20261022/experimental/arcana-argentum` |
| `experimental/arcana-argentum-engine-trial` | `e71766032bb7fb17910b1700df8fa7a9dac3ec8f` | `backup/precleanup-20261022/experimental/arcana-argentum-engine-trial` |
| `experimental/arcana-argentum-integration` | `7a995cf61191412be9f02001bab153da53560aef` | `backup/precleanup-20261022/experimental/arcana-argentum-integration` |
| `experimental/assistance-overhaul` | `25eddfa2d46225b7e97b304a8a0c5572b0c8792a` | `backup/precleanup-20261022/experimental/assistance-overhaul` |
| `experimental/targeting` | `c97da6491e46202d97c9e238c2aaf44b4c5f5e6b` | `backup/precleanup-20261022/experimental/targeting` |
| `GPT-eating-glue` | `5cd24e874e55d92e2f0959e905daeebddf624707` | `backup/precleanup-20261022/GPT-eating-glue` |
| `gpt/arcana-b130-priority-three-preview` | `43b27a89f01e850ec651f85f9be775d7804cb0bd` | `backup/precleanup-20261022/gpt/arcana-b130-priority-three-preview` |
| `gpt/arcana-five-feature-preview` | `97c6142203de00b5cc0742f2795d2279b5cc5239` | `backup/precleanup-20261022/gpt/arcana-five-feature-preview` |
| `gpt/argentum-myriad-planeswalker` | `1f42233980370c5f79caf61bc61f46b37217ee17` | `backup/precleanup-20261022/gpt/argentum-myriad-planeswalker` |
| `gpt/argentum-six-precons` | `ff5e2b719159904b51e3a46d479afa7018d9f4c9` | `backup/precleanup-20261022/gpt/argentum-six-precons` |
| `gpt/argentum-upgrade-20261022` | `1a42f8080842f765017ad09bd5f1241f3fd0ebb8` | `backup/precleanup-20261022/gpt/argentum-upgrade-20261022` |
| `gpt/argentum-upgrade-baseline-20261022` | `98e45f0b3d7f762a20c94a6d8e3cc81a9ea6eb1e` | `backup/precleanup-20261022/gpt/argentum-upgrade-baseline-20261022` |
| `gpt/argentum-upgrade-fix-action-decks-20261022` | `cd8ef174ed6ab6f6e5624ca2600f7f48f9cc7e2a` | `backup/precleanup-20261022/gpt/argentum-upgrade-fix-action-decks-20261022` |
| `gpt/argentum-upgrade-fix-layout-20261022` | `b6f779f62926912e7399cdd848ab6f4811b03283` | `backup/precleanup-20261022/gpt/argentum-upgrade-fix-layout-20261022` |
| `gpt/argentum-upgrade-fix-ui-startup-20261022` | `38785ba546a99b8e5386e082c5f6ec267da6c31a` | `backup/precleanup-20261022/gpt/argentum-upgrade-fix-ui-startup-20261022` |
| `gpt/argentum-upgrade-preview-20261022` | `83aa342e3fcd200710fb9188192189fc7cea7abf` | `backup/precleanup-20261022/gpt/argentum-upgrade-preview-20261022` |
| `gpt/argentum-upgrade-targeted-ci-20261022` | `369b7ff442e1b0815d505a9599902d18008364ea` | `backup/precleanup-20261022/gpt/argentum-upgrade-targeted-ci-20261022` |
| `gpt/argentum-warhammer-eldrazi-import` | `ab0a1fe19453494b7e3f12828bc880dc7e71014d` | `backup/precleanup-20261022/gpt/argentum-warhammer-eldrazi-import` |
| `gpt/b125-apk-dist` | `c4d58954a6c894a4837faddd69ee57f45b7bce2d` | `backup/precleanup-20261022/gpt/b125-apk-dist` |
| `gpt/b126-apk-preview` | `c086c3ae28af6aa7c2c6c134ad8acff8013115ad` | `backup/precleanup-20261022/gpt/b126-apk-preview` |
| `gpt/b129-priority-three` | `151114ac60a5baeff30fd7a9af0772c8df852cdb` | `backup/precleanup-20261022/gpt/b129-priority-three` |
| `gpt/b131-card-compatibility-20261022` | `89536604a175af241e45321f68ead6f1da633c1e` | `backup/precleanup-20261022/gpt/b131-card-compatibility-20261022` |
| `gpt/build122-fixes-from120` | `d57f6c2b6d0924af3804524a8368357b62fdcf13` | `backup/precleanup-20261022/gpt/build122-fixes-from120` |
| `gpt/build123-endstep-fix-from120` | `cc1442af1ddb127441505ff271362a08348a0ba1` | `backup/precleanup-20261022/gpt/build123-endstep-fix-from120` |
| `gpt/build123-verification-tools` | `355f16f8d2601a66ea624d883f0d80218966249e` | `backup/precleanup-20261022/gpt/build123-verification-tools` |
| `gpt/build128-mana-reliability` | `7593b1d5d037216510c3117db3b8f36ed2b944f3` | `backup/precleanup-20261022/gpt/build128-mana-reliability` |
| `gpt/closed-testing-signup` | `f4fe9ab5e8414620dab5598fa5f4bf987e61b438` | `backup/precleanup-20261022/gpt/closed-testing-signup` |
| `gpt/embedded-beta-signup` | `c4b3e3bc7fbbc2268ca187f6569ea6035f2d6c5e` | `backup/precleanup-20261022/gpt/embedded-beta-signup` |
| `gpt/embedded-signup-page` | `b5ca4da94bd1f399169135067b0d26999dc88734` | `backup/precleanup-20261022/gpt/embedded-signup-page` |
| `gpt/engine-delay-125-experiment` | `0a067d2912863eda1f1cbd5c673fc2d390acb4bf` | `backup/precleanup-20261022/gpt/engine-delay-125-experiment` |
| `gpt/engine-delay-126-candidate` | `91af507d189a00f866e6e55b1fb6059f84ead67e` | `backup/precleanup-20261022/gpt/engine-delay-126-candidate` |
| `gpt/engine-delay-127-apk-preview` | `14fe77b01f46bff179f0f72847a5639f111bb1e3` | `backup/precleanup-20261022/gpt/engine-delay-127-apk-preview` |
| `gpt/engine-delay-127-engine-index-candidate` | `1b7980e467b8068a56055d7ca89c4ca5dd7fa050` | `backup/precleanup-20261022/gpt/engine-delay-127-engine-index-candidate` |
| `gpt/engine-delay-127-investigation` | `f4a102b35371e0db29f915bd27e39d9337d843c2` | `backup/precleanup-20261022/gpt/engine-delay-127-investigation` |
| `gpt/engine-delay-127-realistic-profile` | `dfb3dbb454e4769b3cddbc1dd751f6b21bc642ff` | `backup/precleanup-20261022/gpt/engine-delay-127-realistic-profile` |
| `gpt/engine-delay-apk-build` | `025995bb9d37b2aa62a309ce7ab4852e2a81d377` | `backup/precleanup-20261022/gpt/engine-delay-apk-build` |
| `gpt/engine-delay-autopass-candidate` | `22a97ffac32a680f09d7c57a5dd61624216c2978` | `backup/precleanup-20261022/gpt/engine-delay-autopass-candidate` |
| `gpt/engine-delay-test-only` | `ce1db7ce94d9d26962fb341a2a3ca2fae3bd44a8` | `backup/precleanup-20261022/gpt/engine-delay-test-only` |
| `gpt/game-over-cause-logging-20261022` | `273df437383d6766d0f2f8858382361412acb3a8` | `backup/precleanup-20261022/gpt/game-over-cause-logging-20261022` |
| `gpt/generic-life-trigger-stress-tests` | `dde21253708b0cc1b9be8b9324e7605ef3b9159f` | `backup/precleanup-20261022/gpt/generic-life-trigger-stress-tests` |
| `gpt/investigate-endstep-122` | `502ba689d88e7c56f96496dba2ec02e33fa1eefe` | `backup/precleanup-20261022/gpt/investigate-endstep-122` |
| `gpt/landing-page-gallery` | `4be8b8cf3b14b36a89cba06ea84df8aab8454799` | `backup/precleanup-20261022/gpt/landing-page-gallery` |
| `gpt/landing-upcoming-features` | `0ed852f55c24f6e14e58be34735212f21b16a29d` | `backup/precleanup-20261022/gpt/landing-upcoming-features` |
| `gpt/play-com-arcanatable-3e17b24` | `10fc08dfb0fb237a291d3521af613afff2893ded` | `backup/precleanup-20261022/gpt/play-com-arcanatable-3e17b24` |
| `gpt/play-store-3e17b24` | `3e17b24e620590e4170bd4c2de2abaa4d18d9e87` | `backup/precleanup-20261022/gpt/play-store-3e17b24` |
| `gpt/site-screenshots-build112` | `70aac06e202e149a094cf978bce227d168bc0034` | `backup/precleanup-20261022/gpt/site-screenshots-build112` |
| `gpt/six-precon-missing-cards-batch-20261022` | `b76a472438f87a716333f476705306baa42b5881` | `backup/precleanup-20261022/gpt/six-precon-missing-cards-batch-20261022` |
| `gpt/six-precon-worker-eldrazi` | `ac807d746fac1c89f7586717ab0e6c42dfbd1e17` | `backup/precleanup-20261022/gpt/six-precon-worker-eldrazi` |
| `gpt/six-precon-worker-imperium` | `61c93b02c8e95bac63af434caac606a6545515c6` | `backup/precleanup-20261022/gpt/six-precon-worker-imperium` |
| `gpt/six-precon-worker-imperium-tribal` | `878a91d3381eec1b1f56a48de44109111b769c6a` | `backup/precleanup-20261022/gpt/six-precon-worker-imperium-tribal` |
| `gpt/six-precon-worker-integration-20261022` | `d3687c849dfc8b048d7b3551ff8d0bdb01848a47` | `backup/precleanup-20261022/gpt/six-precon-worker-integration-20261022` |
| `gpt/six-precon-worker-necron` | `a9de306a6f0916e96fa013ce8c3ac082ab9c3536` | `backup/precleanup-20261022/gpt/six-precon-worker-necron` |
| `gpt/six-precon-worker-necron-recursion` | `8189e69efb8e602cc1843e55d5ff3651cea4c364` | `backup/precleanup-20261022/gpt/six-precon-worker-necron-recursion` |
| `gpt/six-precon-worker-necron-tomb` | `33e8d125f7a3651716bb554097b0e1aefa9ff361` | `backup/precleanup-20261022/gpt/six-precon-worker-necron-tomb` |
| `gpt/six-precon-worker-tyranid` | `da7ca5c0e0ccabb31a3a67c89d2a36d11697677a` | `backup/precleanup-20261022/gpt/six-precon-worker-tyranid` |
| `gpt/smaug-death-trigger-122` | `49167a2624472296c696eaca65525a72e89fa119` | `backup/precleanup-20261022/gpt/smaug-death-trigger-122` |
| `gpt/table-manual-expand-pinch-zoom` | `929dedf5c1a28aad4ab5d0c7b1f134f33d15d3e4` | `backup/precleanup-20261022/gpt/table-manual-expand-pinch-zoom` |
| `gpt/work-deck-support` | `7593b1d5d037216510c3117db3b8f36ed2b944f3` | `backup/precleanup-20261022/gpt/work-deck-support` |
| `gpt/work-engine-progress` | `7593b1d5d037216510c3117db3b8f36ed2b944f3` | `backup/precleanup-20261022/gpt/work-engine-progress` |
| `gpt/work-local-resume` | `7593b1d5d037216510c3117db3b8f36ed2b944f3` | `backup/precleanup-20261022/gpt/work-local-resume` |
| `gpt/work-log-events` | `7593b1d5d037216510c3117db3b8f36ed2b944f3` | `backup/precleanup-20261022/gpt/work-log-events` |
| `gpt/work-log-grouping` | `74fe1e2e139bce8375bfe0e258dd5b9a658c43da` | `backup/precleanup-20261022/gpt/work-log-grouping` |
| `gpt/work-menu-layout` | `5b2669b3ab06dee75b11250ab7ef1d0773a19c30` | `backup/precleanup-20261022/gpt/work-menu-layout` |
| `gpt/work-multiworker-handoff` | `c76022c2afb4f90710ee1b89527a30a3bf72adf7` | `backup/precleanup-20261022/gpt/work-multiworker-handoff` |
| `gpt/work-popup-notifications` | `88b8894eb45b189ce18f8623cb3044baad80f677` | `backup/precleanup-20261022/gpt/work-popup-notifications` |
| `gpt/work-priority-stops` | `7593b1d5d037216510c3117db3b8f36ed2b944f3` | `backup/precleanup-20261022/gpt/work-priority-stops` |
| `gpt/work-repro-reports` | `7593b1d5d037216510c3117db3b8f36ed2b944f3` | `backup/precleanup-20261022/gpt/work-repro-reports` |
| `gpt/work-targeting-alerts` | `7593b1d5d037216510c3117db3b8f36ed2b944f3` | `backup/precleanup-20261022/gpt/work-targeting-alerts` |
| `gpt/work-token-groups` | `7593b1d5d037216510c3117db3b8f36ed2b944f3` | `backup/precleanup-20261022/gpt/work-token-groups` |
| `gpt/work-turn-recap` | `7593b1d5d037216510c3117db3b8f36ed2b944f3` | `backup/precleanup-20261022/gpt/work-turn-recap` |
| `gpt/zone-casting-graveyard-exile` | `8c62a3f0d106f0df0dbee056087d32fde544f9ec` | `backup/precleanup-20261022/gpt/zone-casting-graveyard-exile` |
| `main` | `c533d0520feeab5a9c98d1d61ddb034b34672bf3` | `backup/precleanup-20261022/main` |
| `wip/build105-bundle` | `964269258708d672f0f9fee201f568272dd679da` | `backup/precleanup-20261022/wip/build105-bundle` |
| `wip/build107-fourplayer` | `cedc2795cd7e6de835b35c72f5416e0889ea3ea1` | `backup/precleanup-20261022/wip/build107-fourplayer` |

## Open PR metadata as captured

| PR | Title | Head | Base | Draft |
|---|---|---|---|---|
| [#43](https://github.com/AARMOU/arcana_table/pull/43) | [Six-precon import] 12 real card implementations and full regression coverage (isolated) | `gpt/six-precon-worker-integration-20261022` | `gpt/argentum-six-precons` | yes |
| [#42](https://github.com/AARMOU/arcana_table/pull/42) | Log authoritative game-over causes and winning-card evidence (Build 131) | `gpt/game-over-cause-logging-20261022` | `gpt/argentum-upgrade-preview-20261022` | yes |
| [#41](https://github.com/AARMOU/arcana_table/pull/41) | [Argentum engine — isolated] Myriad opponent or planeswalker defender choice (CR 702.116) | `gpt/argentum-myriad-planeswalker` | `gpt/argentum-six-precons` | yes |
| [#40](https://github.com/AARMOU/arcana_table/pull/40) | [Six-precon isolated worker] Canoptek Tomb Sentinel — full regression passed | `gpt/six-precon-worker-necron-tomb` | `gpt/argentum-six-precons` | yes |
| [#39](https://github.com/AARMOU/arcana_table/pull/39) | [Six-precon isolated worker] And They Shall Know No Fear — exact tribal protection | `gpt/six-precon-worker-imperium-tribal` | `gpt/argentum-six-precons` | yes |
| [#38](https://github.com/AARMOU/arcana_table/pull/38) | [Six-precon isolated worker] Triarch Praetorian — graveyard ETB and unearth | `gpt/six-precon-worker-necron-recursion` | `gpt/argentum-six-precons` | yes |
| [#37](https://github.com/AARMOU/arcana_table/pull/37) | [Six-precon isolated worker] Deny the Witch — all stack-object kinds tested | `gpt/six-precon-worker-imperium` | `gpt/argentum-six-precons` | yes |
| [#35](https://github.com/AARMOU/arcana_table/pull/35) | Package Build 131 engine-upgrade preview with reusable verified-runtime workflow | `gpt/argentum-upgrade-preview-20261022` | `gpt/argentum-upgrade-targeted-ci-20261022` | yes |
| [#34](https://github.com/AARMOU/arcana_table/pull/34) | [BLOCKED: Argentum Myriad choice gap] Chittering Dispatcher — isolated card work | `gpt/six-precon-worker-eldrazi` | `gpt/argentum-six-precons` | yes |
| [#33](https://github.com/AARMOU/arcana_table/pull/33) | [Six-precon isolated worker] Zoanthrope — real engine scenarios | `gpt/six-precon-worker-tyranid` | `gpt/argentum-six-precons` | yes |
| [#32](https://github.com/AARMOU/arcana_table/pull/32) | [Six-precon isolated worker] Hexmark Destroyer + Canoptek Spyder — green scenarios | `gpt/six-precon-worker-necron` | `gpt/argentum-six-precons` | yes |
| [#31](https://github.com/AARMOU/arcana_table/pull/31) | [Argentum upgrade] Repair Android regressions and compare shipped/replacement runtimes | `gpt/argentum-upgrade-targeted-ci-20261022` | `gpt/argentum-upgrade-20261022` | yes |
| [#30](https://github.com/AARMOU/arcana_table/pull/30) | [Argentum upgrade] Fix targeted screen size and design preview tests | `gpt/argentum-upgrade-fix-layout-20261022` | `gpt/argentum-upgrade-20261022` | yes |
| [#29](https://github.com/AARMOU/arcana_table/pull/29) | [Argentum upgrade] Diagnose shared UI navigation and six resilience test failures | `gpt/argentum-upgrade-fix-ui-startup-20261022` | `gpt/argentum-upgrade-20261022` | yes |
| [#28](https://github.com/AARMOU/arcana_table/pull/28) | [Argentum upgrade] Fix targeted treasure and custom-deck assertions | `gpt/argentum-upgrade-fix-action-decks-20261022` | `gpt/argentum-upgrade-20261022` | yes |
| [#27](https://github.com/AARMOU/arcana_table/pull/27) | [Dev only] Graveyard/exile play controls — engine integration verification (team 14) | `gpt/zone-casting-graveyard-exile` | `gpt/table-manual-expand-pinch-zoom` | yes |
| [#26](https://github.com/AARMOU/arcana_table/pull/26) | [Dev only] Table overview refinements and verification (team 09) | `gpt/table-manual-expand-pinch-zoom` | `gpt/build128-mana-reliability` | yes |
| [#11](https://github.com/AARMOU/arcana_table/pull/11) | Build 128: mana payment reliability, cast affordance, and persistent pool display | `gpt/build128-mana-reliability` | `gpt/engine-delay-127-apk-preview` | yes |
| [#10](https://github.com/AARMOU/arcana_table/pull/10) | Draft: preserve auto-pass behavior while avoiding full legal-action UI enrichment | `gpt/engine-delay-autopass-candidate` | `gpt/build123-endstep-fix-from120` | yes |
| [#8](https://github.com/AARMOU/arcana_table/pull/8) | Build 123 from 120: fix end-step AI stalls and retain the Build 122 fixes | `gpt/build123-endstep-fix-from120` | `GPT-eating-glue` | no |

## Restoration plan

For any preserved branch, use the backup reference's exact commit as the recovery source. Verify the commit and tree before changes. Do not move existing refs without explicit authorization.

## Verification note

Snapshot verification is performed after creating all backup references. Read-back checks must compare each backup's commit SHA to the exact value above; a matching ref proves Git objects remain addressable in this repository. It is not equivalent to a successful clean clone from an independent backup.
