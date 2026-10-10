#!/bin/sh
set -eu

run_dir=$1
protocol=$2
case "$protocol" in ss|vless|hy2) ;; *) exit 64 ;; esac
run_id=${run_dir##*/}
printf '%s' "$run_id" | grep -Eq '^[a-f0-9]{32}$' || exit 64

package="$run_dir/package"
binary="$run_dir/binary/sing-box-1.14.2-linux-amd64/sing-box"
installed=/opt/ownbridge/bin/sing-box-1.14.2
config="$run_dir/config-$protocol.json"
installed_config="/etc/ownbridge/v2-$protocol-$run_id.json"
unit_name="ownbridge-v2-$protocol-$run_id.service"
unit="/etc/systemd/system/$unit_name"
test -f "$binary" && test -f "$config"
python3 "$package/plan.py" contains "$run_dir/plan.txt" "$protocol" || exit 64

if [ -e "$installed" ] && ! cmp -s "$binary" "$installed"; then
    printf '%s\n' 'Existing OwnBridge binary differs from this verified release' >&2
    exit 73
fi
if [ -e "$unit" ] && ! grep -Fxq "# OwnBridge run: $run_id" "$unit"; then
    printf '%s\n' 'Service unit belongs to another run' >&2
    exit 73
fi
if ! id -u ownbridge >/dev/null 2>&1; then
    useradd --system --home-dir /nonexistent --shell /usr/sbin/nologin ownbridge
fi
install -d -m 0755 /opt/ownbridge/bin
install -m 0755 "$binary" "$installed"
install -d -o root -g ownbridge -m 0750 /etc/ownbridge

if [ "$protocol" = hy2 ]; then
    count=$(python3 "$package/plan.py" count "$run_dir/plan.txt" hy2)
    index=1
    while [ "$index" -le "$count" ]; do
        slot=$(printf '%02d' "$index")
        source="$run_dir/secrets/hy2-$slot"
        test -f "$source/cert.pem" && test -f "$source/key.pem"
        install -o root -g ownbridge -m 0640 "$source/cert.pem" \
            "/etc/ownbridge/v2-hy2-$run_id-$slot.crt"
        install -o root -g ownbridge -m 0640 "$source/key.pem" \
            "/etc/ownbridge/v2-hy2-$run_id-$slot.key"
        index=$((index + 1))
    done
fi

install -o root -g ownbridge -m 0640 "$config" "$installed_config"
"$installed" check -c "$installed_config"
sed -e "s|@RUN_ID@|$run_id|g" -e "s|@PROTOCOL@|$protocol|g" \
    "$package/ownbridge-v2.service" >"$unit.part"
chmod 0644 "$unit.part"
mv -f "$unit.part" "$unit"
systemctl daemon-reload
systemctl enable "$unit_name"
systemctl restart "$unit_name"
systemctl is-active --quiet "$unit_name"

ufw_status=$(ufw status)
if printf '%s\n' "$ufw_status" | grep -q '^Status: active'; then
    node_lines=$(python3 "$package/plan.py" nodes "$run_dir/plan.txt" "$protocol")
    printf '%s\n' "$node_lines" |
        while read -r family address port; do
            case "$protocol" in
                ss)
                    ufw allow to "$address" port "$port" proto tcp comment "ownbridge-$protocol:$run_id"
                    ufw allow to "$address" port "$port" proto udp comment "ownbridge-$protocol:$run_id"
                    ;;
                vless) ufw allow to "$address" port "$port" proto tcp comment "ownbridge-$protocol:$run_id" ;;
                hy2) ufw allow to "$address" port "$port" proto udp comment "ownbridge-$protocol:$run_id" ;;
            esac
        done
fi
printf '%s\n' "Activated $protocol service for selected addresses"
