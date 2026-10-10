package app.ownbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ownbridge.core.DeploymentStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeploymentPackageCatalogInstrumentedTest {
    @Test fun newRunsSupportUbuntu2204WithoutChangingEarlierBundles() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val legacy = DeploymentPackageCatalog(assets, DeploymentPackageCatalog.LEGACY_VERSION)
        val ubuntu24 = DeploymentPackageCatalog(assets, DeploymentPackageCatalog.UBUNTU24_VERSION)
        val current = DeploymentPackageCatalog(assets)

        assertFalse(legacy.source(DeploymentStep.PREFLIGHT).contains("24.04"))
        assertFalse(ubuntu24.source(DeploymentStep.PREFLIGHT).contains("22.04"))
        assertTrue(current.source(DeploymentStep.PREFLIGHT).contains("22.04"))
        assertNotEquals(legacy.archive().sha256, current.archive().sha256)
        assertNotEquals(ubuntu24.archive().sha256, current.archive().sha256)
    }
}
