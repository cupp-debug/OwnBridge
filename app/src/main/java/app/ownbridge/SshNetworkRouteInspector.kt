package app.ownbridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

/** Observes only the app's current default route; never reroutes or bypasses a VPN. */
internal class SshNetworkRouteInspector(context: Context) {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    fun current(): SshNetworkRoute = runCatching {
        val active = connectivity.activeNetwork ?: return SshNetworkRoute.UNKNOWN
        val capabilities = connectivity.getNetworkCapabilities(active) ?: return SshNetworkRoute.UNKNOWN
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            SshNetworkRoute.VPN
        } else {
            SshNetworkRoute.OTHER
        }
    }.getOrDefault(SshNetworkRoute.UNKNOWN)

    /** Delivers default-route changes on the main thread for live warning styling. */
    fun observe(onRoute: (SshNetworkRoute) -> Unit): AutoCloseable {
        var observedNetwork: Network? = null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                observedNetwork = network
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (network != observedNetwork) return
                onRoute(if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    SshNetworkRoute.VPN
                } else SshNetworkRoute.OTHER)
            }

            override fun onLost(network: Network) {
                if (network == observedNetwork) {
                    observedNetwork = null
                    onRoute(SshNetworkRoute.UNKNOWN)
                }
            }
        }
        connectivity.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        return AutoCloseable { connectivity.unregisterNetworkCallback(callback) }
    }
}
