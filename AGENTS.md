# Arcana Table — Repository Working Rules

> Project instructions for contributors and coding assistants. This file becomes the baseline policy **only after its documentation-only PR is reviewed and merged into the relevant integration base**. Until then it is the proposed workflow in `gpt/repository-governance-20261022`.

## Before starting any task

1. Read `docs/project/BRANCH_REGISTRY.md`, `docs/project/INTEGRATION_STATUS.md`, and `docs/project/BUILD_MANIFESTS.md`.
2. Refresh GitHub refs and open PRs. The registry is an **as-of record**, not a live worker-presence detector. Check whether the intended implementation already exists and whether another branch or PR owns it.
3. Identify the exact source branch, commit SHA, owning workstream, intended integration base, test gates, and required user authorization.
4. Never infer "idle", "complete", or "safe to delete" from a stale GitHub commit. Unpushed worker changes are invisible to GitHub.
5. For multi-worker tasks, assign one isolated branch per independent change and explicitly state its base; avoid unrelated edits to a worker's branch.

## Safety / ownership

- Do not delete, rename, force-push, rebase, or change the head of any existing branch without explicit owner approval.
- Do not merge, alter production `main`, publish, sign, upload releases/APKs/AABs, or deploy without explicit authorization.
- Treat all open-PR, ongoing-CI, recently-updated, and uncertain branches as **protected active or possibly active** until an owner/worker confirms status.
- Never edit another worker's active branch merely to fix an unrelated integration issue. Use an isolated fix branch with a recorded source SHA and PR base.
- The verified pre-cleanup Git bundle and SHA manifest are the rollback anchor: [backup index](https://github.com/AARMOU/arcana_table/tree/backup/precleanup-20261022/index); restore evidence is documented there. The snapshot does not include unpushed work.
- Prior to any later destructive action, recheck live branch refs, preserve new commits since the snapshot, and obtain approval.

## Evidence-based task states

A task moves through these states separately:

1. `proposed` — requirement documented, no working code verified.
2. `implemented` — exact branch and commit contain the change.
3. `tested` — named test suites passed **at that commit**, with linked workflow logs or reproducible local evidence.
4. `integrated` — exact commit or equivalent reviewed change is present in the designated integration branch.
5. `released` — verified APK/AAB/release explicitly contains the exact source and engine artifacts and has installation evidence.

"Tests passed" does **not** mean `integrated` or `released`. A PR, test file, build number in a title, or green narrow test is not proof of broader behavior.

## Commit and PR hygiene

- Name new working branches for a specific feature/workstream; record base SHA, expected output and acceptance tests.
- PRs should document head/base, exact tests, results, blockers, what remains, and whether another PR must land first.
- Update the registry, workstream status, and feature status whenever branches change; avoid stale duplicate notes.
- If source integration is unsafe or tests are red, leave the PR as draft, preserve the code, and document the smallest fix/retest sequence.
- Do not silently substitute a different Argentum engine JAR, SDK revision or generated card export.
- Separate website Pages changes on `main` from app implementation until a reviewed integration plan explicitly reconciles both.
- Never weaken rules fidelity or AI, drop legal triggers, or cap legitimate tokens to mask performance faults without explicit design approval.

## Build / release truth

A release candidate is identifiable only by a manifest that records:
- Source repository and exact Git commit, merged feature/PR inventory, build number/version code.
- Argentum upstream commit, local patches, exact JAR/artifact hashes, generated card-data hashes.
- Tests with commit-specific links, fails/waivers, API/min SDK, signing provenance and APK/AAB SHA-256.
- Whether the artifact was merely built, device-tested, promoted to integration, or published.

## On resuming from a different ChatGPT conversation

Read the repo's latest documents and current GitHub branches/PRs instead of making claims from conversation memory. If records disagree with GitHub, mark the mismatch and reconcile it explicitly. Do not claim another worker is running merely because a branch has an active-looking name.
