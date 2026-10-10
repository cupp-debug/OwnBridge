package app.ownbridge.core

enum class SavedRunPresence { PRESENT, MISSING }

data class SavedRunCheck(val runId: String, val presence: SavedRunPresence)

/** Inspects only a locally generated run ID; it never changes server files. */
object SavedRunProbe {
    private val runIdPattern = Regex("[a-f0-9]{32}")

    fun command(runId: String): String {
        require(runIdPattern.matches(runId))
        return "if test -d /var/lib/ownbridge/runs/$runId; " +
            "then printf 'present\\n'; else printf 'missing\\n'; fi"
    }

    fun parse(output: String): SavedRunPresence? = when (output.trim()) {
        "present" -> SavedRunPresence.PRESENT
        "missing" -> SavedRunPresence.MISSING
        else -> null
    }
}
