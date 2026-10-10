package app.ownbridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet6Address

/** Describes the app's current default network, without probing or bypassing a VPN. */
internal class Ipv6NetworkInspector(context: Context) {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    fun current(): Ipv6NetworkAvailability = runCatching {
        val active = connectivity.activeNetwork ?: return Ipv6NetworkAvailability.UNKNOWN
        val capabilities = connectivity.getNetworkCapabilities(active)
            ?: return Ipv6NetworkAvailability.UNKNOWN
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return Ipv6NetworkAvailability.UNKNOWN
        }
        val links = connectivity.getLinkProperties(active) ?: return Ipv6NetworkAvailability.UNKNOWN
        Ipv6NetworkAvailability.from(
            hasUsableAddress = links.linkAddresses.any { isUsableIpv6Source(it.address) },
            hasDefaultRoute = links.routes.any { route ->
                route.isDefaultRoute && route.destination.address is Inet6Address
            },
        )
    }.getOrDefault(Ipv6NetworkAvailability.UNKNOWN)
}
