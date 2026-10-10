import importlib.util
import ipaddress
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch


SOURCE_DIR = Path(__file__).resolve().parents[1] / "app/src/main/assets/deployment/v2"
sys.path.insert(0, str(SOURCE_DIR))
SPEC = importlib.util.spec_from_file_location("ownbridge_v2_verify", SOURCE_DIR / "verify.py")
module = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)
import hy2
import ss
import vless
from config_common import selected_nodes


OPENSSL = shutil.which("openssl") or str(Path("C:/Program Files/Git/usr/bin/openssl.exe"))


class ListenerParserTest(unittest.TestCase):
    @patch.object(module.subprocess, "check_output")
    def test_accepts_exact_ipv4_and_ipv6_listeners_not_wildcards(self, check_output):
        check_output.return_value = (
            "LISTEN 0 4096 203.0.113.10:28443 0.0.0.0:*\n"
            "LISTEN 0 4096 [2001:db8::10]:28444 [::]:*\n"
            "LISTEN 0 4096 *:28445 *:*\n"
        )
        found = module.listening_endpoints(["-lnt"])
        self.assertEqual({
            (ipaddress.ip_address("203.0.113.10"), 28443),
            (ipaddress.ip_address("2001:db8::10"), 28444),
        }, found)


class ExportConsistencyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.run_dir = Path(self.temporary.name)
        self.config = self.run_dir / "installed.json"

    def plan(self, protocol):
        (self.run_dir / "plan.txt").write_text(
            f"ownbridge-plan-v2\nipv4 127.0.0.1 {protocol} 28443\n", encoding="ascii",
        )

    def write_config(self, value):
        self.config.write_text(json.dumps(value), encoding="utf-8")

    def verify(self, protocol, **kwargs):
        module.verify_exports(
            self.run_dir, protocol, selected_nodes(self.run_dir, protocol), self.config, **kwargs,
        )

    def test_ss_export_password_and_installed_port_must_match(self):
        self.plan("ss")
        config = ss.build(self.run_dir)
        self.write_config(config)
        self.verify("ss")

        export = self.run_dir / "exports/ss-01.json"
        original = json.loads(export.read_text(encoding="utf-8"))
        export.write_text(json.dumps({**original, "password": "wrong"}), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Client export"):
            self.verify("ss")
        export.write_text(json.dumps(original), encoding="utf-8")
        config["inbounds"][0]["listen_port"] += 1
        self.write_config(config)
        with self.assertRaisesRegex(ValueError, "selected address"):
            self.verify("ss")

    @unittest.skipUnless(Path(OPENSSL).is_file(), "OpenSSL is unavailable")
    def test_vless_export_and_reality_private_key_must_match(self):
        self.plan("vless")
        credential = {
            "uuid": "bf000d23-0752-40b4-affe-68f7707a9661",
            "private_key": "2C-5Zx05L2JI1bQdxbwIhFo_nRhpzx863aplNtxBQEU",
            "public_key": "oF8x5cqTLM2egxDS5Bo7r3ji_fPId4lwJcxfUBW7OlA",
            "short_id": "0123456789abcdef", "server_name": vless.REALITY_SERVER,
        }
        with patch.object(vless, "generate_credential", return_value=credential):
            config = vless.build(self.run_dir, Path("unused"), Path(OPENSSL))
        self.write_config(config)
        self.verify("vless", openssl=Path(OPENSSL))

        config["inbounds"][0]["tls"]["reality"]["private_key"] = "C" * 43
        self.write_config(config)
        with self.assertRaisesRegex(ValueError, "Client export"):
            self.verify("vless", openssl=Path(OPENSSL))

    @unittest.skipUnless(Path(OPENSSL).is_file(), "OpenSSL is unavailable")
    def test_hy2_installed_certificate_and_export_pin_must_match(self):
        self.plan("hy2")
        config = hy2.build(self.run_dir, Path(OPENSSL), use_staged_certificate=True)
        installed = self.run_dir / "installed"
        installed.mkdir()
        slot = f"v2-hy2-{self.run_dir.name}-01"
        staged = self.run_dir / "secrets/hy2-01"
        certificate = installed / f"{slot}.crt"
        key = installed / f"{slot}.key"
        shutil.copy2(staged / "cert.pem", certificate)
        shutil.copy2(staged / "key.pem", key)
        config["inbounds"][0]["tls"]["certificate_path"] = str(certificate)
        config["inbounds"][0]["tls"]["key_path"] = str(key)
        self.write_config(config)
        self.verify("hy2", openssl=Path(OPENSSL), installed_root=installed)

        export = self.run_dir / "exports/hy2-01.json"
        original = json.loads(export.read_text(encoding="utf-8"))
        export.write_text(json.dumps({**original, "fingerprint": "00:" * 31 + "00"}),
                          encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Client export"):
            self.verify("hy2", openssl=Path(OPENSSL), installed_root=installed)
        export.write_text(json.dumps(original), encoding="utf-8")
        other, _ = hy2.material(self.run_dir, 2, "127.0.0.1", Path(OPENSSL))
        shutil.copy2(other / "cert.pem", certificate)
        with self.assertRaisesRegex(ValueError, "Client export"):
            self.verify("hy2", openssl=Path(OPENSSL), installed_root=installed)


if __name__ == "__main__":
    unittest.main()
