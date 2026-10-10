"""Hysteria 2 candidate using an IP-SAN certificate and explicit certificate pin."""

import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile

from config_common import (
    base_config, export_directory, secret_directory, selected_nodes, write_json_atomic,
)


def certificate_fingerprint(openssl: Path, certificate: Path) -> str:
    output = subprocess.run(
        [str(openssl), "x509", "-in", str(certificate), "-noout", "-fingerprint", "-sha256"],
        capture_output=True, text=True, check=True,
    ).stdout.strip()
    match = re.fullmatch(r"sha256 Fingerprint=([0-9A-Fa-f:]{95})", output, re.IGNORECASE)
    if not match or len(match.group(1).split(":")) != 32:
        raise ValueError("Unexpected certificate fingerprint")
    return match.group(1).upper()


def material(run_dir: Path, index: int, address: str, openssl: Path) -> tuple[Path, dict]:
    directory = secret_directory(run_dir)
    target = directory / f"hy2-{index:02}"
    if target.is_symlink():
        raise ValueError("Certificate directory must not be a symlink")
    if not target.exists():
        with tempfile.TemporaryDirectory(prefix=f".hy2-{index:02}.", dir=directory) as temporary:
            staged = Path(temporary)
            certificate = staged / "cert.pem"
            key = staged / "key.pem"
            subprocess.run([
                str(openssl), "req", "-x509", "-newkey", "ec", "-pkeyopt",
                "ec_paramgen_curve:P-256", "-noenc", "-days", "365",
                "-subj", "/CN=OwnBridge", "-addext", f"subjectAltName=IP:{address}",
                "-keyout", str(key), "-out", str(certificate),
            ], capture_output=True, check=True)
            os.chmod(certificate, 0o600)
            os.chmod(key, 0o600)
            write_json_atomic(staged / "credential.json", {
                "password": secrets.token_urlsafe(32),
                "fingerprint": certificate_fingerprint(openssl, certificate),
            })
            try:
                os.rename(staged, target)
            except FileExistsError:
                pass  # Another attempt won; verify and reuse its complete directory.
    if os.name == "posix" and target.stat().st_mode & 0o077:
        raise ValueError("Certificate directory permissions are too broad")
    certificate = target / "cert.pem"
    key = target / "key.pem"
    credential_file = target / "credential.json"
    if not all(path.is_file() and not path.is_symlink() and
               (os.name != "posix" or not path.stat().st_mode & 0o077)
               for path in (certificate, key, credential_file)):
        raise ValueError("Incomplete or exposed Hysteria 2 credential set")
    credential = json.loads(credential_file.read_text(encoding="utf-8"))
    if not isinstance(credential, dict) or not isinstance(credential.get("password"), str) or not re.fullmatch(
        r"[A-Za-z0-9_-]{32,128}", credential["password"]
    ):
        raise ValueError("Invalid saved Hysteria 2 credential")
    if credential.get("fingerprint") != certificate_fingerprint(openssl, certificate):
        raise ValueError("Hysteria 2 certificate pin does not match its certificate")
    return target, credential


def build(run_dir: Path, openssl: Path, *, use_staged_certificate: bool = False) -> dict:
    inbounds = []
    for index, node in enumerate(selected_nodes(run_dir, "hy2"), start=1):
        directory, credential = material(run_dir, index, node.address, openssl)
        write_json_atomic(export_directory(run_dir) / f"hy2-{index:02}.json", {
            "type": "hy2", "password": credential["password"],
            "fingerprint": credential["fingerprint"],
        })
        root = directory if use_staged_certificate else Path("/etc/ownbridge")
        prefix = "" if use_staged_certificate else f"v2-hy2-{run_dir.name}-{index:02}."
        certificate = root / ("cert.pem" if use_staged_certificate else prefix + "crt")
        key = root / ("key.pem" if use_staged_certificate else prefix + "key")
        inbounds.append({
            "type": "hysteria2", "tag": f"ownbridge-hy2-{index:02}",
            "listen": node.address, "listen_port": node.port,
            "users": [{"password": credential["password"]}],
            "tls": {"enabled": True, "certificate_path": str(certificate), "key_path": str(key)},
        })
    return base_config(inbounds)
