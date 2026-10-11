"""Serve only exact OwnBridge subscription files; no listing or upload endpoint."""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import re
import stat
import sys
from urllib.parse import urlsplit


TOKEN_PATH = re.compile(r"/s/([0-9a-f]{64})\.yaml")
MAX_PROFILE_BYTES = 128 * 1024


def handler_for(directory: Path):
    class SubscriptionHandler(BaseHTTPRequestHandler):
        server_version = "OwnBridgeSubscription/1"
        sys_version = ""

        def do_GET(self):
            self._send_profile(include_body=True)

        def do_HEAD(self):
            self._send_profile(include_body=False)

        def do_POST(self):
            self.send_error(405)

        def log_message(self, _format, *_args):
            # Paths are bearer credentials. Never put them in the system journal.
            return

        def _send_profile(self, include_body: bool):
            try:
                target = urlsplit(self.path)
            except ValueError:
                self.send_error(404)
                return
            match = TOKEN_PATH.fullmatch(target.path) if not target.query and not target.fragment else None
            if match is None:
                self.send_error(404)
                return
            path = directory / f"{match.group(1)}.yaml"
            try:
                descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
                with os.fdopen(descriptor, "rb") as profile:
                    info = os.fstat(profile.fileno())
                    if not stat.S_ISREG(info.st_mode) or info.st_size > MAX_PROFILE_BYTES:
                        self.send_error(404)
                        return
                    body = profile.read(MAX_PROFILE_BYTES + 1)
            except (FileNotFoundError, PermissionError, OSError):
                self.send_error(404)
                return
            if len(body) > MAX_PROFILE_BYTES:
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/x-yaml; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            if include_body:
                self.wfile.write(body)

    return SubscriptionHandler


def main() -> None:
    if len(sys.argv) != 3 or not sys.argv[1].isdigit():
        raise SystemExit("Usage: server.py PORT PUBLIC_DIRECTORY")
    port = int(sys.argv[1])
    if port not in range(28600, 28620):
        raise SystemExit("Port outside the OwnBridge subscription range")
    directory = Path(sys.argv[2])
    ThreadingHTTPServer(("0.0.0.0", port), handler_for(directory)).serve_forever()


if __name__ == "__main__":
    main()
