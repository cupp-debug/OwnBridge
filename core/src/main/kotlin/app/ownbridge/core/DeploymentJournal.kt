package app.ownbridge.core

enum class DeploymentStep(val wireName: String) {
    PREFLIGHT("preflight"),
    FETCH("fetch"),
    CONFIGURE("configure"),
    ACTIVATE("activate"),
    VERIFY("verify"),
}

enum class StepState { PENDING, UNCERTAIN, FAILED, DONE }

data class DeploymentJournal(val states: Map<DeploymentStep, StepState>) {
    fun next(): DeploymentStep? = DeploymentStep.entries.firstOrNull { states[it] != StepState.DONE }
}

object DeploymentJournalParser {
    fun parse(output: String): DeploymentJournal {
        val lines = output.lineSequence().filter(String::isNotBlank).toList()
        require(lines.size == DeploymentStep.entries.size)
        val parsed = lines.associate { line ->
            val parts = line.split('=', limit = 2)
            require(parts.size == 2)
            val step = DeploymentStep.entries.single { it.wireName == parts[0] }
            val state = when (parts[1]) {
                "pending" -> StepState.PENDING
                "uncertain" -> StepState.UNCERTAIN
                "failed" -> StepState.FAILED
                "done" -> StepState.DONE
                else -> error("Unknown deployment state")
            }
            step to state
        }
        require(parsed.size == DeploymentStep.entries.size)
        return DeploymentJournal(parsed)
    }
}
