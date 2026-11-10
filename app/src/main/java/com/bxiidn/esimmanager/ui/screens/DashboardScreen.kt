package com.bxiidn.esimmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxiidn.esimmanager.core.Fmt
import com.bxiidn.esimmanager.data.db.ProfileEntity
import com.bxiidn.esimmanager.data.db.ProfileStatus
import com.bxiidn.esimmanager.data.db.UsageSnapshotEntity
import com.bxiidn.esimmanager.device.CapState
import com.bxiidn.esimmanager.device.DeviceReport
import com.bxiidn.esimmanager.device.OperatingMode
import com.bxiidn.esimmanager.device.SystemSubscriptions
import com.bxiidn.esimmanager.net.NetworkSnapshot
import com.bxiidn.esimmanager.ui.Dest
import com.bxiidn.esimmanager.ui.MainViewModel
import com.bxiidn.esimmanager.ui.UsageState
import com.bxiidn.esimmanager.ui.components.BigValue
import com.bxiidn.esimmanager.ui.components.CardGrid
import com.bxiidn.esimmanager.ui.components.KeyValue
import com.bxiidn.esimmanager.ui.components.Notice
import com.bxiidn.esimmanager.ui.components.Panel
import com.bxiidn.esimmanager.ui.components.ScreenColumn
import com.bxiidn.esimmanager.ui.components.StatePill
import com.bxiidn.esimmanager.ui.components.UsageBarChart
import com.bxiidn.esimmanager.ui.components.UsageMeter
import com.bxiidn.esimmanager.ui.openUsageAccessSettings

@Composable
fun DashboardScreen(vm: MainViewModel, onNavigate: (Dest) -> Unit) {
    val report by vm.report.collectAsStateWithLifecycle()
    val network by vm.network.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val subs by vm.systemSubs.collectAsStateWithLifecycle()
    val snapshots by vm.recentSnapshots.collectAsStateWithLifecycle()
    val busy by vm.busyProfiles.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshAll() }

    ScreenColumn(
        title = "Dashboard",
        subtitle = "Ringkasan eSIM, jaringan, dan pemakaian data pada ${report.deviceName}.",
        actions = {
            IconButton(onClick = { vm.refreshAll() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Muat ulang", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
    ) {
        if (report.mode == OperatingMode.WIFI_MANAGEMENT) {
            Notice(
                text = "Mode pengelola via Wi-Fi: perangkat ini tidak memiliki eUICC aktif, jadi profil eSIM tidak bisa " +
                    "dipasang langsung di sini. Semua fitur pengelolaan tetap tersedia, dan profil dapat dipasang dengan " +
                    "memindai QR-nya dari ponsel yang mendukung eSIM.",
                icon = Icons.Outlined.TabletAndroid,
            )
        }

        val providerProfiles = profiles.filter { it.providerId.isNotBlank() && it.status != ProfileStatus.PENDING }
        val cards: List<@Composable () -> Unit> = listOf(
            { EsimStatusCard(report, subs, onNavigate) },
            { ProfilesCard(profiles, onNavigate) },
            { NetworkCard(report, subs, network, onNavigate) },
            { DeviceUsageCard(usage, onRefresh = { vm.refreshUsage() }) },
            { PlansCard(providerProfiles, busy, onSync = { vm.syncProfile(it) }, onNavigate = onNavigate) },
            { HistoryCard(snapshots) },
        )
        CardGrid(cards = cards)
    }
}

@Composable
private fun EsimStatusCard(report: DeviceReport, subs: SystemSubscriptions, onNavigate: (Dest) -> Unit) {
    Panel(title = "Status eSIM", icon = Icons.Outlined.SimCard) {
        BigValue(
            value = if (report.canInstallEsim) "eSIM didukung" else "eSIM tidak tersedia",
            caption = if (report.canInstallEsim) {
                "eUICC aktif terdeteksi melalui EuiccManager."
            } else {
                "Perangkat ini berjalan sebagai pengelola eSIM."
            },
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatePill(
                if (report.hasEuiccFeature) "eUICC" else "Tanpa eUICC",
                if (report.hasEuiccFeature) CapState.YES else CapState.NO,
            )
            StatePill(
                if (report.hasTelephony) "Modem" else "Tanpa modem",
                if (report.hasTelephony) CapState.YES else CapState.NO,
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        KeyValue("EuiccManager", if (report.euiccEnabled) "Aktif" else "Nonaktif")
        KeyValue(
            "eSIM aktif di sistem",
            when {
                !subs.supported -> "Tidak berlaku"
                !subs.permissionGranted -> "Perlu izin telepon"
                else -> subs.list.count { it.isEmbedded }.toString()
            },
        )
        KeyValue("Status aktivasi", if (report.canInstallEsim) "Lewat sistem Android" else "Lewat perangkat lain (QR)")
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onNavigate(Dest.COMPATIBILITY) }) { Text("Lihat kompatibilitas") }
    }
}

@Composable
private fun ProfilesCard(profiles: List<ProfileEntity>, onNavigate: (Dest) -> Unit) {
    Panel(title = "Profil tersimpan", icon = Icons.Outlined.Inventory2) {
        BigValue(value = profiles.size.toString(), caption = "profil eSIM di aplikasi ini")
        Spacer(Modifier.height(12.dp))
        KeyValue("Tersimpan", profiles.count { it.status == ProfileStatus.SAVED }.toString())
        KeyValue("Terpasang (dikonfirmasi sistem)", profiles.count { it.status == ProfileStatus.INSTALLED }.toString())
        KeyValue("Menunggu alokasi penyedia", profiles.count { it.status == ProfileStatus.PENDING }.toString())
        KeyValue("Dari eSIM Store", profiles.count { it.providerId.isNotBlank() }.toString())
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onNavigate(Dest.PROFILES) }) { Text("Kelola profil") }
            OutlinedButton(onClick = { onNavigate(Dest.STORE) }) { Text("Buka Store") }
        }
    }
}

@Composable
private fun NetworkCard(
    report: DeviceReport,
    subs: SystemSubscriptions,
    network: NetworkSnapshot,
    onNavigate: (Dest) -> Unit,
) {
    Panel(
        title = "Operator & jaringan",
        icon = if (network.isWifi) Icons.Outlined.Wifi else Icons.Outlined.CellTower,
    ) {
        val wifi = network.wifi
        BigValue(
            value = when {
                !network.connected -> "Tidak terhubung"
                wifi != null && wifi.ssidAvailable -> wifi.ssid
                else -> network.transport
            },
            caption = when {
                !network.connected -> "Tidak ada jaringan aktif."
                network.validated -> "Internet tersedia (tervalidasi sistem)."
                network.captivePortal -> "Perlu login portal jaringan."
                else -> "Terhubung, internet belum tervalidasi."
            },
        )
        Spacer(Modifier.height(12.dp))
        val carrier = subs.list.map { it.carrierName }.filter { it.isNotBlank() }.distinct().joinToString(", ")
        KeyValue(
            "Operator seluler",
            when {
                !report.hasTelephony -> "Tidak ada modem seluler"
                carrier.isNotBlank() -> carrier
                report.operatorName.isNotBlank() -> report.operatorName
                else -> "Tidak ada layanan"
            },
        )
        KeyValue("Jenis koneksi", network.transport)
        if (wifi != null) {
            KeyValue("Sinyal Wi-Fi", "${wifi.rssi} dBm · level ${wifi.level}/${wifi.maxLevel}")
            KeyValue("Kecepatan tautan", "${wifi.linkSpeedMbps} Mbps · ${wifi.band}")
        }
        KeyValue("Berbayar per kuota", if (network.metered) "Ya" else "Tidak")
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onNavigate(Dest.CONNECTIVITY) }) { Text("Detail konektivitas") }
    }
}

@Composable
private fun DeviceUsageCard(usage: UsageState, onRefresh: () -> Unit) {
    val context = LocalContext.current
    Panel(
        title = "Penggunaan data perangkat",
        icon = Icons.Outlined.DataUsage,
        trailing = {
            if (usage.loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) {
        if (usage.hasAccess && usage.daily.isNotEmpty()) {
            val total = usage.daily.sumOf { it.totalBytes }
            BigValue(value = Fmt.bytes(total), caption = "7 hari terakhir · hari ini ${Fmt.bytes(usage.daily.last().totalBytes)}")
            Spacer(Modifier.height(16.dp))
            UsageBarChart(usage.daily)
            Spacer(Modifier.height(8.dp))
            KeyValue("Wi-Fi", Fmt.bytes(usage.daily.sumOf { it.wifiBytes }))
            KeyValue("Seluler", Fmt.bytes(usage.daily.sumOf { it.mobileBytes }))
        } else {
            BigValue(value = Fmt.bytes(usage.boot.totalBytes), caption = "sejak perangkat dinyalakan (TrafficStats)")
            Spacer(Modifier.height(12.dp))
            KeyValue("Wi-Fi & lainnya", Fmt.bytes(usage.boot.wifiAndOtherBytes))
            KeyValue("Seluler", Fmt.bytes(usage.boot.mobileBytes))
            Spacer(Modifier.height(10.dp))
            Text(
                "Untuk riwayat per hari, izinkan \"Akses penggunaan\" bagi eSIM Manager di Setelan.",
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
private fun PlansCard(
    profiles: List<ProfileEntity>,
    busy: Set<Long>,
    onSync: (Long) -> Unit,
    onNavigate: (Dest) -> Unit,
) {
    Panel(title = "Paket data dari penyedia", icon = Icons.Outlined.Sync) {
        if (profiles.isEmpty()) {
            Text(
                "Belum ada paket yang dibeli lewat eSIM Store. Pemakaian kuota paket akan tampil di sini setelah disinkronkan dari penyedia.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { onNavigate(Dest.STORE) }) { Text("Lihat katalog") }
        } else {
            profiles.take(4).forEachIndexed { index, profile ->
                if (index > 0) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (profile.lastSyncedAt > 0) "Sinkron ${Fmt.dateTime(profile.lastSyncedAt)}" else "Belum disinkronkan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (profile.id in busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(12.dp))
                    } else {
                        IconButton(onClick = { onSync(profile.id) }) {
                            Icon(Icons.Outlined.Sync, contentDescription = "Sinkronkan", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                UsageMeter(profile.dataUsedBytes, profile.dataTotalBytes, profile.unlimited)
            }
        }
    }
}

@Composable
private fun HistoryCard(snapshots: List<UsageSnapshotEntity>) {
    Panel(title = "Riwayat pemakaian paket", icon = Icons.Outlined.History) {
        if (snapshots.isEmpty()) {
            Text(
                "Riwayat terisi setiap kali pemakaian paket disinkronkan dari API penyedia.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            snapshots.take(8).forEach { snapshot ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            snapshot.profileLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Fmt.dateTime(snapshot.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (snapshot.totalBytes > 0) {
                            "${Fmt.bytes(snapshot.usedBytes)} / ${Fmt.bytes(snapshot.totalBytes)}"
                        } else {
                            Fmt.bytes(snapshot.usedBytes)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
