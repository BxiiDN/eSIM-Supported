package com.bxiidn.esimmanager.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bxiidn.esimmanager.data.NewProfileInput
import com.bxiidn.esimmanager.data.db.ProfileSource
import com.bxiidn.esimmanager.lpa.LpaActivationCode
import com.bxiidn.esimmanager.lpa.QrCodes
import com.bxiidn.esimmanager.ui.components.KeyValue
import com.bxiidn.esimmanager.ui.components.Notice
import com.bxiidn.esimmanager.ui.hasPermission
import com.bxiidn.esimmanager.ui.openAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Menambah profil: pindai QR, baca QR dari gambar, atau ketik kode aktivasi. */
@Composable
fun AddProfileDialog(
    hasCamera: Boolean,
    canInstallEsim: Boolean,
    onDismiss: () -> Unit,
    onSave: (NewProfileInput) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(if (hasCamera) 0 else 2) }
    var parsed by remember { mutableStateOf<LpaActivationCode?>(null) }
    var source by remember { mutableStateOf(ProfileSource.MANUAL) }
    var error by remember { mutableStateOf<String?>(null) }
    var manualCode by remember { mutableStateOf("") }
    var decodingImage by remember { mutableStateOf(false) }

    var label by remember { mutableStateOf("") }
    var carrier by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var confirmationCode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var cameraGranted by remember { mutableStateOf(context.hasPermission(Manifest.permission.CAMERA)) }
    var cameraDenied by remember { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        cameraDenied = !granted
    }

    fun accept(raw: String, from: String) {
        when (val result = LpaActivationCode.parse(raw)) {
            is LpaActivationCode.ParseResult.Ok -> {
                parsed = result.code
                source = from
                error = null
            }
            is LpaActivationCode.ParseResult.Error -> error = result.message
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            decodingImage = true
            error = null
            scope.launch {
                val text = withContext(Dispatchers.IO) { QrCodes.decodeFromImage(context, uri) }
                decodingImage = false
                if (text == null) {
                    error = "Tidak ditemukan kode QR pada gambar tersebut."
                } else {
                    accept(text, ProfileSource.IMAGE)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(26.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tambah profil eSIM", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (parsed == null) "Pindai QR dari penyedia atau masukkan kode aktivasi." else "Lengkapi keterangan profil.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Tutup", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.height(20.dp))

                val code = parsed
                if (code == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tab == 0,
                            onClick = { tab = 0; error = null },
                            enabled = hasCamera,
                            label = { Text("Pindai QR") },
                            leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                        FilterChip(
                            selected = tab == 1,
                            onClick = { tab = 1; error = null },
                            label = { Text("Dari gambar") },
                            leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                        FilterChip(
                            selected = tab == 2,
                            onClick = { tab = 2; error = null },
                            label = { Text("Manual") },
                            leadingIcon = { Icon(Icons.Outlined.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                    Spacer(Modifier.height(18.dp))

                    when (tab) {
                        0 -> {
                            if (cameraGranted) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(380.dp)
                                        .clip(MaterialTheme.shapes.large)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                ) {
                                    QrScannerView(
                                        modifier = Modifier.matchParentSize(),
                                        onDecoded = { accept(it, ProfileSource.QR) },
                                        onError = { error = it },
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Arahkan kamera ke QR aktivasi eSIM.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.large)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        Icons.Outlined.CameraAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(32.dp),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Izin kamera diperlukan untuk memindai QR.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    Button(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) { Text("Izinkan kamera") }
                                    if (cameraDenied) {
                                        TextButton(onClick = { context.openAppSettings() }) { Text("Buka setelan aplikasi") }
                                    }
                                }
                            }
                        }
                        1 -> {
                            Text(
                                "Pilih tangkapan layar atau foto berisi QR aktivasi (misalnya dari email penyedia).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { imagePicker.launch("image/*") }, enabled = !decodingImage) {
                                Text(if (decodingImage) "Membaca gambar…" else "Pilih gambar")
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = manualCode,
                                onValueChange = { manualCode = it; error = null },
                                label = { Text("Kode aktivasi") },
                                placeholder = { Text("LPA:1\$alamat-smdp\$kode-aktivasi") },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Ascii,
                                ),
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { accept(manualCode, ProfileSource.MANUAL) }, enabled = manualCode.isNotBlank()) {
                                Text("Periksa kode")
                            }
                        }
                    }

                    error?.let {
                        Spacer(Modifier.height(14.dp))
                        Notice(it)
                    }
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            KeyValue("Alamat SM-DP+", code.smdpAddress, mono = true)
                            KeyValue("Matching ID", maskMiddle(code.matchingId), mono = true)
                            KeyValue("Kode konfirmasi", if (code.confirmationCodeRequired) "Diperlukan" else "Tidak diperlukan")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Nama profil") },
                        placeholder = { Text("mis. Jepang 5 GB") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = carrier,
                            onValueChange = { carrier = it },
                            label = { Text("Operator / penyedia") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = country,
                            onValueChange = { country = it },
                            label = { Text("Negara / wilayah") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (code.confirmationCodeRequired) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = confirmationCode,
                            onValueChange = { confirmationCode = it },
                            label = { Text("Kode konfirmasi (dari penyedia)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Catatan") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Notice(
                        if (canInstallEsim) {
                            "Menyimpan profil tidak memasangnya. Pemasangan dilakukan dari halaman detail profil melalui sistem Android."
                        } else {
                            "Profil hanya disimpan (terenkripsi) di aplikasi ini. Perangkat ini tidak dapat memasang eSIM; " +
                                "gunakan QR di halaman detail untuk memasangnya dari ponsel yang mendukung eSIM."
                        },
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { parsed = null; error = null }) { Text("Ganti kode") }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                onSave(
                                    NewProfileInput(
                                        code = code,
                                        label = label,
                                        carrier = carrier,
                                        country = country,
                                        confirmationCode = confirmationCode,
                                        notes = notes,
                                        source = source,
                                    ),
                                )
                            },
                        ) { Text("Simpan profil") }
                    }
                }
            }
        }
    }
}

/** Menyamarkan bagian tengah nilai sensitif untuk ditampilkan. */
fun maskMiddle(value: String): String {
    if (value.isEmpty()) return "—"
    if (value.length <= 6) return "•".repeat(value.length)
    return value.take(3) + "•".repeat((value.length - 6).coerceAtMost(12)) + value.takeLast(3)
}
