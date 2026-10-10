package app.ownbridge

import app.ownbridge.core.DetectionStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionVpnWarningTest {
    private fun routeEvent(route: SshNetworkRoute) = DetectionEvent(
        step = DetectionStep.NETWORK,
        command = null,
        result = null,
        exitCode = null,
        durationMillis = null,
        status = DetectionStepStatus.PASSED,
        networkRoute = route,
    )

    @Test fun vpnWarningAppearsDuringDetectionAndAfterAnyFailure() {
        val events = listOf(routeEvent(SshNetworkRoute.VPN))
        assertTrue(DetectionUiState.Running(events).showVpnWarning())
        assertTrue(DetectionUiState.Failed(events, DetectionFailure.AUTHENTICATION).showVpnWarning())
        assertTrue(DetectionUiState.Failed(events, DetectionFailure.TIMEOUT).showVpnWarning())
    }

    @Test fun unknownOrNonVpnRoutesDoNotClaimVpnInfluence() {
        assertFalse(DetectionUiState.Running(emptyList()).showVpnWarning())
        assertFalse(DetectionUiState.Failed(
            listOf(routeEvent(SshNetworkRoute.OTHER)), DetectionFailure.NETWORK,
        ).showVpnWarning())
    }
}
