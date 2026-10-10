package app.ownbridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.ownbridge.core.CompositeProgress

internal enum class ProgressAction {
    HIDDEN, RUNNING, RETRY, ENTER_CREDENTIALS, CHECK_CONNECTION, REVIEW_PLAN, NEXT,
}

/** One action above the log; never infer completion from a non-empty partial export. */
internal object ProgressActionPolicy {
    fun composite(
        state: CompositeDeploymentState,
        credentialsAvailable: Boolean,
        hostKeyTrusted: Boolean,
    ): ProgressAction = when (state) {
        CompositeDeploymentState.Idle -> ProgressAction.HIDDEN
        CompositeDeploymentState.LocalRecordCorrupt -> ProgressAction.REVIEW_PLAN
        is CompositeDeploymentState.Running -> ProgressAction.RUNNING
        is CompositeDeploymentState.NeedsReconciliation ->
            retryAction(credentialsAvailable, hostKeyTrusted)
        is CompositeDeploymentState.Settled -> when {
            state.result.failure == null &&
                state.result.journal?.overall(state.record.plan) == CompositeProgress.ALL_SERVER_READY &&
                state.result.nodes.size == state.record.plan.nodes.size -> ProgressAction.NEXT
            state.result.failure == CompositeRunFailure.IDENTITY -> ProgressAction.CHECK_CONNECTION
            state.result.failure == CompositeRunFailure.AUTHENTICATION -> ProgressAction.ENTER_CREDENTIALS
            state.result.failure == CompositeRunFailure.HASH_MISMATCH -> ProgressAction.REVIEW_PLAN
            else -> retryAction(credentialsAvailable, hostKeyTrusted)
        }
    }

    fun legacy(
        state: DeploymentState,
        credentialsAvailable: Boolean,
        hostKeyTrusted: Boolean,
    ): ProgressAction = when (state) {
        DeploymentState.Idle -> ProgressAction.HIDDEN
        is DeploymentState.Running -> ProgressAction.RUNNING
        is DeploymentState.ServerReady -> ProgressAction.NEXT
        is DeploymentState.Failed -> when (state.reason) {
            DeploymentFailure.IDENTITY -> ProgressAction.CHECK_CONNECTION
            DeploymentFailure.AUTHENTICATION -> ProgressAction.ENTER_CREDENTIALS
            DeploymentFailure.HASH_MISMATCH, DeploymentFailure.STORAGE -> ProgressAction.REVIEW_PLAN
            else -> retryAction(credentialsAvailable, hostKeyTrusted)
        }
    }

    private fun retryAction(credentialsAvailable: Boolean, hostKeyTrusted: Boolean): ProgressAction =
        when {
            !hostKeyTrusted -> ProgressAction.CHECK_CONNECTION
            !credentialsAvailable -> ProgressAction.ENTER_CREDENTIALS
            else -> ProgressAction.RETRY
        }
}

@Composable
internal fun ProgressActionButton(action: ProgressAction, onClick: () -> Unit) {
    if (action == ProgressAction.HIDDEN) return
    val label = when (action) {
        ProgressAction.HIDDEN -> error("Handled above")
        ProgressAction.RUNNING -> R.string.progress_action_running
        ProgressAction.RETRY -> R.string.progress_action_retry
        ProgressAction.ENTER_CREDENTIALS -> R.string.progress_action_enter_password
        ProgressAction.CHECK_CONNECTION -> R.string.progress_action_check_connection
        ProgressAction.REVIEW_PLAN -> R.string.progress_action_review_plan
        ProgressAction.NEXT -> R.string.next
    }
    val icon = when (action) {
        ProgressAction.RETRY -> IconAsset.Retry
        ProgressAction.NEXT -> IconAsset.ArrowForward
        ProgressAction.ENTER_CREDENTIALS -> IconAsset.Key
        ProgressAction.CHECK_CONNECTION, ProgressAction.REVIEW_PLAN -> IconAsset.ArrowBack
        ProgressAction.HIDDEN, ProgressAction.RUNNING -> null
    }
    PrimaryAction(stringResource(label), onClick, enabled = action != ProgressAction.RUNNING,
        icon = icon, loading = action == ProgressAction.RUNNING)
}
