"""VLESS over TCP + REALITY candidate; external traffic proof is still required."""

import base64
import binascii
from pathlib import Path
import re
import secrets
import socket
import ssl
import subprocess
import uuid

from config_common import (
    base_config, export_directory, read_or_create_json, secret_directory, selected_nodes,
    write_json_atomic,
)


REALITY_SERVER = "www.cloudflare.com"
KEY_PATTERN = re.compile(r"^[A-Za-z0-9_-]{43}$")
PRIVATE_DER_PREFIX = bytes.fromhex("302e020100300506032b656e04220420")
PUBLIC_DER_PREFIX = bytes.fromhex("302a300506032b656e032100")


def probe_reality_target() -> None:
    context = ssl.create_default_context()
    with socket.create_connection((REALITY_SERVER, 443), timeout=8) as connection:
        with context.wrap_socket(connection, server_hostname=REALITY_SERVER) as tls:
            if tls.version() != "TLSv1.3":
                raise ValueError("Reality handshake target did not negotiate TLS 1.3")


def generate_credential(binary: Path) -> dict:
    output = subprocess.run(
        [str(binary), "generate", "reality-keypair"],
        capture_output=True, text=True, check=True,
    ).stdout
    values = dict(re.findall(r"^(PrivateKey|PublicKey):\s*(\S+)\s*$", output, re.MULTILINE))
    if set(values) != {"PrivateKey", "PublicKey"}:
        raise ValueError("Unexpected Reality keypair output")
    return {
        "uuid": str(uuid.uuid4()),
        "private_key": values["PrivateKey"],
        "public_key": values["PublicKey"],
        "short_id": secrets.token_hex(8),
        "server_name": REALITY_SERVER,
    }


def derive_public_key(private_key: str, openssl: Path) -> str:
    try:
        raw = base64.urlsafe_b64decode(private_key + "=")
        if len(raw) != 32:
            raise ValueError("Invalid Reality private key length")
        result = subprocess.run(
            [str(openssl), "pkey", "-inform", "DER", "-pubout", "-outform", "DER"],
            input=PRIVATE_DER_PREFIX + raw, capture_output=True, check=True,
        )
    except (binascii.Error, OSError, subprocess.CalledProcessError) as error:
        raise ValueError("Cannot derive Reality public key") from error
    if not result.stdout.startswith(PUBLIC_DER_PREFIX) or len(result.stdout) != len(PUBLIC_DER_PREFIX) + 32:
        raise ValueError("Unexpected Reality public key format")
    return base64.urlsafe_b64encode(result.stdout[len(PUBLIC_DER_PREFIX):]).decode("ascii").rstrip("=")


def validate_credential(value: dict, openssl: Path) -> None:
    try:
        uuid.UUID(value["uuid"])
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError("Invalid saved VLESS UUID") from error
    if not all(isinstance(value.get(key), str) and KEY_PATTERN.fullmatch(value[key])
               for key in ("private_key", "public_key")):
        raise ValueError("Invalid saved Reality keypair")
    if not isinstance(value.get("short_id"), str) or not re.fullmatch(r"[0-9a-f]{16}", value["short_id"]):
        raise ValueError("Invalid saved Reality short ID")
    if value.get("server_name") != REALITY_SERVER:
        raise ValueError("Reality server changed within an existing run")
    if derive_public_key(value["private_key"], openssl) != value["public_key"]:
        raise ValueError("Saved Reality public key does not match its private key")


def build(run_dir: Path, binary: Path, openssl: Path) -> dict:
    directory = secret_directory(run_dir)
    inbounds = []
    for index, node in enumerate(selected_nodes(run_dir, "vless"), start=1):
        credential = read_or_create_json(
            directory / f"vless-{index:02}.json", lambda: generate_credential(binary),
        )
        validate_credential(credential, openssl)
        write_json_atomic(export_directory(run_dir) / f"vless-{index:02}.json", {
            "type": "vless", "uuid": credential["uuid"],
            "public_key": credential["public_key"], "short_id": credential["short_id"],
            "server_name": credential["server_name"],
        })
        inbounds.append({
            "type": "vless", "tag": f"ownbridge-vless-{index:02}",
            "listen": node.address, "listen_port": node.port,
            "users": [{"uuid": credential["uuid"]}],
            "tls": {
                "enabled": True, "server_name": credential["server_name"],
                "reality": {
                    "enabled": True,
                    "handshake": {"server": credential["server_name"], "server_port": 443},
                    "private_key": credential["private_key"],
                    "short_id": [credential["short_id"]],
                },
            },
        })
    return base_config(inbounds)
