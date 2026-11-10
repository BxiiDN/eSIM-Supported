package com.bxiidn.esimmanager.provider.esimaccess

import com.bxiidn.esimmanager.provider.CatalogScope
import com.bxiidn.esimmanager.provider.EsimProvider
import com.bxiidn.esimmanager.provider.ProviderBalance
import com.bxiidn.esimmanager.provider.ProviderException
import com.bxiidn.esimmanager.provider.ProviderId
import com.bxiidn.esimmanager.provider.PurchaseResult
import com.bxiidn.esimmanager.provider.PurchasedEsim
import com.bxiidn.esimmanager.provider.RemoteRef
import com.bxiidn.esimmanager.provider.RemoteUsage
import com.bxiidn.esimmanager.provider.StorePackage
import kotlinx.coroutines.delay
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.UUID

/*
 * eSIM Access Open API — https://docs.esimaccess.com
 *   Autentikasi: header RT-AccessCode
 *   POST /api/v1/open/package/list   katalog paket
 *   POST /api/v1/open/esim/order     membuat pesanan (alokasi profil asinkron)
 *   POST /api/v1/open/esim/query     profil hasil pesanan + pemakaian data
 *   POST /api/v1/open/balance/query  saldo akun
 * Harga memakai satuan 1/10.000 USD; volume data dalam byte.
 */

data class EaEnvelope<T>(
    val success: Boolean?,
    val errorCode: String?,
    val errorMsg: String?,
    val obj: T?,
)

class EaEmptyRequest

data class EaPackageListRequest(
    val locationCode: String = "",
    val type: String = "",
    val slug: String = "",
    val packageCode: String = "",
    val iccid: String = "",
)

data class EaPackageListObj(val packageList: List<EaPackage>?)

data class EaPackage(
    val packageCode: String?,
    val slug: String?,
    val name: String?,
    val price: Double?,
    val retailPrice: Double?,
    val currencyCode: String?,
    val volume: Double?,
    val duration: Int?,
    val durationUnit: String?,
    val location: String?,
    val description: String?,
    val speed: String?,
    val locationNetworkList: List<EaLocationNetwork>?,
)

data class EaLocationNetwork(
    val locationName: String?,
    val locationCode: String?,
    val operatorList: List<EaOperator>?,
)

data class EaOperator(val operatorName: String?, val networkType: String?)

data class EaOrderItem(val packageCode: String, val count: Int, val price: Long?)

data class EaOrderRequest(
    val transactionId: String,
    val amount: Long?,
    val packageInfoList: List<EaOrderItem>,
)

data class EaOrderObj(val orderNo: String?, val transactionId: String?)

data class EaPager(val pageNum: Int = 1, val pageSize: Int = 20)

data class EaQueryRequest(
    val orderNo: String? = null,
    val iccid: String? = null,
    val pager: EaPager = EaPager(),
)

data class EaEsimListObj(val esimList: List<EaEsim>?)

data class EaEsim(
    val esimTranNo: String?,
    val orderNo: String?,
    val iccid: String?,
    val ac: String?,
    val qrCodeUrl: String?,
    val smdpStatus: String?,
    val esimStatus: String?,
    val expiredTime: String?,
    val totalVolume: Double?,
    val totalDuration: Int?,
    val durationUnit: String?,
    val orderUsage: Double?,
    val apn: String?,
)

data class EaBalanceObj(val balance: Double?)

interface EsimAccessApi {
    @POST("api/v1/open/package/list")
    suspend fun packages(
        @Header("RT-AccessCode") accessCode: String,
        @Body body: EaPackageListRequest,
    ): EaEnvelope<EaPackageListObj>

    @POST("api/v1/open/esim/order")
    suspend fun order(
        @Header("RT-AccessCode") accessCode: String,
        @Body body: EaOrderRequest,
    ): EaEnvelope<EaOrderObj>

    @POST("api/v1/open/esim/query")
    suspend fun query(
        @Header("RT-AccessCode") accessCode: String,
        @Body body: EaQueryRequest,
    ): EaEnvelope<EaEsimListObj>

    @POST("api/v1/open/balance/query")
    suspend fun balance(
        @Header("RT-AccessCode") accessCode: String,
        @Body body: EaEmptyRequest,
    ): EaEnvelope<EaBalanceObj>
}

class EsimAccessProvider(
    private val api: EsimAccessApi,
    private val accessCode: String,
) : EsimProvider {

    override val id = ProviderId.ESIM_ACCESS

    private fun <T> EaEnvelope<T>.unwrap(): T {
        if (success != true) {
            val code = errorCode?.takeIf { it.isNotBlank() && it != "0" }?.let { " (kode $it)" }.orEmpty()
            throw ProviderException("eSIM Access: ${errorMsg?.takeIf { it.isNotBlank() } ?: "permintaan ditolak"}$code")
        }
        return obj ?: throw ProviderException("eSIM Access mengembalikan respons kosong.")
    }

    override suspend fun testConnection(): String {
        val balance = balance()
        return "Terhubung ke eSIM Access — saldo ${balance.currency} ${"%.2f".format(java.util.Locale.US, balance.amount)}."
    }

    override suspend fun listPackages(scope: CatalogScope, countryCode: String): List<StorePackage> {
        val request = if (scope == CatalogScope.COUNTRY) {
            EaPackageListRequest(locationCode = countryCode.uppercase())
        } else {
            EaPackageListRequest()
        }
        val list = api.packages(accessCode, request).unwrap().packageList.orEmpty()
        val mapped = list.mapNotNull { p ->
            val code = p.packageCode ?: return@mapNotNull null
            val locations = p.location.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val multi = locations.size > 1
            if (scope == CatalogScope.GLOBAL && !multi) return@mapNotNull null
            if (scope == CatalogScope.COUNTRY && multi) return@mapNotNull null
            val networks = p.locationNetworkList.orEmpty()
            val operators = networks.flatMap { it.operatorList.orEmpty() }
            val rawPrice = (p.price ?: 0.0).toLong()
            val days = when (p.durationUnit?.uppercase()) {
                "MONTH" -> (p.duration ?: 0) * 30
                else -> p.duration ?: 0
            }
            StorePackage(
                provider = ProviderId.ESIM_ACCESS,
                id = code,
                title = p.name ?: code,
                countryCode = if (multi) "" else locations.firstOrNull().orEmpty(),
                countryName = if (multi) {
                    "${locations.size} negara"
                } else {
                    networks.firstOrNull()?.locationName ?: locations.firstOrNull().orEmpty()
                },
                operatorName = operators.mapNotNull { it.operatorName }.distinct().take(3).joinToString(", "),
                networkTypes = operators.mapNotNull { it.networkType }.distinct().joinToString(" / "),
                coverage = networks.mapNotNull { it.locationName }.distinct(),
                dataBytes = (p.volume ?: 0.0).toLong(),
                unlimited = false,
                validityDays = days,
                price = rawPrice / PRICE_UNIT,
                retailPrice = (p.retailPrice ?: 0.0) / PRICE_UNIT,
                currency = p.currencyCode?.takeIf { it.isNotBlank() } ?: "USD",
                rawPrice = rawPrice,
            )
        }
        return mapped.sortedWith(compareBy({ it.price }, { it.dataBytes }))
    }

    private fun EaEsim.toPurchased(): PurchasedEsim = PurchasedEsim(
        iccid = iccid.orEmpty(),
        lpaString = ac.orEmpty(),
        qrCodeUrl = qrCodeUrl.orEmpty(),
        remoteRef = esimTranNo.orEmpty(),
        apn = apn.orEmpty(),
        totalBytes = (totalVolume ?: 0.0).toLong(),
        expiresAt = expiredTime.orEmpty(),
        status = esimStatus ?: smdpStatus.orEmpty(),
    )

    override suspend fun purchase(pkg: StorePackage): PurchaseResult {
        val rawPrice = pkg.rawPrice.takeIf { it >= 0 }
        val request = EaOrderRequest(
            transactionId = "esimmgr-" + UUID.randomUUID().toString(),
            amount = rawPrice,
            packageInfoList = listOf(EaOrderItem(packageCode = pkg.id, count = 1, price = rawPrice)),
        )
        val orderNo = api.order(accessCode, request).unwrap().orderNo
        if (orderNo.isNullOrBlank()) throw ProviderException("eSIM Access tidak mengembalikan nomor pesanan.")

        // Alokasi profil berjalan asinkron di sisi penyedia; tunggu sebentar.
        var esims: List<PurchasedEsim> = emptyList()
        for (attempt in 1..POLL_ATTEMPTS) {
            delay(POLL_DELAY_MS)
            esims = runCatching { fetchOrder(orderNo) }.getOrDefault(emptyList())
            if (esims.isNotEmpty()) break
        }
        return PurchaseResult(orderNo, esims)
    }

    override suspend fun fetchOrder(orderRef: String): List<PurchasedEsim> {
        val list = api.query(accessCode, EaQueryRequest(orderNo = orderRef)).unwrap().esimList.orEmpty()
        return list.filter { !it.ac.isNullOrBlank() }.map { it.toPurchased() }
    }

    override suspend fun usage(ref: RemoteRef): RemoteUsage {
        val request = when {
            ref.iccid.isNotBlank() -> EaQueryRequest(iccid = ref.iccid)
            ref.orderRef.isNotBlank() -> EaQueryRequest(orderNo = ref.orderRef)
            else -> throw ProviderException("Profil ini tidak punya ICCID maupun nomor pesanan.")
        }
        val esim = api.query(accessCode, request).unwrap().esimList.orEmpty().firstOrNull()
            ?: throw ProviderException("Profil tidak ditemukan di akun eSIM Access.")
        return RemoteUsage(
            totalBytes = (esim.totalVolume ?: 0.0).toLong(),
            usedBytes = (esim.orderUsage ?: 0.0).toLong(),
            unlimited = false,
            status = esim.esimStatus ?: esim.smdpStatus.orEmpty(),
            expiresAt = esim.expiredTime.orEmpty(),
        )
    }

    override suspend fun balance(): ProviderBalance {
        val raw = api.balance(accessCode, EaEmptyRequest()).unwrap().balance ?: 0.0
        return ProviderBalance(raw / PRICE_UNIT, "USD")
    }

    private companion object {
        const val PRICE_UNIT = 10_000.0
        const val POLL_ATTEMPTS = 5
        const val POLL_DELAY_MS = 3_000L
    }
}
