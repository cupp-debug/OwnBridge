"""Loopback tests for the exact, read-only public subscription route."""

import importlib.util
from http.server import ThreadingHTTPServer
from pathlib import Path
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen


SERVER_SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/assets/subscription/v1/server.py"
spec = importlib.util.spec_from_file_location("ownbridge_subscription_server", SERVER_SOURCE)
server_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server_module)


class SubscriptionServerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.token = "a" * 64
        (self.directory / f"{self.token}.yaml").write_bytes(b"proxies: []\n")
        self.server = ThreadingHTTPServer(
            ("127.0.0.1", 0), server_module.handler_for(self.directory)
        )
        self.addCleanup(self.server.server_close)
        thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(self.server.shutdown)
        self.base = f"http://127.0.0.1:{self.server.server_port}"

    def test_exact_token_get_and_head(self):
        with urlopen(self.base + f"/s/{self.token}.yaml") as response:
            self.assertEqual(response.status, 200)
            self.assertEqual(response.read(), b"proxies: []\n")
            self.assertTrue(response.headers["Content-Type"].startswith("application/x-yaml"))
        with urlopen(Request(self.base + f"/s/{self.token}.yaml", method="HEAD")) as response:
            self.assertEqual(response.status, 200)
            self.assertEqual(response.read(), b"")

    def test_wrong_path_and_query_do_not_disclose_files(self):
        for path in ("/", "/s/", "/s/" + "b" * 64 + ".yaml",
                     f"/s/{self.token}.yaml?copy=1", "/s/%2e%2e/state.json"):
            with self.subTest(path=path), self.assertRaises(HTTPError) as failure:
                urlopen(self.base + path)
            self.assertEqual(failure.exception.code, 404)
            failure.exception.close()

    def test_write_method_is_rejected(self):
        request = Request(self.base + f"/s/{self.token}.yaml", data=b"replace", method="POST")
        with self.assertRaises(HTTPError) as failure:
            urlopen(request)
        self.assertEqual(failure.exception.code, 405)
        failure.exception.close()

    def test_oversized_profile_is_not_served(self):
        (self.directory / f"{self.token}.yaml").write_bytes(
            b"x" * (server_module.MAX_PROFILE_BYTES + 1)
        )
        with self.assertRaises(HTTPError) as failure:
            urlopen(self.base + f"/s/{self.token}.yaml")
        self.assertEqual(failure.exception.code, 404)
        failure.exception.close()


if __name__ == "__main__":
    unittest.main()
