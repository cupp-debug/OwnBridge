"""Local Mihomo traffic and HY2 certificate-pin rejection against v2 generators."""

import json
from http.server import ThreadingHTTPServer
from pathlib import Path
import subprocess
import sys
import tempfile
import threading


SOURCE_DIR = Path(__file__).resolve().parents[1] / "app/src/main/assets/deployment/v2"
sys.path.insert(0, str(SOURCE_DIR))
from configure import configure
import hy2
from loopback_protocol_probe import Handler, available_port, tunnel_request, wait_for_tcp


def mihomo_config(protocol: str, run_dir: Path, port: int, local_port: int,
                  *, wrong_pin: bool = False, server_address: str = "127.0.0.1") -> str:
    base = [
        f"mixed-port: {local_port}",
        "allow-lan: false", "mode: rule", "log-level: error", "proxies:",
        "  - name: OwnBridgeProbe", f"    server: '{server_address}'", f"    port: {port}",
    ]
    if protocol == "ss":
        credential = json.loads((run_dir / "exports/ss-01.json").read_text())
        base += ["    type: ss", "    cipher: aes-256-gcm",
                 f"    password: {credential['password']}", "    udp: true"]
    elif protocol == "vless":
        credential = json.loads((run_dir / "exports/vless-01.json").read_text())
        base += ["    type: vless", f"    uuid: {credential['uuid']}",
                 "    udp: true", "    tls: true", "    network: tcp",
                 f"    servername: {credential['server_name']}",
                 "    client-fingerprint: chrome", "    reality-opts:",
                 f"      public-key: {credential['public_key']}",
                 f"      short-id: {credential['short_id']}"]
    else:
        credential = json.loads((run_dir / "exports/hy2-01.json").read_text())
        fingerprint = "00:" * 31 + "00" if wrong_pin else credential["fingerprint"]
        base += ["    type: hysteria2", f"    password: {credential['password']}",
                 "    skip-cert-verify: true", f"    fingerprint: '{fingerprint}'"]
    base += ["proxy-groups:", "  - name: OwnBridge", "    type: select",
             "    proxies:", "      - OwnBridgeProbe", "rules:", "  - MATCH,OwnBridge"]
    return "\n".join(base) + "\n"


def run_client(mihomo: Path, run_dir: Path, protocol: str, server_port: int,
               target_port: int, local_port: int, *, wrong_pin: bool = False,
               server_address: str = "127.0.0.1") -> None:
    suffix = "-wrong-pin" if wrong_pin else ""
    config = run_dir / f"mihomo-{protocol}{suffix}.yaml"
    config.write_text(mihomo_config(protocol, run_dir, server_port, local_port,
                                    wrong_pin=wrong_pin, server_address=server_address), encoding="utf-8")
    data_dir = run_dir / f"mihomo-{protocol}{suffix}-data"
    data_dir.mkdir()
    checked = subprocess.run([str(mihomo), "-t", "-f", str(config), "-d", str(data_dir)],
                             capture_output=True, text=True)
    if checked.returncode:
        raise RuntimeError(f"Mihomo rejected {protocol} test profile")
    process = subprocess.Popen([str(mihomo), "-f", str(config), "-d", str(data_dir)],
                               stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    try:
        wait_for_tcp(local_port, process)
        if wrong_pin:
            try:
                tunnel_request(local_port, target_port)
            except (OSError, RuntimeError):
                print("hy2: wrong certificate pin rejected")
            else:
                raise RuntimeError("Mihomo accepted an incorrect HY2 certificate pin")
        else:
            tunnel_request(local_port, target_port)
            print(f"{protocol}: Mihomo loopback proxy traffic passed")
    finally:
        process.terminate()
        process.wait(timeout=5)


def main(binary: Path, openssl: Path, mihomo: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="ownbridge-mihomo-probe-") as directory:
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
        hy2_runtime = run_dir / "hy2-runtime.json"
        hy2_runtime.write_text(json.dumps(hy2.build(run_dir, openssl,
                                                       use_staged_certificate=True)))

        httpd = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=httpd.serve_forever, daemon=True).start()
        servers = []
        try:
            for protocol in ports:
                config = hy2_runtime if protocol == "hy2" else run_dir / f"config-{protocol}.json"
                servers.append(subprocess.Popen([str(binary), "run", "-c", str(config)],
                                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
            for protocol, port in ports.items():
                if protocol != "hy2":
                    wait_for_tcp(port, servers[list(ports).index(protocol)])
            for protocol, port in ports.items():
                local = available_port(28449, used)
                used.add(local)
                run_client(mihomo, run_dir, protocol, port, httpd.server_port, local)
            local = available_port(28449, used)
            run_client(mihomo, run_dir, "hy2", ports["hy2"], httpd.server_port, local,
                       wrong_pin=True)
        finally:
            for process in servers:
                process.terminate()
                process.wait(timeout=5)
            httpd.shutdown()
            httpd.server_close()


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: loopback_mihomo_probe.py SING_BOX OPENSSL MIHOMO")
    main(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
