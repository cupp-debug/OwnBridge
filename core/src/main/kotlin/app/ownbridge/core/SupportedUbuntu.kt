package app.ownbridge.core

/** Ubuntu releases covered by the current deployment bundles. */
object SupportedUbuntu {
    private val prettyName = Regex("^Ubuntu (?:22\\.04|24\\.04|26\\.04)(?:\\.\\d+)*(?: .*)?$")

    fun matches(system: String): Boolean = prettyName.matches(system)
}
