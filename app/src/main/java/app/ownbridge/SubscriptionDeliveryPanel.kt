package app.ownbridge

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** The fourth page shows a URL only after an independent HTTP fetch matches the YAML. */
@Composable
internal fun SubscriptionDeliveryPanel(state: SubscriptionDeliveryState, onRequest: () -> Unit) {
    when (state) {
        SubscriptionDeliveryState.Idle -> PrimaryAction(
            stringResource(R.string.subscription_request), onRequest, icon = IconAsset.Link)
        is SubscriptionDeliveryState.Publishing -> PrimaryAction(
            stringResource(R.string.subscription_publishing), onRequest,
            enabled = false, loading = true)
        is SubscriptionDeliveryState.Ready -> ResultContentCard(
            stringResource(R.string.subscription_link_title), state.url,
            helpTopic = ResultHelpTopic.CLASH_SUBSCRIPTION,
        )
        is SubscriptionDeliveryState.Failed -> {
            val message = when (state.reason) {
                SubscriptionFailure.CREDENTIALS -> R.string.subscription_need_password
                SubscriptionFailure.IDENTITY -> R.string.subscription_identity_failed
                SubscriptionFailure.CONNECTION -> R.string.subscription_connection_failed
                SubscriptionFailure.REMOTE -> R.string.subscription_server_failed
                SubscriptionFailure.RETRIEVAL -> R.string.subscription_retrieval_failed
            }
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error)
            PrimaryAction(stringResource(R.string.subscription_retry), onRequest, icon = IconAsset.Link)
        }
    }
}
