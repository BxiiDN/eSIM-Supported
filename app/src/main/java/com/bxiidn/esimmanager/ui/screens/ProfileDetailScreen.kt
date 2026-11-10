package com.bxiidn.esimmanager.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxiidn.esimmanager.core.Fmt
import com.bxiidn.esimmanager.data.db.ProfileEntity
import com.bxiidn.esimmanager.data.db.ProfileStatus
import com.bxiidn.esimmanager.device.DeviceReport
import com.bxiidn.esimmanager.lpa.QrCodes
import com.bxiidn.esimmanager.ui.MainViewModel
import com.bxiidn.esimmanager.ui.SecureWindow
import com.bxiidn.esimmanager.ui.components.CardGrid
import com.bxiidn.esimmanager.ui.components.KeyValue
import com.bxiidn.esimmanager.ui.components.Notice
import com.bxiidn.esimmanager.ui.components.Panel
import com.bxiidn.esimmanager.ui.components.Reveal
import com.bxiidn.esimmanager.ui.components.ScreenColumn
import com.bxiidn.esimmanager.ui.components.StatePill
import com.bxiidn.esimmanager.ui.components.UsageMeter
import com.bxiidn.esimmanager.ui.findActivity

@Composable
fun ProfileDetailScreen(vm: MainViewModel, profileId: Long, onBack: () -> Unit) {
    val profileFlow = remember(profileId) { vm.profile(profileId) }
    val historyFlow = remember(profileId) { vm.usageHistory(profileId) }
    val profile by profileFlow.collectAsStateWithLifecycle(initialValue = null)
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val report by vm.report.collectAsStateWithLifecycle()
    val busy by vm.busyProfiles.collectAsStateWithLifecycle()

    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showInstall by remember { mutableStateOf(false) }

    val current = profile
    ScreenColumn(
        title = current?.label ?: "Profil",
        subtitle = if (current == null) {
            "Memuat…"
        } else {
            listOf(current.carrier, Fmt.countryName(current.country)).filter { it.isNotBlank() }.joinToString(" · ")
        },
        actions = {
            OutlinedButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kembali")
            }
        },
    ) {
        if (current == null) {
            Panel {
                Text(
                    "Profil tidak ditemukan atau sudah dihapus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val (statusText, statusState) = statusLabel(current.status)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatePill(statusText, statusState)
                StatePill(sourceLabel(current.source), com.bxiidn.esimmanager.device.CapState.INFO)
            }

            val cards = ArrayList<@Composable () -> Unit>()
            cards.add { DetailCard(current) }
            if (current.status == ProfileStatus.PENDING) {
                cards.add { PendingCard(syncing = current.id in busy, onSync = { vm.syncProfile(current.id) }) }
            } else {
                cards.add { ActivationCard(vm, current) }
                cards.add { InstallCard(report, current, onInstall = { showInstall = true }, vm = vm) }
            }
            if (current.providerId.isNotBlank() && current.status != ProfileStatus.PENDING) {
                cards.add {
                    Panel(
                        title = "Pemakaian data",
                        icon = Icons.Outlined.DataUsage,
                        trailing = {
                            if (current.id in busy) {
                                CircularProgressIndicator(
                                    Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    ) {
                        UsageMeter(current.dataUsedBytes, current.dataTotalBytes, current.unlimited)
                        Spacer(Modifier.height(12.dp))
                        KeyValue("Status di penyedia", current.remoteStatus)
                        KeyValue("Berlaku sampai", current.expiresAt)
                        KeyValue(
                            "Sinkron terakhir",
                            if (current.lastSyncedAt > 0) Fmt.dateTime(current.lastSyncedAt) else "Belum pernah",
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = { vm.syncProfile(current.id) }, enabled = current.id !in busy) {
                            Text("Sinkronkan dari ${providerLabel(current.providerId)}")
                        }
                        if (history.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "RIWAYAT",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            history.take(10).forEach { snapshot ->
                                KeyValue(Fmt.dateTime(snapshot.timestamp), Fmt.bytes(snapshot.usedBytes))
                            }
                        }
                    }
                }
            }
            cards.add {
                Panel(title = "Kelola", icon = Icons.Outlined.Tune) {
                    if (current.notes.isNotBlank()) {
                        Text(current.notes, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { showEdit = true }) { Text("Ubah keterangan") }
                        OutlinedButton(onClick = { showDelete = true }) { Text("Hapus profil") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Menghapus di sini hanya menghapus catatan dari aplikasi ini; paket di penyedia dan eSIM yang sudah " +
                            "terpasang di perangkat lain tidak ikut terhapus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CardGrid(cards = cards, minColumnWidth = 420.dp)
        }
    }

    if (current != null && showEdit) {
        EditProfileDialog(
            profile = current,
            onDismiss = { showEdit = false },
            onSave = { label, carrier, country, notes ->
                showEdit = false
                vm.updateProfile(current.id, label, carrier, country, notes)
            },
        )
    }

    if (current != null && showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Hapus profil ini?") },
            text = {
                Text(
                    "Kode aktivasi \"${current.label}\" akan dihapus dari aplikasi dan tidak dapat dipulihkan. " +
                        "Pastikan Anda masih memiliki QR atau kode dari penyedia bila profil belum dipasang.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        vm.deleteProfile(current.id) { onBack() }
                    },
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Batal") } },
        )
    }

    if (current != null && showInstall) {
        AlertDialog(
            onDismissRequest = { showInstall = false },
            title = { Text("Unduh eSIM ke perangkat ini?") },
            text = {
                Text(
                    "Aplikasi akan meminta sistem Android (EuiccManager) mengunduh profil dari server operator. Android " +
                        "hanya mengizinkannya bila aplikasi berhak atas profil tersebut atau Anda menyetujuinya di layar " +
                        "sistem. Sebagian besar kode aktivasi hanya bisa dipakai satu kali.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showInstall = false
                        vm.installOnDevice(current)
                    },
                ) { Text("Unduh") }
            },
            dismissButton = { TextButton(onClick = { showInstall = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun DetailCard(profile: ProfileEntity) {
    Panel(title = "Detail profil", icon = Icons.Outlined.Info) {
        KeyValue("Nama", profile.label)
        KeyValue("Operator / penyedia", profile.carrier)
        KeyValue("Negara / wilayah", Fmt.countryName(profile.country))
        KeyValue("Alamat SM-DP+", profile.smdpAddress, mono = true)
        KeyValue("ICCID", profile.iccid, mono = true)
        KeyValue("Kode konfirmasi", if (profile.confirmationCodeRequired) "Diperlukan" else "Tidak diperlukan")
        if (profile.providerId.isNotBlank()) {
            KeyValue("Dibeli lewat", providerLabel(profile.providerId))
            KeyValue("Paket", profile.packageTitle)
            KeyValue("Nomor pesanan", profile.orderRef, mono = true)
            if (profile.apn.isNotBlank()) KeyValue("APN", profile.apn, mono = true)
        }
        KeyValue("Ditambahkan", Fmt.dateTime(profile.createdAt))
        KeyValue("Diperbarui", Fmt.dateTime(profile.updatedAt))
    }
}

@Composable
private fun PendingCard(syncing: Boolean, onSync: () -> Unit) {
    Panel(title = "Menunggu penyedia", icon = Icons.Outlined.Download) {
        Text(
            "Pesanan sudah dibuat, tetapi penyedia belum selesai mengalokasikan profil eSIM. Kode aktivasi akan tersimpan " +
                "di sini setelah sinkronisasi berhasil.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onSync, enabled = !syncing) {
            Text(if (syncing) "Menyinkronkan…" else "Sinkronkan sekarang")
        }
    }
}

@Composable
private fun ActivationCard(vm: MainViewModel, profile: ProfileEntity) {
    var revealed by remember(profile.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    SecureWindow(enabled = revealed)

    Panel(title = "Kode aktivasi", icon = Icons.Outlined.Lock) {
        Text(
            "Disimpan terenkripsi (AES-256-GCM, Android Keystore). Tangkapan layar diblokir selama kode ditampilkan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { revealed = !revealed }) {
                Icon(Icons.Outlined.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (revealed) "Sembunyikan" else "Tampilkan QR & kode")
            }
        }
        Reveal(visible = revealed) {
            val code = remember(profile.activationCodeEnc) { vm.revealActivationCode(profile) }
            val confirmation = remember(profile.confirmationCodeEnc) { vm.revealConfirmationCode(profile) }
            val qr = remember(code) { QrCodes.encode(code)?.asImageBitmap() }
            Column(Modifier.padding(top = 16.dp)) {
                if (code.isBlank()) {
                    Notice("Kode aktivasi tidak dapat dibuka. Kunci enkripsi perangkat mungkin telah berubah.")
                } else {
                    if (qr != null) {
                        Box(
                            Modifier
                                .size(260.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(Color.White)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = qr,
                                contentDescription = "QR aktivasi eSIM",
                                filterQuality = FilterQuality.None,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        code,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (confirmation.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        KeyValue("Kode konfirmasi", confirmation, mono = true)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(code))
                            vm.showMessage("Kode aktivasi disalin.")
                        },
                    ) { Text("Salin kode") }
                }
            }
        }
    }
}

@Composable
private fun InstallCard(report: DeviceReport, profile: ProfileEntity, onInstall: () -> Unit, vm: MainViewModel) {
    val context = LocalContext.current
    Panel(title = "Pemasangan", icon = Icons.Outlined.Download) {
        if (report.canInstallEsim) {
            Text(
                if (profile.status == ProfileStatus.INSTALLED) {
                    "Sistem Android telah mengonfirmasi profil ini terunduh ke eUICC perangkat."
                } else {
                    "Perangkat ini memiliki eUICC aktif. Profil dapat diunduh melalui sistem Android."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onInstall) { Text("Unduh ke perangkat ini") }
                OutlinedButton(onClick = { context.findActivity()?.let { vm.openSystemProvisioning(it, profile) } }) {
                    Text("Penyiapan eSIM sistem")
                }
            }
        } else {
            Notice(
                "Perangkat ini tidak memiliki eUICC, sehingga eSIM tidak dapat dipasang di sini. Untuk memakainya, buka " +
                    "\"Tampilkan QR & kode\" lalu pindai QR tersebut dari ponsel yang mendukung eSIM " +
                    "(Setelan › Jaringan › Tambah eSIM).",
            )
        }
    }
}

@Composable
private fun EditProfileDialog(
    profile: ProfileEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var label by remember { mutableStateOf(profile.label) }
    var carrier by remember { mutableStateOf(profile.carrier) }
    var country by remember { mutableStateOf(profile.country) }
    var notes by remember { mutableStateOf(profile.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah keterangan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nama profil") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = carrier,
                    onValueChange = { carrier = it },
                    label = { Text("Operator / penyedia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("Negara / wilayah") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(label, carrier, country, notes) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
