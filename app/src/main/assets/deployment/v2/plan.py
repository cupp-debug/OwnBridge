"""Strict parser for the versioned, non-secret OwnBridge deployment plan."""

from dataclasses import dataclass
import ipaddress
from pathlib import Path
import sys


HEADER = "ownbridge-plan-v2"
PROTOCOLS = {"ss", "vless", "hy2"}


@dataclass(frozen=True)
class Node:
    family: str
    address: str
    protocol: str
    port: int


def parse_plan(content: bytes) -> tuple[Node, ...]:
    if len(content) > 16_384 or not content.endswith(b"\n"):
        raise ValueError("Plan is too large or missing its final newline")
    try:
        lines = content.decode("ascii").splitlines()
    except UnicodeDecodeError as error:
        raise ValueError("Plan must be ASCII") from error
    if not lines or lines[0] != HEADER or not 1 <= len(lines) - 1 <= 100:
        raise ValueError("Unknown plan schema or invalid node count")

    nodes = []
    seen_targets = set()
    seen_ports = set()
    for line in lines[1:]:
        parts = line.split(" ")
        if len(parts) != 4 or any(not part for part in parts):
            raise ValueError("Invalid plan node")
        family, address, protocol, raw_port = parts
        if family not in {"ipv4", "ipv6"} or protocol not in PROTOCOLS:
            raise ValueError("Unsupported address family or protocol")
        if "%" in address:
            raise ValueError("Scoped IPv6 addresses are not supported")
        try:
            parsed = ipaddress.ip_address(address)
            port = int(raw_port)
        except ValueError as error:
            raise ValueError("Invalid address or port") from error
        if parsed.version != (4 if family == "ipv4" else 6):
            raise ValueError("Address family mismatch")
        if not raw_port.isascii() or not raw_port.isdecimal() or str(port) != raw_port:
            raise ValueError("Port must be a canonical decimal integer")
        if port not in range(28443, 28543):
            raise ValueError("Port outside the supported range")
        target = (family, parsed, protocol)
        if target in seen_targets or port in seen_ports:
            raise ValueError("Duplicate target or port")
        seen_targets.add(target)
        seen_ports.add(port)
        nodes.append(Node(family, address, protocol, port))
    addresses = {(node.family, node.address) for node in nodes}
    protocols = {node.protocol for node in nodes}
    if len(nodes) != len(addresses) * len(protocols):
        raise ValueError("Every selected protocol must cover every selected address")
    return tuple(nodes)


def main(argv: list[str]) -> int:
    if len(argv) not in (3, 4):
        raise SystemExit("Usage: plan.py protocols|contains|count|nodes PLAN_PATH [PROTOCOL]")
    action, path = argv[1:3]
    nodes = parse_plan(Path(path).read_bytes())
    selected = {node.protocol for node in nodes}
    if action == "protocols" and len(argv) == 3:
        for protocol in ("ss", "vless", "hy2"):
            if protocol in selected:
                print(protocol)
        return 0
    if action == "contains" and len(argv) == 4 and argv[3] in PROTOCOLS:
        return 0 if argv[3] in selected else 1
    if action in {"count", "nodes"} and len(argv) == 4 and argv[3] in PROTOCOLS:
        matches = tuple(node for node in nodes if node.protocol == argv[3])
        if not matches:
            return 1
        if action == "count":
            print(len(matches))
        else:
            for node in matches:
                print(f"{node.family} {node.address} {node.port}")
        return 0
    raise SystemExit("Invalid plan command")


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
