"""Small file and plan primitives shared by the v2 protocol modules."""

from collections.abc import Callable
import json
import os
from pathlib import Path
import tempfile

from plan import Node, parse_plan


def selected_nodes(run_dir: Path, protocol: str) -> tuple[Node, ...]:
    nodes = tuple(node for node in parse_plan((run_dir / "plan.txt").read_bytes())
                  if node.protocol == protocol)
    if not nodes:
        raise ValueError(f"Protocol was not selected: {protocol}")
    return nodes


def secret_directory(run_dir: Path) -> Path:
    directory = run_dir / "secrets"
    directory.mkdir(mode=0o700, exist_ok=True)
    if directory.is_symlink() or (os.name == "posix" and directory.stat().st_mode & 0o077):
        raise ValueError("Secret directory permissions are too broad")
    return directory


def export_directory(run_dir: Path) -> Path:
    directory = run_dir / "exports"
    directory.mkdir(mode=0o700, exist_ok=True)
    if directory.is_symlink() or (os.name == "posix" and directory.stat().st_mode & 0o077):
        raise ValueError("Export directory permissions are too broad")
    return directory


def read_or_create_json(path: Path, create: Callable[[], dict]) -> dict:
    if path.is_symlink():
        raise ValueError("Credential path must not be a symlink")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        value = create()
        encoded = (json.dumps(value, separators=(",", ":")) + "\n").encode("utf-8")
        try:
            descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        except FileExistsError:
            value = json.loads(path.read_text(encoding="utf-8"))
        else:
            with os.fdopen(descriptor, "wb") as stream:
                stream.write(encoded)
                stream.flush()
                os.fsync(stream.fileno())
    if not isinstance(value, dict) or path.stat().st_size > 4096 or (
        os.name == "posix" and path.stat().st_mode & 0o077
    ):
        raise ValueError(f"Invalid or exposed credential file: {path.name}")
    return value


def write_json_atomic(path: Path, value: dict) -> None:
    descriptor, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(value, stream, separators=(",", ":"), sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, 0o600)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def base_config(inbounds: list[dict]) -> dict:
    return {
        "log": {"level": "warn"},
        "inbounds": inbounds,
        "outbounds": [{"type": "direct", "tag": "direct"}],
    }
