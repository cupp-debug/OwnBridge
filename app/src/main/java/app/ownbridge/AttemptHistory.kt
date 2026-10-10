package app.ownbridge

/** Recent attempts remain inspectable in this session, with the newest attempt shown first. */
internal object AttemptHistory {
    private const val MAX_PRIOR_EVENTS = 60

    fun composite(previous: List<CompositeRunEvent>): List<CompositeRunEvent> =
        if (previous.isEmpty()) emptyList() else listOf(CompositeRunEvent(
            CompositeRunAction.PRIOR_ATTEMPT, null, null, null, CompositeRunEventStatus.PASSED,
        )) + retainRecent(previous) { it.action == CompositeRunAction.PRIOR_ATTEMPT }

    fun legacy(previous: List<DeploymentEvent>): List<DeploymentEvent> =
        if (previous.isEmpty()) emptyList() else listOf(DeploymentEvent(
            DeploymentAction.PRIOR_ATTEMPT, null, null, null, null, null, DeploymentEventStatus.PASSED,
        )) + retainRecent(previous) { it.action == DeploymentAction.PRIOR_ATTEMPT }

    private fun <T> retainRecent(previous: List<T>, isMarker: (T) -> Boolean): List<T> {
        val boundary = previous.indexOfFirst(isMarker).takeIf { it >= 0 } ?: previous.size
        val latest = previous.take(boundary).takeLast(MAX_PRIOR_EVENTS)
        return latest + previous.drop(boundary).take(MAX_PRIOR_EVENTS - latest.size)
    }
}
