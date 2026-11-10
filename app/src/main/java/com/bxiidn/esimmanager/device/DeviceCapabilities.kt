package com.bxiidn.esimmanager.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.euicc.EuiccManager
import android.view.Display
import java.util.Locale

enum class CapState { YES, NO, PARTIAL, INFO }

data class Capability(
    val title: String,
    val value: String,
    val state: CapState,
    val detail: String = "",
)

enum class OperatingMode {
    /** Perangkat punya eUICC aktif: unduh/hapus profil lewat EuiccManager tersedia. */
    FULL_ESIM,

    /** Tanpa eUICC: aplikasi berjalan sebagai pengelola lewat Wi-Fi. */
    WIFI_MANAGEMENT,
}

data class DeviceReport(
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val codename: String,
    val soc: String,
    val abis: String,
    val androidRelease: String,
    val sdkInt: Int,
    val isXiaomiPad6: Boolean,
    val hasTelephony: Boolean,
    val hasEuiccFeature: Boolean,
    val euiccEnabled: Boolean,
    val canInstallEsim: Boolean,
    val hasCamera: Boolean,
    val display: String,
    val refreshRates: String,
    val operatorName: String,
    val mode: OperatingMode,
    val hardware: List<Capability>,
    val euicc: List<Capability>,
    val features: List<Capability>,
    val limitations: List<String>,
)

data class SystemSubscription(
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val countryIso: String,
    val slotIndex: Int,
    val isEmbedded: Boolean,
    val mccMnc: String,
)

data class SystemSubscriptions(
    val supported: Boolean,
    val permissionGranted: Boolean,
    val list: List<SystemSubscription>,
)

/** Deteksi kemampuan hardware dan API eSIM dengan API resmi Android. */
class DeviceCapabilities(context: Context) {
    private val context = context.applicationContext

    fun build(): DeviceReport {
        val pm = context.packageManager
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val euiccManager = context.getSystemService(EuiccManager::class.java)

        val hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val hasTelephonyData = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA)
        val hasSubscription = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)
        val hasRadio = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)
        val hasEuiccFeature = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC)
        val hasMep = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC_MEP)
        val hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val hasWifi = pm.hasSystemFeature(PackageManager.FEATURE_WIFI)

        val euiccEnabled = runCatching { euiccManager?.isEnabled == true }.getOrDefault(false)
        val canInstall = hasEuiccFeature && euiccEnabled
        val euiccOs = if (canInstall) runCatching { euiccManager?.euiccInfo?.osVersion }.getOrNull() else null
        var eidText = "—"
        var eidRestricted = false
        if (canInstall) {
            try {
                eidText = euiccManager?.eid?.takeIf { it.isNotBlank() } ?: "Tidak tersedia"
            } catch (e: Exception) {
                eidText = "Dibatasi sistem"
                eidRestricted = true
            }
        }

        val phoneType = runCatching { telephony?.phoneType }.getOrNull()
        val phoneTypeLabel = when (phoneType) {
            TelephonyManager.PHONE_TYPE_GSM -> "GSM / LTE / NR"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP"
            TelephonyManager.PHONE_TYPE_NONE -> "Tidak ada radio seluler"
            else -> "Tidak diketahui"
        }
        val activeModems = runCatching { telephony?.activeModemCount }.getOrNull() ?: 0
        val supportedModems = runCatching { telephony?.supportedModemCount }.getOrNull() ?: 0
        val simState = runCatching { telephony?.simState }.getOrNull()
        val simStateLabel = when (simState) {
            TelephonyManager.SIM_STATE_READY -> "Siap"
            TelephonyManager.SIM_STATE_ABSENT -> "Tidak ada kartu"
            TelephonyManager.SIM_STATE_PIN_REQUIRED, TelephonyManager.SIM_STATE_PUK_REQUIRED -> "Terkunci"
            TelephonyManager.SIM_STATE_NOT_READY -> "Belum siap"
            null -> "Tidak tersedia"
            else -> "Tidak diketahui"
        }
        val operatorName = runCatching { telephony?.networkOperatorName }.getOrNull().orEmpty()

        val hasModem = hasTelephony && phoneType != null && phoneType != TelephonyManager.PHONE_TYPE_NONE

        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val codename = Build.DEVICE.orEmpty()
        val isPad6 = codename.equals("pipa", ignoreCase = true) ||
            model.startsWith("23043RP34", ignoreCase = true) ||
            (manufacturer.equals("Xiaomi", ignoreCase = true) && model.replace(" ", "").contains("Pad6", ignoreCase = true) &&
                !model.contains("Pro", ignoreCase = true) && !model.contains("Max", ignoreCase = true))
        val deviceName = if (isPad6) "Xiaomi Pad 6" else "$manufacturer $model".trim()

        val soc = listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
            .filter { !it.isNullOrBlank() && it != Build.UNKNOWN }
            .joinToString(" ")
            .ifBlank { Build.HARDWARE.orEmpty() }
        val abis = Build.SUPPORTED_ABIS.joinToString(", ")

        val displayManager = context.getSystemService(DisplayManager::class.java)
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        val displaySummary = if (mode != null) {
            val w = maxOf(mode.physicalWidth, mode.physicalHeight)
            val h = minOf(mode.physicalWidth, mode.physicalHeight)
            "$w × $h px"
        } else {
            "Tidak diketahui"
        }
        val rates = display?.supportedModes
            ?.map { it.refreshRate }
            ?.map { Math.round(it) }
            ?.distinct()
            ?.sorted()
            .orEmpty()
        val refreshRates = if (rates.isEmpty()) "Tidak diketahui" else rates.joinToString(" / ") { "$it Hz" }
        val currentRate = display?.refreshRate?.let { Math.round(it) }

        val operatingMode = if (canInstall) OperatingMode.FULL_ESIM else OperatingMode.WIFI_MANAGEMENT

        val hardware = listOf(
            Capability("Perangkat", deviceName, CapState.INFO, "$manufacturer · $model · kode \"$codename\""),
            Capability("Chipset", soc.ifBlank { "Tidak diketahui" }, CapState.INFO, "ABI: $abis"),
            Capability("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", CapState.INFO),
            Capability(
                "Layar",
                displaySummary,
                CapState.INFO,
                "Refresh rate didukung: $refreshRates" + (currentRate?.let { " · aktif: $it Hz" } ?: ""),
            ),
            Capability(
                "Arsitektur ARM64",
                if (Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")) "Didukung" else "Tidak didukung",
                if (Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")) CapState.YES else CapState.NO,
            ),
            Capability(
                "Modem seluler",
                if (hasModem) phoneTypeLabel else "Tidak ada",
                if (hasModem) CapState.YES else CapState.NO,
                if (hasModem) {
                    "Modem aktif: $activeModems dari $supportedModems · status SIM: $simStateLabel"
                } else {
                    "FEATURE_TELEPHONY = $hasTelephony · tipe radio: $phoneTypeLabel"
                },
            ),
            Capability(
                "Data seluler",
                if (hasTelephonyData) "Didukung" else "Tidak didukung",
                if (hasTelephonyData) CapState.YES else CapState.NO,
                "FEATURE_TELEPHONY_DATA · akses radio: ${if (hasRadio) "ya" else "tidak"} · langganan: ${if (hasSubscription) "ya" else "tidak"}",
            ),
            Capability(
                "Wi-Fi",
                if (hasWifi) "Didukung" else "Tidak didukung",
                if (hasWifi) CapState.YES else CapState.NO,
            ),
            Capability(
                "Kamera",
                if (hasCamera) "Didukung" else "Tidak ada",
                if (hasCamera) CapState.YES else CapState.NO,
                "Dipakai untuk memindai QR aktivasi eSIM.",
            ),
        )

        val euicc = listOf(
            Capability(
                "Chip eUICC (eSIM)",
                if (hasEuiccFeature) "Terdeteksi" else "Tidak ada",
                if (hasEuiccFeature) CapState.YES else CapState.NO,
                "PackageManager.FEATURE_TELEPHONY_EUICC = $hasEuiccFeature",
            ),
            Capability(
                "EuiccManager",
                when {
                    euiccManager == null -> "Layanan tidak tersedia"
                    euiccEnabled -> "Aktif"
                    else -> "Nonaktif"
                },
                if (euiccEnabled) CapState.YES else CapState.NO,
                "EuiccManager.isEnabled() = $euiccEnabled",
            ),
            Capability(
                "Multi profil aktif (MEP)",
                if (hasMep) "Didukung" else "Tidak didukung",
                if (hasMep) CapState.YES else CapState.NO,
                "FEATURE_TELEPHONY_EUICC_MEP",
            ),
            Capability(
                "Versi OS eUICC",
                euiccOs?.takeIf { it.isNotBlank() } ?: "—",
                CapState.INFO,
            ),
            Capability(
                "EID",
                eidText,
                CapState.INFO,
                if (eidRestricted) {
                    "Membaca EID memerlukan izin sistem (READ_PRIVILEGED_PHONE_STATE) atau hak operator."
                } else {
                    ""
                },
            ),
        )

        val installState = if (canInstall) CapState.PARTIAL else CapState.NO
        val installDetail = if (canInstall) {
            "Memanggil EuiccManager secara nyata. Android hanya mengizinkannya bila aplikasi punya hak operator " +
                "atas profil tersebut atau pengguna menyetujui dialog sistem; bila ditolak, aplikasi membuka " +
                "penyiapan eSIM bawaan Android."
        } else {
            "Perangkat ini tidak memiliki eUICC aktif, jadi fitur ini dimatikan."
        }
        val features = listOf(
            Capability("Dashboard & pemantauan jaringan", "Tersedia", CapState.YES),
            Capability(
                "Pindai QR aktivasi (kamera)",
                if (hasCamera) "Tersedia" else "Tidak tersedia",
                if (hasCamera) CapState.YES else CapState.NO,
            ),
            Capability("Baca QR dari gambar & input manual", "Tersedia", CapState.YES),
            Capability("Simpan profil terenkripsi", "Tersedia", CapState.YES, "AES-256-GCM, kunci di Android Keystore."),
            Capability(
                "Katalog & pembelian paket",
                "Tersedia",
                CapState.YES,
                "Melalui API resmi penyedia; memerlukan kredensial API milik Anda.",
            ),
            Capability(
                "Tampilkan QR untuk perangkat lain",
                "Tersedia",
                CapState.YES,
                "Profil dapat dipasang dengan memindai QR dari ponsel yang mendukung eSIM.",
            ),
            Capability(
                "Unduh eSIM ke perangkat ini",
                if (canInstall) "Tersedia bersyarat" else "Tidak tersedia",
                installState,
                installDetail,
            ),
            Capability(
                "Hapus eSIM dari perangkat ini",
                if (canInstall) "Tersedia bersyarat" else "Tidak tersedia",
                installState,
                if (canInstall) "EuiccManager.deleteSubscription — tunduk pada aturan hak akses yang sama." else installDetail,
            ),
            Capability(
                "Statistik data harian",
                "Perlu izin akses penggunaan",
                CapState.PARTIAL,
                "NetworkStatsManager; izin diberikan lewat Setelan › Akses penggunaan.",
            ),
        )

        val limitations = ArrayList<String>()
        if (!hasEuiccFeature) {
            limitations += "Perangkat ini tidak memiliki chip eUICC, sehingga profil eSIM tidak dapat diunduh atau diaktifkan langsung di perangkat ini."
        } else if (!euiccEnabled) {
            limitations += "Chip eUICC terdeteksi tetapi layanan eSIM sistem (LPA) tidak aktif, sehingga unduhan profil tidak tersedia."
        }
        if (!hasModem) {
            limitations += "Tidak ada modem seluler: perangkat hanya terhubung lewat Wi-Fi dan tidak dapat memakai paket data eSIM secara langsung."
        }
        if (!canInstall) {
            limitations += "Profil yang disimpan atau dibeli di sini dipasang dengan memindai QR-nya dari ponsel atau perangkat lain yang mendukung eSIM."
        }
        if (isPad6) {
            limitations += "Xiaomi Pad 6 (Snapdragon 870, Wi-Fi) dijual tanpa modem seluler dan tanpa eUICC; ini batasan hardware, bukan batasan aplikasi."
        }
        if (!hasCamera) {
            limitations += "Tanpa kamera, kode aktivasi hanya bisa dimasukkan manual atau dibaca dari berkas gambar."
        }

        return DeviceReport(
            deviceName = deviceName,
            manufacturer = manufacturer,
            model = model,
            codename = codename,
            soc = soc,
            abis = abis,
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            isXiaomiPad6 = isPad6,
            hasTelephony = hasModem,
            hasEuiccFeature = hasEuiccFeature,
            euiccEnabled = euiccEnabled,
            canInstallEsim = canInstall,
            hasCamera = hasCamera,
            display = displaySummary,
            refreshRates = refreshRates,
            operatorName = operatorName,
            mode = operatingMode,
            hardware = hardware,
            euicc = euicc,
            features = features,
            limitations = limitations,
        )
    }

    /** Daftar SIM/eSIM aktif di sistem (butuh izin READ_PHONE_STATE dan modem). */
    fun systemSubscriptions(): SystemSubscriptions {
        val pm = context.packageManager
        val supported = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val granted = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        if (!supported || !granted) return SystemSubscriptions(supported, granted, emptyList())
        val manager = context.getSystemService(SubscriptionManager::class.java)
        val list = runCatching {
            manager?.activeSubscriptionInfoList.orEmpty().map { info ->
                SystemSubscription(
                    subscriptionId = info.subscriptionId,
                    displayName = info.displayName?.toString().orEmpty(),
                    carrierName = info.carrierName?.toString().orEmpty(),
                    countryIso = info.countryIso.orEmpty().uppercase(Locale.US),
                    slotIndex = info.simSlotIndex,
                    isEmbedded = info.isEmbedded,
                    mccMnc = listOfNotNull(info.mccString, info.mncString).joinToString("-"),
                )
            }
        }.getOrDefault(emptyList())
        return SystemSubscriptions(supported, granted, list)
    }
}
