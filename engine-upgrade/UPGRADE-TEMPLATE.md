# Repeatable Argentum upgrade and APK packaging

## The rule

Validate a changed engine once, fix app regressions with focused comparisons, then package the exact green candidate. Reuse build outputs; never treat a cache hit as a passing test. An unrelated change does not invalidate every completed suite, but a changed engine, patch, card definition or dependency requires the relevant suites again. A new engine still needs the complete engine and integration gates before delivery.

## Upgrade record (copy for each new target)

| Input | Record |
| --- | --- |
| Arcana baseline | Exact commit and installed build number |
| Upstream engine | Exact commit, never moving main |
| Patch series | Content hashes; apply cumulatively with no fuzzy acceptance |
| Runtime | All four matching Java 17 JAR hashes |
| Card data | Full corpus/index, Portal corpus, retained Arcana custom definitions |
| Evidence | Successful workflow run, exact tested commit, XML counts and skips |
| Preview | Higher version, isolated package, stable private signing key/certificate |
| Phone checks | Mana/Treasures, targeting, multiplayer/resume and high-trigger game |

## Which work needs repeating?

| Change since a green candidate | Required work |
| --- | --- |
| APK version/label only | Verify source diff, successful-run identity and reports; checksum runtime; package/sign/verify APK |
| Test harness only | Run repaired tests against baseline and replacement, then the complete app suite once at the final candidate |
| App behavior | Focused affected tests while iterating; complete app suite, lint and Android packaging at the final candidate |
| Custom card definitions | Card scenario/custom-card regressions and affected app tests; full custom-card regression at the final batch |
| Engine, SDK, AI, mandatory patch, dependency or export changes | Full applicable upstream rules/card/AI and custom-card suites, exports, app suite, lint and packaging |
| Performance change | Identical baseline/candidate fixtures, behavioral parity and stress evidence before making speed claims |

## Reusable packaging workflow

`.github/workflows/argentum-upgrade-preview.yml` reads `engine-upgrade/preview-config.json`. For a new validated candidate, update its exact source commit, successful validation run, expected test/skip counts, engine pin and preview version. Keep the audit pin and full validation workflow engine pin consistent. The source guard requires the runtime/app/patch inputs to match that successful candidate; the only app-file difference it permits is the version code/name.

The workflow downloads evidence from the pinned successful run and checks its actual XML totals. It restores a runtime bundle keyed by upstream/patch/build-recipe hashes, or rebuilds all four modules and matching exports on a cache miss. It verifies every bundled file and requires all four JARs to be byte-identical to the tested manifest. It validates the corpus/index and Java 17 class versions before compiling the APK. Hash mismatch or changed app inputs stop packaging and require investigation/revalidation.

The workflow retains the verified runtime as an artifact and caches it for later packaging runs. It also caches Gradle dependencies. The first packaging run after introducing this template must rebuild because the earlier validation retained reports, but not binaries. Cache retention is best-effort: an evicted cache causes a rebuild, never a skipped verification. Validation artifacts expire after 30 days; expired evidence requires a new evidence-producing validation run, not a fabricated receipt.

CI produces an unsigned isolated-preview payload. Sign locally with the saved original Arcana key, keep key/password files outside GitHub, zipalign and verify the final package/version/certificate before delivery. B131 uses `com.arcanatable.argentum` and the original saved certificate; B130's temporary debug certificate differs, so B130 cannot be updated in place. Removing an old preview deletes its local app data; the regular `com.arcanatable` installation is separate.

Keep production merges, Play publication and unrelated card-development branch integration as separately authorized actions.

## Current verified candidate

Commit `369b7ff442e1b0815d505a9599902d18008364ea`, run [39840218785](https://github.com/AARMOU/arcana_table/actions/runs/39840218785): baseline focused 32 passed; replacement focused passed; full replacement Android suite 206 passed, zero failures/errors, 4 live-service tests skipped; lint and debug assembly passed. B131 changes only version metadata in the tested Android source.

## Weekly automatic upgrade

`.github/workflows/argentum-weekly-upgrade.yml` runs every Monday (and on demand). `engine-upgrade/weekly.py` rebuilds the patch series as commits on the current pin and **rebases** them onto upstream `main` with a three-way merge, instead of requiring each `.patch` to apply byte-for-byte. It then rewrites `argentum-patches/`, the audit pin and `preview-config.json`, proves the result with the strict `audit_patches.py`, rebuilds the four jars and card export, and runs the imported-card checks and the full app suite. The outcome is one rolling draft PR from `bot/argentum-weekly` into development, plus a line on issue #81. Nothing merges on its own.

Imported cards: custom cards must compile and pass their scenario tests on the new engine; every premade-deck card that resolved before must still resolve; the six official precons (`investigation/argentum-six-precons/deck-manifest.json`) are compared slot by slot, indexed coverage reported separately from rules-verified behavior, and a newly missing slot blocks; a custom card upstream newly implements blocks the upgrade (the collision test stays strict) until it is retired or approved in its own reviewed change; deck cards whose upstream rules changed are listed for a phone check.

Security: the workflow has two jobs. `validate` runs upstream's build scripts with a read-only token and no stored credentials, so it cannot change anything remotely; it uploads a candidate patch and its SHA-256. `publish` never runs upstream code: it checks the hash, refuses any path outside `argentum-patches/`, `engine-upgrade/`, the four `argentum-*.jar` files and `assets/argentum/`, then pushes `bot/argentum-weekly` and opens the PR. A push to `claude/argentum-weekly-upgrade` is a dry run: `validate` only, nothing published. `engine-upgrade/tests/test_weekly_workflow.py` enforces all of this and runs first in every run. Schedule: Mondays 09:17 UTC (03:17 Denver in summer, 02:17 in winter).

Files an Arcana patch deliberately replaces are listed in `engine-upgrade/rebase-rules.json`; when upstream edits one, the rebase keeps Arcana's replacement and the PR names the upstream commits to review.

Conflicts: `CHANGELOG.md` hunks take upstream and are dropped from refreshed patches. Recorded resolutions in `engine-upgrade/rerere/` replay automatically. Any other conflict opens an issue with the hunk; resolve it once with `git rerere` enabled, copy the new `.git/rr-cache/<id>` folder into `engine-upgrade/rerere/`, commit, and re-run. A patch upstream fully absorbs is retired from the series and named in the PR.

Adding an engine change: put the patch in `argentum-patches/` **and** list it in `MANDATORY` in `audit_patches.py`. That list is the only series the weekly upgrade carries forward; a patch kept in its own folder with its own apply script stays pinned to the old engine and turns every weekly PR red.
