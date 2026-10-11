"""Exercise activation's actual firewall commands without touching a system firewall."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/assets/deployment/v2/activate.sh"
SH = shutil.which("sh") or "C:/Program Files/Git/usr/bin/sh.exe"


@unittest.skipUnless(Path(SH).is_file(), "POSIX shell is unavailable")
class FirewallActivationTest(unittest.TestCase):
    def run_firewall(self, status=None, protocol="ss", status_exit=0):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            log = root / "ufw.log"
            python = root / "python3"
            python.write_bytes(b"#!/bin/sh\nprintf '%s\\n' 'ipv4 203.0.113.10 28443'\n")
            python.chmod(0o755)
            if status is not None:
                ufw = root / "ufw"
                ufw.write_bytes(
                    b"#!/bin/sh\nif [ \"$1\" = status ]; then\n"
                    b"    printf '%s\\n' \"$UFW_STATUS\"\n    exit \"$UFW_EXIT\"\nfi\n"
                    b"printf '%s\\n' \"$*\" >>\"$UFW_LOG\"\n")
                ufw.chmod(0o755)
            env = os.environ.copy()
            env["PATH"] = str(root) + os.pathsep + str(Path(SH).parent)
            env.update(UFW_STATUS=status or "", UFW_EXIT=str(status_exit), UFW_LOG=log.as_posix())
            if status is None and shutil.which("ufw", path=env["PATH"]):
                self.skipTest("Test shell directory contains a real UFW binary")
            source = SOURCE.read_bytes().decode("utf-8")
            script = (f"set -eu\nprotocol={protocol}\nrun_id=test-run\npackage=/unused\nrun_dir=/unused\n"
                      + source[source.index("ufw_status=\n"):])
            result = subprocess.run([SH, "-s"], input=script.encode("utf-8"),
                                    capture_output=True, env=env, timeout=10)
            return result, log.read_text().splitlines() if log.exists() else []

    def test_missing_and_inactive_ufw_do_not_block_activation(self):
        for status in (None, "Status: inactive"):
            with self.subTest(status=status):
                result, rules = self.run_firewall(status)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual([], rules)

    def test_active_ufw_receives_only_each_protocols_required_rules(self):
        for protocol, transports in (("ss", ("tcp", "udp")), ("vless", ("tcp",)), ("hy2", ("udp",))):
            with self.subTest(protocol=protocol):
                result, rules = self.run_firewall("Status: active", protocol)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual([
                    f"allow to 203.0.113.10 port 28443 proto {transport} comment ownbridge-{protocol}:test-run"
                    for transport in transports
                ], rules)

    def test_installed_ufw_errors_are_not_ignored(self):
        result, rules = self.run_firewall("", status_exit=7)
        self.assertEqual(7, result.returncode)
        self.assertEqual([], rules)


if __name__ == "__main__":
    unittest.main()
