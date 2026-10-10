package app.ownbridge

import java.util.Base64

/** Single-line SharedPreferences storage avoids XML indentation in multiline manifests. */
internal object CompositeManifestPersistence {
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(encoded: String): ByteArray = Base64.getDecoder().decode(encoded)

    /** Older builds stored literal newlines; XML formatting could append spaces after the last one. */
    fun decodeLegacy(value: String): ByteArray =
        value.trimEnd(' ', '\t', '\r').toByteArray(Charsets.US_ASCII)
}
