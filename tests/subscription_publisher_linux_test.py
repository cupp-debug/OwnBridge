"""Publication replay tests; OS service changes are replaced with test doubles."""

import importlib.util
import errno
import json
import os
import socket
import sys
from pathlib import Path
import tempfile
import threading
import types
import unittest
from unittest.mock import patch
from http.server import ThreadingHTTPServer


ASSETS = Path(__file__).resolve().parents[1] / "app/src/main/assets/subscription/v1"


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class SubscriptionPublisherTest(unittest.TestCase):
    def setUp(self):
        if os.name == "posix":
            self.publisher = load_module("subscription_publisher", ASSETS / "publish.py")
        else:
            fake_pwd = types.ModuleType("pwd")
            with patch.dict(sys.modules, {"pwd": fake_pwd}):
                self.publisher = load_module("subscription_publisher", ASSETS / "publish.py")
        self.original_ensure_service = self.publisher.ensure_service
        self.server_module = load_module("subscription_server_for_publish", ASSETS / "server.py")
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.public = root / "public"
        self.state = root / "state"
        self.runs = root / "runs"
        self.public.mkdir()
        self.state.mkdir()
        self.run_id = "b" * 32
        source = self.runs / self.run_id / "subscription-v1"
        source.mkdir(parents=True)
        self.profile = source / "profile.yaml"
        self.profile.write_bytes(b"proxies: []\n")
        self.server = ThreadingHTTPServer(
            ("127.0.0.1", 0), self.server_module.handler_for(self.public)
        )
        self.addCleanup(self.server.server_close)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.addCleanup(self.server.shutdown)
        for name, value in (("PUBLIC", self.public), ("STATE", self.state), ("RUNS", self.runs)):
            setting = patch.object(self.publisher, name, value)
            setting.start()
            self.addCleanup(setting.stop)
        for name, value in (("ensure_service", lambda _source: self.server.server_port),
                            ("os.geteuid", lambda: 0), ("os.chown", lambda *_args: None),
                            ("shutil.which", lambda _name: None)):
            setting = patch.object(self.publisher, name, value) if "." not in name else None
            if setting is None:
                module, attribute = name.split(".")
                setting = patch.object(getattr(self.publisher, module), attribute, value, create=True)
            setting.start()
            self.addCleanup(setting.stop)
        setting = patch.object(self.publisher.pwd, "getpwnam",
            lambda _name: types.SimpleNamespace(pw_gid=os.getgid() if os.name == "posix" else 1000),
            create=True)
        setting.start()
        self.addCleanup(setting.stop)
        if os.name != "posix":
            # Windows reports different POSIX mode bits for private temp files.
            setting = patch.object(self.publisher, "private_json",
                lambda path: json.loads(path.read_text(encoding="utf-8")))
            setting.start()
            self.addCleanup(setting.stop)

    def test_retry_keeps_same_token_and_profile(self):
        sha = self.publisher.digest(self.profile.read_bytes())
        first_port, first_token = self.publisher.publish(self.run_id, sha)
        second_port, second_token = self.publisher.publish(self.run_id, sha)
        self.assertEqual((first_port, first_token), (second_port, second_token))
        self.assertEqual((self.public / f"{first_token}.yaml").read_bytes(), b"proxies: []\n")

    def test_changed_profile_cannot_replace_existing_url(self):
        original = self.publisher.digest(self.profile.read_bytes())
        _, token = self.publisher.publish(self.run_id, original)
        self.profile.write_bytes(b"proxies: [changed]\n")
        with self.assertRaises(ValueError):
            self.publisher.publish(self.run_id, self.publisher.digest(self.profile.read_bytes()))
        self.assertEqual((self.public / f"{token}.yaml").read_bytes(), b"proxies: []\n")

    def test_invalid_run_is_rejected_before_write(self):
        with self.assertRaises(ValueError):
            self.publisher.publish("../escape", "a" * 64)
        self.assertEqual(list(self.state.iterdir()), [])

    def test_ipv4_port_selection_works_without_ipv6_stack(self):
        original_socket = socket.socket

        def ipv4_only_socket(family, kind):
            if family == socket.AF_INET6:
                raise OSError(errno.EAFNOSUPPORT, "IPv6 is unavailable")
            return original_socket(family, kind)

        with patch.object(self.publisher.socket, "socket", side_effect=ipv4_only_socket):
            self.assertIn(self.publisher.free_port(), range(28600, 28620))

    def test_unknown_existing_unit_fails_before_service_setup(self):
        unit = Path(self.temp.name) / "ownbridge-subscription.service"
        unit.write_text("[Service]\nExecStart=/unexpected\n", encoding="utf-8")
        with patch.object(self.publisher, "UNIT", unit), patch.object(
            self.publisher, "PORT_FILE", self.state / "port.json"
        ), patch.object(self.publisher, "ROOT", Path(self.temp.name) / "root"), patch.object(
            self.publisher, "SERVER", Path(self.temp.name) / "server-v1.py"
        ), patch.object(self.publisher, "ensure_account", side_effect=AssertionError("side effect")):
            with self.assertRaisesRegex(ValueError, "Unknown subscription service"):
                self.original_ensure_service(ASSETS / "server.py")


if __name__ == "__main__":
    unittest.main()
