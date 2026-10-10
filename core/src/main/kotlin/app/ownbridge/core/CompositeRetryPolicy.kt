package app.ownbridge.core

/** Decides bounded work for one attempt; the caller never loops failed steps indefinitely. */
object CompositeRetryPolicy {
    // Shared checks are safe and idempotent; rerunning them catches a replaced or damaged binary.
    val shared: List<CompositeStep.Shared> = SharedStage.entries.map(CompositeStep::Shared)

    fun protocolSteps(journal: CompositeDeploymentJournal, protocol: ProxyProtocol): List<CompositeStep.Protocol> {
        fun state(stage: ProtocolStage) = journal.states.getValue(CompositeStep.Protocol(protocol, stage))
        val needsConfiguration = state(ProtocolStage.CONFIGURE) != StepState.DONE
        val needsActivation = needsConfiguration || state(ProtocolStage.ACTIVATE) != StepState.DONE ||
            state(ProtocolStage.VERIFY) in setOf(StepState.FAILED, StepState.UNCERTAIN)
        return buildList {
            if (needsConfiguration) add(CompositeStep.Protocol(protocol, ProtocolStage.CONFIGURE))
            if (needsActivation) add(CompositeStep.Protocol(protocol, ProtocolStage.ACTIVATE))
            // Even a done marker cannot replace a fresh service/listener check.
            add(CompositeStep.Protocol(protocol, ProtocolStage.VERIFY))
        }
    }
}
