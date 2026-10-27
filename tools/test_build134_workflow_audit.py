"""Pure-standard-library regressions for the frozen Build 134 workflow audit."""
import unittest
from build134_workflow_audit import audit, git_blob_sha, steps, RELEASE_SCOPE, PREVIEW_SCOPE


def fixture():
    common = [f'      - name: gate {i}\n        run: echo {i}\n' for i in range(28)]
    common += [f'      - uses: action{i}@012345\n' for i in range(5)]
    common[0] = '      - name: Verify original logo and tutorial assets inside unsigned release package\n' + RELEASE_SCOPE + '\n'
    preview_common = common.copy()
    preview_common[0] = preview_common[0].replace(RELEASE_SCOPE, PREVIEW_SCOPE)
    prefix = 'on:\n  workflow_dispatch:\npermissions:\n  contents: read\n  actions: read\njobs:\n  compatibility:\n    steps:\n'
    extras = ['      - name: Build installable combined test APK\n        run: echo build\n',
              '      - name: Verify installable APK and record provenance\n        run: echo verify\n',
              '      - name: Upload APK only when every integrated source, card, AI, and app gate passed\n        if: success()\n        uses: actions/upload-artifact@v4\n        with:\n          if-no-files-found: error\n']
    return prefix + ''.join(common), prefix + ''.join(preview_common + extras)


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

    def test_preview_permission_escalation_rejected(self):
        a, b = fixture()
        self.assertTrue(any('permissions' in x for x in audit(
            a, b.replace('  actions: read\n', '  actions: read\n  issues: write\n'))))

    def test_job_runner_drift_rejected(self):
        a, b = fixture()
        self.assertTrue(any('job configuration' in x for x in audit(
            a, b.replace('  compatibility:\n', '  compatibility:\n    runs-on: self-hosted\n'))))

    def test_packaging_steps_cannot_be_interleaved(self):
        a, b = fixture()
        step = '      - name: Verify installable APK and record provenance\n        run: echo verify\n'
        self.assertTrue(any('contiguous' in x for x in audit(
            a, b.replace(step, '').replace('      - name: gate 4\n', step + '      - name: gate 4\n'))))

    def test_contiguous_packaging_before_final_shared_gate_rejected(self):
        a, b = fixture()
        marker = '      - name: Build installable combined test APK\n'
        before, packaging = b.split(marker, 1)
        packaging = marker + packaging
        moved = before.replace('      - name: gate 27\n', packaging + '      - name: gate 27\n')
        self.assertEqual(len(steps(moved)), 36)
        self.assertTrue(any('must follow all shared release gates' in x for x in audit(a, moved)))

    def test_contiguous_packaging_before_first_shared_gate_rejected(self):
        a, b = fixture()
        marker = '      - name: Build installable combined test APK\n'
        before, packaging = b.split(marker, 1)
        packaging = marker + packaging
        moved = before.replace('      - name: gate 1\n', packaging + '      - name: gate 1\n')
        self.assertEqual(len(steps(moved)), 36)
        self.assertTrue(any('must follow all shared release gates' in x for x in audit(a, moved)))

    def test_commented_upload_condition_does_not_count(self):
        a, b = fixture()
        self.assertTrue(any('upload' in x for x in audit(
            a, b.replace('        if: success()\n', '        if: always()\n        # if: success()\n'))))

    def test_step_removal_rejected(self):
        a, b = fixture()
        self.assertTrue(audit(a, b.replace('      - name: gate 4\n        run: echo 4\n', '')))

    def test_git_blob_hash(self):
        self.assertEqual(git_blob_sha(b'hello\n'), 'ce013625030ba8dba906f756967f9e9ca394464a')

    def test_parser_ignores_shell_inline(self):
        self.assertEqual(len(steps('      - name: one\n        run: |\n          echo "- name: not a step"\n')), 1)


if __name__ == '__main__':
    unittest.main()
