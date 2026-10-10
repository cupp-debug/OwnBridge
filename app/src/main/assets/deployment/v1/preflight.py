import ipaddress
import json
import os
import pathlib
import platform
import shutil
import socket
import subprocess
import sys

run_dir = pathlib.Path(sys.argv[1])
lines = (run_dir / "plan.txt").read_text(encoding="ascii").splitlines()
if len(lines) != 2:
    raise SystemExit("Invalid deployment plan")
address = str(ipaddress.IPv4Address(lines[0]))
port = int(lines[1])
if port not in range(28443, 28543):
    raise SystemExit("Port outside the supported range")
if os.geteuid() != 0:
    raise SystemExit("Root is required for this first deployment milestone")

release = dict(
    line.split("=", 1) for line in pathlib.Path("/etc/os-release").read_text().splitlines()
    if "=" in line
)
if release.get("ID", "").strip('"') != "ubuntu" or release.get("VERSION_ID", "").strip('"') != "26.04":
    raise SystemExit("Only Ubuntu 26.04 is tested for this bundle")
if platform.machine() != "x86_64":
    raise SystemExit("Only x86_64 is tested for this bundle")
for tool in ("ip", "ss", "curl", "sha256sum", "tar", "systemctl", "ufw", "flock"):
    if not shutil.which(tool):
        raise SystemExit(f"Missing required tool: {tool}")

interfaces = json.loads(subprocess.check_output(["ip", "-j", "-4", "addr", "show", "scope", "global"]))
assigned = {item["local"] for iface in interfaces for item in iface.get("addr_info", [])}
if address not in assigned:
    raise SystemExit("Selected IPv4 is not assigned to this server")

unit = pathlib.Path("/etc/systemd/system/ownbridge-ss.service")
owned = unit.exists() and str(run_dir) in unit.read_text()
if unit.exists() and not owned:
    raise SystemExit("A different OwnBridge service already owns this name")
if not unit.exists() and subprocess.run(
    ["systemctl", "cat", "ownbridge-ss.service"], capture_output=True
).returncode == 0:
    raise SystemExit("An existing service unit already owns this name")
if not owned:
    for kind in (socket.SOCK_STREAM, socket.SOCK_DGRAM):
        with socket.socket(socket.AF_INET, kind) as probe:
            try:
                probe.bind((address, port))
            except OSError as error:
                raise SystemExit(f"Selected port is unavailable: {error}") from error

print(f"Preflight passed: Ubuntu 26.04 x86_64; {address}:{port} available")
