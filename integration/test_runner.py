import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('integration_runner', Path(__file__).with_name('runner.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True, stderr=subprocess.DEVNULL).strip()


class ForwardPortTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.origin = self.root / 'origin'
        self.origin.mkdir()
        git(self.origin, 'init', '-b', 'source')
        git(self.origin, 'config', 'user.name', 'Test')
        git(self.origin, 'config', 'user.email', 'test@example.invalid')
        self.source = 'android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Example.kt'
        self.scenario = 'android/custom-cards/src/test/kotlin/com/arcanatable/customcards/ExampleScenarioTest.kt'
        self.registry = runner.REGISTRY
        self.write(self.registry, 'object Registry {\n    private const val POOL = "Card pool 10/26"\n    val all = listOf(\n        Entry(Existing, "SET", "Deck"),\n    )\n}\n')
        self.write('ui.txt', 'original UI\n')
        self.commit()
        self.ancestor = git(self.origin, 'rev-parse', 'HEAD')
        git(self.origin, 'checkout', '-b', 'android')
        self.write('ui.txt', 'new Android UI preserved\n')
        self.commit()
        self.base = git(self.origin, 'rev-parse', 'HEAD')
        git(self.origin, 'checkout', 'source')
        self.write(self.source, 'val Example = "approved definition"\n')
        self.write(self.scenario, 'class ExampleScenarioTest\n')
        p = self.origin / self.registry
        p.write_text(p.read_text().replace('        Entry(Existing', '        Entry(Example, "SET", "Deck"),\n        Entry(Existing'))
        self.commit()
        self.head = git(self.origin, 'rev-parse', 'HEAD')
        self.candidate = self.root / 'candidate'
        subprocess.run(['git', 'clone', '-q', '-b', 'android', str(self.origin), str(self.candidate)], check=True)
        self.plan = {'sourceSha': self.head, 'sourceBase': self.ancestor, 'baseSha': self.base,
                     'controlSha': 'c' * 40, 'target': 'gpt/b141-candidate-20261101',
                     'paths': {'symbol': 'Example', 'source': self.source, 'scenario': self.scenario, 'registry': self.registry},
                     'sourceFiles': [self.source, self.scenario, self.registry]}

    def write(self, path, content):
        p = self.origin / path
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content)

    def commit(self):
        git(self.origin, 'add', '.')
        git(self.origin, 'commit', '-qm', 'fixture')

    def test_forward_port_preserves_destination_ui_and_uses_exact_approved_sources(self):
        result = runner.prepare(self.plan, self.candidate)
        self.assertEqual((self.candidate / 'ui.txt').read_text(), 'new Android UI preserved\n')
        self.assertEqual(result[self.source], 'val Example = "approved definition"\n')
        self.assertIn('Entry(Example', result[self.registry])

    def test_dirty_destination_cannot_be_overwritten(self):
        (self.candidate / 'ui.txt').write_text('uncommitted')
        with self.assertRaisesRegex(runner.Blocked, 'not clean'):
            runner.prepare(self.plan, self.candidate)

    def test_registry_accepts_other_integrated_cards_without_overwriting_them(self):
        old = 'object Registry {\n    val all = listOf(\n        Entry(Existing, "SET", "Deck"),\n    )\n}\n'
        source = old.replace('        Entry(Existing', '        Entry(Example, "SET", "Deck"),\n        Entry(Existing')
        destination = old.replace('        Entry(Existing', '        Entry(PreviouslyApproved, "SET", "Deck"),\n        Entry(Existing')
        merged = runner.merge_registry(destination, source, '        Entry(Example, "SET", "Deck"),', 'Example')
        self.assertIn('Entry(PreviouslyApproved', merged)
        self.assertIn('Entry(Example', merged)
        self.assertEqual(merged.replace('        Entry(Example, "SET", "Deck"),\n', ''), destination)

    def test_wrong_base_and_unsafe_paths_rejected(self):
        for patch in ({'baseSha': self.head}, {'target': 'main'}, {'paths': {**self.plan['paths'], 'source': '../outside'}}):
            with self.subTest(patch=patch), self.assertRaises(runner.Blocked):
                runner.prepare({**self.plan, **patch}, self.candidate)

    def test_undeclared_extra_source_file_rejected(self):
        self.write('engine.jar', 'unexpected')
        self.commit()
        self.plan['sourceSha'] = git(self.origin, 'rev-parse', 'HEAD')
        with self.assertRaisesRegex(runner.Blocked, 'allowlist'):
            runner.prepare(self.plan, self.candidate)

    def test_registry_rewrite_or_second_card_entry_rejected(self):
        for patch in ('+        Entry(Other, "SET", "Deck"),\n', '-        Entry(Existing, "SET", "Deck"),\n',
                      '+        Entry(Example, "SET", "Deck"),\n+        danger()\n'):
            with self.subTest(patch=patch), self.assertRaises(runner.Blocked):
                runner.registry_addition(patch, 'Example')

    def test_named_deck_constant_forward_port_preserves_other_destination_changes(self):
        registry = self.origin / self.registry
        registry.write_text(registry.read_text().replace(
            'Entry(Example, "SET", "Deck")', 'Entry(Example, "SET", POOL)'))
        self.commit()
        self.plan['sourceSha'] = git(self.origin, 'rev-parse', 'HEAD')
        result = runner.prepare(self.plan, self.candidate)
        self.assertIn('Entry(Example, "SET", POOL)', result[self.registry])
        self.assertIn('private const val POOL = "Card pool 10/26"', result[self.registry])
        self.assertEqual((self.candidate / 'ui.txt').read_text(), 'new Android UI preserved\n')

    def test_named_deck_constant_requires_identical_immutable_literal(self):
        addition = '        Entry(Example, "SET", POOL),'
        patch = '+' + addition + '\n'
        self.assertEqual(runner.registry_addition(patch, 'Example'), addition)
        good = 'private const val POOL = "Card pool 10/26"\n'
        runner.validate_registry_deck_constant(addition, good, good)
        for source, destination in (
            (good, ''),
            (good, 'private const val POOL = "Different"\n'),
            ('val POOL = "Card pool 10/26"\n', good),
            (good + good, good),
            (good, good + good),
        ):
            with self.subTest(source=source, destination=destination), self.assertRaisesRegex(
                    runner.Blocked, 'deck constant'):
                runner.validate_registry_deck_constant(addition, source, destination)
        for bad in (
            '+        Entry(Example, "SET", POOL + "extra"),\n',
            '+        Entry(Example, "SET", pool),\n',
            '+        Entry(Example, "SET", getDeck()),\n',
            '+        Entry(Example, "SET", POOL),\n+        Entry(Other, "SET", POOL),\n',
        ):
            with self.subTest(patch=bad), self.assertRaises(runner.Blocked):
                runner.registry_addition(bad, 'Example')

    def test_named_deck_constant_drift_blocks_before_any_source_is_applied(self):
        registry = self.origin / self.registry
        registry.write_text(registry.read_text().replace(
            'Entry(Example, "SET", "Deck")', 'Entry(Example, "SET", POOL)'))
        self.commit()
        self.plan['sourceSha'] = git(self.origin, 'rev-parse', 'HEAD')
        dest = self.candidate / self.registry
        dest.write_text(dest.read_text().replace(
            'private const val POOL = "Card pool 10/26"',
            'private const val POOL = "Different destination"'))
        git(self.candidate, 'config', 'user.name', 'Test')
        git(self.candidate, 'config', 'user.email', 'test@example.invalid')
        git(self.candidate, 'add', '.')
        git(self.candidate, 'commit', '-qm', 'destination constant drift')
        self.plan['baseSha'] = git(self.candidate, 'rev-parse', 'HEAD')
        with self.assertRaisesRegex(runner.Blocked, 'deck constant'):
            runner.prepare(self.plan, self.candidate)
        self.assertFalse((self.candidate / self.source).exists())

    def test_conflict_does_not_modify_destination(self):
        p = self.candidate / self.registry
        p.write_text('incompatible registry\n')
        git(self.candidate, 'config', 'user.name', 'Test')
        git(self.candidate, 'config', 'user.email', 'test@example.invalid')
        git(self.candidate, 'add', '.')
        git(self.candidate, 'commit', '-qm', 'destination change')
        self.plan['baseSha'] = git(self.candidate, 'rev-parse', 'HEAD')
        with self.assertRaisesRegex(runner.Blocked, 'conflict'):
            runner.prepare(self.plan, self.candidate)
        self.assertEqual(p.read_text(), 'incompatible registry\n')
        self.assertFalse((self.candidate / self.source).exists())


class ExportAndJUnitTests(unittest.TestCase):
    def test_new_custom_card_cannot_silently_be_shadowed_by_upstream(self):
        runner.reject_upstream_collision('Existing\tcard\nExample\ttoken\n', 'Example')
        with self.assertRaisesRegex(runner.Blocked, 'already defines'):
            runner.reject_upstream_collision('Existing\tcard\neXample\tcard\n', 'Example')

    def test_only_the_approved_card_may_be_added_to_export(self):
        old = json.dumps({'name': 'Existing', 'script': {'life': 1}})
        new = old + '\n' + json.dumps({'name': 'Example'})
        runner.data_delta(old, new, 'Example')
        for after in (new.replace('"life": 1', '"life": 2'), new + '\n' + json.dumps({'name': 'Other'}),
                      new + '\n' + json.dumps({'name': 'Example'}), old):
            with self.assertRaises(runner.Blocked):
                runner.data_delta(old, after, 'Example')

    def test_missing_zero_skipped_or_incomplete_independent_results_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaises(runner.Blocked):
                runner.verify_junit(root, 'TEST-*.xml')
            p = root / 'TEST-X.xml'
            p.write_text('<testsuite tests="1"><testcase name="a"/></testsuite>')
            self.assertEqual(runner.verify_junit(root, 'TEST-*.xml', expected=1), 1)
            for content in ('<testsuite tests="0"/>', '<testsuite tests="2"><testcase/></testsuite>',
                            '<testsuite tests="1" skipped="1"><testcase><skipped/></testcase></testsuite>',
                            '<testsuite tests="1"><testcase><failure/></testcase></testsuite>'):
                p.write_text(content)
                with self.assertRaises(runner.Blocked):
                    runner.verify_junit(root, 'TEST-*.xml', expected=1)

class RetryEvidenceTests(unittest.TestCase):
    def test_complete_single_failure_is_structured_and_preserves_exact_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            custom = root / 'android/custom-cards/build/test-results/test/TEST-Card.xml'
            android = root / 'android/app/build/test-results/testDebugUnitTest/TEST-LAN.xml'
            custom.parent.mkdir(parents=True)
            android.parent.mkdir(parents=True)
            custom.write_text('<testsuite tests="1"><testcase name="card"/></testsuite>')
            android.write_text('<testsuite tests="1" failures="1"><testcase classname="LAN" name="bind">'
                               '<failure type="java.lang.AssertionError" message="bind error"/>'
                               '</testcase></testsuite>')
            self.assertEqual(runner.failure_evidence(root), {'complete': True, 'cases': [
                {'className': 'LAN', 'name': 'bind', 'type': 'java.lang.AssertionError', 'message': 'bind error'}]})
            # Every failed case is retained: a second failure cannot hide behind truncation.
            android.write_text('<testsuite tests="2" failures="2">' +
                               '<testcase><failure/></testcase>' * 2 + '</testsuite>')
            self.assertEqual(len(runner.failure_evidence(root)['cases']), 2)
            for bad in ('<testsuite tests="1" failures="2"><testcase><failure/></testcase></testsuite>',
                        '<testsuite tests="2"><testcase/></testsuite>', '<broken>'):
                android.write_text(bad)
                self.assertFalse(runner.failure_evidence(root)['complete'])
            android.unlink()
            self.assertFalse(runner.failure_evidence(root)['complete'])


if __name__ == '__main__':
    unittest.main()
