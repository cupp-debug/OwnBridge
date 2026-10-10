package app.ownbridge

import java.net.Inet6Address
import java.net.InetAddress

/** UNKNOWN must never be presented as proof that the device lacks IPv6. */
internal enum class Ipv6NetworkAvailability {
    AVAILABLE, UNAVAILABLE, UNKNOWN;

    companion object {
        fun from(hasUsableAddress: Boolean, hasDefaultRoute: Boolean): Ipv6NetworkAvailability =
            if (hasUsableAddress && hasDefaultRoute) AVAILABLE else UNAVAILABLE
    }
}

internal fun isUsableIpv6Source(address: InetAddress): Boolean = address is Inet6Address &&
    !address.isAnyLocalAddress && !address.isLinkLocalAddress &&
    !address.isLoopbackAddress && !address.isMulticastAddress && !address.isSiteLocalAddress
