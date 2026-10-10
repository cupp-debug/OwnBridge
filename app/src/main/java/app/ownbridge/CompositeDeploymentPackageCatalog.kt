package app.ownbridge

import android.content.res.AssetManager
import app.ownbridge.core.CompositeStep
import app.ownbridge.core.DeploymentArchive
import app.ownbridge.core.DeploymentBundle
import app.ownbridge.core.ProtocolStage
import app.ownbridge.core.SharedStage

/** A fixed preview bundle. The writable Android path must not select it before traffic gates pass. */
internal class CompositeDeploymentPackageCatalog(private val assets: AssetManager) {
    val version: String = VERSION

    fun archive(): DeploymentArchive = DeploymentBundle(
        FILE_NAMES.associateWith { file ->
            assets.open("deployment/v2/$file").use { it.readBytes() }
        },
    ).archive()

    fun source(step: CompositeStep): String {
        val files = when (step) {
            is CompositeStep.Shared -> when (step.stage) {
                SharedStage.PREFLIGHT -> listOf("preflight.py", "plan.py")
                SharedStage.FETCH -> listOf("fetch.sh")
            }
            is CompositeStep.Protocol -> when (step.stage) {
                ProtocolStage.CONFIGURE -> listOf(
                    "configure.py", "config_common.py", "${step.protocol.wireName}.py",
                )
                ProtocolStage.ACTIVATE -> listOf("activate.sh", "ownbridge-v2.service")
                ProtocolStage.VERIFY -> listOf("verify.py")
            }
        }
        return files.joinToString("\n\n") { file ->
            val code = assets.open("deployment/v2/$file").bufferedReader().use { it.readText() }
            "# $file\n$code"
        }
    }

    companion object {
        const val VERSION = "composite-v2-ubuntu22"
        val SUPPORTED_VERSIONS = setOf(
            "composite-v2-preview", "composite-v2-multi-run",
            "composite-v2-ubuntu24-preview", "composite-v2-ubuntu22-trial", VERSION,
        )
        val FILE_NAMES = listOf(
            "runner.sh", "plan.py", "preflight.py", "fetch.sh", "configure.py",
            "config_common.py", "ss.py", "vless.py", "hy2.py", "activate.sh",
            "verify.py", "ownbridge-v2.service", "manifest.txt",
        )
    }
}
