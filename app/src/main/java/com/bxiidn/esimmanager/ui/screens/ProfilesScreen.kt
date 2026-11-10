package com.bxiidn.esimmanager.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxiidn.esimmanager.core.Fmt
import com.bxiidn.esimmanager.data.db.ProfileEntity
import com.bxiidn.esimmanager.data.db.ProfileSource
import com.bxiidn.esimmanager.data.db.ProfileStatus
import com.bxiidn.esimmanager.device.CapState
import com.bxiidn.esimmanager.device.SystemSubscription
import com.bxiidn.esimmanager.provider.ProviderId
import com.bxiidn.esimmanager.ui.MainViewModel
import com.bxiidn.esimmanager.ui.components.CardGrid
import com.bxiidn.esimmanager.ui.components.EmptyState
import com.bxiidn.esimmanager.ui.components.Notice
import com.bxiidn.esimmanager.ui.components.Panel
import com.bxiidn.esimmanager.ui.components.ScreenColumn
import com.bxiidn.esimmanager.ui.components.SectionLabel
import com.bxiidn.esimmanager.ui.components.StatePill
import com.bxiidn.esimmanager.ui.findActivity

fun statusLabel(status: String): Pair<String, CapState> = when (status) {
    ProfileStatus.INSTALLED -> "Terpasang" to CapState.YES
    ProfileStatus.PENDING -> "Menunggu alokasi" to CapState.PARTIAL
    else -> "Tersimpan" to CapState.INFO
}

fun sourceLabel(source: String): String = when (source) {
    ProfileSource.QR -> "Pindai QR"
    ProfileSource.IMAGE -> "QR dari gambar"
    ProfileSource.STORE -> "eSIM Store"
    else -> "Input manual"
}

fun providerLabel(providerId: String): String =
    runCatching { ProviderId.valueOf(providerId).label }.getOrDefault("")

@Composable
fun ProfilesScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val report by vm.report.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val subs by vm.systemSubs.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SystemSubscription?>(null) }

    val phonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshDevice()
    }

    LaunchedEffect(Unit) { vm.refreshDevice() }

    ScreenColumn(
        title = "Profil eSIM",
        subtitle = "Simpan, lihat, dan kelola kode aktivasi eSIM Anda.",
        actions = {
            Button(onClick = { showAdd = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tambah profil")
            }
        },
    ) {
        if (!report.canInstallEsim) {
            Notice(
                text = "Perangkat ini tidak dapat memasang eSIM. Profil disimpan terenkripsi di sini dan dapat dipasang " +
                    "dengan memindai QR-nya dari perangkat lain yang mendukung eSIM.",
                icon = Icons.Outlined.TabletAndroid,
            )
        }

        if (profiles.isEmpty()) {
            Panel {
                EmptyState(
                    icon = Icons.Outlined.SimCard,
                    title = "Belum ada profil",
                    message = "Tambahkan profil dengan memindai QR aktivasi, memilih gambar QR, atau mengetik kode aktivasi. " +
                        "Paket yang dibeli lewat eSIM Store juga muncul di sini.",
                    action = { Button(onClick = { showAdd = true }) { Text("Tambah profil") } },
                )
            }
        } else {
            SectionLabel("Tersimpan di aplikasi · ${profiles.size}")
            val cards = ArrayList<@Composable () -> Unit>()
            for (profile in profiles) {
                val card: @Composable () -> Unit = {
                    ProfileCard(profile = profile, onClick = { onOpen(profile.id) })
                }
                cards.add(card)
            }
            CardGrid(cards = cards, minColumnWidth = 340.dp)
        }

        // SIM / eSIM yang benar-benar aktif di sistem (hanya pada perangkat bermodem).
        if (subs.supported) {
            SectionLabel("SIM & eSIM di sistem perangkat")
            Panel {
                when {
                    !subs.permissionGranted -> {
                        Text(
                            "Izin telepon diperlukan untuk membaca daftar SIM/eSIM aktif dari sistem.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { phonePermission.launch(Manifest.permission.READ_PHONE_STATE) }) {
                            Text("Izinkan")
                        }
                    }
                    subs.list.isEmpty() -> Text(
                        "Tidak ada SIM atau eSIM aktif di perangkat ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> subs.list.forEachIndexed { index, sub ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Smartphone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sub.displayName.ifBlank { sub.carrierName.ifBlank { "SIM ${sub.slotIndex + 1}" } },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    listOf(
                                        sub.carrierName,
                                        Fmt.countryName(sub.countryIso),
                                        "slot ${sub.slotIndex + 1}",
                                        sub.mccMnc,
                                    ).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatePill(if (sub.isEmbedded) "eSIM" else "SIM fisik", CapState.INFO)
                            if (sub.isEmbedded && report.canInstallEsim) {
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { deleteTarget = sub }) { Text("Hapus") }
                            }
                        }
                    }
                }
                if (report.canInstallEsim) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { context.findActivity()?.let { vm.openSystemEsimSettings(it) } }) {
                        Text("Buka setelan eSIM sistem")
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddProfileDialog(
            hasCamera = report.hasCamera,
            canInstallEsim = report.canInstallEsim,
            onDismiss = { showAdd = false },
            onSave = { input ->
                showAdd = false
                vm.addProfile(input) { id -> onOpen(id) }
            },
        )
    }

    deleteTarget?.let { sub ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus eSIM dari perangkat?") },
            text = {
                Text(
                    "Profil \"${sub.displayName.ifBlank { sub.carrierName }}\" akan dihapus dari eUICC perangkat ini melalui " +
                        "EuiccManager. Tindakan ini tidak dapat dibatalkan, dan Android dapat menolaknya bila aplikasi tidak " +
                        "memiliki hak operator atas profil tersebut.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        vm.deleteSystemSubscription(sub.subscriptionId)
                    },
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun ProfileCard(profile: ProfileEntity, onClick: () -> Unit) {
    val (statusText, statusState) = statusLabel(profile.status)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Fmt.flag(profile.country.takeIf { it.length == 2 }),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(profile.carrier, Fmt.countryName(profile.country)).filter { it.isNotBlank() }
                            .joinToString(" · ").ifBlank { profile.smdpAddress.ifBlank { "Tanpa keterangan" } },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatePill(statusText, statusState)
                StatePill(sourceLabel(profile.source), CapState.INFO)
            }
            if (profile.providerId.isNotBlank() && profile.status != ProfileStatus.PENDING) {
                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        profile.unlimited -> "Kuota tanpa batas · ${profile.validityDays} hari"
                        profile.dataTotalBytes > 0 ->
                            "${Fmt.bytes(profile.dataUsedBytes)} terpakai dari ${Fmt.bytes(profile.dataTotalBytes)}" +
                                if (profile.validityDays > 0) " · ${profile.validityDays} hari" else ""
                        else -> providerLabel(profile.providerId)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Ditambahkan ${Fmt.date(profile.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
