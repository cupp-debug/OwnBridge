import importlib.util
from pathlib import Path
import socket
import sys
import tempfile
import unittest
from unittest.mock import patch


SOURCE_DIR = Path(__file__).resolve().parents[1] / "app/src/main/assets/deployment/v2"
sys.path.insert(0, str(SOURCE_DIR))
SPEC = importlib.util.spec_from_file_location("ownbridge_v2_preflight", SOURCE_DIR / "preflight.py")
module = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


class PreflightTest(unittest.TestCase):
    def test_minimal_ubuntu_without_ufw_passes_when_actual_dependencies_exist(self):
        from unittest.mock import Mock

        run_dir = Path("/test-run")
        plan_path = Mock()
        plan_path.read_bytes.return_value = b"plan"
        nodes = ()
        with patch.object(module.os, "geteuid", return_value=0, create=True), \
                patch.object(module.platform, "machine", return_value="x86_64"), \
                patch.object(module.Path, "read_text", return_value='ID=ubuntu\nVERSION_ID="24.04"\n'), \
                patch.object(module.Path, "__truediv__", return_value=plan_path), \
                patch.object(module, "parse_plan", return_value=nodes), \
                patch.object(module.shutil, "which", side_effect=lambda name: None if name == "ufw" else name), \
                patch.object(module, "assigned_global_scope_addresses", return_value=set()), \
                patch.object(module, "check_assigned_addresses"), \
                patch.object(module, "check_unit_ownership", return_value=set()), \
                patch.object(module, "check_available_ports") as ports:
            module.main(run_dir)
            ports.assert_called_once_with(nodes, set())
            with patch.object(module.shutil, "which", side_effect=lambda name: None if name == "curl" else name):
                with self.assertRaisesRegex(SystemExit, "Missing required tools: curl"):
                    module.main(run_dir)

    def test_preview_release_gate_accepts_ubuntu_2204_only_as_an_exact_version(self):
        for version in ("22.04", "24.04", "26.04"):
            with self.subTest(version=version):
                self.assertTrue(module.supported_release({"ID": "ubuntu", "VERSION_ID": version}))
        for release in ({"ID": "ubuntu", "VERSION_ID": "20.04"},
                        {"ID": "ubuntu", "VERSION_ID": "22.040"},
                        {"ID": "debian", "VERSION_ID": "22.04"}):
            with self.subTest(release=release):
                self.assertFalse(module.supported_release(release))

    @patch.object(module.subprocess, "check_output")
    def test_iproute_empty_address_entry_does_not_hide_assigned_addresses(self, check_output):
        import ipaddress

        check_output.side_effect = [
            b'[{"addr_info":[{"local":"203.0.113.10"}]}]',
            b'[{"addr_info":[{"local":"2001:db8::1"},{}]}]',
        ]
        self.assertEqual({
            ipaddress.ip_address("203.0.113.10"),
            ipaddress.ip_address("2001:db8::1"),
        }, module.assigned_global_scope_addresses())

    def test_protocols_request_only_their_transports(self):
        self.assertEqual((socket.SOCK_STREAM, socket.SOCK_DGRAM), module.required_sockets("ss"))
        self.assertEqual((socket.SOCK_STREAM,), module.required_sockets("vless"))
        self.assertEqual((socket.SOCK_DGRAM,), module.required_sockets("hy2"))

    def test_selected_ipv6_must_be_assigned_and_globally_routable(self):
        import ipaddress
        from plan import Node

        public = Node("ipv6", "2001:4860::1", "hy2", 28443)
        module.check_assigned_addresses((public,), {ipaddress.ip_address(public.address)})
        with self.assertRaises(ValueError):
            module.check_assigned_addresses((public,), set())
        ula = Node("ipv6", "fd00::1", "hy2", 28443)
        with self.assertRaises(ValueError):
            module.check_assigned_addresses((ula,), {ipaddress.ip_address(ula.address)})

    @patch.object(module.subprocess, "run")
    def test_existing_legacy_run_allows_a_different_address_and_protocol(self, systemctl):
        from plan import Node

        systemctl.return_value.returncode = 1
        old_id, new_id = "a" * 32, "b" * 32
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            units = root / "units"
            runs = root / "runs"
            units.mkdir()
            (runs / old_id).mkdir(parents=True)
            (units / "ownbridge-v2-ss.service").write_text(f"# OwnBridge run: {old_id}\n")
            (runs / old_id / "plan.txt").write_text(
                "ownbridge-plan-v2\nipv4 203.0.113.10 ss 28444\n")
            new = (Node("ipv6", "2001:4860::1", "ss", 28447),)
            self.assertEqual(set(), module.check_unit_ownership(runs / new_id, new, units, runs))

    @patch.object(module.subprocess, "run")
    def test_duplicate_target_or_reserved_port_is_rejected(self, systemctl):
        from plan import Node

        systemctl.return_value.returncode = 1
        old_id, new_id = "a" * 32, "b" * 32
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            units = root / "units"
            runs = root / "runs"
            units.mkdir()
            (runs / old_id).mkdir(parents=True)
            (units / "ownbridge-v2-ss.service").write_text(f"# OwnBridge run: {old_id}\n")
            (runs / old_id / "plan.txt").write_text(
                "ownbridge-plan-v2\nipv4 203.0.113.10 ss 28444\n")
            cases = (
                Node("ipv4", "203.0.113.10", "ss", 28447),
                Node("ipv6", "2001:4860::1", "ss", 28444),
            )
            for node in cases:
                with self.subTest(node=node), self.assertRaises(ValueError):
                    module.check_unit_ownership(runs / new_id, (node,), units, runs)

    def test_unknown_unit_cannot_be_claimed(self):
        from plan import Node

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            units = root / "units"
            units.mkdir()
            (units / "ownbridge-v2-ss.service").write_text("[Service]\n")
            with self.assertRaisesRegex(ValueError, "ownership"):
                module.check_unit_ownership(root / ("b" * 32),
                    (Node("ipv6", "2001:4860::1", "ss", 28447),), units, root)


if __name__ == "__main__":
    unittest.main()
