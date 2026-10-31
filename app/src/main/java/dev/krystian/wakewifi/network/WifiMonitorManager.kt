package dev.krystian.wakewifi.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

class WifiMonitorManager(
    private val context: Context,
    private val onWifiConnected: () -> Unit
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isConnectedToWifi = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun startMonitoring() {
        if (networkCallback != null) {
            Log.d("WakeWifi", "WifiMonitorManager: startMonitoring called but already monitoring")
            return
        }

        // Initial check
        isConnectedToWifi = checkCurrentWifiState()
        Log.d("WakeWifi", "WifiMonitorManager: startMonitoring called. Initial isConnectedToWifi = $isConnectedToWifi")

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val caps = connectivityManager.getNetworkCapabilities(network)
                val hasWifi = caps != null &&
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                Log.d("WakeWifi", "WifiMonitorManager: onAvailable network=$network, hasWifi=$hasWifi, previous isConnectedToWifi=$isConnectedToWifi")
                if (hasWifi) {
                    if (!isConnectedToWifi) {
                        isConnectedToWifi = true
                        Log.d("WakeWifi", "WifiMonitorManager: Wi-Fi transitioned from disconnected/lost to connected. Triggering onWifiConnected()")
                        onWifiConnected()
                    } else {
                        Log.d("WakeWifi", "WifiMonitorManager: onAvailable received but already connected to Wi-Fi")
                    }
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d("WakeWifi", "WifiMonitorManager: onLost network=$network, previous isConnectedToWifi=$isConnectedToWifi")
                isConnectedToWifi = false
                Log.d("WakeWifi", "WifiMonitorManager: Wi-Fi lost. isConnectedToWifi set to false")
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val hasWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                Log.d("WakeWifi", "WifiMonitorManager: onCapabilitiesChanged network=$network, caps=$networkCapabilities, hasWifi=$hasWifi, previous isConnectedToWifi=$isConnectedToWifi")
                if (hasWifi) {
                    if (!isConnectedToWifi) {
                        isConnectedToWifi = true
                        Log.d("WakeWifi", "WifiMonitorManager: Wi-Fi capabilities changed to connected with internet. Triggering onWifiConnected()")
                        onWifiConnected()
                    } else {
                        Log.d("WakeWifi", "WifiMonitorManager: onCapabilitiesChanged indicates connected, but already connected")
                    }
                } else {
                    if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        isConnectedToWifi = false
                        Log.d("WakeWifi", "WifiMonitorManager: Wi-Fi transport lost in capabilities changed. isConnectedToWifi set to false")
                    }
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
            Log.d("WakeWifi", "WifiMonitorManager: NetworkCallback registered successfully")
        } catch (e: Exception) {
            Log.e("WakeWifi", "WifiMonitorManager: Failed to register network callback", e)
            e.printStackTrace()
        }
    }

    private fun checkCurrentWifiState(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isWifi = caps != null &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Log.d("WakeWifi", "WifiMonitorManager: checkCurrentWifiState activeNetwork=$activeNetwork, isWifi=$isWifi")
        return isWifi
    }

    fun stopMonitoring() {
        Log.d("WakeWifi", "WifiMonitorManager: stopMonitoring called")
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
                Log.d("WakeWifi", "WifiMonitorManager: NetworkCallback unregistered successfully")
            } catch (e: Exception) {
                Log.e("WakeWifi", "WifiMonitorManager: Failed to unregister network callback", e)
                e.printStackTrace()
            }
        }
        networkCallback = null
        isConnectedToWifi = false
    }
}
