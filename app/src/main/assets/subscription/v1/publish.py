"""Idempotently publish one verified profile without touching proxy services."""

import hashlib
import errno
import json
import os
from pathlib import Path
import pwd
import re
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
from urllib.request import urlopen


ROOT = Path("/var/lib/ownbridge-subscription")
PUBLIC = ROOT / "public"
STATE = ROOT / "state"
SERVER = ROOT / "server-v1.py"
PORT_FILE = STATE / "port.json"
UNIT = Path("/etc/systemd/system/ownbridge-subscription.service")
ACCOUNT = "ownbridge-subscription"
RUNS = Path("/var/lib/ownbridge/runs")
RUN_ID = re.compile(r"[0-9a-f]{32}")
SHA256 = re.compile(r"[0-9a-f]{64}")


def digest(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def private_json(path: Path) -> dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
        raise ValueError(f"Untrusted subscription state: {path.name}")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Invalid subscription state")
    return value


def write_private(path: Path, content: bytes) -> None:
    with tempfile.NamedTemporaryFile(prefix=".ownbridge-", dir=path.parent, delete=False) as staged:
        name = Path(staged.name)
        try:
            os.fchmod(staged.fileno(), 0o600)
            staged.write(content)
            staged.flush()
            os.fsync(staged.fileno())
        except BaseException:
            name.unlink(missing_ok=True)
            raise
    try:
        os.replace(name, path)
    finally:
        name.unlink(missing_ok=True)


def free_port() -> int:
    for port in range(28600, 28620):
        available = True
        for family, host in ((socket.AF_INET, "0.0.0.0"), (socket.AF_INET6, "::")):
            try:
                with socket.socket(family, socket.SOCK_STREAM) as probe:
                    if family == socket.AF_INET6:
                        probe.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 1)
                    probe.bind((host, port))
            except OSError as error:
                if family == socket.AF_INET6 and error.errno in (
                    errno.EAFNOSUPPORT, errno.EPROTONOSUPPORT, errno.EADDRNOTAVAIL,
                ):
                    continue
                available = False
                break
        if available:
            return port
    raise ValueError("No free subscription port in 28600-28619")


def unit_text(port: int) -> str:
    return f"""# OwnBridge subscription v1
[Unit]
Description=OwnBridge read-only subscription delivery
After=network-online.target

[Service]
Type=simple
User={ACCOUNT}
Group={ACCOUNT}
ExecStart=/usr/bin/python3 -I -B {SERVER} {port} {PUBLIC}
Restart=on-failure
RestartSec=3
NoNewPrivileges=yes
ProtectSystem=strict
ProtectHome=yes
PrivateTmp=yes
CapabilityBoundingSet=
TasksMax=32
MemoryMax=64M

[Install]
WantedBy=multi-user.target
"""


def ensure_account() -> int:
    try:
        return pwd.getpwnam(ACCOUNT).pw_gid
    except KeyError:
        subprocess.run([
            "useradd", "--system", "--user-group", "--no-create-home",
            "--home-dir", str(ROOT), "--shell", "/usr/sbin/nologin", ACCOUNT,
        ], check=True, capture_output=True)
        return pwd.getpwnam(ACCOUNT).pw_gid


def ensure_service(source: Path) -> int:
    if ROOT.is_symlink() or PUBLIC.is_symlink() or STATE.is_symlink():
        raise ValueError("Subscription directory must not be a symlink")
    if UNIT.is_symlink():
        raise ValueError("Unknown subscription service already exists")
    saved_port = None
    if PORT_FILE.exists() or PORT_FILE.is_symlink():
        saved_port = private_json(PORT_FILE).get("port")
        if type(saved_port) is not int or saved_port not in range(28600, 28620):
            raise ValueError("Invalid saved subscription port")
    if UNIT.exists():
        if saved_port is None or UNIT.read_text(encoding="utf-8") != unit_text(saved_port):
            raise ValueError("Unknown subscription service already exists")
    if (SERVER.exists() or SERVER.is_symlink()) and (
        SERVER.is_symlink() or SERVER.read_bytes() != source.read_bytes()
    ):
        raise ValueError("Existing subscription server version differs")
    gid = ensure_account()
    ROOT.mkdir(mode=0o750, parents=True, exist_ok=True)
    PUBLIC.mkdir(mode=0o750, exist_ok=True)
    STATE.mkdir(mode=0o700, exist_ok=True)
    os.chown(ROOT, 0, gid)
    os.chown(PUBLIC, 0, gid)
    os.chmod(ROOT, 0o750)
    os.chmod(PUBLIC, 0o750)
    os.chmod(STATE, 0o700)
    code = source.read_bytes()
    if SERVER.exists():
        if SERVER.is_symlink() or SERVER.read_bytes() != code:
            raise ValueError("Existing subscription server version differs")
    else:
        write_private(SERVER, code)
        os.chmod(SERVER, 0o750)
        os.chown(SERVER, 0, gid)

    if saved_port is None:
        port = free_port()
        write_private(PORT_FILE, json.dumps({"port": port}).encode("ascii"))
    else:
        port = saved_port

    expected = unit_text(port)
    if not UNIT.exists():
        write_private(UNIT, expected.encode("utf-8"))
        os.chmod(UNIT, 0o644)
    subprocess.run(["systemctl", "daemon-reload"], check=True, capture_output=True)
    subprocess.run(["systemctl", "enable", "--now", UNIT.name], check=True, capture_output=True)
    subprocess.run(["systemctl", "is-active", "--quiet", UNIT.name], check=True, capture_output=True)
    return port


def publish(run_id: str, expected_sha: str) -> tuple[int, str]:
    if os.geteuid() != 0 or not RUN_ID.fullmatch(run_id) or not SHA256.fullmatch(expected_sha):
        raise ValueError("Invalid publication request")
    source_dir = RUNS / run_id / "subscription-v1"
    source = source_dir / "profile.yaml"
    if source.is_symlink() or not source.is_file():
        raise ValueError("Subscription profile is missing")
    profile = source.read_bytes()
    if not 1 <= len(profile) <= 128 * 1024 or digest(profile) != expected_sha:
        raise ValueError("Subscription profile SHA-256 mismatch")
    port = ensure_service(source_dir / "server.py")
    record = STATE / f"{run_id}.json"
    if record.exists():
        saved = private_json(record)
        token = saved.get("token")
        if not isinstance(token, str) or not SHA256.fullmatch(token) or saved.get("sha256") != expected_sha:
            raise ValueError("Existing subscription differs from this profile")
    else:
        token = secrets.token_hex(32)
        write_private(record, json.dumps({"token": token, "sha256": expected_sha}).encode("ascii"))
    target = PUBLIC / f"{token}.yaml"
    if target.exists():
        if target.is_symlink() or digest(target.read_bytes()) != expected_sha:
            raise ValueError("Published profile differs from its saved record")
    else:
        write_private(target, profile)
        os.chmod(target, 0o640)
        os.chown(target, 0, pwd.getpwnam(ACCOUNT).pw_gid)
    with urlopen(f"http://127.0.0.1:{port}/s/{token}.yaml", timeout=5) as response:
        if response.status != 200 or digest(response.read(128 * 1024 + 1)) != expected_sha:
            raise ValueError("Local subscription response differs from the profile")
    if shutil.which("ufw"):
        status = subprocess.run(["ufw", "status"], capture_output=True, text=True, check=True).stdout
        if status.startswith("Status: active"):
            subprocess.run(["ufw", "allow", f"{port}/tcp"], check=True, capture_output=True)
    return port, token


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: publish.py RUN_ID PROFILE_SHA256")
    try:
        listen_port, access_token = publish(sys.argv[1], sys.argv[2])
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"Subscription publication failed: {error}") from None
    print(f"{listen_port} {access_token} {sys.argv[2]}")
