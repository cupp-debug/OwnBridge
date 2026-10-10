package app.ownbridge

import android.content.res.AssetManager
import app.ownbridge.core.DeploymentArchive
import app.ownbridge.core.DeploymentBundle
import app.ownbridge.core.DeploymentStep

internal class DeploymentPackageCatalog(
    private val assets: AssetManager,
    val version: String = CURRENT_VERSION,
) {
    init {
        require(version in supportedVersions)
    }

    fun archive(): DeploymentArchive = DeploymentBundle(
        fileNames.associateWith { name ->
            assets.open(assetPath(name)).use { it.readBytes() }
        },
    ).archive()

    fun source(step: DeploymentStep): String {
        val name = when (step) {
            DeploymentStep.PREFLIGHT -> "preflight.py"
            DeploymentStep.FETCH -> "fetch.sh"
            DeploymentStep.CONFIGURE -> "configure.py"
            DeploymentStep.ACTIVATE -> "activate.sh"
            DeploymentStep.VERIFY -> "verify.sh"
        }
        return assets.open(assetPath(name)).bufferedReader().use { it.readText() }
    }

    private fun assetPath(name: String): String = when (version) {
        LEGACY_VERSION -> "deployment/v1/$name"
        UBUNTU24_VERSION -> if (name == "preflight.py" || name == "manifest.txt") {
            "deployment/v1-ubuntu24/$name"
        } else "deployment/v1/$name"
        CURRENT_VERSION -> if (name == "preflight.py" || name == "manifest.txt") {
            "deployment/v1-ubuntu22/$name"
        } else "deployment/v1/$name"
        else -> error("Unsupported deployment package")
    }

    companion object {
        const val LEGACY_VERSION = "ss-v1"
        const val UBUNTU24_VERSION = "ss-v1-ubuntu24"
        const val CURRENT_VERSION = "ss-v1-ubuntu22"
        private val supportedVersions = setOf(LEGACY_VERSION, UBUNTU24_VERSION, CURRENT_VERSION)
        val fileNames = listOf(
            "runner.sh",
            "preflight.py",
            "fetch.sh",
            "configure.py",
            "activate.sh",
            "verify.sh",
            "ownbridge-ss.service",
            "manifest.txt",
        )
    }
}
