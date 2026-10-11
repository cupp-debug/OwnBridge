package app.ownbridge

import app.ownbridge.core.DeploymentArchive

/** Recover only the exact public CRLF bundle; the remote helper also checks stage markers. */
internal class CompositeBundleLfRecovery(
    private val transport: CompositeSshTransport,
    private val catalog: CompositeDeploymentPackageCatalog,
) {
    fun required(directory: String): Boolean {
        val response = transport.checked(
            "for name in package/runner.sh package.crlf/runner.sh; do " +
                "if test -f $directory/\$name; then sha256sum $directory/\$name; fi; done",
        ).text
        return response.lineSequence().any { it.substringBefore(' ') == BROKEN_RUNNER_SHA256 }
    }

    fun repair(directory: String, archive: DeploymentArchive): String {
        val staging = "$directory/lf-repair-${archive.sha256}"
        transport.ensureFiles(staging, listOf(
            RemoteFile("repair.py", catalog.lfRecoveryScript()),
            RemoteFile("bundle.zip", archive.bytes),
        ))
        return transport.checked("python3 -I $staging/repair.py $directory ${archive.sha256}").text.trim()
    }

    private companion object {
        const val BROKEN_RUNNER_SHA256 = "1e12a477e89f5cb4fddacf9ffd418444a86d558a4cec85800c593ca01a441588"
    }
}
