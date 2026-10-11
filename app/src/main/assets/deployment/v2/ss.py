"""Shadowsocks inbounds for only the selected plan addresses."""

from pathlib import Path
import re
import secrets

from config_common import (
    base_config, export_directory, read_or_create_json, secret_directory, selected_nodes,
    write_json_atomic,
)


def build(run_dir: Path) -> dict:
    directory = secret_directory(run_dir)
    inbounds = []
    for index, node in enumerate(selected_nodes(run_dir, "ss"), start=1):
        credential = read_or_create_json(
            directory / f"ss-{index:02}.json",
            lambda: {"password": secrets.token_urlsafe(32)},
        )
        password = credential.get("password")
        if not isinstance(password, str) or not re.fullmatch(r"[A-Za-z0-9_-]{32,128}", password):
            raise ValueError("Invalid saved Shadowsocks credential")
        write_json_atomic(export_directory(run_dir) / f"ss-{index:02}.json", {
            "type": "ss", "password": password,
        })
        inbounds.append({
            "type": "shadowsocks", "tag": f"ownbridge-ss-{index:02}",
            "listen": node.address, "listen_port": node.port,
            "method": "aes-256-gcm", "password": password,
        })
    return base_config(inbounds)
