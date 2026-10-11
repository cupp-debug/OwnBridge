#!/bin/sh
set -eu

run_dir=$1
version=1.14.2
expected=a684484d7477d1437282ee411f4d131d0340aaad60a7868841ebd5d87dd8a0c6
archive="$run_dir/sing-box-$version-linux-amd64.tar.gz"
url="https://github.com/SagerNet/sing-box/releases/download/v$version/sing-box-$version-linux-amd64.tar.gz"

if ! printf '%s  %s\n' "$expected" "$archive" | sha256sum -c --status >/dev/null 2>&1; then
    curl --fail --location --silent --show-error --retry 2 --connect-timeout 15 --max-time 180 \
        "$url" -o "$archive.part"
    printf '%s  %s\n' "$expected" "$archive.part" | sha256sum -c --status
    mv -f "$archive.part" "$archive"
fi

mkdir -p "$run_dir/binary"
tar -xzf "$archive" -C "$run_dir/binary" --no-same-owner \
    "sing-box-$version-linux-amd64/sing-box"
test -f "$run_dir/binary/sing-box-$version-linux-amd64/sing-box"
printf '%s\n' "Verified sing-box $version archive and extracted its binary"
