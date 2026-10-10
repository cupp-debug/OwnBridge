"""Disposable localhost-only traffic probe for generated SS, VLESS and HY2 configs.

This uses sing-box on both ends to expose configuration/handshake errors before
VPS and Mihomo tests. It does not prove Mihomo interoperability or WAN reachability.
"""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import threading
import time


SOURCE_DIR = Path(__file__).resolve().parents[1] / "app/src/main/assets/deployment/v2"
sys.path.insert(0, str(SOURCE_DIR))
from configure import configure
import hy2


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = b"ownbridge-probe-ok"
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *unused):
        pass


def available_port(start: int, used: set[int]) -> int:
    for port in range(start, 28543):
        if port in used:
            continue
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as tcp, \
                    socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as udp:
                tcp.bind(("127.0.0.1", port))
                udp.bind(("127.0.0.1", port))
            return port
        except OSError:
            continue
    raise RuntimeError("No disposable protocol port is available")


def wait_for_tcp(port: int, process: subprocess.Popen, secrets: tuple[str, ...] = ()) -> None:
    for _ in range(30):
        if process.poll() is not None:
            detail = process.stderr.read().decode(errors="replace") if process.stderr else ""
            for value in secrets:
                detail = detail.replace(value, "[redacted]")
            raise RuntimeError(f"Local proxy process exited before listening: {detail[-500:]}")
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.2):
                return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError("Local proxy did not start listening")


def tunnel_request(proxy_port: int, target_port: int) -> None:
    with socket.create_connection(("127.0.0.1", proxy_port), timeout=10) as connection:
        connection.settimeout(10)
        connection.sendall(
            f"CONNECT 127.0.0.1:{target_port} HTTP/1.1\r\n"
            f"Host: 127.0.0.1:{target_port}\r\n\r\n".encode()
        )
        response = b""
        while b"\r\n\r\n" not in response:
            response += connection.recv(4096)
            if len(response) > 8192:
                raise RuntimeError("Unexpected proxy CONNECT response")
        if b" 200 " not in response.split(b"\r\n", 1)[0]:
            raise RuntimeError("Proxy CONNECT did not succeed")
        connection.sendall(b"GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
        body = response.split(b"\r\n\r\n", 1)[1]
        while b"ownbridge-probe-ok" not in body:
            chunk = connection.recv(4096)
            if not chunk:
                break
            body += chunk
        if b"ownbridge-probe-ok" not in body:
            raise RuntimeError("Proxy tunnel carried no test response")


def client_outbound(protocol: str, run_dir: Path, port: int) -> dict:
    if protocol == "ss":
        credential = json.loads((run_dir / "secrets/ss-01.json").read_text())
        return {"type": "shadowsocks", "tag": "proxy", "server": "127.0.0.1",
                "server_port": port, "method": "aes-256-gcm", "password": credential["password"]}
    if protocol == "vless":
        credential = json.loads((run_dir / "secrets/vless-01.json").read_text())
        return {"type": "vless", "tag": "proxy", "server": "127.0.0.1",
                "server_port": port, "uuid": credential["uuid"], "tls": {
                "enabled": True, "server_name": credential["server_name"],
                "utls": {"enabled": True, "fingerprint": "chrome"},
                "reality": {"enabled": True, "public_key": credential["public_key"],
                                "short_id": credential["short_id"]}}}
    credential = json.loads((run_dir / "secrets/hy2-01/credential.json").read_text())
    return {"type": "hysteria2", "tag": "proxy", "server": "127.0.0.1",
            "server_port": port, "password": credential["password"], "tls": {
                "enabled": True, "server_name": "127.0.0.1",
                "certificate_path": str(run_dir / "secrets/hy2-01/cert.pem")}}


def main(binary: Path, openssl: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="ownbridge-loopback-") as directory:
        run_dir = Path(directory)
        used = set()
        ports = {}
        for protocol in ("ss", "vless", "hy2"):
            port = available_port(28443, used)
            used.add(port)
            ports[protocol] = port
        (run_dir / "plan.txt").write_text("ownbridge-plan-v2\n" + "".join(
            f"ipv4 127.0.0.1 {protocol} {port}\n" for protocol, port in ports.items()
        ), encoding="ascii")
        for protocol in ports:
            configure(run_dir, protocol, binary, openssl, probe_reality=False)
        runtime = run_dir / "hy2-runtime.json"
        runtime.write_text(json.dumps(hy2.build(run_dir, openssl, use_staged_certificate=True)))

        httpd = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        server_thread = threading.Thread(target=httpd.serve_forever, daemon=True)
        server_thread.start()
        servers = []
        try:
            for protocol in ports:
                config = runtime if protocol == "hy2" else run_dir / f"config-{protocol}.json"
                servers.append(subprocess.Popen([str(binary), "run", "-c", str(config)],
                                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
            for protocol, port in ports.items():
                if protocol != "hy2":
                    wait_for_tcp(port, servers[list(ports).index(protocol)])
            for protocol, port in ports.items():
                proxy_port = available_port(28449, used)
                used.add(proxy_port)
                client = {"log": {"level": "warn"},
                          "inbounds": [{"type": "mixed", "listen": "127.0.0.1",
                                        "listen_port": proxy_port}],
                          "outbounds": [client_outbound(protocol, run_dir, port)],
                          "route": {"final": "proxy"}}
                config = run_dir / f"client-{protocol}.json"
                config.write_text(json.dumps(client), encoding="utf-8")
                sensitive = tuple(str(value) for value in (
                    client["outbounds"][0].get("password"), client["outbounds"][0].get("uuid"),
                ) if value)
                checked = subprocess.run([str(binary), "check", "-c", str(config)],
                                         capture_output=True, text=True)
                if checked.returncode:
                    detail = checked.stderr
                    for value in sensitive:
                        detail = detail.replace(value, "[redacted]")
                    raise RuntimeError(f"{protocol} client configuration rejected: {detail[-500:]}")
                process = subprocess.Popen([str(binary), "run", "-c", str(config)],
                                           stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
                try:
                    wait_for_tcp(proxy_port, process, sensitive)
                    tunnel_request(proxy_port, httpd.server_port)
                    print(f"{protocol}: loopback proxy traffic passed")
                finally:
                    process.terminate()
                    process.wait(timeout=5)
        finally:
            for process in servers:
                process.terminate()
                process.wait(timeout=5)
            httpd.shutdown()
            httpd.server_close()


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: loopback_protocol_probe.py SING_BOX_BINARY OPENSSL_BINARY")
    main(Path(sys.argv[1]), Path(sys.argv[2]))
