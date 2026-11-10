package com.bxiidn.esimmanager.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxiidn.esimmanager.device.CapState
import com.bxiidn.esimmanager.device.Capability
import com.bxiidn.esimmanager.device.OperatingMode
import com.bxiidn.esimmanager.ui.MainViewModel
import com.bxiidn.esimmanager.ui.components.BigValue
import com.bxiidn.esimmanager.ui.components.CardGrid
import com.bxiidn.esimmanager.ui.components.Panel
import com.bxiidn.esimmanager.ui.components.ScreenColumn
import com.bxiidn.esimmanager.ui.components.StatePill

@Composable
fun CompatibilityScreen(vm: MainViewModel) {
    val report by vm.report.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshDevice() }

    ScreenColumn(
        title = "Kompatibilitas perangkat",
        subtitle = "Hasil deteksi hardware dan API eSIM Android pada perangkat ini.",
        actions = {
            IconButton(onClick = { vm.refreshDevice() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Periksa ulang", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
    ) {
        Panel(title = "Mode operasi", icon = Icons.Outlined.TabletAndroid) {
            BigValue(
                value = if (report.mode == OperatingMode.FULL_ESIM) "eSIM penuh" else "Pengelola eSIM via Wi-Fi",
                caption = if (report.mode == OperatingMode.FULL_ESIM) {
                    "${report.deviceName} memiliki eUICC aktif; unduh dan hapus profil tersedia melalui EuiccManager."
                } else if (report.isXiaomiPad6) {
                    "Mode kompatibilitas Xiaomi Pad 6: seluruh dashboard dan pengelolaan berjalan lewat Wi-Fi."
                } else {
                    "${report.deviceName} tidak memiliki eUICC aktif; aplikasi berjalan sebagai pengelola lewat Wi-Fi."
                },
            )
            if (report.limitations.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ReportProblem,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Keterbatasan perangkat ini", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                report.limitations.forEach { limitation ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            limitation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        val cards: List<@Composable () -> Unit> = listOf(
            { CapabilityPanel("Hardware", Icons.Outlined.Memory, report.hardware) },
            { CapabilityPanel("eUICC & EuiccManager", Icons.Outlined.SimCard, report.euicc) },
            { CapabilityPanel("Fitur aplikasi di perangkat ini", Icons.Outlined.Checklist, report.features) },
        )
        CardGrid(cards = cards, minColumnWidth = 420.dp)
    }
}

@Composable
private fun CapabilityPanel(title: String, icon: ImageVector, items: List<Capability>) {
    Panel(title = title, icon = icon) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    if (item.state == CapState.INFO) {
                        Text(
                            item.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        StatePill(item.value, item.state)
                    }
                }
                if (item.detail.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
