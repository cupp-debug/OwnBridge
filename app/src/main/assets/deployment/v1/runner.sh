#!/bin/sh
set -eu

step=${1:-}
run_id=${2:-}
printf '%s' "$run_id" | grep -Eq '^[a-f0-9]{32}$' || exit 64
case "$step" in
    preflight|fetch|configure|activate|verify|status) ;;
    *) exit 64 ;;
esac

run_dir="/var/lib/ownbridge/runs/$run_id"
package="$run_dir/package"
test -f "$package/runner.sh" || exit 66

if [ "$step" = status ]; then
    for name in preflight fetch configure activate verify; do
        if [ -f "$run_dir/$name.done" ]; then state=done
        elif [ -f "$run_dir/$name.running" ]; then state=uncertain
        elif [ -f "$run_dir/$name.failed" ]; then state=failed
        else state=pending
        fi
        printf '%s=%s\n' "$name" "$state"
    done
    exit 0
fi

exec 9>"$run_dir/lock"
flock -n 9 || exit 75
printf '%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >"$run_dir/$step.running"
rm -f "$run_dir/$step.failed"

run_step() {
    case "$step" in
        preflight) python3 "$package/preflight.py" "$run_dir" ;;
        fetch) sh "$package/fetch.sh" "$run_dir" ;;
        configure) python3 "$package/configure.py" "$run_dir" ;;
        activate) sh "$package/activate.sh" "$run_dir" ;;
        verify) sh "$package/verify.sh" "$run_dir" ;;
    esac
}

if run_step; then
    mv -f "$run_dir/$step.running" "$run_dir/$step.done"
else
    code=$?
    mv -f "$run_dir/$step.running" "$run_dir/$step.failed"
    exit "$code"
fi
