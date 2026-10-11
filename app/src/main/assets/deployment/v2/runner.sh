#!/bin/sh
set -eu

step=${1:-}
run_id=${2:-}
printf '%s' "$run_id" | grep -Eq '^[a-f0-9]{32}$' || exit 64
case "$step" in
    status|shared.preflight|shared.fetch|ss.configure|ss.activate|ss.verify|\
        vless.configure|vless.activate|vless.verify|hy2.configure|hy2.activate|hy2.verify) ;;
    *) exit 64 ;;
esac

run_dir="/var/lib/ownbridge/runs/$run_id"
package="$run_dir/package"
test -f "$package/runner.sh" || exit 66
test -f "$run_dir/plan.txt" || exit 66
test -f "$run_dir/plan.sha256" || exit 66
(cd "$run_dir" && sha256sum -c --status plan.sha256) || exit 65

protocols=$(python3 "$package/plan.py" protocols "$run_dir/plan.txt")
if [ "$step" = status ]; then
    for item in shared.preflight shared.fetch; do
        if [ -f "$run_dir/$item.running" ]; then state=uncertain
        elif [ -f "$run_dir/$item.failed" ]; then state=failed
        elif [ -f "$run_dir/$item.done" ]; then state=done
        else state=pending
        fi
        printf '%s=%s\n' "$item" "$state"
    done
    for protocol in $protocols; do
        for stage in configure activate verify; do
            item="$protocol.$stage"
            if [ -f "$run_dir/$item.running" ]; then state=uncertain
            elif [ -f "$run_dir/$item.failed" ]; then state=failed
            elif [ -f "$run_dir/$item.done" ]; then state=done
            else state=pending
            fi
            printf '%s=%s\n' "$item" "$state"
        done
    done
    exit 0
fi

case "$step" in
    shared.preflight) prerequisite= ;;
    shared.fetch) prerequisite=shared.preflight ;;
    *)
        protocol=${step%%.*}
        python3 "$package/plan.py" contains "$run_dir/plan.txt" "$protocol" || exit 64
        case "${step#*.}" in
            configure) prerequisite=shared.fetch ;;
            activate) prerequisite="$protocol.configure" ;;
            verify) prerequisite="$protocol.activate" ;;
        esac
        ;;
esac
if [ -n "$prerequisite" ]; then test -f "$run_dir/$prerequisite.done" || exit 76; fi

exec 9>"$run_dir/lock"
flock -n 9 || exit 75
rm -f "$run_dir/$step.done" "$run_dir/$step.failed"
printf '%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >"$run_dir/$step.running"

run_step() {
    case "$step" in
        shared.preflight) python3 "$package/preflight.py" "$run_dir" ;;
        shared.fetch) sh "$package/fetch.sh" "$run_dir" ;;
        *.configure) python3 "$package/configure.py" "$run_dir" "$protocol" ;;
        *.activate) sh "$package/activate.sh" "$run_dir" "$protocol" ;;
        *.verify) python3 "$package/verify.py" "$run_dir" "$protocol" ;;
    esac
}

if run_step; then
    mv -f "$run_dir/$step.running" "$run_dir/$step.done"
else
    code=$?
    mv -f "$run_dir/$step.running" "$run_dir/$step.failed"
    exit "$code"
fi
