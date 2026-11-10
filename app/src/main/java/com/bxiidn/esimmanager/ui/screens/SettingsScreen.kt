package com.bxiidn.esimmanager.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxiidn.esimmanager.BuildConfig
import com.bxiidn.esimmanager.device.CapState
import com.bxiidn.esimmanager.provider.ProviderId
import com.bxiidn.esimmanager.ui.CredentialTest
import com.bxiidn.esimmanager.ui.MainViewModel
import com.bxiidn.esimmanager.ui.StoreViewModel
import com.bxiidn.esimmanager.ui.components.CardGrid
import com.bxiidn.esimmanager.ui.components.KeyValue
import com.bxiidn.esimmanager.ui.components.Notice
import com.bxiidn.esimmanager.ui.components.Panel
import com.bxiidn.esimmanager.ui.components.ScreenColumn
import com.bxiidn.esimmanager.ui.components.StatePill
import com.bxiidn.esimmanager.ui.hasPermission
import com.bxiidn.esimmanager.ui.openAppSettings
import com.bxiidn.esimmanager.ui.openUrl
import com.bxiidn.esimmanager.ui.openUsageAccessSettings

@Composable
fun SettingsScreen(vm: MainViewModel, storeVm: StoreViewModel) {
    val configured by storeVm.configured.collectAsStateWithLifecycle()
    val test by storeVm.test.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()

    ScreenColumn(
        title = "Pengaturan",
        subtitle = "Kredensial API penyedia, izin aplikasi, dan informasi keamanan.",
    ) {
        Notice(
            "Kredensial API disimpan terenkripsi di perangkat ini (AES-256-GCM dengan kunci Android Keystore) dan hanya " +
                "dikirim ke server penyedia yang bersangkutan.",
        )
        val cards: List<@Composable () -> Unit> = listOf(
            { EsimAccessCard(storeVm, configured.contains(ProviderId.ESIM_ACCESS), test) },
            { AiraloCard(storeVm, configured.contains(ProviderId.AIRALO), test) },
            { PermissionsCard(vm, usage.hasAccess) },
            { AboutCard() },
        )
        CardGrid(cards = cards, minColumnWidth = 420.dp)
    }
}

@Composable
private fun SecretField(value: String, onValueChange: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Sembunyikan" else "Tampilkan",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TestResult(test: CredentialTest?, provider: ProviderId) {
    if (test == null || test.provider != provider) return
    Spacer(Modifier.height(12.dp))
    if (test.running) {
        Text(
            "Menguji koneksi ke ${provider.label}…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Row(verticalAlignment = Alignment.Top) {
            StatePill(if (test.success) "Berhasil" else "Gagal", if (test.success) CapState.YES else CapState.NO)
            Spacer(Modifier.width(10.dp))
            Text(
                test.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun EsimAccessCard(storeVm: StoreViewModel, isConfigured: Boolean, test: CredentialTest?) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var accessCode by remember(version) { mutableStateOf(storeVm.esimAccessCode()) }

    Panel(
        title = "eSIM Access",
        icon = Icons.Outlined.Key,
        trailing = { StatePill(if (isConfigured) "Terhubung" else "Belum diatur", if (isConfigured) CapState.YES else CapState.NO) },
    ) {
        Text(
            "Masukkan Access Code dari konsol eSIM Access (menu Developer). Pembelian memotong saldo akun eSIM Access Anda.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SecretField(accessCode, { accessCode = it }, "Access Code (RT-AccessCode)")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { storeVm.saveEsimAccess(accessCode) }, enabled = accessCode.isNotBlank()) {
                Text("Simpan & uji")
            }
            if (isConfigured) {
                OutlinedButton(
                    onClick = {
                        storeVm.clearCredentials(ProviderId.ESIM_ACCESS)
                        version++
                    },
                ) { Text("Hapus") }
            }
            TextButton(onClick = { context.openUrl("https://esimaccess.com") }) { Text("Situs penyedia") }
        }
        TestResult(test, ProviderId.ESIM_ACCESS)
    }
}

@Composable
private fun AiraloCard(storeVm: StoreViewModel, isConfigured: Boolean, test: CredentialTest?) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val saved = remember(version) { storeVm.airaloCredentials() }
    var clientId by remember(version) { mutableStateOf(saved.clientId) }
    var clientSecret by remember(version) { mutableStateOf(saved.clientSecret) }
    var sandbox by remember(version) { mutableStateOf(saved.sandbox) }

    Panel(
        title = "Airalo Partner API",
        icon = Icons.Outlined.Key,
        trailing = { StatePill(if (isConfigured) "Terhubung" else "Belum diatur", if (isConfigured) CapState.YES else CapState.NO) },
    ) {
        Text(
            "Masukkan Client ID dan Client Secret dari Airalo Partner Platform. Pembelian ditagihkan ke akun mitra Airalo Anda.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SecretField(clientId, { clientId = it }, "Client ID")
        Spacer(Modifier.height(10.dp))
        SecretField(clientSecret, { clientSecret = it }, "Client Secret")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mode sandbox", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Memakai server uji Airalo; pesanan sandbox bukan eSIM sungguhan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = sandbox, onCheckedChange = { sandbox = it })
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { storeVm.saveAiralo(clientId, clientSecret, sandbox) },
                enabled = clientId.isNotBlank() && clientSecret.isNotBlank(),
            ) { Text("Simpan & uji") }
            if (isConfigured) {
                OutlinedButton(
                    onClick = {
                        storeVm.clearCredentials(ProviderId.AIRALO)
                        version++
                    },
                ) { Text("Hapus") }
            }
            TextButton(onClick = { context.openUrl("https://partners.airalo.com") }) { Text("Situs penyedia") }
        }
        TestResult(test, ProviderId.AIRALO)
    }
}

@Composable
private fun PermissionsCard(vm: MainViewModel, hasUsageAccess: Boolean) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick++
        vm.refreshDevice()
    }
    // `tick` dibaca agar status izin dihitung ulang setelah dialog izin ditutup.
    val camera = remember(tick) { context.hasPermission(Manifest.permission.CAMERA) }
    val phone = remember(tick) { context.hasPermission(Manifest.permission.READ_PHONE_STATE) }
    val location = remember(tick) { context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) }

    Panel(title = "Izin aplikasi", icon = Icons.Outlined.AdminPanelSettings) {
        PermissionRow("Kamera", "Memindai QR aktivasi eSIM.", camera) { launcher.launch(Manifest.permission.CAMERA) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PermissionRow("Telepon", "Membaca daftar SIM/eSIM sistem (perangkat bermodem).", phone) {
            launcher.launch(Manifest.permission.READ_PHONE_STATE)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PermissionRow("Lokasi", "Hanya untuk menampilkan nama jaringan Wi-Fi.", location) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PermissionRow("Akses penggunaan", "Statistik data harian perangkat.", hasUsageAccess) {
            context.openUsageAccessSettings()
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { context.openAppSettings() }) { Text("Setelan aplikasi") }
            TextButton(
                onClick = {
                    tick++
                    vm.refreshAll()
                },
            ) { Text("Periksa ulang") }
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onRequest: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            StatePill("Diizinkan", CapState.YES)
        } else {
            OutlinedButton(onClick = onRequest) { Text("Izinkan") }
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    Panel(title = "Tentang", icon = Icons.Outlined.Info) {
        KeyValue("Aplikasi", "eSIM Manager")
        KeyValue("Versi", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        KeyValue("Arsitektur", "ARM64 (arm64-v8a)")
        KeyValue("Penyimpanan profil", "Room + enkripsi Keystore")
        Spacer(Modifier.height(10.dp))
        Text(
            "Aplikasi ini memakai API resmi Android (EuiccManager, ConnectivityManager, NetworkStatsManager) dan API resmi " +
                "penyedia eSIM. Tidak ada aktivasi yang disimulasikan: status \"Terpasang\" hanya muncul setelah sistem " +
                "Android mengonfirmasi unduhan profil.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { context.openUrl("https://github.com/BxiiDN/eSIM-Supported") }) {
            Text("Repositori GitHub")
        }
    }
}
