package com.bxiidn.esimmanager.device

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.telephony.euicc.DownloadableSubscription
import android.telephony.euicc.EuiccManager
import com.bxiidn.esimmanager.EsimApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Hasil nyata dari LPA sistem untuk operasi unduh/hapus profil. */
data class EuiccEvent(
    val action: String,
    val profileId: Long,
    val subscriptionId: Int,
    val resultCode: Int,
    val detailedCode: Int,
    val operationCode: Int,
    val errorCode: Int,
    val smdxSubjectCode: String,
    val smdxReasonCode: String,
    val resultIntent: Intent,
) {
    val isSuccess: Boolean get() = resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK
    val isResolvable: Boolean get() = resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR
    val isDownload: Boolean get() = action == EuiccController.ACTION_DOWNLOAD
}

object EuiccEvents {
    private val _latest = MutableStateFlow<EuiccEvent?>(null)
    val latest: StateFlow<EuiccEvent?> = _latest.asStateFlow()

    fun publish(event: EuiccEvent) {
        _latest.value = event
    }

    fun consume() {
        _latest.value = null
    }
}

sealed interface EuiccStart {
    data object Started : EuiccStart
    data class Unavailable(val reason: String) : EuiccStart
}

/**
 * Pembungkus API resmi android.telephony.euicc.EuiccManager.
 * Tidak ada simulasi: bila perangkat tidak punya eUICC aktif, operasi ditolak
 * dengan alasan yang jelas dan tidak pernah dilaporkan berhasil.
 */
class EuiccController(context: Context) {
    private val context = context.applicationContext
    private val manager: EuiccManager? = this.context.getSystemService(EuiccManager::class.java)

    val isAvailable: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC) &&
            runCatching { manager?.isEnabled == true }.getOrDefault(false)

    private fun callback(action: String, profileId: Long, subscriptionId: Int): PendingIntent {
        val intent = Intent(context, EuiccResultReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_PROFILE_ID, profileId)
            .putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)
        val requestCode = (System.nanoTime() and 0x7FFFFFFF).toInt()
        // FLAG_MUTABLE diperlukan karena sistem menambahkan extra hasil ke intent ini.
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun unavailable(): EuiccStart.Unavailable = EuiccStart.Unavailable(
        "Perangkat ini tidak memiliki eUICC aktif, jadi profil eSIM tidak dapat dipasang atau dihapus di sini.",
    )

    /** Meminta LPA sistem mengunduh profil dari SM-DP+ memakai kode aktivasi. */
    fun download(activationCode: String, profileId: Long): EuiccStart {
        val euicc = manager
        if (!isAvailable || euicc == null) return unavailable()
        if (activationCode.isBlank()) return EuiccStart.Unavailable("Kode aktivasi kosong.")
        return try {
            val subscription = DownloadableSubscription.forActivationCode(activationCode)
            euicc.downloadSubscription(subscription, true, callback(ACTION_DOWNLOAD, profileId, -1))
            EuiccStart.Started
        } catch (e: Exception) {
            EuiccStart.Unavailable("EuiccManager menolak permintaan: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Menghapus profil eSIM yang terpasang di eUICC perangkat ini. */
    fun delete(subscriptionId: Int): EuiccStart {
        val euicc = manager
        if (!isAvailable || euicc == null) return unavailable()
        return try {
            euicc.deleteSubscription(subscriptionId, callback(ACTION_DELETE, -1, subscriptionId))
            EuiccStart.Started
        } catch (e: Exception) {
            EuiccStart.Unavailable("EuiccManager menolak permintaan: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Melanjutkan operasi yang butuh persetujuan pengguna di layar sistem. */
    fun resolve(activity: Activity, event: EuiccEvent): Boolean {
        val euicc = manager ?: return false
        return try {
            euicc.startResolutionActivity(
                activity,
                0,
                event.resultIntent,
                callback(event.action, event.profileId, event.subscriptionId),
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Membuka penyiapan eSIM bawaan Android dengan kode aktivasi (universal link resmi Android). */
    fun openSystemProvisioning(activity: Activity, lpaString: String): Boolean {
        if (!isAvailable) return false
        return try {
            val uri = Uri.parse("https://esimsetup.android.com/esim_qrcode_provisioning?carddata=" + Uri.encode(lpaString))
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Membuka halaman pengelolaan eSIM di Setelan sistem. */
    fun openSystemEsimSettings(activity: Activity): Boolean {
        val candidates = listOf(
            Intent(EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
        )
        for (intent in candidates) {
            try {
                activity.startActivity(intent)
                return true
            } catch (e: Exception) {
                // coba kandidat berikutnya
            }
        }
        return false
    }

    companion object {
        const val ACTION_DOWNLOAD = "com.bxiidn.esimmanager.action.EUICC_DOWNLOAD_RESULT"
        const val ACTION_DELETE = "com.bxiidn.esimmanager.action.EUICC_DELETE_RESULT"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_SUBSCRIPTION_ID = "subscription_id"

        /** Menerjemahkan hasil LPA menjadi penjelasan untuk pengguna. */
        fun describe(event: EuiccEvent): String {
            val what = if (event.isDownload) "Unduhan profil eSIM" else "Penghapusan profil eSIM"
            return when {
                event.isSuccess -> "$what berhasil dikonfirmasi oleh sistem."
                event.isResolvable ->
                    "$what memerlukan tindakan Anda di layar sistem (persetujuan, kode konfirmasi, atau pilihan SIM)."
                else -> {
                    val parts = ArrayList<String>()
                    parts += "$what ditolak oleh sistem."
                    parts += "Biasanya karena aplikasi pihak ketiga tidak punya hak operator atas profil ini."
                    val codes = ArrayList<String>()
                    if (event.detailedCode != 0) codes += "detail ${event.detailedCode}"
                    if (event.operationCode != 0) codes += "operasi ${event.operationCode}"
                    if (event.errorCode != 0) codes += "error ${event.errorCode}"
                    if (event.smdxSubjectCode.isNotBlank()) codes += "subjek ${event.smdxSubjectCode}"
                    if (event.smdxReasonCode.isNotBlank()) codes += "alasan ${event.smdxReasonCode}"
                    if (codes.isNotEmpty()) parts += "Kode: ${codes.joinToString(", ")}."
                    parts.joinToString(" ")
                }
            }
        }
    }
}

class EuiccResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val profileId = intent.getLongExtra(EuiccController.EXTRA_PROFILE_ID, -1)
        val event = EuiccEvent(
            action = action,
            profileId = profileId,
            subscriptionId = intent.getIntExtra(EuiccController.EXTRA_SUBSCRIPTION_ID, -1),
            resultCode = resultCode,
            detailedCode = intent.getIntExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE, 0),
            operationCode = intent.getIntExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE, 0),
            errorCode = intent.getIntExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE, 0),
            smdxSubjectCode = intent.getStringExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_SMDX_SUBJECT_CODE).orEmpty(),
            smdxReasonCode = intent.getStringExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_SMDX_REASON_CODE).orEmpty(),
            resultIntent = intent,
        )
        EuiccEvents.publish(event)

        // Status "terpasang" hanya dicatat setelah LPA sistem mengonfirmasi keberhasilan.
        if (event.isSuccess && event.isDownload && profileId > 0) {
            val app = context.applicationContext as? EsimApp ?: return
            val pending = goAsync()
            app.container.appScope.launch {
                try {
                    app.container.profiles.markInstalled(profileId)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
