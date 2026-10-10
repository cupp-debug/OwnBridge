package app.ownbridge

/** Save verified evidence before releasing the only active recovery pointer. */
internal suspend fun persistCompletedRun(
    runId: String,
    composite: CompositeDeploymentState.Settled?,
    legacy: DeploymentState.ServerReady?,
    password: String,
    request: SubscriptionRequest?,
    delivery: SubscriptionDeliveryState,
    history: HistoryController,
    compositeDeployment: CompositeDeploymentController,
    deployment: DeploymentController,
): Boolean {
    if ((composite != null) == (legacy != null) ||
        (composite?.record?.runId ?: legacy?.runId) != runId) return false
    if (composite != null) history.captureComposite(composite, password)
    else if (legacy != null) history.captureLegacy(legacy, password)
    request?.takeIf { it.runId == runId }?.let {
        history.captureDelivery(runId, it.sshHost, it.yaml, delivery)
    }
    if (!history.saveCompleted(runId)) return false
    val released = if (composite != null) compositeDeployment.archiveCompleted()
        else deployment.finishCompleted()
    if (released) history.finishRun(runId)
    return released
}
