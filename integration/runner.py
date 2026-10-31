#!/usr/bin/env python3
"""Card-only forward-port and destination checks. Never builds an APK or uses a write token."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

REGISTRY = 'android/custom-cards/src/main/kotlin/com/arcanatable/customcards/CustomCards.kt'
GENERATED = ['android/app/src/main/assets/argentum/custom-cards.jsonl',
             'android/app/src/main/assets/argentum/custom-cards.index.tsv',
             'docs/agents/generated/pattern-index.json', 'docs/agents/generated/pattern-index.md']
GATES = ['engine-pins', 'export', 'patterns', 'cardkit', 'scenario', 'independent',
         'custom-and-android', 'release-safety', 'scope']


class Blocked(ValueError):
    pass


def sha(value):
    return hashlib.sha256(value).hexdigest()


def call(args, root, capture=False):
    result = subprocess.run(args, cwd=root, text=True, stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.STDOUT if capture else None)
    if result.returncode:
        raise Blocked('Command failed: ' + ' '.join(args) +
                      (': ' + result.stdout[-1800:] if capture else ' (see job log/JUnit)'))
    return result.stdout if capture else ''


def validate_plan(plan):
    for field in ('sourceSha', 'sourceBase', 'baseSha', 'controlSha'):
        if not re.fullmatch('[a-f0-9]{40}', plan.get(field, '')):
            raise Blocked('Invalid exact commit pin: ' + field)
    if plan.get('target') != 'gpt/b140-card-request-test-apk-20261028':
        raise Blocked('Unapproved destination branch')
    symbol = plan.get('paths', {}).get('symbol', '')
    if not re.fullmatch('[A-Z][A-Za-z0-9]+', symbol):
        raise Blocked('Unsafe card symbol')
    paths = plan['paths']
    expected = {'symbol': symbol,
                'source': f'android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/{symbol}.kt',
                'scenario': f'android/custom-cards/src/test/kotlin/com/arcanatable/customcards/{symbol}ScenarioTest.kt',
                'registry': REGISTRY}
    if paths != expected:
        raise Blocked('Card paths do not match trusted suite')
    return [paths['source'], paths['scenario'], REGISTRY]


def registry_addition(patch, symbol):
    lines = [line for line in patch.splitlines() if line[:1] in ('+', '-') and not line.startswith(('+++', '---'))]
    pattern = r'\+\s*Entry\(' + re.escape(symbol) + r',\s*"[^"\n]+",\s*(?:"[^"\n]+"|[A-Z][A-Z0-9_]*)\),\s*'
    if len(lines) != 1 or not re.fullmatch(pattern, lines[0]):
        raise Blocked('Registry change is not exactly one new card entry')
    return lines[0][1:]



def validate_registry_deck_constant(addition, source, destination):
    """Named deck constants are safe only when both registries define the same immutable literal."""
    match = re.search(r',\s*([A-Z][A-Z0-9_]*)\),\s*$', addition)
    if not match:
        return  # Existing quoted-literal entries remain valid.
    name = match.group(1)
    declaration = re.compile(
        r'^\s*private const val ' + re.escape(name) + r'\s*=\s*"([^"\r\n]+)"\s*$',
        re.MULTILINE)
    source_values = declaration.findall(source)
    destination_values = declaration.findall(destination)
    if len(source_values) != 1 or destination_values != source_values:
        raise Blocked('Registry deck constant missing or differs between source and destination: ' + name)


def merge_registry(destination, source, addition, symbol):
    """Insert the one approved Entry without conflicting with other approved additions."""
    if re.search(r'\bEntry\(\s*' + re.escape(symbol) + r'\s*,', destination):
        raise Blocked('Card is already registered on destination')
    lines = source.splitlines(keepends=True)
    matches = [i for i, line in enumerate(lines) if line.rstrip('\r\n') == addition]
    if len(matches) != 1:
        raise Blocked('Approved registry insertion is ambiguous')
    at = matches[0]
    for line in lines[at + 1:]:
        if re.match(r'\s*Entry\(', line) and destination.count(line) == 1:
            return destination.replace(line, addition + '\n' + line, 1)
    for line in reversed(lines[:at]):
        if re.match(r'\s*Entry\(', line) and destination.count(line) == 1:
            return destination.replace(line, line + addition + '\n', 1)
    raise Blocked('Card-only forward-port conflict: no unique registry anchor remains')


def prepare(plan, candidate):
    candidate = Path(candidate).resolve()
    source_paths = validate_plan(plan)
    if call(['git', 'rev-parse', 'HEAD'], candidate, True).strip() != plan['baseSha']:
        raise Blocked('Checkout is not the planned Android base')
    if call(['git', 'status', '--porcelain'], candidate, True).strip():
        raise Blocked('Destination checkout is not clean')
    call(['git', 'fetch', '--no-tags', 'origin', plan['sourceSha'], plan['sourceBase']], candidate)
    ancestor = call(['git', 'merge-base', plan['sourceBase'], plan['sourceSha']], candidate, True).strip()
    changed = call(['git', 'diff', '--name-only', ancestor, plan['sourceSha']], candidate, True).splitlines()
    if set(changed) != set(plan['sourceFiles']) or not set(changed) <= set(source_paths + GENERATED):
        raise Blocked('Source diff changed or exceeds card-only allowlist')
    for path in source_paths:
        mode = call(['git', 'ls-tree', plan['sourceSha'], '--', path], candidate, True).split()[0]
        if mode != '100644':
            raise Blocked('Source is not a regular UTF-8 file: ' + path)
    registry_patch = call(['git', 'diff', ancestor, plan['sourceSha'], '--', REGISTRY], candidate, True)
    addition = registry_addition(registry_patch, plan['paths']['symbol'])
    registry_source = call(['git', 'show', plan['sourceSha'] + ':' + REGISTRY], candidate, True)
    registry_path = candidate / REGISTRY
    destination_registry = registry_path.read_text()
    validate_registry_deck_constant(addition, registry_source, destination_registry)
    merged_registry = merge_registry(destination_registry, registry_source, addition, plan['paths']['symbol'])
    patch = call(['git', 'diff', '--binary', ancestor, plan['sourceSha'], '--', *source_paths[:2]], candidate, True)
    result = subprocess.run(['git', 'apply', '--check', '-'], cwd=candidate, input=patch, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        raise Blocked('Card-only forward-port conflict: ' + result.stdout[-1800:])
    subprocess.run(['git', 'apply', '-'], cwd=candidate, input=patch, text=True, check=True)
    registry_path.write_text(merged_registry, encoding='utf-8')
    return {path: (candidate / path).read_text(encoding='utf-8') for path in source_paths}


def verify_junit(root, pattern, expected=None, no_skips=True):
    files = list(Path(root).glob(pattern))
    if not files:
        raise Blocked('Missing JUnit results: ' + str(root) + '/' + pattern)
    total = 0
    for path in files:
        suite = ET.parse(path).getroot()
        total += int(suite.get('tests', '0'))
        keys = ['failures', 'errors'] + (['skipped'] if no_skips else [])
        if any(int(suite.get(key, '0')) for key in keys):
            raise Blocked('JUnit did not pass: ' + path.name)
        cases = suite.findall('testcase')
        if len(cases) != int(suite.get('tests', '0')) or any(
                case.find('failure') is not None or case.find('error') is not None or
                (no_skips and case.find('skipped') is not None) for case in cases):
            raise Blocked('Invalid/incomplete JUnit case evidence: ' + path.name)
    if total < 1 or (expected is not None and total != expected):
        raise Blocked(f'Incomplete JUnit execution: expected={expected}, actual={total}')
    return total


def data_delta(before, after, name):
    old = [json.loads(line) for line in before.splitlines() if line.strip()]
    new = [json.loads(line) for line in after.splitlines() if line.strip()]
    if len({c['name'].casefold() for c in new}) != len(new):
        raise Blocked('Duplicate exported card names')
    if [c for c in new if c['name'] != name] != old or sum(c['name'] == name for c in new) != 1:
        raise Blocked('Export changes cards other than the approved addition')


def reject_upstream_collision(index, name):
    for row in index.splitlines():
        fields = row.split('\t')
        if len(fields) >= 2 and fields[1] in ('card', 'basic') and fields[0].casefold() == name.casefold():
            raise Blocked('Destination upstream already defines this card; automatic override is forbidden')


def failures(candidate):
    out = []
    for root in ('android/custom-cards/build/test-results', 'android/app/build/test-results'):
        for path in (candidate / root).rglob('TEST-*.xml'):
            for case in ET.parse(path).getroot().iter('testcase'):
                failure = case.find('failure')
                if failure is None:
                    failure = case.find('error')
                if failure is not None:
                    out.append(case.get('classname', '') + '.' + case.get('name', '') + ': ' +
                               (failure.get('message') or failure.text or '')[:500])
    return out[:40]


def failure_evidence(candidate):
    """Complete structured JUnit evidence; malformed/missing results never permit retry."""
    cases = []
    try:
        for module in ('custom-cards', 'app'):
            pattern = ('test' if module == 'custom-cards' else 'testDebugUnitTest')
            files = list((candidate / 'android' / module / 'build/test-results' / pattern).glob('TEST-*.xml'))
            if not files:
                return {'complete': False, 'cases': []}
            for path in files:
                suite = ET.parse(path).getroot()
                tests = suite.findall('testcase')
                failed = [f for c in tests for f in list(c) if f.tag in ('failure', 'error')]
                if (len(tests) != int(suite.get('tests', '-1')) or
                        len(failed) != int(suite.get('failures', '0')) + int(suite.get('errors', '0'))):
                    return {'complete': False, 'cases': []}
                for case in tests:
                    for failure in list(case):
                        if failure.tag in ('failure', 'error'):
                            cases.append({'className': case.get('classname'), 'name': case.get('name'),
                                          'type': failure.get('type'), 'message': failure.get('message')})
        return {'complete': True, 'cases': cases}
    except (ET.ParseError, ValueError, OSError):
        return {'complete': False, 'cases': []}


def validate(plan, trusted, candidate, output):
    trusted, candidate, output = Path(trusted).resolve(), Path(candidate).resolve(), Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    report = {'schema': 1, 'planHash': sha(json.dumps(plan, separators=(',', ':'), ensure_ascii=False).encode()),
              'status': 'blocked', 'gates': [], 'hashes': {}}
    files = []

    def gate(name, function):
        try:
            value = function()
            report['gates'].append({'name': name, 'ok': True, 'detail': str(value or 'passed')})
            print('PASS:', name, flush=True)
            return True
        except Blocked as error:
            report['gates'].append({'name': name, 'ok': False, 'detail': str(error)[:1800]})
            print('BLOCKED:', name, str(error), flush=True)
            return False

    try:
        prepare(plan, candidate)
        policy = json.loads((trusted / 'integration/policy.json').read_text())
        def engine():
            for name, expected in policy['engineJars'].items():
                if sha((candidate / 'android/app/libs' / name).read_bytes()) != expected:
                    raise Blocked('Destination engine differs from authorized pin: ' + name)
            reject_upstream_collision((candidate / 'android/app/src/main/assets/argentum/all-cards.index.tsv').read_text(), plan['suite']['name'])
        if not gate('engine-pins', engine):
            raise Blocked('Engine upgrade requires a reviewed integration-policy update')
        old_data = call(['git', 'show', plan['baseSha'] + ':' + GENERATED[0]], candidate, True)
        android = candidate / 'android'
        gradle = ['./gradlew', '--no-daemon', '--max-workers=2', '--console=plain']
        if not gate('export', lambda: call(gradle + [':custom-cards:exportCards'], android)):
            raise Blocked('Card is not export-compatible with the destination SDK')
        gate('patterns', lambda: call(['tools/cardkit/cardkit', 'patterns'], candidate))
        def cardkit():
            call(['python3', '-m', 'unittest', 'discover', '-s', 'tools/cardkit/tests'], candidate)
            call(['tools/cardkit/cardkit', 'check'], candidate)
            call(['tools/cardkit/cardkit', 'docs-check'], candidate)
            call(['python3', 'tools/check_android_regex.py'], candidate)
            call(['python3', '-m', 'unittest', 'discover', '-s', 'tools/tests', '-p', 'test_android_regex.py'], candidate)
        gate('cardkit', cardkit)
        results = candidate / 'android/custom-cards/build/test-results/test'
        scenario = 'com.arcanatable.customcards.' + plan['paths']['symbol'] + 'ScenarioTest'
        def scenario_test():
            call(gradle + [':custom-cards:test', '--tests', scenario], android)
            return str(verify_junit(results, 'TEST-' + scenario + '.xml')) + ' scenario tests'
        gate('scenario', scenario_test)
        suite = plan['suite']
        runner = str(trusted / 'certification/runner.py')
        runner_args = ['--manifest', str(trusted / 'certification/suites.json'), '--key', suite['key'],
                       '--candidate', str(candidate)]
        injected = candidate / 'android/custom-cards/src/test/kotlin/com/arcanatable/customcards' / Path(suite['source']).name
        call(['python3', runner, 'prepare', *runner_args, '--trusted', str(trusted),
              '--github-output', str(output / 'suite-output.txt')], trusted)
        def independent():
            call(gradle + [':custom-cards:test', '--tests', suite['testClass']], android)
            call(['python3', runner, 'verify', *runner_args], trusted)
        gate('independent', independent)
        def complete():
            call(gradle + ['--continue', ':custom-cards:test', ':app:testDebugUnitTest'], android)
            verify_junit(results, 'TEST-*.xml', no_skips=False)
            verify_junit(candidate / 'android/app/build/test-results/testDebugUnitTest', 'TEST-*.xml', no_skips=False)
        gate('custom-and-android', complete)
        def release():
            call(gradle + [':app:testReleaseUnitTest', '--tests', 'com.arcanatable.ReleaseHasNoTestScenariosTest',
                          '--tests', 'com.arcanatable.LauncherBrandingTest'], android)
            root = candidate / 'android/app/build/test-results/testReleaseUnitTest'
            for cls in ('ReleaseHasNoTestScenariosTest', 'LauncherBrandingTest'):
                verify_junit(root, 'TEST-com.arcanatable.' + cls + '.xml')
        gate('release-safety', release)
        injected.unlink()
        def scope():
            allowed = validate_plan(plan) + GENERATED
            # New source files are untracked until this explicit, allowlisted add.
            call(['git', 'add', '--', *allowed], candidate)
            actual = call(['git', 'diff', '--cached', '--name-only'], candidate, True).splitlines()
            if set(actual) != set(allowed):
                raise Blocked('Destination changed-file scope differs from the seven allowed files')
            if call(['git', 'diff', '--name-only'], candidate, True).strip():
                raise Blocked('Unexpected tracked changes outside integration bundle')
            data_delta(old_data, (candidate / GENERATED[0]).read_text(), suite['name'])
            names = [json.loads(line)['name'] for line in (candidate / GENERATED[0]).read_text().splitlines() if line.strip()]
            indexed = [line.split('\t')[0] for line in (candidate / GENERATED[1]).read_text().splitlines() if line.strip()]
            if names != indexed:
                raise Blocked('Exported card data/index mismatch')
            call(['git', 'diff', '--cached', '--check'], candidate)
            for path in allowed:
                file = candidate / path
                if file.is_symlink():
                    raise Blocked('Integration file is a symlink')
                content = file.read_text(encoding='utf-8')
                files.append({'path': path, 'mode': '100644', 'type': 'blob', 'content': content})
                report['hashes'][path] = sha(content.encode())
        gate('scope', scope)
    except Blocked as error:
        report['reason'] = str(error)
    finally:
        present = {g['name'] for g in report['gates']}
        report['gates'] += [{'name': name, 'ok': False, 'detail': 'Not run after earlier blocker'} for name in GATES if name not in present]
        report['failures'] = failures(candidate)
        report['failureEvidence'] = failure_evidence(candidate)
        if all(g['ok'] for g in report['gates']):
            report['status'] = 'passed'
        (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        (output / 'files.json').write_text(json.dumps(files, ensure_ascii=False) + '\n')
        for module in ('custom-cards', 'app'):
            root = candidate / 'android' / module / 'build/test-results'
            for xml in root.rglob('TEST-*.xml'):
                dest = output / 'junit' / module / xml.relative_to(root)
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(xml, dest)
        print('INTEGRATION RESULT:', report['status'], flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['prepare', 'validate'])
    parser.add_argument('--plan', required=True)
    parser.add_argument('--candidate', required=True)
    parser.add_argument('--trusted', default='trusted')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    plan = json.loads(Path(args.plan).read_text())
    if args.command == 'prepare':
        result = prepare(plan, args.candidate)
        Path(args.output).write_text(json.dumps(result, ensure_ascii=False))
    else:
        validate(plan, args.trusted, args.candidate, args.output)


if __name__ == '__main__':
    main()
