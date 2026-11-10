package com.bxiidn.esimmanager.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun Context.launch(intent: Intent): Boolean = try {
    if (findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    true
} catch (e: Exception) {
    false
}

/** Membuka Setelan › Akses penggunaan (izin untuk statistik data harian). */
fun Context.openUsageAccessSettings(): Boolean =
    launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) || openAppSettings()

fun Context.openAppSettings(): Boolean =
    launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

fun Context.openWifiSettings(): Boolean = launch(Intent(Settings.ACTION_WIFI_SETTINGS))

fun Context.openUrl(url: String): Boolean = launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

/** Mencegah tangkapan layar selama data sensitif (kode aktivasi/QR) ditampilkan. */
@Composable
fun SecureWindow(enabled: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(enabled, activity) {
        if (enabled) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (enabled) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
