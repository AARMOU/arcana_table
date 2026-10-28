#!/usr/bin/env python3
"""Read-only, fail-closed parity check for frozen Build 134 workflows.

Usage: python3 tools/build134_workflow_audit.py RELEASE_YML PREVIEW_YML
No network access, builds, signing, deployment or workflow dispatch.
"""
import argparse
import hashlib
import re
from pathlib import Path

RELEASE_BLOB = '2acf0538f14a7e2ec51f20a827e564cdff46cbb7'
PREVIEW_BLOB = '1aba82df5dfc07f88f9bfa54f9d9fff485494de6'
PREVIEW_ONLY = (
    'Build installable combined test APK',
    'Verify installable APK and record provenance',
    'Upload APK only when every integrated source, card, AI, and app gate passed',
)
RELEASE_SCOPE = "          echo 'Validation only: unsigned APK/AAB are not uploaded, signed or distributed.' > combined-evidence/validation-scope.txt"
PREVIEW_SCOPE = "          echo 'Validation only: Unsigned production APK/AAB are validation-only; only the approved debug preview is uploaded.' > combined-evidence/validation-scope.txt"


def git_blob_sha(content: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()


def steps(source: str) -> list[tuple[str, str]]:
    """Extract whole six-space-indented YAML step blocks without executing YAML."""
    found = []
    current = []
    for line in source.splitlines(keepends=True):
        if re.match(r'^      - (?:name|uses): ', line):
            if current:
                found.append(_step(current))
            current = [line]
        elif current:
            current.append(line)
    if current:
        found.append(_step(current))
    return found


def _step(lines: list[str]) -> tuple[str, str]:
    header = lines[0].strip()
    return (header.removeprefix('- name: ') if header.startswith('- name: ') else header,
            ''.join(lines))


def audit(release: str, preview: str) -> list[str]:
    problems = []
    release_steps, preview_steps = steps(release), steps(preview)
    if len(release_steps) != 33 or len(preview_steps) != 36:
        problems.append(f'step count changed: release={len(release_steps)}, preview={len(preview_steps)} (expected 33/36)')
    release_named = sum(not name.startswith('- uses:') for name, _ in release_steps)
    if release_named != 28:
        problems.append(f'release named-step count changed: {release_named} (expected 28)')
    extras = [(name, body) for name, body in preview_steps if name in PREVIEW_ONLY]
    if [name for name, _ in extras] != list(PREVIEW_ONLY):
        problems.append('preview-only build/identity/upload step set or order changed')
    # The debug build, identity check and upload form one uninterrupted gate.
    positions = [i for i, (name, _) in enumerate(preview_steps) if name in PREVIEW_ONLY]
    if len(positions) == 3 and positions != list(range(positions[0], positions[0] + 3)):
        problems.append('preview-only packaging steps are no longer contiguous')
    # The final two shared steps collect diagnostics after a failure; they
    # intentionally follow the debug build, identity check and upload. The
    # preview artifact must follow every *required* release validation gate.
    diagnostic_tail = (
        'Capture unresolved three-way source conflicts for manual port',
        'Upload conflict and patch reports even on failure',
    )
    if tuple(name for name, _ in release_steps[-2:]) != diagnostic_tail:
        problems.append('release diagnostic tail changed; review packaging boundary')
    required_gate_count = len(release_steps) - len(diagnostic_tail)
    if len(positions) == 3 and positions != list(range(required_gate_count, required_gate_count + 3)):
        problems.append('preview-only packaging must follow all required release gates and precede diagnostic tail')
    common = [(name, body) for name, body in preview_steps if name not in PREVIEW_ONLY]
    if len(common) != len(release_steps):
        problems.append('common step count changed')
    for index, ((left_name, left_body), (right_name, right_body)) in enumerate(zip(release_steps, common)):
        if left_name != right_name:
            problems.append(f'step {index} identity changed: {left_name!r} vs {right_name!r}')
        if left_name == 'Verify original logo and tutorial assets inside unsigned release package':
            if left_body.count(RELEASE_SCOPE) != 1 or right_body.count(PREVIEW_SCOPE) != 1:
                problems.append('validation-scope evidence message changed unexpectedly')
            left_body = left_body.replace(RELEASE_SCOPE, '<reviewed descriptive message>')
            right_body = right_body.replace(PREVIEW_SCOPE, '<reviewed descriptive message>')
        if left_body != right_body:
            problems.append(f'shared step {index} differs: {left_name}')
    # Both jobs must use the same runner, timeout and job-level configuration.
    def job_config(source: str) -> str | None:
        if '\njobs:\n' not in source or '    steps:\n' not in source:
            return None
        return source.split('\njobs:\n', 1)[1].split('    steps:\n', 1)[0]

    if job_config(release) is None or job_config(release) != job_config(preview):
        problems.append('release/preview job configuration differs')
    # Cross-workflow equality cannot catch identical runner/container/matrix
    # changes. Only the reviewed job-level execution settings are allowed.
    reviewed_job = ('  compatibility:\n'
                    '    runs-on: ubuntu-24.04\n'
                    '    timeout-minutes: 120\n')
    for name, source in [('release', release), ('preview', preview)]:
        if job_config(source) != reviewed_job:
            problems.append(f'{name}: compatibility runner/timeout/job settings differ from reviewed baseline')
    for name, source in [('release', release), ('preview', preview)]:
        # An extra reusable-workflow job with no steps can execute outside
        # the reviewed compatibility job. Identical additions to both files
        # would otherwise pass step parity and job-config comparison.
        job_roots = re.findall(r'(?m)^jobs[ \t]*:', source)
        if len(job_roots) != 1:
            problems.append(f'{name}: duplicate or missing root jobs declaration')
        else:
            job_body = source.split('\njobs:', 1)[1]
            job_names = re.findall(r'(?m)^  ([A-Za-z_][A-Za-z0-9_-]*):[ \t]*(?:#.*)?$', job_body)
            if job_names != ['compatibility']:
                problems.append(f'{name}: unexpected job set {job_names!r}; only compatibility is reviewed')
        header = source.split('\njobs:', 1)[0] + '\n'
        permissions = re.search(r'(?m)^permissions:\n((?:  [a-z-]+: [^\n]+\n)+)', header)
        if not permissions or permissions.group(1) != '  contents: read\n  actions: read\n':
            problems.append(f'{name}: expected read-only permissions missing or extra grants present')
        # GitHub YAML accepts job-level permissions that override the read-only
        # workflow defaults. Identical escalation in both jobs would otherwise
        # pass the cross-workflow job-configuration parity comparison.
        if re.search(r'(?m)^    permissions\s*:', job_config(source) or ''):
            problems.append(f'{name}: job-level permissions override is not reviewed')
        # Matching changes in both workflows can still introduce unreviewed
        # execution controls. Job-level conditions can silently skip all
        # validation, while environment/defaults can alter command behavior.
        job_controls = re.findall(
            r'(?m)^    (env|defaults|if|continue-on-error|environment)\s*:',
            job_config(source) or '',
        )
        if job_controls:
            problems.append(f'{name}: unreviewed job-level controls {job_controls!r}')
        if re.search(r'(?m)^(?:env|defaults)\s*:', header):
            problems.append(f'{name}: unreviewed workflow-level env/defaults')
        # Reject duplicate root declarations: the first match can look safe
        # while GitHub/YAML interprets a later declaration differently.
        if len(re.findall(r'(?m)^permissions\s*:', header)) != 1:
            problems.append(f'{name}: duplicate or missing root permissions declaration')
        if len(re.findall(r'(?m)^on\s*:', header)) != 1:
            problems.append(f'{name}: duplicate or missing root workflow triggers')
        # Trigger allowlist is asymmetric by design: release may push only
        # changes to its own workflow on its frozen validation branch;
        # the debug APK preview must remain manual-only. Hash pins alone
        # cannot express this security property in synthetic regressions.
        trigger_block = re.search(r'(?ms)^on:\n(.*?)(?=^[a-z][a-z_-]*:|\Z)', header)
        expected_triggers = (
            "  push:\n"
            "    branches: [gpt/release-upgraded-validation-20261025]\n"
            "    paths: ['.github/workflows/release-upgraded-source-validation.yml']\n"
            "  workflow_dispatch:\n"
            if name == 'release' else "  workflow_dispatch:\n"
        )
        if not trigger_block or trigger_block.group(1) != expected_triggers:
            problems.append('preview is no longer manual-only' if name == 'preview' else
                            'release: workflow triggers changed; review before activation')
    # Shared validation steps must remain hard gates. Comparing the two
    # workflows is insufficient: an identical 'if: false' or
    # 'continue-on-error: true' in both could silently waive a test.
    # The Android failure annotation is deliberately always-on for diagnosis,
    # but it does not replace any mandatory validation step.
    annotation = 'Annotate Android unit-test failures (diagnostic, read-only)'
    for label, shared in [('release', release_steps), ('preview', common)]:
        for step_name, step_body in shared[:required_gate_count]:
            conditions = re.findall(r'(?m)^        if: ([^\n]+)$', step_body)
            expected = ['always()'] if step_name == annotation else []
            if conditions != expected:
                problems.append(f'{label}: unreviewed required-gate condition: {step_name}')
            if re.search(r'(?m)^        continue-on-error\s*:', step_body):
                problems.append(f'{label}: required gate may ignore failure: {step_name}')
    if len(extras) == 3:
        build, verify, upload = (body for _, body in extras)
        # Preview-only steps have no release counterpart. Check their
        # security-critical behavior as well as the complete file blob pins.
        if (not re.search(r'(?m)^          \./gradlew [^\n]*:app:assembleDebug\b', build)
                or re.search(r'(?m)^        (?:if|continue-on-error):', build)):
            problems.append('preview build must assemble debug APK without skip/continue-on-error')
        required_verify = (
            r'^          test -s android/app/build/outputs/apk/debug/app-debug\.apk$',
            r"^          grep -F \"package: name='com\.arcanatable\.argentum' versionCode='134'",
            r'^          .*apksigner" verify --print-certs android/app/build/outputs/apk/debug/app-debug\.apk',
            r'^          sha256sum android/app/build/outputs/apk/debug/app-debug\.apk > combined-evidence/apk\.sha256$',
        )
        if (any(not re.search(pattern, verify, re.M) for pattern in required_verify)
                or re.search(r'(?m)^        (?:if|continue-on-error):', verify)):
            problems.append('preview identity/signature/hash verification weakened or skippable')
        if (re.findall(r'(?m)^          path: [^\n]+$', upload) !=
                ['          path: android/app/build/outputs/apk/debug/app-debug.apk']
                or re.findall(r'(?m)^          name: [^\n]+$', upload) !=
                ['          name: arcana-combined-argentum-eldrazi94-build134-logo-polish-debug-apk']
                or re.search(r'(?m)^        continue-on-error:', upload)):
            problems.append('preview upload must contain only reviewed debug APK artifact')
        upload = extras[2][1]
        if (re.findall(r'^        if: [^\n]+$', upload, re.M) != ['        if: success()']
                or not re.search(r'^          if-no-files-found: error$', upload, re.M)
                or not re.search(r'^        uses: actions/upload-artifact@v4$', upload, re.M)):
            problems.append('preview upload success/fail-closed conditions changed')
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('release', type=Path)
    parser.add_argument('preview', type=Path)
    args = parser.parse_args()
    raw_release, raw_preview = args.release.read_bytes(), args.preview.read_bytes()
    problems = audit(raw_release.decode('utf-8'), raw_preview.decode('utf-8'))
    for label, actual, expected in [('release', git_blob_sha(raw_release), RELEASE_BLOB),
                                    ('preview', git_blob_sha(raw_preview), PREVIEW_BLOB)]:
        if actual != expected:
            problems.append(f'{label} blob changed: {actual} != reviewed {expected}; re-audit before accepting')
    if problems:
        for problem in problems:
            print('FAIL:', problem)
        return 1
    print('PASS: frozen 33 shared steps (28 named, 5 unnamed); 3 preview-only steps; read-only permissions; manual preview')
    print('NOTICE: preview upload-artifact@v4 remains tag-referenced; pin/review before authorized activation')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
