import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/assets/deployment/v2/plan.py"
SPEC = importlib.util.spec_from_file_location("ownbridge_v2_plan", SOURCE)
module = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


class PlanParserTest(unittest.TestCase):
    def test_accepts_dual_stack_and_three_protocols(self):
        nodes = module.parse_plan(
            b"ownbridge-plan-v2\n"
            b"ipv4 203.0.113.10 ss 28443\n"
            b"ipv4 203.0.113.10 vless 28444\n"
            b"ipv4 203.0.113.10 hy2 28445\n"
            b"ipv6 2001:db8::10 ss 28446\n"
            b"ipv6 2001:db8::10 vless 28447\n"
            b"ipv6 2001:db8::10 hy2 28448\n"
        )
        self.assertEqual(6, len(nodes))
        self.assertEqual("hy2", nodes[-1].protocol)

    def test_rejects_unknown_duplicate_or_malformed_nodes(self):
        invalid = (
            b"ownbridge-plan-v1\nipv4 203.0.113.10 ss 28443\n",
            b"ownbridge-plan-v2\nipv4 203.0.113.10 unknown 28443\n",
            b"ownbridge-plan-v2\nipv6 203.0.113.10 ss 28443\n",
            b"ownbridge-plan-v2\nipv4 203.0.113.10 ss 28443\nipv4 203.0.113.10 ss 28444\n",
            b"ownbridge-plan-v2\nipv4 203.0.113.10 ss 28443\nipv4 203.0.113.11 hy2 28443\n",
            b"ownbridge-plan-v2\nipv4 203.0.113.10 ss 28443\n"
            b"ipv4 203.0.113.10 hy2 28444\nipv6 2001:db8::10 ss 28445\n",
            b"ownbridge-plan-v2\nipv4 203.0.113.10 ss 028443\n",
            b"ownbridge-plan-v2\nipv6 fe80::1%eth0 ss 28443\n",
            b"ownbridge-plan-v2\nipv4 203.0.113.10 ss 28443",
        )
        for content in invalid:
            with self.subTest(content=content), self.assertRaises(ValueError):
                module.parse_plan(content)

    def test_cli_never_exposes_an_unselected_module(self):
        with tempfile.TemporaryDirectory() as directory:
            plan = Path(directory) / "plan.txt"
            plan.write_bytes(b"ownbridge-plan-v2\nipv4 203.0.113.10 ss 28443\n")
            protocols = subprocess.run(
                [sys.executable, str(SOURCE), "protocols", str(plan)],
                capture_output=True, text=True, check=True,
            )
            self.assertEqual("ss\n", protocols.stdout)
            absent = subprocess.run(
                [sys.executable, str(SOURCE), "contains", str(plan), "hy2"],
                capture_output=True, text=True,
            )
            self.assertEqual(1, absent.returncode)


if __name__ == "__main__":
    unittest.main()
