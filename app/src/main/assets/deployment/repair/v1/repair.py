"""Repair known CRLF or missing-UFW bundles before any protocol deployment starts."""

import fcntl
import hashlib
from pathlib import Path
import re
import shutil
import sys
import tempfile
import zipfile


LEGACY_PREFLIGHT_SHA256 = {
    "0f6c333a9511ef848e88922646052c9fa24b2bccf33cd205f674f69cf6c94c64",
    "aabada5cce2c71b6862ac41a68820eaeb0799e7e69bee5d1ab0c92a76cee6dcb",
}
LEGACY_REQUIRED = b'"systemctl", "ufw", "flock"'
FIXED_REQUIRED = b'"systemctl", "flock"'
LEGACY_UFW = b"ufw_status=$(ufw status)\n"
FIXED_UFW = b"ufw_status=\nif command -v ufw >/dev/null 2>&1; then\n    ufw_status=$(ufw status)\nfi\n"
FILES = {
    "runner.sh", "plan.py", "preflight.py", "fetch.sh", "configure.py",
    "config_common.py", "ss.py", "vless.py", "hy2.py", "activate.sh",
    "verify.py", "ownbridge-v2.service", "manifest.txt",
}


def digest(path: Path) -> str:
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"Expected a regular file: {path.name}")
    return hashlib.sha256(path.read_bytes()).hexdigest()


def contents(path: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(path) as archive:
        if len(archive.namelist()) != len(FILES) or set(archive.namelist()) != FILES:
            raise ValueError("Unexpected deployment archive contents")
        if sum(item.file_size for item in archive.infolist()) > 128 * 1024:
            raise ValueError("Deployment archive is too large")
        return {name: archive.read(name) for name in FILES}


def matches(directory: Path, files: dict[str, bytes]) -> bool:
    if directory.is_symlink() or not directory.is_dir():
        return False
    entries = list(directory.iterdir())
    if any(item.is_symlink() for item in entries):
        return False
    if {item.name for item in entries if item.is_file()} != FILES:
        return False
    if any(item.is_dir() and item.name != "__pycache__" for item in entries):
        return False
    return all((directory / name).read_bytes() == data for name, data in files.items())


def corrected(name: str, data: bytes) -> bytes:
    data = data.replace(b"\r\n", b"\n")
    if name == "preflight.py":
        data = data.replace(LEGACY_REQUIRED, FIXED_REQUIRED, 1)
    elif name == "activate.sh":
        data = data.replace(LEGACY_UFW, FIXED_UFW, 1)
    return data


def recover(run_dir: Path, fixed_archive: Path, expected: str) -> str:
    if not re.fullmatch(r"[a-f0-9]{32}", run_dir.name) or run_dir.is_symlink():
        raise ValueError("Invalid run directory")
    if not re.fullmatch(r"[a-f0-9]{64}", expected) or digest(fixed_archive) != expected:
        raise ValueError("Corrected archive digest mismatch")
    fixed = contents(fixed_archive)
    if any(b"\r" in data or not data.endswith(b"\n") for data in fixed.values()):
        raise ValueError("Corrected archive must use LF")
    lock_path = run_dir / "lock"
    if lock_path.is_symlink():
        raise ValueError("Invalid run lock")
    with lock_path.open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        archive = run_dir / "bundle.zip"
        backup = run_dir / "bundle.before.zip"
        package = run_dir / "package"
        package_backup = run_dir / "package.before"
        if any(path.is_symlink() for path in (archive, backup, package, package_backup)):
            raise ValueError("Recovery paths must not be symbolic links")
        archive_hash = digest(archive) if archive.exists() else None
        backup_hash = digest(backup) if backup.exists() else None
        if archive_hash not in (None, expected):
            original = contents(archive)
            original_hash = archive_hash
        elif backup_hash is not None:
            original = contents(backup)
            original_hash = backup_hash
        else:
            raise ValueError("Only a known pre-deployment bundle can be repaired")
        if hashlib.sha256(original["preflight.py"]).hexdigest() not in LEGACY_PREFLIGHT_SHA256:
            raise ValueError("Only a known pre-deployment bundle can be repaired")
        if backup_hash not in (None, original_hash):
            raise ValueError("Unrecognized backup archive")
        if any(corrected(name, original[name]) != fixed[name] for name in FILES):
            raise ValueError("Recovery may only change line endings and optional UFW handling")
        if archive_hash == expected and matches(package, fixed):
            return "Deployment package already restored"
        markers = (item for state in ("running", "done", "failed")
                   for item in run_dir.glob(f"*.{state}"))
        if any(item.name != "shared.preflight.failed" for item in markers):
            raise ValueError("Deployment stages already started; recovery refused")
        plan_hash = digest(run_dir / "plan.txt")
        if (run_dir / "plan.sha256").read_bytes() != f"{plan_hash}  plan.txt\n".encode("ascii"):
            raise ValueError("Plan digest mismatch")
        if package_backup.exists() and not matches(package_backup, original):
            raise ValueError("Original package backup differs")
        package_is_original = matches(package, original)
        package_is_fixed = matches(package, fixed)
        if package.exists() and not (package_is_original or package_is_fixed):
            raise ValueError("Deployment package was modified")
        if not package.exists() and not matches(package_backup, original):
            raise ValueError("Original package is missing")
        if package_is_original and package_backup.exists():
            raise ValueError("Both original package paths exist")
        if archive_hash == original_hash and backup.exists():
            raise ValueError("Both original archive paths exist")

        # Build the replacement before moving either active path. Keep both original backups.
        prepared = Path(tempfile.mkdtemp(prefix="package-lf-", dir=fixed_archive.parent))
        try:
            for name, data in fixed.items():
                (prepared / name).write_bytes(data)
            if package_is_original:
                package.rename(package_backup)
            if not package_is_fixed:
                prepared.rename(package)
            if archive_hash == original_hash:
                archive.rename(backup)
            if archive_hash != expected:
                ready = fixed_archive.parent / "bundle-ready.zip"
                shutil.copyfile(fixed_archive, ready)
                ready.replace(archive)
        finally:
            if prepared.exists():
                shutil.rmtree(prepared)
        return "Restored supported deployment package; original files preserved"


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: repair.py RUN_DIRECTORY EXPECTED_SHA256")
    try:
        print(recover(Path(sys.argv[1]), Path(__file__).resolve().parent / "bundle.zip", sys.argv[2]))
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        raise SystemExit(str(error)) from error
