"""Safety invariants for .github/workflows/argentum-weekly-upgrade.yml.

Run: python3 -m unittest -v engine-upgrade/tests/test_weekly_workflow.py   (needs PyYAML)

The workflow builds upstream Argentum, i.e. runs third-party build scripts. These tests pin the trust
boundary so it cannot quietly regress:
  * the job that runs upstream code holds a read-only token and stores no credentials, so it cannot
    push, open PRs or edit issues, on a dry run or otherwise;
  * the job that can write never checks out or runs upstream code and never runs on a dry run;
  * the upstream engine is checked out by exact commit, never a moving branch;
  * publish applies only an in-scope candidate (engine-upgrade/candidate_scope.py, run from a copy taken
    before the candidate is applied), and replaces the bot branch only with a lease on a head it wrote;
  * the run ends red unless the upgrade is green, so a failing dry run can never look like a success;
  * the running copy of this workflow (main's, on a schedule) must equal the tested copy on the base.
"""
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/argentum-weekly-upgrade.yml"
WRITE_COMMANDS = re.compile(r"\bgit\s+push\b|\bgh\s+(pr|issue|api|release|workflow)\b|\bcurl\b.*-X\s*(POST|PUT|PATCH|DELETE)")
# `gradle` as a command (gradle, ./gradlew, gradle-locked), not a `*.gradle.kts` path on the scope allowlist.
UPSTREAM_CODE = re.compile(r"gradle(?!\.kts)|wingedsheep|argentum-engine|weekly\.py\s+(rebase|install)|python3?\s+(-\S+\s+)*\S*audit_patches|setup-java|setup-android")


def load():
    data = yaml.safe_load(WORKFLOW.read_text())
    data["on"] = data.pop(True, data.get("on"))  # YAML 1.1 reads the key `on` as True
    return data


def text(node) -> str:
    return json.dumps(node)


class WeeklyWorkflowSafety(unittest.TestCase):
    def setUp(self):
        self.wf = load()
        self.jobs = self.wf["jobs"]
        self.validate = self.jobs["validate"]
        self.publish = self.jobs["publish"]

    def test_default_token_has_no_permissions(self):
        self.assertEqual(self.wf.get("permissions"), {})

    def test_only_three_jobs(self):
        self.assertEqual(set(self.jobs), {"validate", "publish", "verdict"})

    def test_validate_token_is_read_only(self):
        self.assertEqual(self.validate["permissions"], {"contents": "read"})

    def test_validate_checkouts_store_no_credentials(self):
        checkouts = [s for s in self.validate["steps"] if str(s.get("uses", "")).startswith("actions/checkout")]
        self.assertGreaterEqual(len(checkouts), 2)
        for step in checkouts:
            self.assertIs(step.get("with", {}).get("persist-credentials"), False, step)

    def test_validate_never_writes_remotely(self):
        for step in self.validate["steps"]:
            self.assertIsNone(WRITE_COMMANDS.search(step.get("run", "")), step.get("name"))
            self.assertNotIn("GH_TOKEN", text(step.get("env", {})), step.get("name"))
            self.assertNotIn("github.token", text(step), step.get("name"))
            self.assertNotIn("secrets.", text(step), step.get("name"))

    def test_upstream_checked_out_by_exact_commit(self):
        upstream = [s for s in self.validate["steps"]
                    if s.get("with", {}).get("repository") == "wingedsheep/argentum-engine"]
        self.assertEqual(len(upstream), 1)
        self.assertEqual(upstream[0]["with"]["ref"], "${{ steps.target.outputs.sha }}")
        resolve = next(s for s in self.validate["steps"] if s.get("id") == "target")
        self.assertIn("[0-9a-f]{40}", resolve["run"])

    def test_publish_skipped_on_dry_run(self):
        self.assertIn("github.event_name != 'push'", self.publish["if"])
        self.assertEqual(self.publish["needs"], "validate")

    def test_publish_runs_no_upstream_code(self):
        for step in self.publish["steps"]:
            self.assertIsNone(UPSTREAM_CODE.search(text(step)), step.get("name") or step.get("uses"))
        checkout = next(s for s in self.publish["steps"] if str(s.get("uses", "")).startswith("actions/checkout"))
        self.assertNotIn("repository", checkout.get("with", {}))
        self.assertEqual(checkout["with"]["ref"], "${{ needs.validate.outputs.base_sha }}")

    def test_publish_verifies_candidate_before_applying(self):
        names = [s.get("name", "") for s in self.publish["steps"]]
        verify = names.index("Verify the candidate before using it")
        self.assertLess(verify, names.index("Push the upgrade branch"))
        run = self.publish["steps"][verify]["run"]
        self.assertIn("sha256sum -c", run)
        self.assertIn("outside the upgrade scope", run)
        self.assertNotIn(".github", run.split("allowed =")[1].split(")")[0])

    def test_every_blocked_upgrade_is_reported(self):
        # A conflict or a failed engine build must reach publish (issue) on live runs and fail dry runs,
        # never stop silently.
        for status in ("rebased", "conflict", "build_failed"):
            self.assertIn(status, self.publish["if"])
        report = next(s for s in self.publish["steps"] if s.get("name") == "Report a blocked upgrade")
        self.assertIn("build_failed", report["if"])
        self.assertIn("conflict", report["if"])
        final = next(s for s in self.validate["steps"] if s.get("id") == "final")
        self.assertIn("build_failed", final["run"])
        build = next(s for s in self.validate["steps"] if s.get("id") == "build")
        self.assertTrue(build.get("continue-on-error"))
        dry = next(s for s in self.validate["steps"] if s.get("name") == "Fail the dry run when the upgrade is blocked")
        self.assertIn("build_failed", dry["if"])
        self.assertIn("conflict", dry["if"])
        self.assertEqual(self.validate["outputs"]["status"], "${{ steps.final.outputs.status }}")

    def test_only_publish_pushes(self):
        pushers = [(job, s.get("name")) for job, spec in self.jobs.items() for s in spec["steps"]
                   if re.search(r"\bgit\s+push\b", s.get("run", ""))]
        self.assertEqual(pushers, [("publish", "Push the upgrade branch")])


    def step(self, job, name):
        return next(s for s in self.jobs[job]["steps"] if s.get("name") == name)

    def test_running_workflow_must_match_the_tested_copy(self):
        names = [s.get("name", "") for s in self.validate["steps"]]
        drift = names.index("The running workflow is the reviewed copy on the base")
        self.assertLess(drift, names.index("Workflow safety checks"))
        run = self.validate["steps"][drift]["run"]
        self.assertIn("git diff --exit-code", run)
        self.assertIn(".github/workflows/argentum-weekly-upgrade.yml", run)
        self.assertEqual(self.validate["steps"][drift]["env"]["RUNNING_SHA"], "${{ github.sha }}")

    def test_safety_checks_include_the_scope_tests(self):
        run = self.step("validate", "Workflow safety checks")["run"]
        for name in ("test_weekly_workflow.py", "test_candidate_scope.py", "test_weekly_docs.py",
                     "test_weekly_handoff.py", "test_weekly_retired.py", "test_weekly_imports.py",
                     "test_check_android_engine.py"):
            self.assertIn(name, run)

    def test_failed_engine_build_gives_a_starting_branch_but_no_pr(self):
        # A failed build still packages its refreshed patches and pins (the engine never built, so no jars
        # change) and publish pushes them to the bot branch as the place a fix starts (engine-upgrade/HANDOFF.md).
        # It is reported as an issue only: no PR, so a half-upgraded tree can never be merged.
        candidate = next(s for s in self.validate["steps"] if s.get("id") == "candidate")
        self.assertEqual(candidate["if"],
                         "steps.final.outputs.status == 'rebased' || steps.final.outputs.status == 'build_failed'")
        final = next(s for s in self.validate["steps"] if s.get("id") == "final")
        self.assertIn("build_failed", final["run"])
        install = next(s for s in self.validate["steps"] if s.get("name") == "Install the new engine into the app")
        self.assertEqual(install["if"], "steps.final.outputs.status == 'rebased'")
        for name in ("Verify the candidate before using it", "Apply the candidate and check what it changed",
                     "Push the upgrade branch"):
            self.assertEqual(self.step("publish", name)["if"],
                             "needs.validate.outputs.status == 'rebased' || needs.validate.outputs.status == 'build_failed'", name)
        self.assertEqual(self.step("publish", "Open or refresh the draft PR")["if"],
                         "needs.validate.outputs.status == 'rebased'")

    def test_fix_branches_are_dry_runs(self):
        branches = self.wf["on"]["push"]["branches"]
        self.assertIn("gpt/argentum-weekly-fix-**", branches)
        self.assertIn("claude/argentum-weekly-fix-**", branches)
        self.assertNotIn("bot/argentum-weekly", branches)
        self.assertIn("github.event_name != 'push'", self.publish["if"])
        rebase = next(s for s in self.validate["steps"] if s.get("id") == "rebase")
        self.assertEqual(rebase["env"]["WEEKLY_FORCE"], "${{ github.event_name == 'push' && '1' || '' }}")

    def test_no_bytecode_in_the_candidate_checkout(self):
        self.assertEqual(self.wf["env"]["PYTHONDONTWRITEBYTECODE"], "1")

    def test_red_runs_carry_handoff_notes(self):
        report = next(s for s in self.validate["steps"] if s.get("id") == "report")
        self.assertIn("weekly.py', 'handoff'", report["run"])
        self.assertIn("if not green:", report["run"])

    def test_publish_checks_the_applied_index_with_a_trusted_copy(self):
        names = [s.get("name", "") for s in self.publish["steps"]]
        verify = names.index("Verify the candidate before using it")
        apply = names.index("Apply the candidate and check what it changed")
        self.assertLess(verify, apply)
        self.assertLess(apply, names.index("Push the upgrade branch"))
        self.assertIn('cp engine-upgrade/candidate_scope.py "$RUNNER_TEMP/candidate_scope.py"',
                      self.publish["steps"][verify]["run"])
        run = self.publish["steps"][apply]["run"]
        self.assertLess(run.index("git apply --index"), run.index('python3 -I "$RUNNER_TEMP/candidate_scope.py"'))
        self.assertNotIn("engine-upgrade/candidate_scope.py", run)  # never the copy the patch may have touched

    def test_pre_check_allowlist_is_narrow(self):
        allowed = self.step("publish", "Verify the candidate before using it")["run"].split("allowed =")[1].split(")")[0]
        self.assertNotIn("'engine-upgrade/'", allowed)
        self.assertNotIn(".github", allowed)
        self.assertIn("not all(pairs)", self.step("publish", "Verify the candidate before using it")["run"])

    def test_bot_branch_replaced_only_with_a_lease_on_its_own_head(self):
        run = self.step("publish", "Push the upgrade branch")["run"]
        self.assertNotRegex(run, r"git push (-f|--force)\b(?!-with-lease)")
        self.assertIn('--force-with-lease="refs/heads/$BOT_BRANCH:$remote"', run)
        self.assertIn('git ls-remote origin "refs/heads/$BOT_BRANCH"', run)
        self.assertIn("Automated by .github/workflows/argentum-weekly-upgrade.yml", run)
        self.assertLess(run.index("exit 1"), run.index("git push"))

    def test_verdict_fails_unless_green(self):
        verdict = self.jobs["verdict"]
        self.assertEqual(verdict["needs"], ["validate", "publish"])
        self.assertEqual(verdict["if"], "always()")
        self.assertEqual(verdict["permissions"], {"issues": "write"})
        self.assertTrue(all("uses" not in s for s in verdict["steps"]))  # no checkout, no repo code
        run = self.step("verdict", "Overall result")["run"]
        self.assertIn('[ "$VALIDATE" != success ]', run)
        self.assertIn('[ "$GREEN" != true ]', run)
        self.assertIn('[ "$EVENT" = push ] && [ "$PUBLISH" != skipped ]', run)
        self.assertEqual(self.step("verdict", "Overall result")["env"]["GREEN"], "${{ needs.validate.outputs.green }}")

    def test_live_failures_that_published_nothing_reach_81(self):
        notice = self.step("verdict", "Tell issue 81 when a live run failed without publishing anything")
        self.assertIn("github.event_name == 'workflow_dispatch'", notice["if"])  # scheduled: the status alert
        self.assertIn("needs.validate.result != 'success'", notice["if"])
        self.assertIn("gh issue comment 81", notice["run"])
        self.assertNotIn("@", notice["run"].replace("$GITHUB", ""))

    def test_runner_and_actions_are_pinned(self):
        for name, job in self.jobs.items():
            self.assertEqual(job["runs-on"], "ubuntu-24.04", name)
            for s in job["steps"]:
                if "uses" in s:
                    self.assertRegex(s["uses"], r"^[\w-]+/[\w-]+@[0-9a-f]{40}$", s["uses"])

    def test_agent_docs_are_refreshed_and_published(self):
        docs = next(s for s in self.validate["steps"] if s.get("id") == "docs")
        for cmd in ("weekly.py docs argentum-engine", "cardkit sdk-index", "cardkit docs-check",
                    "unittest discover -s tools/cardkit/tests"):
            self.assertIn(cmd, docs["run"])
        self.assertIn("steps.docs.outcome", text(self.step("validate", "Write the report")))
        add = next(s for s in self.validate["steps"] if s.get("id") == "candidate")["run"]
        for path in ("docs/agents/generated/sdk-api.md", "docs/agents/argentum-upstream", "AGENTS.md",
                     "tools/cardkit/cardkit_lib/repo.py"):
            self.assertIn(path, add)

    # --- added 2026-11-02 after upstream's JDK 25 bump blocked every run -----------------------------
    EARLY = "'23 11 * * *'"

    def test_daily_upgrade_is_live_and_one_day_behind(self):
        crons = [c["cron"] for c in self.wf["on"]["schedule"]]
        self.assertIn("23 11 * * *", crons)
        self.assertEqual(self.wf["env"]["LAG_HOURS"], "${{ github.event_name == 'schedule' && '24' || inputs.lag_hours || '0' }}")
        self.assertNotIn("schedule", self.publish["if"])  # scheduled runs publish (and merge when green)
        self.assertNotIn("EARLY_WARNING", text(self.wf))
        target = next(s for s in self.validate["steps"] if s.get("id") == "target")["run"]
        self.assertIn('--before="$LAG_HOURS hours ago"', target)
        self.assertIn("--filter=tree:0", target)  # commit history only: nothing of upstream is run or checked out
        self.assertIn("^[0-9]{1,3}$", target)

    def test_scheduled_runs_post_to_81_only_on_a_new_problem(self):
        for name in ("Open or refresh the draft PR", "Merge a green upgrade into the integration branch", "Report a blocked upgrade"):
            run = self.step("publish", name)["run"]
            for line in run.splitlines():
                # A green run that could not be merged is a problem someone must act on: it always posts.
                if "gh issue comment 81" in line and "could not be merged" not in line:
                    self.assertIn('[ "$GITHUB_EVENT_NAME" = schedule ] ||', line, name)
        status = self.step("verdict", "Publish the engine status for Arcana Control")["run"]
        self.assertIn("if scheduled and problem and not same:", status)
        self.assertIn("Goose", status)
        self.assertIn("held >= 20", status)  # Claude only when the same problem outlives a day

    def test_engine_jdk_follows_upstream(self):
        jdk = next(s for s in self.validate["steps"] if s.get("id") == "jdk")
        self.assertIn("gradle-daemon-jvm.properties", jdk["run"])
        self.assertIn("jvmToolchain", jdk["run"])
        names = [s.get("name", s.get("uses", "")) for s in self.validate["steps"]]
        setups = [s for s in self.validate["steps"] if str(s.get("uses", "")).startswith("actions/setup-java")]
        self.assertEqual(setups[0]["with"]["java-version"], "${{ steps.jdk.outputs.version }}")
        self.assertLess(names.index(jdk["name"]), self.validate["steps"].index(setups[0]))

    def report_env(self):
        return self.step("validate", "Write the report")["env"]

    def test_android_safety_gates_green(self):
        android = next(s for s in self.validate["steps"] if s.get("id") == "android")
        self.assertIn("tools/check_android_engine.py check", android["run"])
        self.assertIn('--baseline-jars "$RUNNER_TEMP/old-libs"', android["run"])
        self.assertTrue(android.get("continue-on-error"))
        self.assertEqual(self.report_env()["ANDROID"], "${{ steps.android.outcome }}")
        self.assertIn("env['ANDROID'] == 'success'", self.step("validate", "Write the report")["run"])
        keep = self.step("validate", "Keep the shipped card data and engine jars for comparison")["run"]
        self.assertIn('android/app/libs "$RUNNER_TEMP/old-libs"', keep)
        names = [s.get("name") for s in self.validate["steps"]]
        self.assertLess(names.index("Keep the shipped card data and engine jars for comparison"),
                        names.index("Install the new engine into the app"))

    def test_app_library_versions_follow_the_engine(self):
        align = self.step("validate", "Raise the app's library versions to the engine's")
        self.assertIn("check_android_engine.py align", align["run"])
        names = [s.get("name") for s in self.validate["steps"]]
        self.assertLess(names.index("Install the new engine into the app"), names.index(align["name"]))
        self.assertLess(names.index(align["name"]), names.index("App test suite on the new engine"))
        add = next(s for s in self.validate["steps"] if s.get("id") == "candidate")["run"]
        allowed = self.step("publish", "Verify the candidate before using it")["run"].split("allowed =")[1].split(")")[0]
        for path in ("android/build.gradle.kts", "android/app/build.gradle.kts", "android/custom-cards/build.gradle.kts"):
            self.assertIn(path, add)
            self.assertIn(f"'{path}'", allowed)
        self.assertNotIn("'android/'", allowed)

    def test_current_build_is_read_from_main_and_gates_green(self):
        current = next(s for s in self.validate["steps"] if s.get("id") == "current")
        self.assertIn("integration/policy.json", current["run"])
        self.assertIn("MANDATORY", current["run"])
        cards = next(s for s in self.validate["steps"] if s.get("id") == "currentcards")
        self.assertEqual(cards["env"]["CURRENT_BRANCH"], "${{ steps.current.outputs.branch }}")
        self.assertNotIn("b140", text(self.wf).lower())  # no hard-coded numbered build left
        self.assertTrue(cards["run"].lstrip().startswith("set +e"), "a failing card must still be reported")
        self.assertIn("git archive FETCH_HEAD android |", cards["run"])  # the build's own tree, not ours
        self.assertIn('cp android/app/libs/argentum-*.jar "$d/android/app/libs/"', cards["run"])
        self.assertIn('align --engine-versions argentum-engine/gradle/libs.versions.toml --android "$d/android"', cards["run"])
        self.assertIn('weekly.py reconcile 4 --android "$d/android" --name currentreconcile', cards["run"])
        report = self.step("validate", "Write the report")["run"]
        self.assertIn("env['CURRENTCARDS'] == 'success'", report)
        self.assertIn("synced,", report)
        self.assertEqual(self.report_env()["SYNCED"], "${{ steps.current.outputs.synced }}")

    def test_patches_retired_on_purpose_do_not_count_as_missing(self):
        run = next(s for s in self.validate["steps"] if s.get("id") == "current")["run"]
        self.assertIn("RETIRED = ", run)
        self.assertIn("p not in base and p not in retired", run)

    def test_engine_status_takes_only_fixed_keys_from_validate(self):
        status = self.step("verdict", "Publish the engine status for Arcana Control")
        self.assertIn("github.event_name != 'push'", status["if"])
        self.assertIn("arcana-control-status-v1:engine", status["run"])
        for key in ("FAILED", "BLOCKER", "UPSTREAM", "CURRENT_BRANCH"):
            self.assertTrue(status["env"][key].startswith("${{ needs.validate.outputs."), key)
            self.assertNotIn("${{", status["run"])  # validate's outputs reach the script only through env
        self.assertTrue(status["run"].lstrip().startswith("set +e"), "status publishing must not turn a run red")
        self.assertTrue(status["run"].rstrip().endswith("exit 0"))
        self.assertIn("if k in plain", status["run"])  # unknown keys are dropped, never echoed
        self.assertIn(r"[0-9]{4}-[a-z0-9-]+\.patch", status["run"])
        self.assertIn("issues/78/comments", status["run"])
        self.assertIn("gh issue comment 81", status["run"])
        self.assertNotIn("@", status["run"].replace("=@$RUNNER_TEMP", "").replace("'@', '＠'", ""))


if __name__ == "__main__":
    unittest.main()
