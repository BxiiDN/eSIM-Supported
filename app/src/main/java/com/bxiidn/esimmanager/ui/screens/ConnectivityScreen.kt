package com.bxiidn.esimmanager.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxiidn.esimmanager.core.Fmt
import com.bxiidn.esimmanager.device.CapState
import com.bxiidn.esimmanager.device.DeviceReport
import com.bxiidn.esimmanager.net.NetworkSnapshot
import com.bxiidn.esimmanager.ui.Dest
import com.bxiidn.esimmanager.ui.MainViewModel
import com.bxiidn.esimmanager.ui.ProbeState
import com.bxiidn.esimmanager.ui.UsageState
import com.bxiidn.esimmanager.ui.components.BigValue
import com.bxiidn.esimmanager.ui.components.CardGrid
import com.bxiidn.esimmanager.ui.components.KeyValue
import com.bxiidn.esimmanager.ui.components.Panel
import com.bxiidn.esimmanager.ui.components.ScreenColumn
import com.bxiidn.esimmanager.ui.components.StatePill
import com.bxiidn.esimmanager.ui.components.UsageBarChart
import com.bxiidn.esimmanager.ui.openUsageAccessSettings
import com.bxiidn.esimmanager.ui.openWifiSettings
import kotlinx.coroutines.delay

@Composable
fun ConnectivityScreen(vm: MainViewModel, onNavigate: (Dest) -> Unit) {
    val network by vm.network.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val probe by vm.probe.collectAsStateWithLifecycle()

    // Sinyal Wi-Fi tidak memicu callback, jadi status disegarkan berkala selama layar terbuka.
    LaunchedEffect(Unit) {
        vm.refreshUsage()
        while (true) {
            vm.refreshNetwork()
            delay(3_000)
        }
    }

    ScreenColumn(
        title = "Konektivitas",
        subtitle = "Status internet, Wi-Fi, alamat jaringan, dan pemakaian data perangkat.",
        actions = {
            IconButton(onClick = { vm.refreshNetwork(); vm.refreshUsage() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Muat ulang", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
    ) {
        val cards: List<@Composable () -> Unit> = listOf(
            { InternetCard(network) },
            { WifiCard(network, onRefresh = { vm.refreshNetwork() }) },
            { ProbeCard(probe, onRun = { vm.runProbe() }) },
            { AddressCard(network) },
            { UsageCard(usage, onRefresh = { vm.refreshUsage() }) },
            { EsimCompatCard(report, onNavigate) },
        )
        CardGrid(cards = cards)
    }
}

@Composable
private fun InternetCard(network: NetworkSnapshot) {
    Panel(title = "Koneksi internet", icon = Icons.Outlined.Public) {
        BigValue(
            value = if (network.connected) network.transport else "Tidak terhubung",
            caption = when {
                !network.connected -> "Tidak ada jaringan aktif."
                network.validated -> "Sistem memvalidasi akses internet."
                network.captivePortal -> "Jaringan meminta login (captive portal)."
                else -> "Terhubung, akses internet belum tervalidasi."
            },
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatePill(
                if (network.validated) "Internet OK" else "Belum tervalidasi",
                if (network.validated) CapState.YES else CapState.PARTIAL,
            )
            if (network.vpn) StatePill("VPN", CapState.INFO)
            if (network.metered) StatePill("Berkuota", CapState.INFO)
        }
        Spacer(Modifier.height(12.dp))
        KeyValue("Jenis koneksi", network.transport)
        KeyValue("Perkiraan unduh", if (network.downKbps > 0) "${network.downKbps / 1000} Mbps" else "—")
        KeyValue("Perkiraan unggah", if (network.upKbps > 0) "${network.upKbps / 1000} Mbps" else "—")
        KeyValue("Dihitung berkuota", if (network.metered) "Ya" else "Tidak")
    }
}

@Composable
private fun WifiCard(network: NetworkSnapshot, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }
    Panel(title = "Wi-Fi", icon = Icons.Outlined.Wifi) {
        val wifi = network.wifi
        if (wifi == null) {
            Text(
                "Perangkat tidak sedang terhubung ke Wi-Fi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { context.openWifiSettings() }) { Text("Buka setelan Wi-Fi") }
        } else {
            BigValue(
                value = if (wifi.ssidAvailable) wifi.ssid else "Wi-Fi terhubung",
                caption = "${wifi.rssi} dBm · level sinyal ${wifi.level} dari ${wifi.maxLevel}",
            )
            Spacer(Modifier.height(12.dp))
            KeyValue("Kecepatan tautan", "${wifi.linkSpeedMbps} Mbps")
            KeyValue("Kirim / terima", "${wifi.txSpeedMbps} / ${wifi.rxSpeedMbps} Mbps")
            KeyValue("Frekuensi", if (wifi.frequencyMhz > 0) "${wifi.frequencyMhz} MHz (${wifi.band})" else "—")
            KeyValue("Standar", wifi.standard)
            if (!wifi.ssidAvailable) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Android hanya memberikan nama jaringan (SSID) bila izin lokasi diberikan dan layanan lokasi aktif.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text("Tampilkan nama jaringan")
                }
            }
        }
    }
}

@Composable
private fun ProbeCard(probe: ProbeState, onRun: () -> Unit) {
    Panel(
        title = "Uji akses internet",
        icon = Icons.Outlined.NetworkCheck,
        trailing = {
            if (probe.running) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) {
        val result = probe.result
        if (result == null) {
            Text(
                "Mengirim permintaan HTTP nyata ke server pemeriksa konektivitas dan mengukur waktu responsnya.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BigValue(
                value = if (result.reachable) "${result.latencyMs} ms" else "Gagal",
                caption = if (result.reachable) {
                    "Respons ${result.detail} dari ${result.endpoint}"
                } else {
                    result.detail
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Diuji ${Fmt.dateTime(result.checkedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onRun, enabled = !probe.running) {
            Text(if (probe.running) "Menguji…" else "Jalankan uji")
        }
    }
}

@Composable
private fun AddressCard(network: NetworkSnapshot) {
    Panel(title = "Informasi jaringan perangkat", icon = Icons.Outlined.Lan) {
        if (!network.connected) {
            Text(
                "Tidak ada jaringan aktif.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            KeyValue("Antarmuka", network.interfaceName, mono = true)
            KeyValue("Alamat IP", network.ipAddresses.joinToString("\n"), mono = true)
            KeyValue("Gateway", network.gateway, mono = true)
            KeyValue("DNS", network.dnsServers.joinToString("\n"), mono = true)
            KeyValue("MTU", if (network.mtu > 0) network.mtu.toString() else "—")
        }
    }
}

@Composable
private fun UsageCard(usage: UsageState, onRefresh: () -> Unit) {
    val context = LocalContext.current
    Panel(title = "Monitoring penggunaan data", icon = Icons.Outlined.DataUsage) {
        if (usage.hasAccess && usage.daily.isNotEmpty()) {
            BigValue(
                value = Fmt.bytes(usage.daily.sumOf { it.totalBytes }),
                caption = "total 7 hari terakhir (NetworkStatsManager)",
            )
            Spacer(Modifier.height(16.dp))
            UsageBarChart(usage.daily)
            Spacer(Modifier.height(8.dp))
            usage.daily.reversed().take(7).forEach { day ->
                KeyValue(day.label, "Wi-Fi ${Fmt.bytes(day.wifiBytes)} · Seluler ${Fmt.bytes(day.mobileBytes)}")
            }
        } else {
            BigValue(value = Fmt.bytes(usage.boot.totalBytes), caption = "sejak perangkat dinyalakan (TrafficStats)")
            Spacer(Modifier.height(12.dp))
            KeyValue("Wi-Fi & lainnya", Fmt.bytes(usage.boot.wifiAndOtherBytes))
            KeyValue("Seluler", Fmt.bytes(usage.boot.mobileBytes))
            Spacer(Modifier.height(10.dp))
            Text(
                "Riwayat harian memerlukan izin \"Akses penggunaan\" yang Anda berikan sendiri di Setelan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { context.openUsageAccessSettings() }) { Text("Beri akses") }
                OutlinedButton(onClick = onRefresh) { Text("Periksa lagi") }
            }
        }
    }
}

@Composable
private fun EsimCompatCard(report: DeviceReport, onNavigate: (Dest) -> Unit) {
    Panel(title = "Pemeriksaan kompatibilitas eSIM", icon = Icons.Outlined.SimCard) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatePill(
                if (report.hasEuiccFeature) "eUICC ada" else "Tanpa eUICC",
                if (report.hasEuiccFeature) CapState.YES else CapState.NO,
            )
            StatePill(
                if (report.hasTelephony) "Modem ada" else "Tanpa modem",
                if (report.hasTelephony) CapState.YES else CapState.NO,
            )
        }
        Spacer(Modifier.height(12.dp))
        KeyValue("EuiccManager", if (report.euiccEnabled) "Aktif" else "Nonaktif")
        KeyValue("Pemasangan eSIM di perangkat ini", if (report.canInstallEsim) "Tersedia bersyarat" else "Tidak tersedia")
        KeyValue("Pengelolaan lewat Wi-Fi", "Tersedia")
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onNavigate(Dest.COMPATIBILITY) }) { Text("Laporan lengkap") }
    }
}
