"""Server-side checks only; client import and real proxy traffic remain separate gates."""

import ipaddress
import json
import os
from pathlib import Path
import subprocess
import sys

from config_common import selected_nodes
from hy2 import certificate_fingerprint
from vless import derive_public_key


def private_json(path: Path) -> dict:
    if not path.is_file() or path.is_symlink() or path.stat().st_size > 4096 or (
        os.name == "posix" and path.stat().st_mode & 0o077
    ):
        raise ValueError(f"Missing or exposed deployment record: {path.name}")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Invalid deployment record: {path.name}")
    return value


def nested(value: dict, *keys: str):
    for key in keys:
        if not isinstance(value, dict) or key not in value:
            raise ValueError(f"Installed configuration is missing {'.'.join(keys)}")
        value = value[key]
    return value


def verify_exports(run_dir: Path, protocol: str, nodes: tuple, config: Path,
                   openssl: Path = Path("/usr/bin/openssl"),
                   installed_root: Path = Path("/etc/ownbridge")) -> None:
    """Reject running services whose client credentials differ from their installed config."""
    exports = run_dir / "exports"
    if not exports.is_dir() or exports.is_symlink() or (
        os.name == "posix" and exports.stat().st_mode & 0o077
    ):
        raise ValueError("Client export directory is missing or exposed")
    inbounds = nested(json.loads(config.read_text(encoding="utf-8")), "inbounds")
    if not isinstance(inbounds, list) or len(inbounds) != len(nodes):
        raise ValueError("Installed inbound count differs from selected addresses")

    for index, (node, inbound) in enumerate(zip(nodes, inbounds), start=1):
        slot = f"{index:02}"
        exported = private_json(exports / f"{protocol}-{slot}.json")
        credential_path = run_dir / "secrets" / f"{protocol}-{slot}.json"
        if protocol == "hy2":
            credential_path = run_dir / "secrets" / f"hy2-{slot}" / "credential.json"
        credential = private_json(credential_path)
        if not isinstance(inbound, dict) or (
            inbound.get("tag") != f"ownbridge-{protocol}-{slot}"
            or inbound.get("listen") != node.address
            or inbound.get("listen_port") != node.port
        ):
            raise ValueError(f"Installed {protocol} inbound differs from selected address")

        if protocol == "ss":
            expected = {"type": "ss", "password": credential.get("password")}
            matched = (
                inbound.get("type") == "shadowsocks"
                and inbound.get("method") == "aes-256-gcm"
                and inbound.get("password") == credential.get("password")
            )
        elif protocol == "vless":
            expected = {
                "type": "vless", "uuid": credential.get("uuid"),
                "public_key": credential.get("public_key"),
                "short_id": credential.get("short_id"),
                "server_name": credential.get("server_name"),
            }
            matched = (
                inbound.get("type") == "vless"
                and nested(inbound, "users") == [{"uuid": credential.get("uuid")}]
                and nested(inbound, "tls", "enabled") is True
                and nested(inbound, "tls", "server_name") == credential.get("server_name")
                and nested(inbound, "tls", "reality", "enabled") is True
                and nested(inbound, "tls", "reality", "handshake") == {
                    "server": credential.get("server_name"), "server_port": 443,
                }
                and nested(inbound, "tls", "reality", "private_key") == credential.get("private_key")
                and nested(inbound, "tls", "reality", "short_id") == [credential.get("short_id")]
                and derive_public_key(credential["private_key"], openssl) == credential.get("public_key")
            )
        elif protocol == "hy2":
            expected = {
                "type": "hy2", "password": credential.get("password"),
                "fingerprint": credential.get("fingerprint"),
            }
            cert = installed_root / f"v2-hy2-{run_dir.name}-{slot}.crt"
            key = installed_root / f"v2-hy2-{run_dir.name}-{slot}.key"
            matched = (
                inbound.get("type") == "hysteria2"
                and nested(inbound, "users") == [{"password": credential.get("password")}]
                and nested(inbound, "tls", "enabled") is True
                and nested(inbound, "tls", "certificate_path") == str(cert)
                and nested(inbound, "tls", "key_path") == str(key)
                and cert.is_file() and not cert.is_symlink()
                and key.is_file() and not key.is_symlink()
                and certificate_fingerprint(openssl, cert) == credential.get("fingerprint")
            )
        else:
            raise ValueError("Unsupported protocol")
        if not matched or exported != expected or any(value is None for value in expected.values()):
            raise ValueError(f"Client export does not match installed {protocol} service")


def listening_endpoints(arguments: list[str]) -> set[tuple[ipaddress.IPv4Address | ipaddress.IPv6Address, int]]:
    output = subprocess.check_output(["ss", "-H", *arguments], text=True)
    endpoints = set()
    for line in output.splitlines():
        columns = line.split()
        if len(columns) < 5:
            continue
        endpoint = columns[3]
        host, separator, raw_port = endpoint.rpartition(":")
        if not separator:
            continue
        try:
            endpoints.add((ipaddress.ip_address(host.strip("[]")), int(raw_port)))
        except ValueError:
            continue
    return endpoints


def verify(run_dir: Path, protocol: str) -> None:
    nodes = selected_nodes(run_dir, protocol)
    run_id = run_dir.name
    unit_name = f"ownbridge-v2-{protocol}-{run_id}.service"
    unit = Path(f"/etc/systemd/system/{unit_name}")
    config = Path(f"/etc/ownbridge/v2-{protocol}-{run_id}.json")
    binary = Path("/opt/ownbridge/bin/sing-box-1.14.2")
    if not unit.is_file() or f"# OwnBridge run: {run_id}\n" not in unit.read_text():
        raise ValueError("Service ownership does not match this run")
    if not config.is_file() or not binary.is_file():
        raise ValueError("Installed configuration or binary is missing")
    subprocess.run([str(binary), "check", "-c", str(config)], check=True, capture_output=True)
    subprocess.run(["systemctl", "is-active", "--quiet", unit_name],
                   check=True)
    verify_exports(run_dir, protocol, nodes, config)

    tcp = listening_endpoints(["-lnt"])
    udp = listening_endpoints(["-lnu"])
    for index, node in enumerate(nodes, start=1):
        endpoint = (ipaddress.ip_address(node.address), node.port)
        if protocol in ("ss", "vless") and endpoint not in tcp:
            raise ValueError(f"Missing TCP listener for selected {node.family} node")
        if protocol in ("ss", "hy2") and endpoint not in udp:
            raise ValueError(f"Missing UDP listener for selected {node.family} node")
        if protocol == "hy2":
            certificate = Path(f"/etc/ownbridge/v2-hy2-{run_id}-{index:02}.crt")
            subprocess.run(["openssl", "x509", "-in", str(certificate), "-checkend", "0",
                            "-noout"], check=True, capture_output=True)
    print(f"Server-side {protocol} checks passed; client traffic is not yet verified")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: verify.py RUN_DIR PROTOCOL")
    verify(Path(sys.argv[1]), sys.argv[2])
