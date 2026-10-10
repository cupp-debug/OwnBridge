"""Generate and validate only the selected protocol's immutable-run configuration."""

from pathlib import Path
import subprocess
import sys
import tempfile

import hy2
import ss
import vless
from config_common import write_json_atomic


def configure(
    run_dir: Path, protocol: str, binary: Path, openssl: Path, *, probe_reality: bool = True,
) -> None:
    if not binary.is_file():
        raise ValueError("Verified sing-box binary is missing")
    if protocol == "ss":
        staging = production = ss.build(run_dir)
    elif protocol == "vless":
        if probe_reality:
            vless.probe_reality_target()
        staging = production = vless.build(run_dir, binary, openssl)
    elif protocol == "hy2":
        staging = hy2.build(run_dir, openssl, use_staged_certificate=True)
        production = hy2.build(run_dir, openssl)
    else:
        raise ValueError("Protocol is not supported")

    with tempfile.TemporaryDirectory(prefix=".config-check-", dir=run_dir) as directory:
        test_config = Path(directory) / "config.json"
        write_json_atomic(test_config, staging)
        result = subprocess.run(
            [str(binary), "check", "-c", str(test_config)], capture_output=True, text=True,
        )
        if result.returncode != 0:
            raise ValueError(f"sing-box rejected {protocol} configuration")
    write_json_atomic(run_dir / f"config-{protocol}.json", production)
    print(f"Generated and syntax-checked {protocol} configuration for selected addresses")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: configure.py RUN_DIR PROTOCOL")
    run_dir = Path(sys.argv[1])
    binary = run_dir / "binary/sing-box-1.14.2-linux-amd64/sing-box"
    configure(run_dir, sys.argv[2], binary, Path("/usr/bin/openssl"))
