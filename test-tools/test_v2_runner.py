"""Black-box checks for the remote shell gate before any deployment stage runs."""

from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest


SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/assets/deployment/v2/runner.sh"
SH = shutil.which("sh") or "C:/Program Files/Git/usr/bin/sh.exe"
CYGPATH = shutil.which("cygpath") or "C:/Program Files/Git/usr/bin/cygpath.exe"


@unittest.skipUnless(
    Path(SH).is_file() and (os.name != "nt" or Path(CYGPATH).is_file()),
    "POSIX shell is unavailable",
)
class RunnerHashGateTest(unittest.TestCase):
    def test_invalid_plan_hash_stops_before_status_or_configuration(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run_id = "a" * 32
            run_dir = root / run_id
            package = run_dir / "package"
            package.mkdir(parents=True)
            (package / "runner.sh").write_text("placeholder\n", encoding="ascii")
            (run_dir / "plan.txt").write_text(
                "ownbridge-plan-v2\nipv4 127.0.0.1 ss 28443\n", encoding="ascii",
            )
            (run_dir / "plan.sha256").write_text("0" * 64 + "  plan.txt\n", encoding="ascii")
            unix_root = (subprocess.check_output([CYGPATH, "-u", str(root)], text=True).strip()
                         if os.name == "nt" else root.as_posix())
            original = SOURCE.read_text(encoding="utf-8")
            needle = 'run_dir="/var/lib/ownbridge/runs/$run_id"'
            self.assertIn(needle, original)
            script = original.replace(needle, f'run_dir="{unix_root}/$run_id"', 1)
            environment = os.environ.copy()
            environment["PATH"] = str(Path(SH).parent) + os.pathsep + environment.get("PATH", "")

            for step in ("status", "ss.configure"):
                with self.subTest(step=step):
                    result = subprocess.run(
                        [SH, "-s", step, run_id], input=script, text=True,
                        capture_output=True, timeout=10, env=environment,
                    )
                    self.assertEqual(65, result.returncode, result.stderr)
                    self.assertFalse(any(run_dir.glob("*.running")))
                    self.assertFalse(any(run_dir.glob("*.done")))


if __name__ == "__main__":
    unittest.main()
