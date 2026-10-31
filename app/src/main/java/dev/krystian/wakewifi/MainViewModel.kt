package dev.krystian.wakewifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.krystian.wakewifi.audio.AlarmEngine
import dev.krystian.wakewifi.audio.AlarmState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isMonitoring: Boolean = false,
    val isWifiConnected: Boolean = false,
    val isAlarmRinging: Boolean = false
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var alarmEngine: AlarmEngine? = null

    init {
        viewModelScope.launch {
            AlarmState.isRinging.collect { ringing ->
                _uiState.update { it.copy(isAlarmRinging = ringing) }
            }
        }
    }

    fun init(context: Context) {
        if (alarmEngine == null) {
            alarmEngine = AlarmEngine(context.applicationContext)
        }
        setupNetworkMonitoring(context.applicationContext)
    }

    private fun setupNetworkMonitoring(context: Context) {
        if (networkCallback != null) return
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Initial check (ignoring mobile data)
        updateWifiState(connectivityManager)

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                updateWifiState(connectivityManager)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                updateWifiState(connectivityManager)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                updateWifiState(connectivityManager)
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateWifiState(connectivityManager: ConnectivityManager) {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        // Explicitly check for Wi-Fi transport and Internet capability, ignoring mobile data
        val isWifi = caps != null &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        _uiState.update { it.copy(isWifiConnected = isWifi) }
    }

    fun setMonitoring(monitoring: Boolean) {
        _uiState.update { it.copy(isMonitoring = monitoring) }
    }

    fun toggleTestAlarm(context: Context) {
        val ringing = _uiState.value.isAlarmRinging
        if (ringing) {
            AlarmEngine.stopAlarm(context)
        } else {
            AlarmEngine.startAlarm(context)
        }
    }

    fun stopAlarm(context: Context) {
        AlarmEngine.stopAlarm(context)
    }

    override fun onCleared() {
        super.onCleared()
        // Do not stop alarm globally if background service is running, but clean up network callback if needed.
    }
}
