from pathlib import Path
import json
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch


SOURCE_DIR = Path(__file__).resolve().parents[1] / "app/src/main/assets/deployment/v2"
sys.path.insert(0, str(SOURCE_DIR))
import ss
import vless
import hy2


OPENSSL = shutil.which("openssl") or str(Path("C:/Program Files/Git/usr/bin/openssl.exe"))
REALITY_PRIVATE = "2C-5Zx05L2JI1bQdxbwIhFo_nRhpzx863aplNtxBQEU"
REALITY_PUBLIC = "oF8x5cqTLM2egxDS5Bo7r3ji_fPId4lwJcxfUBW7OlA"


class ConfigTest(unittest.TestCase):
    @unittest.skipUnless(Path(OPENSSL).is_file(), "OpenSSL is unavailable")
    def test_every_protocol_subset_generates_only_selected_client_exports(self):
        protocols = ("ss", "vless", "hy2")
        reality = {
            "uuid": "bf000d23-0752-40b4-affe-68f7707a9661",
            "private_key": REALITY_PRIVATE, "public_key": REALITY_PUBLIC,
            "short_id": "0123456789abcdef", "server_name": vless.REALITY_SERVER,
        }
        for mask in range(1, 1 << len(protocols)):
            selected = tuple(protocol for index, protocol in enumerate(protocols)
                             if mask & (1 << index))
            with self.subTest(protocols=selected), tempfile.TemporaryDirectory() as directory:
                run_dir = Path(directory)
                plan = "ownbridge-plan-v2\n" + "".join(
                    f"ipv4 127.0.0.1 {protocol} {28443 + index}\n"
                    for index, protocol in enumerate(selected)
                )
                (run_dir / "plan.txt").write_text(plan, encoding="ascii")
                with patch.object(vless, "generate_credential", return_value=reality):
                    for protocol in selected:
                        if protocol == "ss":
                            ss.build(run_dir)
                        elif protocol == "vless":
                            vless.build(run_dir, Path("unused"), Path(OPENSSL))
                        else:
                            hy2.build(run_dir, Path(OPENSSL), use_staged_certificate=True)
                actual = {path.stem.split("-")[0] for path in (run_dir / "exports").glob("*.json")}
                self.assertEqual(set(selected), actual)
                self.assertEqual(len(selected), len(list((run_dir / "exports").glob("*.json"))))
                self.assertEqual(set(selected), {
                    path.name.split("-")[0] for path in (run_dir / "secrets").iterdir()
                })

    def test_shadowsocks_only_uses_selected_addresses_and_stable_secret(self):
        with tempfile.TemporaryDirectory() as directory:
            run_dir = Path(directory)
            (run_dir / "plan.txt").write_bytes(
                b"ownbridge-plan-v2\nipv4 203.0.113.10 ss 28443\n"
                b"ipv6 2001:db8::10 ss 28444\n"
            )
            first = ss.build(run_dir)
            second = ss.build(run_dir)
            self.assertEqual(first, second)
            self.assertEqual(2, len(first["inbounds"]))
            self.assertEqual("2001:db8::10", first["inbounds"][1]["listen"])
            self.assertEqual([], list((run_dir / "secrets").glob("vless-*")))
            exports = sorted((run_dir / "exports").glob("ss-*.json"))
            self.assertEqual(2, len(exports))
            self.assertEqual(
                {"type": "ss", "password": first["inbounds"][0]["password"]},
                json.loads(exports[0].read_text(encoding="utf-8")),
            )

    @unittest.skipUnless(Path(OPENSSL).is_file(), "OpenSSL is unavailable")
    def test_vless_retry_does_not_rotate_uuid_or_reality_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            run_dir = Path(directory)
            (run_dir / "plan.txt").write_bytes(
                b"ownbridge-plan-v2\nipv4 203.0.113.10 vless 28443\n"
            )
            fixture = {
                "uuid": "bf000d23-0752-40b4-affe-68f7707a9661",
                "private_key": REALITY_PRIVATE,
                "public_key": REALITY_PUBLIC,
                "short_id": "0123456789abcdef",
                "server_name": vless.REALITY_SERVER,
            }
            with patch.object(vless, "generate_credential", return_value=fixture) as generator:
                first = vless.build(run_dir, Path("unused"), Path(OPENSSL))
                second = vless.build(run_dir, Path("unused"), Path(OPENSSL))
            self.assertEqual(first, second)
            generator.assert_called_once()
            self.assertEqual(fixture["uuid"], first["inbounds"][0]["users"][0]["uuid"])
            self.assertEqual([], list((run_dir / "secrets").glob("hy2-*")))
            exported = json.loads((run_dir / "exports/vless-01.json").read_text(encoding="utf-8"))
            self.assertEqual({
                "type": "vless", "uuid": fixture["uuid"],
                "public_key": fixture["public_key"], "short_id": fixture["short_id"],
                "server_name": fixture["server_name"],
            }, exported)
            self.assertNotIn("private_key", exported)

    @unittest.skipUnless(Path(OPENSSL).is_file(), "OpenSSL is unavailable")
    def test_vless_rejects_saved_public_key_not_derived_from_private_key(self):
        with tempfile.TemporaryDirectory() as directory:
            run_dir = Path(directory)
            (run_dir / "plan.txt").write_bytes(
                b"ownbridge-plan-v2\nipv4 203.0.113.10 vless 28443\n"
            )
            fixture = {
                "uuid": "bf000d23-0752-40b4-affe-68f7707a9661",
                "private_key": REALITY_PRIVATE, "public_key": "B" * 43,
                "short_id": "0123456789abcdef", "server_name": vless.REALITY_SERVER,
            }
            with patch.object(vless, "generate_credential", return_value=fixture):
                with self.assertRaisesRegex(ValueError, "does not match"):
                    vless.build(run_dir, Path("unused"), Path(OPENSSL))
            self.assertFalse((run_dir / "exports/vless-01.json").exists())

    @unittest.skipUnless(Path(OPENSSL).is_file(), "OpenSSL is unavailable")
    def test_one_module_failure_does_not_rotate_other_protocol_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            run_dir = Path(directory)
            (run_dir / "plan.txt").write_bytes(
                b"ownbridge-plan-v2\nipv4 127.0.0.1 ss 28443\n"
                b"ipv4 127.0.0.1 vless 28444\n"
                b"ipv4 127.0.0.1 hy2 28445\n"
            )
            ss.build(run_dir)
            with patch.object(vless, "generate_credential", side_effect=RuntimeError("injected")):
                with self.assertRaises(RuntimeError):
                    vless.build(run_dir, Path("unused"), Path(OPENSSL))
            hy2.build(run_dir, Path(OPENSSL), use_staged_certificate=True)
            baseline = {protocol: (run_dir / f"exports/{protocol}-01.json").read_bytes()
                        for protocol in ("ss", "hy2")}
            self.assertFalse((run_dir / "exports/vless-01.json").exists())

            reality = {
                "uuid": "bf000d23-0752-40b4-affe-68f7707a9661",
                "private_key": REALITY_PRIVATE, "public_key": REALITY_PUBLIC,
                "short_id": "0123456789abcdef", "server_name": vless.REALITY_SERVER,
            }
            with patch.object(vless, "generate_credential", return_value=reality):
                vless.build(run_dir, Path("unused"), Path(OPENSSL))
            ss.build(run_dir)
            hy2.build(run_dir, Path(OPENSSL), use_staged_certificate=True)
            self.assertTrue((run_dir / "exports/vless-01.json").is_file())
            self.assertEqual(baseline, {protocol: (run_dir / f"exports/{protocol}-01.json").read_bytes()
                                        for protocol in ("ss", "hy2")})

    @unittest.skipUnless(Path(OPENSSL).is_file(), "OpenSSL is unavailable")
    def test_hy2_retry_keeps_certificate_and_rejects_pin_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            run_dir = Path(directory)
            (run_dir / "plan.txt").write_bytes(
                b"ownbridge-plan-v2\nipv4 127.0.0.1 hy2 28443\n"
            )
            first, credential = hy2.material(run_dir, 1, "127.0.0.1", Path(OPENSSL))
            second, retry = hy2.material(run_dir, 1, "127.0.0.1", Path(OPENSSL))
            self.assertEqual(first, second)
            self.assertEqual(credential, retry)
            hy2.build(run_dir, Path(OPENSSL), use_staged_certificate=True)
            self.assertEqual({
                "type": "hy2", "password": credential["password"],
                "fingerprint": credential["fingerprint"],
            }, json.loads((run_dir / "exports/hy2-01.json").read_text(encoding="utf-8")))
            file = first / "credential.json"
            file.write_text(json.dumps({**credential, "fingerprint": "00:" * 31 + "00"}))
            with self.assertRaises(ValueError):
                hy2.material(run_dir, 1, "127.0.0.1", Path(OPENSSL))


if __name__ == "__main__":
    unittest.main()
