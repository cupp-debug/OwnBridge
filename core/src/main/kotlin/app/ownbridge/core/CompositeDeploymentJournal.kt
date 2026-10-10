package app.ownbridge.core

enum class SharedStage(val wireName: String) { PREFLIGHT("preflight"), FETCH("fetch") }
enum class ProtocolStage(val wireName: String) {
    CONFIGURE("configure"), ACTIVATE("activate"), VERIFY("verify")
}

sealed interface CompositeStep {
    val wireName: String

    data class Shared(val stage: SharedStage) : CompositeStep {
        override val wireName: String get() = "shared.${stage.wireName}"
    }

    data class Protocol(val protocol: ProxyProtocol, val stage: ProtocolStage) : CompositeStep {
        override val wireName: String get() = "${protocol.wireName}.${stage.wireName}"
    }
}

object CompositeWorkOrder {
    fun forPlan(plan: CompositeDeploymentPlan): List<CompositeStep> =
        SharedStage.entries.map(CompositeStep::Shared) +
            ProxyProtocol.entries.filter { it in plan.protocols }.flatMap { protocol ->
                ProtocolStage.entries.map { stage -> CompositeStep.Protocol(protocol, stage) }
            }
}

enum class ProtocolProgress { PENDING, UNCERTAIN, FAILED, SERVER_READY }
enum class CompositeProgress { PENDING, UNCERTAIN, FAILED, PARTIAL, ALL_SERVER_READY }

data class CompositeDeploymentJournal(val states: Map<CompositeStep, StepState>) {
    /** A failed shared stage may be abandoned without hiding any started protocol work. */
    fun noProtocolWorkStarted(plan: CompositeDeploymentPlan): Boolean =
        plan.protocols.all { protocol ->
            ProtocolStage.entries.all { stage ->
                states.getValue(CompositeStep.Protocol(protocol, stage)) == StepState.PENDING
            }
        }

    fun protocolProgress(protocol: ProxyProtocol): ProtocolProgress {
        val stages = ProtocolStage.entries.map { states.getValue(CompositeStep.Protocol(protocol, it)) }
        return when {
            stages.all { it == StepState.DONE } -> ProtocolProgress.SERVER_READY
            StepState.UNCERTAIN in stages -> ProtocolProgress.UNCERTAIN
            StepState.FAILED in stages -> ProtocolProgress.FAILED
            else -> ProtocolProgress.PENDING
        }
    }

    fun overall(plan: CompositeDeploymentPlan): CompositeProgress {
        val shared = SharedStage.entries.map { states.getValue(CompositeStep.Shared(it)) }
        if (StepState.UNCERTAIN in shared) return CompositeProgress.UNCERTAIN
        if (StepState.FAILED in shared) return CompositeProgress.FAILED
        if (StepState.PENDING in shared) return CompositeProgress.PENDING
        val statuses = plan.protocols.map(::protocolProgress)
        return when {
            statuses.all { it == ProtocolProgress.SERVER_READY } -> CompositeProgress.ALL_SERVER_READY
            ProtocolProgress.UNCERTAIN in statuses -> CompositeProgress.UNCERTAIN
            ProtocolProgress.SERVER_READY in statuses && ProtocolProgress.FAILED in statuses ->
                CompositeProgress.PARTIAL
            ProtocolProgress.FAILED in statuses -> CompositeProgress.FAILED
            else -> CompositeProgress.PENDING
        }
    }
}

object CompositeJournalParser {
    fun parse(output: String, plan: CompositeDeploymentPlan): CompositeDeploymentJournal {
        val expected = CompositeWorkOrder.forPlan(plan)
        val byName = expected.associateBy(CompositeStep::wireName)
        val lines = output.lineSequence().filter(String::isNotBlank).toList()
        require(lines.size == expected.size)
        val states = lines.associate { line ->
            val pair = line.split('=', limit = 2)
            require(pair.size == 2)
            val step = byName[pair[0]] ?: error("Unexpected journal step")
            val state = when (pair[1]) {
                "pending" -> StepState.PENDING
                "uncertain" -> StepState.UNCERTAIN
                "failed" -> StepState.FAILED
                "done" -> StepState.DONE
                else -> error("Unknown journal state")
            }
            step to state
        }
        require(states.keys == expected.toSet())
        return CompositeDeploymentJournal(states)
    }
}
