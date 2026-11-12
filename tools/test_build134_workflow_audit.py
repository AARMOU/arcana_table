"""Pure-standard-library regressions for the frozen Build 134 workflow audit."""
import unittest
from build134_workflow_audit import audit, git_blob_sha, steps, RELEASE_SCOPE, PREVIEW_SCOPE


def fixture():
    common = [f'      - name: gate {i}\n        run: echo {i}\n' for i in range(26)]
    common += [f'      - uses: action{i}@012345\n' for i in range(5)]
    common += ['      - name: Capture unresolved three-way source conflicts for manual port\n        run: echo capture\n',
               '      - name: Upload conflict and patch reports even on failure\n        run: echo diagnostics\n']
    common[0] = '      - name: Verify original logo and tutorial assets inside unsigned release package\n' + RELEASE_SCOPE + '\n'
    preview_common = common.copy()
    preview_common[0] = preview_common[0].replace(RELEASE_SCOPE, PREVIEW_SCOPE)
    prefix = ('on:\n  workflow_dispatch:\npermissions:\n  contents: read\n  actions: read\n'
              'jobs:\n  compatibility:\n    runs-on: ubuntu-24.04\n'
              '    timeout-minutes: 120\n    steps:\n')
    extras = [
        '      - name: Build installable combined test APK\n'
        '        run: |\n'
        '          ./gradlew --no-daemon :app:assembleDebug\n',
        '      - name: Verify installable APK and record provenance\n'
        '        run: |\n'
        '          test -s android/app/build/outputs/apk/debug/app-debug.apk\n'
        "          grep -F \"package: name='com.arcanatable.argentum' versionCode='134'\" combined-evidence/package-info.txt\n"
        '          "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs android/app/build/outputs/apk/debug/app-debug.apk\n'
        '          sha256sum android/app/build/outputs/apk/debug/app-debug.apk > combined-evidence/apk.sha256\n',
        '      - name: Upload APK only when every integrated source, card, AI, and app gate passed\n'
        '        if: success()\n'
        '        uses: actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02\n'
        '        with:\n'
        '          name: arcana-combined-argentum-eldrazi94-build134-logo-polish-debug-apk\n'
        '          retention-days: 14\n'
        '          if-no-files-found: error\n'
        '          path: android/app/build/outputs/apk/debug/app-debug.apk\n',
    ]
    release_prefix = prefix.replace(
        'on:\n  workflow_dispatch:\n',
        "on:\n  push:\n    branches: [gpt/release-upgraded-validation-20261025]\n"
        "    paths: ['.github/workflows/release-upgraded-source-validation.yml']\n"
        "  workflow_dispatch:\n",
    )
    return release_prefix + ''.join(common), prefix + ''.join(preview_common[:-2] + extras + preview_common[-2:])


class WorkflowAuditTest(unittest.TestCase):
    def test_frozen_shape(self):
        self.assertEqual(audit(*fixture()), [])

    def test_assertion_drift_rejected(self):
        a, b = fixture()
        self.assertTrue(any('shared step' in x for x in audit(a, b.replace('echo 4', 'echo SKIP'))))

    def test_scope_message_not_arbitrary_whitelist(self):
        a, b = fixture()
        self.assertTrue(audit(a, b.replace('only the approved debug preview', 'some unreviewed build')))

    def test_auto_trigger_rejected(self):
        a, b = fixture()
        self.assertTrue(any('manual-only' in x for x in audit(a, b.replace('on:\n', 'on:\n  push:\n'))))

    def test_upload_fail_closed(self):
        a, b = fixture()
        self.assertTrue(any('upload' in x for x in audit(a, b.replace('if-no-files-found: error', 'if-no-files-found: ignore'))))

    def test_unlisted_workflow_call_trigger_rejected(self):
        a, b = fixture()
        self.assertTrue(any('manual-only' in x for x in audit(
            a, b.replace('  workflow_dispatch:\n', '  workflow_dispatch:\n  workflow_call:\n'))))

    def test_release_push_cannot_expand_to_main(self):
        a, b = fixture()
        widened = a.replace('branches: [gpt/release-upgraded-validation-20261025]',
                            'branches: [main]')
        self.assertTrue(any('release: workflow triggers changed' in x
                            for x in audit(widened, b)))

    def test_release_push_cannot_expand_to_all_paths(self):
        a, b = fixture()
        widened = a.replace("paths: ['.github/workflows/release-upgraded-source-validation.yml']",
                            "paths: ['**']")
        self.assertTrue(any('release: workflow triggers changed' in x
                            for x in audit(widened, b)))

    def test_release_unreviewed_pull_request_trigger_rejected(self):
        a, b = fixture()
        widened = a.replace('  workflow_dispatch:\n',
                            '  pull_request:\n  workflow_dispatch:\n')
        self.assertTrue(any('release: workflow triggers changed' in x
                            for x in audit(widened, b)))

    def test_preview_schedule_trigger_rejected(self):
        a, b = fixture()
        widened = b.replace('  workflow_dispatch:\n',
                            '  schedule:\n    - cron: "0 * * * *"\n  workflow_dispatch:\n')
        self.assertTrue(any('manual-only' in x
                            for x in audit(a, widened)))

    def test_preview_permission_escalation_rejected(self):
        a, b = fixture()
        self.assertTrue(any('permissions' in x for x in audit(
            a, b.replace('  actions: read\n', '  actions: read\n  issues: write\n'))))

    def test_job_runner_drift_rejected(self):
        a, b = fixture()
        self.assertTrue(any('job configuration' in x for x in audit(
            a, b.replace('  compatibility:\n', '  compatibility:\n    runs-on: self-hosted\n'))))

    def test_identical_self_hosted_runner_rejected(self):
        a, b = fixture()
        def mutate(source):
            return source.replace('runs-on: ubuntu-24.04', 'runs-on: self-hosted')
        problems = audit(mutate(a), mutate(b))
        self.assertTrue(any('reviewed baseline' in x for x in problems), problems)

    def test_identical_timeout_expansion_rejected(self):
        a, b = fixture()
        def mutate(source):
            return source.replace('timeout-minutes: 120', 'timeout-minutes: 600')
        problems = audit(mutate(a), mutate(b))
        self.assertTrue(any('reviewed baseline' in x for x in problems), problems)

    def test_identical_privileged_container_rejected(self):
        a, b = fixture()
        def mutate(source):
            return source.replace('    timeout-minutes: 120\n',
                                  '    timeout-minutes: 120\n    container: privileged-image\n')
        problems = audit(mutate(a), mutate(b))
        self.assertTrue(any('reviewed baseline' in x for x in problems), problems)

    def test_identical_matrix_strategy_rejected(self):
        a, b = fixture()
        def mutate(source):
            return source.replace('    timeout-minutes: 120\n',
                                  '    timeout-minutes: 120\n    strategy:\n      matrix:\n        os: [ubuntu-24.04, self-hosted]\n')
        problems = audit(mutate(a), mutate(b))
        self.assertTrue(any('reviewed baseline' in x for x in problems), problems)

    def test_packaging_steps_cannot_be_interleaved(self):
        a, b = fixture()
        # Move the real multi-line verification step. A stale placeholder
        # would leave the fixture unchanged and never exercise this guard.
        verify = '      - name: Verify installable APK and record provenance\n'
        upload = '      - name: Upload APK only when every integrated source, card, AI, and app gate passed\n'
        before, remainder = b.split(verify, 1)
        verify_body, after = remainder.split(upload, 1)
        moved = (before + upload + after).replace(
            '      - name: gate 4\n', verify + verify_body + '      - name: gate 4\n')
        self.assertEqual(len(steps(moved)), 36)
        self.assertTrue(any('contiguous' in x for x in audit(a, moved)))

    def test_contiguous_packaging_before_final_required_gate_rejected(self):
        a, b = fixture()
        marker = '      - name: Build installable combined test APK\n'
        tail = '      - name: Capture unresolved three-way source conflicts for manual port\n'
        before, remainder = b.split(marker, 1)
        packaging, diagnostics = remainder.split(tail, 1)
        moved = (before + tail + diagnostics).replace(
            '      - name: gate 25\n', marker + packaging + '      - name: gate 25\n')
        self.assertEqual(len(steps(moved)), 36)
        self.assertTrue(any('must follow all required release gates' in x for x in audit(a, moved)))

    def test_contiguous_packaging_before_first_required_gate_rejected(self):
        a, b = fixture()
        marker = '      - name: Build installable combined test APK\n'
        tail = '      - name: Capture unresolved three-way source conflicts for manual port\n'
        before, remainder = b.split(marker, 1)
        packaging, diagnostics = remainder.split(tail, 1)
        moved = (before + tail + diagnostics).replace(
            '      - name: gate 1\n', marker + packaging + '      - name: gate 1\n')
        self.assertEqual(len(steps(moved)), 36)
        self.assertTrue(any('must follow all required release gates' in x for x in audit(a, moved)))

    def test_packaging_after_diagnostic_tail_rejected(self):
        a, b = fixture()
        marker = '      - name: Build installable combined test APK\n'
        tail = '      - name: Capture unresolved three-way source conflicts for manual port\n'
        before, remainder = b.split(marker, 1)
        packaging, diagnostics = remainder.split(tail, 1)
        moved = before + tail + diagnostics + marker + packaging
        self.assertEqual(len(steps(moved)), 36)
        self.assertTrue(any('precede diagnostic tail' in x for x in audit(a, moved)))

    def test_diagnostic_tail_identity_drift_rejected(self):
        a, b = fixture()
        self.assertTrue(any('diagnostic tail changed' in x for x in audit(
            a.replace('Capture unresolved three-way source conflicts for manual port', 'Different final gate'), b)))

    def test_commented_upload_condition_does_not_count(self):
        a, b = fixture()
        self.assertTrue(any('upload' in x for x in audit(
            a, b.replace('        if: success()\n', '        if: always()\n        # if: success()\n'))))

    def test_step_removal_rejected(self):
        a, b = fixture()
        self.assertTrue(audit(a, b.replace('      - name: gate 4\n        run: echo 4\n', '')))

    def test_preview_build_must_remain_debug(self):
        a, b = fixture()
        self.assertTrue(any('preview build' in x for x in audit(
            a, b.replace(':app:assembleDebug', ':app:assembleRelease'))))

    def test_preview_verify_must_enforce_debug_identity(self):
        a, b = fixture()
        self.assertTrue(any('preview identity' in x for x in audit(
            a, b.replace("com.arcanatable.argentum' versionCode='134'",
                         "com.arcanatable' versionCode='134'"))))

    def test_preview_verify_cannot_be_skipped(self):
        a, b = fixture()
        self.assertTrue(any('preview identity' in x for x in audit(
            a, b.replace('      - name: Verify installable APK and record provenance\n',
                         '      - name: Verify installable APK and record provenance\n'
                         '        if: false\n'))))

    def test_preview_upload_cannot_target_production_bundle(self):
        a, b = fixture()
        self.assertTrue(any('only reviewed debug APK' in x for x in audit(
            a, b.replace('path: android/app/build/outputs/apk/debug/app-debug.apk',
                         'path: android/app/build/outputs/bundle/release/app-release.aab'))))

    def test_preview_upload_cannot_add_second_path(self):
        a, b = fixture()
        self.assertTrue(any('only reviewed debug APK' in x for x in audit(
            a, b.replace('          path: android/app/build/outputs/apk/debug/app-debug.apk\n',
                         '          path: android/app/build/outputs/apk/debug/app-debug.apk\n'
                         '          path: android/app/build/outputs/bundle/release/app-release.aab\n'))))

    def test_preview_upload_cannot_continue_on_error(self):
        a, b = fixture()
        self.assertTrue(any('only reviewed debug APK' in x for x in audit(
            a, b.replace('        if: success()\n',
                         '        continue-on-error: true\n        if: success()\n'))))

    def test_preview_job_level_permission_escalation_rejected(self):
        a, b = fixture()
        widened = b.replace('  compatibility:\n',
                            '  compatibility:\n    permissions:\n      contents: write\n')
        self.assertTrue(any('job-level permissions' in x for x in audit(a, widened)))

    def test_identical_job_level_permission_escalation_rejected(self):
        a, b = fixture()
        def widen(source):
            return source.replace('  compatibility:\n',
                                  '  compatibility:\n    permissions:\n      contents: write\n')
        self.assertTrue(any('job-level permissions' in x for x in audit(widen(a), widen(b))))

    def test_duplicate_root_permissions_override_rejected(self):
        a, b = fixture()
        widened = b.replace('jobs:\n',
                            'permissions:\n  contents: write\n  actions: write\njobs:\n')
        self.assertTrue(any('duplicate or missing root permissions' in x
                            for x in audit(a, widened)))

    def test_duplicate_root_trigger_override_rejected(self):
        a, b = fixture()
        widened = b.replace('jobs:\n',
                            'on:\n  push:\n    branches: [main]\njobs:\n')
        self.assertTrue(any('duplicate or missing root workflow triggers' in x
                            for x in audit(a, widened)))

    def test_identical_unreviewed_reusable_job_rejected(self):
        a, b = fixture()
        extra = ('  unauthorized:\n'
                 '    uses: ./.github/workflows/unreviewed.yml\n'
                 '    permissions: write-all\n')
        problems = audit(a + extra, b + extra)
        self.assertTrue(any('unexpected job set' in x for x in problems), problems)

    def test_identical_renamed_compatibility_job_rejected(self):
        a, b = fixture()
        a = a.replace('  compatibility:\n', '  other:\n')
        b = b.replace('  compatibility:\n', '  other:\n')
        problems = audit(a, b)
        self.assertTrue(any('unexpected job set' in x for x in problems), problems)

    def test_duplicate_root_jobs_key_rejected(self):
        a, b = fixture()
        b += 'jobs:\n  other:\n    uses: ./.github/workflows/unreviewed.yml\n'
        problems = audit(a, b)
        self.assertTrue(any('duplicate or missing root jobs' in x for x in problems), problems)

    def test_identical_workflow_env_injection_rejected(self):
        a, b = fixture()
        def widen(source):
            return source.replace('jobs:\n', 'env:\n  GRADLE_OPTS: -DskipTests=true\njobs:\n')
        problems = audit(widen(a), widen(b))
        self.assertTrue(any('workflow-level env/defaults' in x for x in problems), problems)

    def test_identical_job_skip_condition_rejected(self):
        a, b = fixture()
        def widen(source):
            return source.replace('  compatibility:\n', '  compatibility:\n    if: false\n')
        problems = audit(widen(a), widen(b))
        self.assertTrue(any('unreviewed job-level controls' in x for x in problems), problems)

    def test_identical_job_environment_rejected(self):
        a, b = fixture()
        def widen(source):
            return source.replace('  compatibility:\n', '  compatibility:\n    environment: production\n')
        problems = audit(widen(a), widen(b))
        self.assertTrue(any('unreviewed job-level controls' in x for x in problems), problems)

    def test_identical_required_gate_skip_rejected(self):
        a, b = fixture()
        def weaken(source):
            return source.replace('      - name: gate 12\n',
                                  '      - name: gate 12\n        if: false\n')
        problems = audit(weaken(a), weaken(b))
        self.assertTrue(any('required-gate condition' in x for x in problems), problems)

    def test_identical_required_gate_continue_on_error_rejected(self):
        a, b = fixture()
        def weaken(source):
            return source.replace('      - name: gate 12\n',
                                  '      - name: gate 12\n        continue-on-error: true\n')
        problems = audit(weaken(a), weaken(b))
        self.assertTrue(any('required gate may ignore failure' in x for x in problems), problems)

    def test_reviewed_always_on_failure_annotation_allowed(self):
        a, b = fixture()
        def annotate(source):
            return source.replace(
                '      - name: gate 10\n',
                '      - name: Annotate Android unit-test failures (diagnostic, read-only)\n'
                '        if: always()\n')
        self.assertEqual(audit(annotate(a), annotate(b)), [])

    def test_identical_annotation_condition_drift_rejected(self):
        a, b = fixture()
        def weaken(source):
            return source.replace(
                '      - name: gate 10\n',
                '      - name: Annotate Android unit-test failures (diagnostic, read-only)\n'
                '        if: false\n')
        problems = audit(weaken(a), weaken(b))
        self.assertTrue(any('required-gate condition' in x for x in problems), problems)

    def test_preview_upload_mutable_tag_rejected(self):
        a, b = fixture()
        weakened = b.replace('actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02',
                             'actions/upload-artifact@v4')
        self.assertTrue(any('preview upload success/fail-closed' in x
                            for x in audit(a, weakened)))

    def test_preview_upload_unreviewed_sha_rejected(self):
        a, b = fixture()
        weakened = b.replace('actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02',
                             'actions/upload-artifact@' + '0' * 40)
        self.assertTrue(any('preview upload success/fail-closed' in x
                            for x in audit(a, weakened)))

    def test_git_blob_hash(self):
        self.assertEqual(git_blob_sha(b'hello\n'), 'ce013625030ba8dba906f756967f9e9ca394464a')

    def test_parser_ignores_shell_inline(self):
        self.assertEqual(len(steps('      - name: one\n        run: |\n          echo "- name: not a step"\n')), 1)


if __name__ == '__main__':
    unittest.main()
