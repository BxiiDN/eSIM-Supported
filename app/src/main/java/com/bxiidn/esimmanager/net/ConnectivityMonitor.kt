package com.bxiidn.esimmanager.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WifiSnapshot(
    val ssid: String,
    val ssidAvailable: Boolean,
    val rssi: Int,
    val level: Int,
    val maxLevel: Int,
    val linkSpeedMbps: Int,
    val txSpeedMbps: Int,
    val rxSpeedMbps: Int,
    val frequencyMhz: Int,
    val band: String,
    val standard: String,
)

data class NetworkSnapshot(
    val connected: Boolean = false,
    val transport: String = "Tidak terhubung",
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val validated: Boolean = false,
    val captivePortal: Boolean = false,
    val metered: Boolean = false,
    val vpn: Boolean = false,
    val downKbps: Int = 0,
    val upKbps: Int = 0,
    val interfaceName: String = "",
    val ipAddresses: List<String> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val gateway: String = "",
    val mtu: Int = 0,
    val wifi: WifiSnapshot? = null,
)

/** Status jaringan langsung dari ConnectivityManager / WifiManager. */
class ConnectivityMonitor(context: Context) {
    private val context = context.applicationContext
    private val connectivity = this.context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = this.context.getSystemService(WifiManager::class.java)

    private val _snapshot = MutableStateFlow(NetworkSnapshot())
    val snapshot: StateFlow<NetworkSnapshot> = _snapshot.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = refresh()
    }

    init {
        runCatching { connectivity?.registerDefaultNetworkCallback(callback) }
        refresh()
    }

    fun refresh() {
        _snapshot.value = runCatching { read() }.getOrDefault(NetworkSnapshot())
    }

    private fun read(): NetworkSnapshot {
        val cm = connectivity ?: return NetworkSnapshot()
        val network = cm.activeNetwork ?: return NetworkSnapshot()
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkSnapshot()
        val link = cm.getLinkProperties(network)

        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val transport = when {
            isWifi -> "Wi-Fi"
            isCellular -> "Seluler"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> "USB"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Lainnya"
        }

        val gateway = link?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway?.hostAddress.orEmpty()

        return NetworkSnapshot(
            connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            transport = transport,
            isWifi = isWifi,
            isCellular = isCellular,
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            captivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            downKbps = caps.linkDownstreamBandwidthKbps,
            upKbps = caps.linkUpstreamBandwidthKbps,
            interfaceName = link?.interfaceName.orEmpty(),
            ipAddresses = link?.linkAddresses.orEmpty().mapNotNull { it.address?.hostAddress },
            dnsServers = link?.dnsServers.orEmpty().mapNotNull { it.hostAddress },
            gateway = gateway,
            mtu = link?.mtu ?: 0,
            wifi = if (isWifi) readWifi(caps) else null,
        )
    }

    @Suppress("DEPRECATION")
    private fun readWifi(caps: NetworkCapabilities): WifiSnapshot? {
        val fromCaps = caps.transportInfo as? WifiInfo
        // SSID hanya diberikan sistem bila izin lokasi aktif; WifiManager dipakai sebagai sumbernya.
        val legacy = runCatching { wifiManager?.connectionInfo }.getOrNull()
        val info = fromCaps ?: legacy ?: return null

        val hasLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val rawSsid = (legacy?.ssid ?: info.ssid).orEmpty().removeSurrounding("\"")
        val ssidKnown = hasLocation && rawSsid.isNotBlank() && rawSsid != WifiManager.UNKNOWN_SSID

        val rssi = info.rssi
        val maxLevel = runCatching { wifiManager?.maxSignalLevel }.getOrNull() ?: 4
        val level = runCatching { wifiManager?.calculateSignalLevel(rssi) }.getOrNull() ?: 0
        val frequency = info.frequency
        val band = when {
            frequency in 2400..2500 -> "2,4 GHz"
            frequency in 4900..5900 -> "5 GHz"
            frequency in 5925..7125 -> "6 GHz"
            frequency > 0 -> "$frequency MHz"
            else -> "—"
        }
        val standard = when (info.wifiStandard) {
            1 -> "Wi-Fi lama (802.11a/b/g)"
            4 -> "Wi-Fi 4 (802.11n)"
            5 -> "Wi-Fi 5 (802.11ac)"
            6 -> "Wi-Fi 6 (802.11ax)"
            7 -> "WiGig (802.11ad)"
            8 -> "Wi-Fi 7 (802.11be)"
            else -> "Tidak diketahui"
        }
        return WifiSnapshot(
            ssid = if (ssidKnown) rawSsid else "",
            ssidAvailable = ssidKnown,
            rssi = rssi,
            level = level,
            maxLevel = maxLevel,
            linkSpeedMbps = info.linkSpeed.coerceAtLeast(0),
            txSpeedMbps = info.txLinkSpeedMbps.coerceAtLeast(0),
            rxSpeedMbps = info.rxLinkSpeedMbps.coerceAtLeast(0),
            frequencyMhz = frequency,
            band = band,
            standard = standard,
        )
    }
}
