"""Regression tests for the public bundle's guarded, restartable LF repair."""

import hashlib
import importlib.util
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch
import zipfile


SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/assets/deployment/repair/v1/repair.py"
try:
    import fcntl
except ImportError:
    fcntl = types.SimpleNamespace(LOCK_EX=2, LOCK_NB=4, flock=lambda *args: None)

with patch.dict(sys.modules, {"fcntl": fcntl}):
    spec = importlib.util.spec_from_file_location("deployment_lf_repair", SOURCE)
    repair = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(repair)
PUBLIC_PREFLIGHT_SHA256 = set(repair.LEGACY_PREFLIGHT_SHA256)


class LfRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.run = root / ("a" * 32)
        package = self.run / "package"
        package.mkdir(parents=True)
        self.original = {name: b"# deployment asset\r\n" for name in repair.FILES}
        self.fixed = {name: data.replace(b"\r\n", b"\n") for name, data in self.original.items()}
        for name, data in self.original.items():
            (package / name).write_bytes(data)
        self.old_zip = self.run / "bundle.zip"
        self.write_zip(self.old_zip, self.original)
        self.fixed_zip = root / "fixed.zip"
        self.write_zip(self.fixed_zip, self.fixed)
        self.expected = repair.digest(self.fixed_zip)
        known = patch.object(repair, "LEGACY_PREFLIGHT_SHA256",
                             {hashlib.sha256(self.original["preflight.py"]).hexdigest()})
        known.start()
        self.addCleanup(known.stop)
        plan = b"ownbridge-plan-v2\nipv4 203.0.113.1 ss 28443\n"
        (self.run / "plan.txt").write_bytes(plan)
        (self.run / "plan.sha256").write_bytes(
            (hashlib.sha256(plan).hexdigest() + "  plan.txt\n").encode("ascii"))

    @staticmethod
    def write_zip(path, files):
        with zipfile.ZipFile(path, "w") as archive:
            for name, data in sorted(files.items()):
                archive.writestr(name, data)

    def recover(self):
        return repair.recover(self.run, self.fixed_zip, self.expected)

    def test_unstarted_task_is_repaired_and_originals_are_preserved(self):
        old_bytes = self.old_zip.read_bytes()
        self.assertIn("Restored", self.recover())
        self.assertEqual(self.expected, repair.digest(self.old_zip))
        self.assertTrue(repair.matches(self.run / "package", self.fixed))
        self.assertEqual(old_bytes, (self.run / "bundle.before.zip").read_bytes())
        self.assertTrue(repair.matches(self.run / "package.before", self.original))
        (self.run / "ss.activate.done").touch()
        self.assertIn("already restored", self.recover())

    def test_real_public_asset_contents_are_recognized_independent_of_zip_compression(self):
        assets = SOURCE.parents[2] / "v2"
        self.fixed = {name: (assets / name).read_bytes() for name in repair.FILES}
        baseline = dict(self.fixed)
        baseline["preflight.py"] = baseline["preflight.py"].replace(repair.FIXED_REQUIRED, repair.LEGACY_REQUIRED, 1)
        baseline["activate.sh"] = baseline["activate.sh"].replace(repair.FIXED_UFW, repair.LEGACY_UFW, 1)
        self.original = {name: data.replace(b"\n", b"\r\n") for name, data in baseline.items()}
        self.assertIn(hashlib.sha256(self.original["preflight.py"]).hexdigest(), PUBLIC_PREFLIGHT_SHA256)
        for name, data in self.original.items():
            (self.run / "package" / name).write_bytes(data)
        with zipfile.ZipFile(self.old_zip, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in sorted(self.original.items()):
                archive.writestr(name, data)
        self.write_zip(self.fixed_zip, self.fixed)
        self.expected = repair.digest(self.fixed_zip)
        with patch.object(repair, "LEGACY_PREFLIGHT_SHA256", PUBLIC_PREFLIGHT_SHA256):
            self.assertIn("Restored", self.recover())

    def test_real_lf_preflight_failure_can_be_recovered(self):
        assets = SOURCE.parents[2] / "v2"
        self.fixed = {name: (assets / name).read_bytes() for name in repair.FILES}
        self.original = dict(self.fixed)
        self.original["preflight.py"] = self.fixed["preflight.py"].replace(
            repair.FIXED_REQUIRED, repair.LEGACY_REQUIRED, 1)
        self.original["activate.sh"] = self.fixed["activate.sh"].replace(repair.FIXED_UFW, repair.LEGACY_UFW, 1)
        for name, data in self.original.items():
            (self.run / "package" / name).write_bytes(data)
        self.write_zip(self.old_zip, self.original)
        self.write_zip(self.fixed_zip, self.fixed)
        self.expected = repair.digest(self.fixed_zip)
        marker = self.run / "shared.preflight.failed"
        marker.write_bytes(b"original failure timestamp\n")
        with patch.object(repair, "LEGACY_PREFLIGHT_SHA256", PUBLIC_PREFLIGHT_SHA256):
            self.assertIn("Restored", self.recover())
        self.assertEqual(b"original failure timestamp\n", marker.read_bytes())

    def test_started_tasks_are_never_modified(self):
        for state in ("done", "failed", "running"):
            with self.subTest(state=state):
                marker = self.run / ("ss.configure." + state)
                marker.touch()
                before = self.old_zip.read_bytes()
                with self.assertRaisesRegex(ValueError, "already started"):
                    self.recover()
                self.assertEqual(before, self.old_zip.read_bytes())
                self.assertFalse((self.run / "bundle.before.zip").exists())
                marker.unlink()

    def test_unrecognized_archive_and_modified_package_are_rejected(self):
        original_zip = self.old_zip.read_bytes()
        self.write_zip(self.old_zip, dict(self.original, **{"preflight.py": b"changed\r\n"}))
        with self.assertRaisesRegex(ValueError, "known pre-deployment"):
            self.recover()
        self.old_zip.write_bytes(original_zip)
        (self.run / "package/runner.sh").write_bytes(b"changed\r\n")
        with self.assertRaisesRegex(ValueError, "modified"):
            self.recover()

    def test_non_newline_changes_and_corrupt_plan_are_rejected(self):
        changed = dict(self.fixed, **{"runner.sh": b"different code\n"})
        self.write_zip(self.fixed_zip, changed)
        self.expected = repair.digest(self.fixed_zip)
        with self.assertRaisesRegex(ValueError, "only change line endings"):
            self.recover()
        self.write_zip(self.fixed_zip, self.fixed)
        self.expected = repair.digest(self.fixed_zip)
        (self.run / "plan.txt").write_bytes(b"changed plan\n")
        with self.assertRaisesRegex(ValueError, "Plan digest"):
            self.recover()

    def test_interrupted_path_swaps_can_resume(self):
        (self.run / "package").rename(self.run / "package.before")
        self.old_zip.rename(self.run / "bundle.before.zip")
        self.assertIn("Restored", self.recover())
        self.assertTrue(repair.matches(self.run / "package", self.fixed))
        self.assertEqual(self.expected, repair.digest(self.old_zip))


if __name__ == "__main__":
    unittest.main()
