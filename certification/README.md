# Independent card certification (Issue #81)

The owner review Worker correctly refuses Approve when an exact Copilot PR head lacks a separate independent rules check. Green Cardkit/Android CI is necessary, **not sufficient**. This folder provides an actual, narrowly scoped verifier rather than renaming ordinary CI as certification.

## First supported card: Resurrection Orb (#408, PR #410)

The trusted default-branch workflow `.github/workflows/arcana-independent-card-certification.yml` listens for the existing Cardkit and Android CI completion events. It only considers an **open Copilot-authored** `Implement Resurrection Orb` PR that fixes the open trusted queue issue #408, targets the expected integration branch, and whose **current exact commit** has successful `toolkit`, `scaffold`, and `cards-and-android` checks.

For that eligible head, a separate no-write-credentials runner checks out the candidate commit, injects **four independent JUnit behavioral scenarios from trusted `main`**, executes them with the committed engine, and checks the JUnit XML actually contains the expected passing test cases. Unlike Copilot's own scenario tests, the agent cannot modify these verification scenarios in its PR.

A distinct publishing job, with no checkout of untrusted code and only `checks:write`, revalidates current PR head, open work unit, and prerequisite CI. It publishes an **`Arcana independent certification`** check on the exact reviewed commit. If independent scenarios fail, the check is marked failure and approval remains blocked. A new Copilot commit necessarily needs a fresh pass. Neither this workflow nor approval merges, deploys, signs, or ships anything.

The checks cover: correct equip mana, attached-only lifelink, no resurrection for an unequipped creature, graveyard residence until the next end step, and return under the original owner rather than a temporary controller. They are **card-specific evidence**; they do not prove every possible combination or whole-game stability. Do not describe generic Cardkit success as Oracle certification.

## Safety, rollout, and limitations

- The workflow is inert until approved and merged to the default branch. No Cloudflare Worker deployment or GitHub Actions variable/budget change is required for this first case.
- Once merged, a **new** successful Cardkit or Development CI completion on the current #410 head is necessary to trigger it. Previously completed workflow events are not replayed by GitHub.
- The dashboard review feed republishes periodically, so the button may not update immediately. Owner sign-in and the existing exact-commit server-side gate remain authoritative.
- **Other cards remain uncertified** until a separate trusted independent scenario suite and explicit allowlist policy are added. This is intentionally fail-closed; do not generalize approval by regexing arbitrary test names.
- Do not add arbitrary PR-controlled scripts to the trusted publishing job or expose a write token to Gradle, untrusted tests, pull-request artifacts, or downloaded code.
- The existing dashboard/Worker use a broad `/certif/i` name match. A separate hardening review should pin the check's trusted provenance and exact name before treating this gate as protection against adversarial contributors. The first implementation only automates the missing positive result, **not** that security upgrade.
- Review the independent Kotlin tests and the real first workflow result before considering its behavior validated. A green PR-side YAML check is not proof the workflow will execute correctly in production.
