package app.ownbridge

import app.ownbridge.core.DeploymentArchive

/** Recover recognized pre-deployment bundles; the remote helper verifies every changed byte. */
internal class CompositeBundleLfRecovery(
    private val transport: CompositeSshTransport,
    private val catalog: CompositeDeploymentPackageCatalog,
) {
    fun required(directory: String): Boolean {
        val response = transport.checked(
            "for name in package/preflight.py package.before/preflight.py; do " +
                "if test -f $directory/\$name; then sha256sum $directory/\$name; fi; done",
        ).text
        if (response.lineSequence().none { it.substringBefore(' ') in LEGACY_PREFLIGHT_SHA256 }) return false
        val started = transport.checked(
            "for path in $directory/*.running $directory/*.done $directory/*.failed; do " +
                "if test -f \"\$path\" && test \"\${path##*/}\" != shared.preflight.failed; then " +
                "printf started; break; fi; done",
        ).text.trim() == "started"
        return !started
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
        val LEGACY_PREFLIGHT_SHA256 = setOf(
            "0f6c333a9511ef848e88922646052c9fa24b2bccf33cd205f674f69cf6c94c64",
            "aabada5cce2c71b6862ac41a68820eaeb0799e7e69bee5d1ab0c92a76cee6dcb",
        )
    }
}
