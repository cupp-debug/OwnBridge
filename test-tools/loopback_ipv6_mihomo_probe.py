"""Local IPv6-listener traffic gate; it does not prove WAN IPv6 availability."""

import json
from http.server import ThreadingHTTPServer
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import threading
import time

from loopback_mihomo_probe import run_client
from loopback_protocol_probe import Handler, available_port


SOURCE_DIR = Path(__file__).resolve().parents[1] / "app/src/main/assets/deployment/v2"
sys.path.insert(0, str(SOURCE_DIR))
from configure import configure
import hy2


def available_ipv6_port(used: set[int]) -> int:
    for port in range(28443, 28543):
        if port in used:
            continue
        try:
            with socket.socket(socket.AF_INET6, socket.SOCK_STREAM) as tcp, \
                    socket.socket(socket.AF_INET6, socket.SOCK_DGRAM) as udp:
                tcp.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 1)
                udp.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 1)
                tcp.bind(("::1", port))
                udp.bind(("::1", port))
            return port
        except OSError:
            continue
    raise RuntimeError("No disposable IPv6 protocol port is available")


def wait_for_ipv6(port: int, process: subprocess.Popen) -> None:
    for _ in range(30):
        if process.poll() is not None:
            raise RuntimeError("IPv6 protocol server exited before listening")
        try:
            with socket.create_connection(("::1", port), timeout=0.2):
                return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError("IPv6 protocol server did not start listening")


def main(binary: Path, openssl: Path, mihomo: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="ownbridge-ipv6-probe-") as directory:
        run_dir = Path(directory)
        used = set()
        ports = {}
        for protocol in ("ss", "vless", "hy2"):
            port = available_ipv6_port(used)
            used.add(port)
            ports[protocol] = port
        (run_dir / "plan.txt").write_text("ownbridge-plan-v2\n" + "".join(
            f"ipv6 ::1 {protocol} {port}\n" for protocol, port in ports.items()
        ), encoding="ascii")
        for protocol in ports:
            configure(run_dir, protocol, binary, openssl, probe_reality=False)
        hy2_runtime = run_dir / "hy2-runtime.json"
        hy2_runtime.write_text(json.dumps(hy2.build(
            run_dir, openssl, use_staged_certificate=True,
        )), encoding="utf-8")

        httpd = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=httpd.serve_forever, daemon=True).start()
        servers = {}
        try:
            for protocol, port in ports.items():
                config = hy2_runtime if protocol == "hy2" else run_dir / f"config-{protocol}.json"
                servers[protocol] = subprocess.Popen(
                    [str(binary), "run", "-c", str(config)],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                )
                if protocol != "hy2":
                    wait_for_ipv6(port, servers[protocol])
            for protocol, port in ports.items():
                local = available_port(28443, used)
                used.add(local)
                run_client(mihomo, run_dir, protocol, port, httpd.server_port, local,
                           server_address="::1")
            print("IPv6 loopback: all three Mihomo protocol traffic probes passed")
        finally:
            for server in servers.values():
                server.terminate()
                server.wait(timeout=5)
            httpd.shutdown()
            httpd.server_close()


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: loopback_ipv6_mihomo_probe.py SING_BOX OPENSSL MIHOMO")
    main(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
