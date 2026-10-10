"""Opt-in VPS probe: authenticate through a deployed SS inbound without printing secrets."""

import json
import os
import pathlib
import socket
import subprocess
import sys
import tempfile
import time


def main(run_dir: pathlib.Path) -> None:
    address, port_text = (run_dir / "plan.txt").read_text(encoding="ascii").splitlines()
    secret = (run_dir / "secret.txt").read_text(encoding="ascii").strip()
    binary = "/opt/ownbridge/bin/sing-box-1.14.2"
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        local_port = reservation.getsockname()[1]

    config = {
        "log": {"level": "error"},
        "inbounds": [{
            "type": "socks", "tag": "probe-in", "listen": "127.0.0.1", "listen_port": local_port,
        }],
        "outbounds": [{
            "type": "shadowsocks", "tag": "probe-out", "server": address,
            "server_port": int(port_text), "method": "aes-256-gcm", "password": secret,
        }],
    }
    with tempfile.TemporaryDirectory(prefix="probe-", dir=run_dir) as temporary:
        config_path = pathlib.Path(temporary) / "client.json"
        descriptor = os.open(config_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            json.dump(config, output)

        check = subprocess.run([binary, "check", "-c", str(config_path)], capture_output=True)
        if check.returncode != 0:
            raise RuntimeError("Temporary SS client config failed validation")
        process = subprocess.Popen(
            [binary, "run", "-c", str(config_path)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        try:
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("Temporary SS client exited before testing")
                try:
                    with socket.create_connection(("127.0.0.1", local_port), timeout=0.5):
                        break
                except OSError:
                    time.sleep(0.1)
            else:
                raise RuntimeError("Temporary SOCKS port did not open")

            request = subprocess.run(
                [
                    "curl", "--silent", "--show-error", "--fail", "--max-time", "20",
                    "--socks5-hostname", f"127.0.0.1:{local_port}", "--output", "/dev/null",
                    "--write-out", "%{http_code}", "https://www.gstatic.com/generate_204",
                ],
                capture_output=True,
                text=True,
                timeout=25,
            )
            if request.returncode != 0 or request.stdout != "204":
                raise RuntimeError(
                    f"SS request failed (curl exit {request.returncode}, HTTP {request.stdout or 'none'})"
                )
            print("Shadowsocks-authenticated HTTPS request succeeded (HTTP 204)")
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]))
