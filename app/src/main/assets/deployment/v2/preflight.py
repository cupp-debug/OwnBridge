"""Read-only server checks before any v2 protocol resource is created."""

import ipaddress
import json
import os
from pathlib import Path
import platform
import re
import shutil
import socket
import subprocess
import sys

from plan import Node, parse_plan


def required_sockets(protocol: str) -> tuple[int, ...]:
    return {
        "ss": (socket.SOCK_STREAM, socket.SOCK_DGRAM),
        "vless": (socket.SOCK_STREAM,),
        "hy2": (socket.SOCK_DGRAM,),
    }[protocol]


IpAddress = ipaddress.IPv4Address | ipaddress.IPv6Address


def check_assigned_addresses(nodes: tuple[Node, ...], assigned: set[IpAddress]) -> None:
    for node in nodes:
        address = ipaddress.ip_address(node.address)
        if address not in assigned:
            raise ValueError(f"Selected {node.family} address is not assigned: {node.address}")
        if node.family == "ipv6" and not address.is_global:
            raise ValueError(f"Selected IPv6 is not globally routable: {node.address}")


def assigned_global_scope_addresses() -> set[IpAddress]:
    values = set()
    for family in ("-4", "-6"):
        output = subprocess.check_output(["ip", "-j", family, "addr", "show", "scope", "global"])
        for interface in json.loads(output):
            for entry in interface.get("addr_info", []):
                if "local" in entry:
                    values.add(ipaddress.ip_address(entry["local"]))
    return values


def check_unit_ownership(
    run_dir: Path,
    nodes: tuple[Node, ...],
    unit_directory: Path = Path("/etc/systemd/system"),
    runs_root: Path = Path("/var/lib/ownbridge/runs"),
) -> set[str]:
    """Permit other owned runs, but never duplicate a target or reuse an unknown unit."""
    owned: set[str] = set()
    run_id = run_dir.name
    selected = {(ipaddress.ip_address(node.address), node.protocol) for node in nodes}
    selected_ports = {node.port for node in nodes}
    protocols = {node.protocol for node in nodes}
    for unit in unit_directory.glob("ownbridge-v2-*.service"):
        match = re.fullmatch(r"ownbridge-v2-(ss|vless|hy2)(?:-([a-f0-9]{32}))?\.service", unit.name)
        if not match or unit.is_symlink():
            raise ValueError(f"Unknown OwnBridge service: {unit.name}")
        protocol, name_id = match.groups()
        content = unit.read_text(encoding="utf-8")
        owner = re.search(r"^# OwnBridge run: ([a-f0-9]{32})$", content, re.MULTILINE)
        if owner is None or (name_id is not None and name_id != owner.group(1)):
            raise ValueError(f"Cannot verify service ownership: {unit.name}")
        owner_id = owner.group(1)
        if owner_id == run_id:
            if protocol not in protocols:
                raise ValueError(f"Unexpected service in this run: {unit.name}")
            owned.add(protocol)
            continue
        old_plan = runs_root / owner_id / "plan.txt"
        if not old_plan.is_file() or old_plan.is_symlink():
            raise ValueError(f"Missing plan for existing service: {unit.name}")
        old_nodes = parse_plan(old_plan.read_bytes())
        if protocol not in {node.protocol for node in old_nodes}:
            raise ValueError(f"Existing service disagrees with its plan: {unit.name}")
        if selected_ports & {node.port for node in old_nodes if node.protocol == protocol}:
            raise ValueError(f"Port reserved by existing service: {unit.name}")
        conflicts = selected & {
            (ipaddress.ip_address(node.address), node.protocol)
            for node in old_nodes if node.protocol == protocol
        }
        if conflicts:
            raise ValueError(f"Address and protocol already deployed: {unit.name}")
    for protocol in protocols - owned:
        unit_name = f"ownbridge-v2-{protocol}-{run_id}.service"
        if subprocess.run(["systemctl", "cat", unit_name], capture_output=True).returncode == 0:
            raise ValueError(f"Service name is already in use: {unit_name}")
    return owned


def check_available_ports(nodes: tuple[Node, ...], owned_protocols: set[str]) -> None:
    for node in nodes:
        if node.protocol in owned_protocols:
            continue  # Resume verification checks the already-running service separately.
        family = socket.AF_INET if node.family == "ipv4" else socket.AF_INET6
        for kind in required_sockets(node.protocol):
            with socket.socket(family, kind) as probe:
                if family == socket.AF_INET6:
                    probe.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 1)
                try:
                    probe.bind((node.address, node.port))
                except OSError as error:
                    raise ValueError(f"Selected {node.protocol} port is unavailable: {error}") from error


def supported_release(release: dict[str, str]) -> bool:
    return (release.get("ID", "").strip('"') == "ubuntu" and
            release.get("VERSION_ID", "").strip('"') in {"22.04", "24.04", "26.04"})


def main(run_dir: Path) -> None:
    if os.geteuid() != 0:
        raise SystemExit("Root is required")
    release = dict(
        line.split("=", 1) for line in Path("/etc/os-release").read_text().splitlines()
        if "=" in line
    )
    if not supported_release(release):
        raise SystemExit("Only Ubuntu 22.04, 24.04 and 26.04 are enabled for this preview bundle")
    if platform.machine() != "x86_64":
        raise SystemExit("Only x86_64 is tested for this bundle")
    nodes = parse_plan((run_dir / "plan.txt").read_bytes())
    required = {"ip", "ss", "curl", "sha256sum", "tar", "systemctl", "flock"}
    if any(node.protocol in ("vless", "hy2") for node in nodes):
        required.add("openssl")
    missing = sorted(tool for tool in required if not shutil.which(tool))
    if missing:
        raise SystemExit(f"Missing required tools: {', '.join(missing)}")
    check_assigned_addresses(nodes, assigned_global_scope_addresses())
    owned = check_unit_ownership(run_dir, nodes)
    check_available_ports(nodes, owned)
    print(f"Preflight passed: {len(nodes)} selected nodes; no resources changed")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: preflight.py RUN_DIR")
    main(Path(sys.argv[1]))
