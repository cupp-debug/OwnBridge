#!/bin/sh
set -eu

run_dir=$1
binary="$run_dir/binary/sing-box-1.14.2-linux-amd64/sing-box"
installed=/opt/ownbridge/bin/sing-box-1.14.2
unit=/etc/systemd/system/ownbridge-ss.service
run_id=${run_dir##*/}
test -f "$run_dir/config.json"
test -f "$binary"

if [ -e "$installed" ] && ! cmp -s "$binary" "$installed"; then
    printf '%s\n' 'Existing OwnBridge binary differs from this verified release' >&2
    exit 73
fi
if ! id -u ownbridge >/dev/null 2>&1; then
    useradd --system --home-dir /nonexistent --shell /usr/sbin/nologin ownbridge
fi
install -d -m 0755 /opt/ownbridge/bin
install -m 0755 "$binary" "$installed"
"$installed" check -c "$run_dir/config.json"
install -d -o root -g ownbridge -m 0750 /etc/ownbridge
install -o root -g ownbridge -m 0640 "$run_dir/config.json" "/etc/ownbridge/ss-$run_id.json"

if [ -e "$unit" ] && ! grep -Fq "$run_dir" "$unit"; then
    printf '%s\n' 'Service unit conflict' >&2
    exit 73
fi
sed -e "s|@RUN_DIR@|$run_dir|g" -e "s|@RUN_ID@|$run_id|g" \
    "$run_dir/package/ownbridge-ss.service" >"$unit.part"
chmod 0644 "$unit.part"
mv -f "$unit.part" "$unit"
systemctl daemon-reload
systemctl enable --now ownbridge-ss.service
systemctl is-active --quiet ownbridge-ss.service

address=$(sed -n '1p' "$run_dir/plan.txt")
port=$(sed -n '2p' "$run_dir/plan.txt")
if ufw status | grep -q '^Status: active'; then
    ufw allow to "$address" port "$port" proto tcp comment "ownbridge-ss:$run_id"
    ufw allow to "$address" port "$port" proto udp comment "ownbridge-ss:$run_id"
fi
printf '%s\n' 'Service is active; firewall rule applied if UFW is enabled'
