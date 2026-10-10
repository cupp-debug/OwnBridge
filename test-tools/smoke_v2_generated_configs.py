"""Check actual v2 generators against a verified local sing-box binary."""

import json
from pathlib import Path
import sys
import tempfile


SOURCE_DIR = Path(__file__).resolve().parents[1] / "app/src/main/assets/deployment/v2"
sys.path.insert(0, str(SOURCE_DIR))
from configure import configure


def main(binary: Path, openssl: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="ownbridge-v2-smoke-") as directory:
        run_dir = Path(directory)
        (run_dir / "plan.txt").write_bytes(
            b"ownbridge-plan-v2\n"
            b"ipv4 127.0.0.1 ss 28443\n"
            b"ipv4 127.0.0.1 vless 28444\n"
            b"ipv4 127.0.0.1 hy2 28445\n"
            b"ipv6 ::1 ss 28446\n"
            b"ipv6 ::1 vless 28447\n"
            b"ipv6 ::1 hy2 28448\n"
        )
        for protocol in ("ss", "vless", "hy2"):
            configure(run_dir, protocol, binary, openssl, probe_reality=False)
            config = json.loads((run_dir / f"config-{protocol}.json").read_text())
            assert len(config["inbounds"]) == 2
            assert {inbound["listen"] for inbound in config["inbounds"]} == {"127.0.0.1", "::1"}
            assert all(inbound["type"] == {
                "ss": "shadowsocks", "vless": "vless", "hy2": "hysteria2",
            }[protocol] for inbound in config["inbounds"])


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: smoke_v2_generated_configs.py SING_BOX_BINARY OPENSSL_BINARY")
    main(Path(sys.argv[1]), Path(sys.argv[2]))
