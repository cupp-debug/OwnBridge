"""Exercise all seven selected-protocol combinations with real local Mihomo traffic.

This proves local generator/export interoperability, not WAN or Android reachability.
"""

import json
from http.server import ThreadingHTTPServer
from pathlib import Path
import subprocess
import sys
import tempfile
import threading

from loopback_mihomo_probe import run_client
from loopback_protocol_probe import Handler, available_port, wait_for_tcp


SOURCE_DIR = Path(__file__).resolve().parents[1] / "app/src/main/assets/deployment/v2"
sys.path.insert(0, str(SOURCE_DIR))
from configure import configure
import hy2


def main(binary: Path, openssl: Path, mihomo: Path) -> None:
    used = set()
    with tempfile.TemporaryDirectory(prefix="ownbridge-combinations-") as directory:
        root = Path(directory)
        httpd = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=httpd.serve_forever, daemon=True).start()
        try:
            protocols = ("ss", "vless", "hy2")
            for mask in range(1, 1 << len(protocols)):
                selected = tuple(protocol for index, protocol in enumerate(protocols)
                                 if mask & (1 << index))
                run_dir = root / f"selection-{mask}"
                run_dir.mkdir()
                ports = {}
                for protocol in selected:
                    port = available_port(28443, used)
                    used.add(port)
                    ports[protocol] = port
                (run_dir / "plan.txt").write_text("ownbridge-plan-v2\n" + "".join(
                    f"ipv4 127.0.0.1 {protocol} {port}\n"
                    for protocol, port in ports.items()
                ), encoding="ascii")
                for protocol in selected:
                    configure(run_dir, protocol, binary, openssl, probe_reality=False)
                assert {file.stem.split("-")[0] for file in (run_dir / "exports").glob("*.json")} == set(selected)
                servers = {}
                try:
                    for protocol, port in ports.items():
                        config = run_dir / f"config-{protocol}.json"
                        if protocol == "hy2":
                            config = run_dir / "hy2-runtime.json"
                            config.write_text(json.dumps(hy2.build(
                                run_dir, openssl, use_staged_certificate=True,
                            )), encoding="utf-8")
                        servers[protocol] = subprocess.Popen(
                            [str(binary), "run", "-c", str(config)],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                        )
                        if protocol != "hy2":
                            wait_for_tcp(port, servers[protocol])
                    for protocol, port in ports.items():
                        local = available_port(28443, used)
                        used.add(local)
                        run_client(mihomo, run_dir, protocol, port, httpd.server_port, local)
                    print(f"Combination {mask}/7 passed: {', '.join(selected)}")
                finally:
                    for server in servers.values():
                        server.terminate()
                        server.wait(timeout=5)
        finally:
            httpd.shutdown()
            httpd.server_close()


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: loopback_combinations_probe.py SING_BOX OPENSSL MIHOMO")
    main(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
