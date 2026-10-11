import json
import os
import pathlib
import secrets
import sys

run_dir = pathlib.Path(sys.argv[1])
address, port_text = (run_dir / "plan.txt").read_text(encoding="ascii").splitlines()
port = int(port_text)
secret_path = run_dir / "secret.txt"
config_path = run_dir / "config.json"

if secret_path.exists():
    secret = secret_path.read_text(encoding="ascii").strip()
else:
    secret = secrets.token_urlsafe(32)
    descriptor = os.open(secret_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="ascii") as output:
        output.write(secret + "\n")

config = {
    "log": {"level": "warn"},
    "inbounds": [{
        "type": "shadowsocks",
        "tag": "ownbridge-ss",
        "listen": address,
        "listen_port": port,
        "method": "aes-256-gcm",
        "password": secret,
    }],
    "outbounds": [{"type": "direct", "tag": "direct"}],
}
temporary = config_path.with_suffix(".json.part")
descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(descriptor, "w", encoding="utf-8") as output:
    json.dump(config, output, separators=(",", ":"))
    output.write("\n")
os.replace(temporary, config_path)
os.chmod(config_path, 0o600)
print("Generated or reused one Shadowsocks credential; config stored with mode 0600")
