package app.ownbridge

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import app.ownbridge.core.CompositeDeploymentPlan
import app.ownbridge.core.CompositePlanManifest

/** Non-secret recovery pointer. The VPS journal, not this record, decides what actually ran. */
internal data class CompositeDeploymentRecord(
    val runId: String,
    val plan: CompositeDeploymentPlan,
    val bundleVersion: String,
)

internal sealed interface CompositeStoredRecord {
    data object Missing : CompositeStoredRecord
    data object Corrupt : CompositeStoredRecord
    data class Valid(val record: CompositeDeploymentRecord) : CompositeStoredRecord
}

internal class CompositeDeploymentRecordStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "composite_deployment_record", Context.MODE_PRIVATE,
    )

    fun loadState(): CompositeStoredRecord {
        if (preferences.all.keys.none { it in activeKeys }) return CompositeStoredRecord.Missing
        return runCatching {
            val id = requireNotNull(preferences.getString("run_id", null))
            val host = requireNotNull(preferences.getString("host", null))
            val os = requireNotNull(preferences.getString("os", null))
            val manifest = preferences.getString("manifest_b64", null)?.let(CompositeManifestPersistence::decode)
                ?: CompositeManifestPersistence.decodeLegacy(
                    requireNotNull(preferences.getString("manifest", null)),
                )
            val version = requireNotNull(preferences.getString("bundle_version", null))
            require(Regex("[a-f0-9]{32}").matches(id))
            require(version in CompositeDeploymentPackageCatalog.SUPPORTED_VERSIONS)
            val plan = CompositePlanManifest.parse(host, os, manifest)
            CompositeStoredRecord.Valid(CompositeDeploymentRecord(id, plan, version))
        }.getOrElse { CompositeStoredRecord.Corrupt }
    }

    @SuppressLint("UseKtx")
    fun save(record: CompositeDeploymentRecord): Boolean {
        require(Regex("[a-f0-9]{32}").matches(record.runId))
        require(record.bundleVersion in CompositeDeploymentPackageCatalog.SUPPORTED_VERSIONS)
        val current = loadState()
        if (current is CompositeStoredRecord.Corrupt ||
            current is CompositeStoredRecord.Valid && current.record != record) return false
        return preferences.edit().writeActive(record).commit()
    }

    /** One commit archives the previous pointer and installs the reviewed new task. */
    @SuppressLint("UseKtx")
    fun replaceActive(previous: CompositeDeploymentRecord, next: CompositeDeploymentRecord): Boolean {
        require(Regex("[a-f0-9]{32}").matches(next.runId))
        require(next.bundleVersion in CompositeDeploymentPackageCatalog.SUPPORTED_VERSIONS)
        if (previous.runId == next.runId ||
            (loadState() as? CompositeStoredRecord.Valid)?.record != previous) return false
        return preferences.edit().writeArchive(previous, "replaced")
            .writeActive(next).commit()
    }

    @SuppressLint("UseKtx")
    fun clear(): Boolean = preferences.edit().apply {
        activeKeys.forEach(::remove)
    }.commit()

    /** Atomically releases the active pointer while retaining a non-secret task snapshot. */
    fun archiveCompleted(record: CompositeDeploymentRecord): Boolean = archive(record, "completed")

    /** The authenticated server has no directory for this run; never resume it there. */
    fun archiveMissingRemote(record: CompositeDeploymentRecord): Boolean =
        archive(record, "remote_missing")

    @SuppressLint("UseKtx")
    private fun archive(record: CompositeDeploymentRecord, reason: String): Boolean {
        if ((loadState() as? CompositeStoredRecord.Valid)?.record != record) return false
        return preferences.edit().writeArchive(record, reason).apply {
            activeKeys.forEach(::remove)
        }.commit()
    }

    private fun SharedPreferences.Editor.writeActive(record: CompositeDeploymentRecord) = apply {
        putString("run_id", record.runId)
        putString("host", record.plan.sshHost)
        putString("os", record.plan.os)
        putString("manifest_b64", CompositeManifestPersistence.encode(CompositePlanManifest.bytes(record.plan)))
        remove("manifest")
        putString("bundle_version", record.bundleVersion)
    }

    private fun SharedPreferences.Editor.writeArchive(record: CompositeDeploymentRecord, reason: String) = apply {
        val prefix = "archive.${record.runId}."
        putString(prefix + "host", record.plan.sshHost)
        putString(prefix + "os", record.plan.os)
        putString(prefix + "manifest_b64", CompositeManifestPersistence.encode(
            CompositePlanManifest.bytes(record.plan)))
        putString(prefix + "bundle_version", record.bundleVersion)
        putString(prefix + "reason", reason)
        putLong(prefix + "archived_at", System.currentTimeMillis())
    }

    fun archived(): List<CompositeDeploymentRecord> = preferences.all.keys
        .mapNotNull { Regex("archive\\.([a-f0-9]{32})\\.host").matchEntire(it)?.groupValues?.get(1) }
        .sorted()
        .mapNotNull { id ->
            runCatching {
                val prefix = "archive.$id."
                val host = requireNotNull(preferences.getString(prefix + "host", null))
                val os = requireNotNull(preferences.getString(prefix + "os", null))
                val manifest = CompositeManifestPersistence.decode(
                    requireNotNull(preferences.getString(prefix + "manifest_b64", null)))
                val version = requireNotNull(preferences.getString(prefix + "bundle_version", null))
                CompositeDeploymentRecord(id, CompositePlanManifest.parse(host, os, manifest), version)
            }.getOrNull()
        }

    private val activeKeys = setOf("run_id", "host", "os", "manifest_b64", "manifest", "bundle_version")
}
