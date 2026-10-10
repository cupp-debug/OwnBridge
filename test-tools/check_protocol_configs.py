"""Local syntax smoke test for candidate sing-box 1.14.2 VLESS/HY2 inbounds.

This creates disposable credentials/certificates in a temporary directory, invokes
`sing-box check`, and never starts listeners or changes a VPS. It is not a protocol
traffic test or a production deployment generator.
"""

import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import uuid


def check(binary: Path, config: dict, directory: Path, name: str) -> None:
    path = directory / f"{name}.json"
    path.write_text(json.dumps(config), encoding="utf-8")
    result = subprocess.run([str(binary), "check", "-c", str(path)], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"{name} configuration rejected: {result.stderr[-1000:]}")
    print(f"{name} configuration accepted by sing-box")


def main(binary: Path, openssl: Path) -> None:
    if not binary.is_file() or not openssl.is_file():
        raise SystemExit("Provide existing sing-box and OpenSSL binary paths")
    with tempfile.TemporaryDirectory(prefix="ownbridge-config-check-") as temp:
        directory = Path(temp)
        generated = subprocess.run(
            [str(binary), "generate", "reality-keypair"], capture_output=True, text=True, check=True,
        ).stdout
        private = re.search(r"^PrivateKey:\s*(\S+)\s*$", generated, re.MULTILINE)
        if not private:
            raise RuntimeError("Unexpected sing-box Reality key output")
        vless = {
            "log": {"level": "warn"},
            "inbounds": [{
                "type": "vless", "tag": "probe-vless", "listen": "127.0.0.1", "listen_port": 28444,
                "users": [{"uuid": str(uuid.uuid4())}],
                "tls": {
                    "enabled": True, "server_name": "www.cloudflare.com",
                    "reality": {
                        "enabled": True,
                        "handshake": {"server": "www.cloudflare.com", "server_port": 443},
                        "private_key": private.group(1), "short_id": ["0123456789abcdef"],
                    },
                },
            }],
            "outbounds": [{"type": "direct", "tag": "direct"}],
        }
        check(binary, vless, directory, "vless")

        cert = directory / "cert.pem"
        key = directory / "key.pem"
        subprocess.run([
            str(openssl), "req", "-x509", "-newkey", "ec", "-pkeyopt",
            "ec_paramgen_curve:P-256", "-noenc", "-days", "1",
            "-subj", "/CN=localhost", "-addext", "subjectAltName=IP:127.0.0.1",
            "-keyout", str(key), "-out", str(cert),
        ], capture_output=True, check=True)
        hy2 = {
            "log": {"level": "warn"},
            "inbounds": [{
                "type": "hysteria2", "tag": "probe-hy2", "listen": "127.0.0.1",
                "listen_port": 28445, "users": [{"password": "disposable-probe-credential"}],
                "tls": {"enabled": True, "certificate_path": str(cert), "key_path": str(key)},
            }],
            "outbounds": [{"type": "direct", "tag": "direct"}],
        }
        check(binary, hy2, directory, "hy2")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: python check_protocol_configs.py SING_BOX_BINARY OPENSSL_BINARY")
    main(Path(sys.argv[1]), Path(sys.argv[2]))
