package app.ownbridge

import android.content.Context
import android.annotation.SuppressLint
import java.util.Locale

/** Host keys are public data, but the pin must survive app restarts to detect changes. */
internal class HostKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("ssh_host_keys", Context.MODE_PRIVATE)

    fun get(host: String): String? = preferences.getString(key(host), null)

    /** The remembered public host fingerprints, never credentials or private keys. */
    fun savedIdentities(): Map<String, String> = preferences.all.mapNotNull { (key, value) ->
        val fingerprint = value as? String ?: return@mapNotNull null
        if (!key.startsWith("22|")) return@mapNotNull null
        key.removePrefix("22|") to fingerprint
    }.sortedBy { it.first }.toMap()

    @SuppressLint("UseKtx") // KTX edit discards commit's result; a failed pin must block trust.
    fun pin(host: String, fingerprint: String): Boolean =
        preferences.edit().putString(key(host), fingerprint).commit()

    @SuppressLint("UseKtx")
    fun forget(host: String): Boolean = preferences.edit().remove(key(host)).commit()

    /** Replace only the key the user was warned about; never trust an unseen later key. */
    @Synchronized
    @SuppressLint("UseKtx")
    fun replace(host: String, expected: String, observed: String): Boolean {
        if (get(host) != expected) return false
        return preferences.edit().putString(key(host), observed).commit()
    }

    private fun key(host: String): String = "22|${host.trim().lowercase(Locale.ROOT)}"
}
