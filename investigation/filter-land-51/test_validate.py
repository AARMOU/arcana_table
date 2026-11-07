import importlib.util
import io
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('validate', Path(__file__).with_name('validate.py'))
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)
RUN_LOGGED = v.run_logged


class RunnerTests(unittest.TestCase):
    def baseline(self, root, runner):
        build = root / 'validation'
        with patch.object(v, 'ROOT', root), patch.object(v, 'BUILD', build), \
             patch.object(v, 'hashes', return_value={}), \
             patch.object(v, 'run_logged', side_effect=runner), \
             patch('sys.argv', ['validate.py', '--baseline']):
            return v.main()

    def test_compile_failure_cannot_copy_stale_xml(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            reports = root / 'android/custom-cards/build/test-results/test'
            reports.mkdir(parents=True)
            (reports / 'TEST-old.xml').write_text('<testsuite failures="0"/>')
            old = root / 'validation/baseline-xml'
            old.mkdir(parents=True)
            (old / 'TEST-old.xml').write_text('historical')
            def fail(command, log):
                self.assertFalse(reports.exists())
                return 1
            self.assertEqual(self.baseline(root, fail), 1)
            runs = [p for p in (root / 'validation').iterdir() if p.name != 'baseline-xml']
            self.assertEqual(len(runs), 1)
            self.assertFalse((runs[0] / 'xml').exists())
            self.assertIn('"exit_code": 1', (runs[0] / 'manifest.json').read_text())
            self.assertEqual((old / 'TEST-old.xml').read_text(), 'historical')

    def test_success_requires_fresh_xml_and_unique_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            reports = root / 'android/custom-cards/build/test-results/test'
            def success(command, log):
                reports.mkdir(parents=True)
                (reports / 'TEST-current.xml').write_text('current')
                return 0
            self.assertEqual(self.baseline(root, success), 0)
            self.assertEqual(self.baseline(root, success), 0)
            self.assertEqual(len(list((root / 'validation').glob('*/xml/TEST-current.xml'))), 2)
            with self.assertRaisesRegex(RuntimeError, 'no current JUnit'):
                self.baseline(root, lambda *args: 0)

    def test_interrupt_keeps_lock_during_tree_cleanup(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            child = Mock()
            child.stdout.__iter__ = Mock(side_effect=KeyboardInterrupt)
            def cleanup(process):
                self.assertTrue((root / 'validation/validation.lock').exists())
                with self.assertRaises(FileExistsError):
                    self.baseline(root, lambda *args: self.fail('duplicate started'))
            def interrupt(command, log):
                with patch.object(v.subprocess, 'Popen', return_value=child), \
                     patch.object(v, 'stop_tree', side_effect=cleanup) as stop:
                    try:
                        return RUN_LOGGED(command, log)
                    finally:
                        stop.assert_called_once_with(child)
            with self.assertRaises(KeyboardInterrupt):
                self.baseline(root, interrupt)
            self.assertFalse((root / 'validation/validation.lock').exists())

    def test_uncertain_cleanup_retains_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaises(v.UnconfirmedProcessTree):
                self.baseline(root, Mock(side_effect=v.UnconfirmedProcessTree('unknown')))
            self.assertTrue((root / 'validation/validation.lock').exists())

    def test_stop_tree_waits_after_tree_kill(self):
        child = Mock(pid=12345)
        child.poll.return_value = None
        with patch.object(v.subprocess, 'run') as kill:
            v.stop_tree(child)
        if v.os.name == 'nt':
            self.assertEqual(kill.call_args.args[0], ['taskkill', '/PID', '12345', '/T', '/F'])
        child.wait.assert_called_once_with(timeout=30)

    def test_failed_tree_kill_is_uncertain(self):
        child = Mock(pid=12345)
        child.poll.return_value = None
        with patch.object(v.subprocess, 'run', side_effect=subprocess.CalledProcessError(1, 'taskkill')):
            with self.assertRaises(v.UnconfirmedProcessTree):
                v.stop_tree(child)

    @unittest.skipUnless(v.os.name == 'nt', 'Windows tree cleanup')
    def test_real_child_tree_stops_on_output_failure(self):
        import sys
        command = [sys.executable, '-u', '-c',
                   'import subprocess,sys,time; '
                   'p=subprocess.Popen([sys.executable,"-c","import time; time.sleep(45)"]); '
                   'print(p.pid,flush=True); time.sleep(45)']
        child_pid = []
        class FailingLog:
            def write(self, line):
                child_pid.append(int(line))
                raise OSError('injected output failure')
        with self.assertRaises(OSError):
            RUN_LOGGED(command, FailingLog())
        import ctypes
        handle = ctypes.windll.kernel32.OpenProcess(0x1000, False, child_pid[0])
        if handle:
            try:
                code = ctypes.c_ulong()
                self.assertTrue(ctypes.windll.kernel32.GetExitCodeProcess(handle, ctypes.byref(code)))
                self.assertNotEqual(code.value, 259)
            finally:
                ctypes.windll.kernel32.CloseHandle(handle)

    def test_output_error_cleans_child_before_propagating(self):
        child = Mock()
        child.stdout.__iter__ = Mock(return_value=iter(['line']))
        log = Mock()
        log.write.side_effect = OSError('disk full')
        with patch.object(v.subprocess, 'Popen', return_value=child), \
             patch.object(v, 'stop_tree') as stop:
            with self.assertRaises(OSError):
                v.run_logged(['unused'], log)
        stop.assert_called_once_with(child)


if __name__ == '__main__':
    unittest.main()
