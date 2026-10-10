package app.ownbridge

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import app.ownbridge.core.ShadowsocksPlan

internal data class DeploymentRecord(
    val runId: String,
    val plan: ShadowsocksPlan,
    val bundleVersion: String,
)

internal class DeploymentRecordStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "deployment_record", Context.MODE_PRIVATE,
    )

    fun load(): DeploymentRecord? {
        val id = preferences.getString("run_id", null) ?: return null
        val host = preferences.getString("host", null) ?: return null
        val address = preferences.getString("address", null) ?: return null
        val os = preferences.getString("os", null) ?: return null
        val port = preferences.getInt("port", -1)
        if (!id.matches(Regex("[a-f0-9]{32}")) || port !in 28443..28542) return null
        val version = preferences.getString("bundle_version", DeploymentPackageCatalog.LEGACY_VERSION) ?: return null
        return DeploymentRecord(id, ShadowsocksPlan(host, address, port, os), version)
    }

    @SuppressLint("UseKtx")
    fun save(record: DeploymentRecord): Boolean = preferences.edit().writeActive(record).commit()

    /** Preserve the previous non-secret plan when a reviewed new task takes over. */
    @SuppressLint("UseKtx")
    fun replaceActive(previous: DeploymentRecord, next: DeploymentRecord): Boolean {
        if (previous.runId == next.runId || load() != previous) return false
        val prefix = "archive.${previous.runId}."
        return preferences.edit()
            .putString(prefix + "host", previous.plan.sshHost)
            .putString(prefix + "address", previous.plan.address)
            .putInt(prefix + "port", previous.plan.port)
            .putString(prefix + "os", previous.plan.os)
            .putString(prefix + "bundle_version", previous.bundleVersion)
            .putLong(prefix + "archived_at", System.currentTimeMillis())
            .writeActive(next)
            .commit()
    }

    private fun SharedPreferences.Editor.writeActive(record: DeploymentRecord) = apply {
        putString("run_id", record.runId)
        putString("host", record.plan.sshHost)
        putString("address", record.plan.address)
        putInt("port", record.plan.port)
        putString("os", record.plan.os)
        putString("bundle_version", record.bundleVersion)
    }

    /** Release only the verified active pointer; server state is not touched. */
    @SuppressLint("UseKtx")
    fun release(record: DeploymentRecord): Boolean {
        if (load() != record) return false
        return preferences.edit().remove("run_id").remove("host").remove("address")
            .remove("port").remove("os").remove("bundle_version").commit()
    }
}
