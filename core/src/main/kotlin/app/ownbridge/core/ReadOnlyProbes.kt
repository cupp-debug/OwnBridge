package app.ownbridge.core

enum class DetectionStep {
    NETWORK,
    HOST_KEY,
    AUTHENTICATION,
    SYSTEM,
    ADDRESSES,
    PORTS,
    SERVICES,
    FIREWALL,
    SAVED_RUN,
}

data class ReadOnlyProbe(val step: DetectionStep, val command: String)

/** Commands are constants: no user input or credentials are interpolated into a shell. */
object ReadOnlyProbes {
    val all = listOf(
        ReadOnlyProbe(DetectionStep.SYSTEM, "cat /etc/os-release"),
        ReadOnlyProbe(DetectionStep.ADDRESSES, "ip -o addr show scope global"),
        ReadOnlyProbe(DetectionStep.PORTS, "ss -H -lntup"),
        ReadOnlyProbe(
            DetectionStep.SERVICES,
            "systemctl list-units --type=service --state=running --plain --no-legend --no-pager",
        ),
        ReadOnlyProbe(
            DetectionStep.FIREWALL,
            "if command -v ufw >/dev/null 2>&1; then ufw status; else printf 'ufw not installed\\n'; fi",
        ),
    )
}
