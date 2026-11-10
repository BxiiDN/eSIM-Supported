package com.bxiidn.esimmanager.provider

import retrofit2.HttpException
import java.io.IOException

enum class ProviderId(val label: String, val site: String) {
    ESIM_ACCESS("eSIM Access", "esimaccess.com"),
    AIRALO("Airalo Partner API", "partners.airalo.com"),
}

enum class CatalogScope(val label: String) {
    COUNTRY("Per negara"),
    GLOBAL("Regional & global"),
}

/** Paket data di katalog penyedia. */
data class StorePackage(
    val provider: ProviderId,
    val id: String,
    val title: String,
    val countryCode: String,
    val countryName: String,
    val operatorName: String,
    val networkTypes: String,
    val coverage: List<String>,
    val dataBytes: Long,
    val unlimited: Boolean,
    val validityDays: Int,
    /** Harga yang ditagihkan ke saldo/akun penyedia milik pengguna. */
    val price: Double,
    /** Harga ritel yang disarankan penyedia, 0 bila tidak ada. */
    val retailPrice: Double,
    val currency: String,
    /** Nilai harga mentah dari API (dipakai lagi saat memesan), -1 bila tidak relevan. */
    val rawPrice: Long,
)

/** Profil eSIM yang dikembalikan penyedia setelah pembelian. */
data class PurchasedEsim(
    val iccid: String,
    val lpaString: String,
    val qrCodeUrl: String,
    val remoteRef: String,
    val apn: String,
    val totalBytes: Long,
    val expiresAt: String,
    val status: String,
)

data class PurchaseResult(
    val orderRef: String,
    /** Kosong bila penyedia masih mengalokasikan profil (dicek ulang lewat sinkronisasi). */
    val esims: List<PurchasedEsim>,
)

data class RemoteUsage(
    val totalBytes: Long,
    val usedBytes: Long,
    val unlimited: Boolean,
    val status: String,
    val expiresAt: String,
)

data class ProviderBalance(val amount: Double, val currency: String)

data class RemoteRef(val iccid: String, val remoteRef: String, val orderRef: String)

class ProviderException(message: String) : Exception(message)

/** Kontrak integrasi penyedia eSIM. Semua panggilan menuju API resmi penyedia. */
interface EsimProvider {
    val id: ProviderId

    /** Memverifikasi kredensial; mengembalikan keterangan singkat bila berhasil. */
    suspend fun testConnection(): String

    suspend fun listPackages(scope: CatalogScope, countryCode: String): List<StorePackage>

    /** Membeli SATU eSIM untuk paket ini. Ini transaksi nyata pada akun penyedia. */
    suspend fun purchase(pkg: StorePackage): PurchaseResult

    /** Mengambil ulang profil dari pesanan yang alokasinya tertunda. */
    suspend fun fetchOrder(orderRef: String): List<PurchasedEsim>

    suspend fun usage(ref: RemoteRef): RemoteUsage

    /** Saldo akun penyedia, null bila penyedia tidak menyediakannya. */
    suspend fun balance(): ProviderBalance?
}

fun Throwable.userMessage(): String = when (this) {
    is ProviderException -> message ?: "Permintaan ke penyedia gagal."
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull().orEmpty().take(240)
        when (code()) {
            401, 403 -> "Kredensial API ditolak penyedia (HTTP ${code()}). Periksa kembali di Pengaturan."
            404 -> "Data tidak ditemukan di penyedia (HTTP 404)."
            422 -> "Permintaan ditolak penyedia (HTTP 422). $body"
            429 -> "Batas permintaan penyedia tercapai (HTTP 429). Coba lagi beberapa saat."
            else -> "Penyedia mengembalikan HTTP ${code()}. $body"
        }.trim()
    }
    is IOException -> "Tidak dapat menghubungi penyedia. Periksa koneksi internet. (${message ?: javaClass.simpleName})"
    else -> message ?: javaClass.simpleName
}
