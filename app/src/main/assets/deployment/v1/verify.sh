#!/bin/sh
set -eu

run_dir=$1
address=$(sed -n '1p' "$run_dir/plan.txt")
port=$(sed -n '2p' "$run_dir/plan.txt")
run_id=${run_dir##*/}
test -f "$run_dir/secret.txt"
test -f "$run_dir/config.json"
test -f "/etc/ownbridge/ss-$run_id.json"
/opt/ownbridge/bin/sing-box-1.14.2 check -c "/etc/ownbridge/ss-$run_id.json"
systemctl is-active --quiet ownbridge-ss.service
ss -H -lnt | grep -Fq "$address:$port"
ss -H -lnu | grep -Fq "$address:$port"
printf '%s\n' 'Config valid, service active and selected address/port listening'
