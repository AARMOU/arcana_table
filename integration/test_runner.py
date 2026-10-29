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
        self.write(self.registry, 'object Registry {\n    val all = listOf(\n        Entry(Existing, "SET", "Deck"),\n    )\n}\n')
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
                     'controlSha': 'c' * 40, 'target': 'gpt/b140-card-request-test-apk-20261028',
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


if __name__ == '__main__':
    unittest.main()
