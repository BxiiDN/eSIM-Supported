package com.bxiidn.esimmanager.provider.airalo

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
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/*
 * Airalo Partner API v2 — https://developers.partners.airalo.com
 *   POST /v2/token              (x-www-form-urlencoded: client_id, client_secret, grant_type)
 *   GET  /v2/packages           (filter[country], filter[type], limit, page)
 *   POST /v2/orders             (multipart: quantity, package_id, type, description)
 *   GET  /v2/sims/{iccid}/usage (remaining/total dalam MB)
 */

data class AiraloEnvelope<T>(val data: T?, val meta: AiraloMeta?)

data class AiraloMeta(
    val message: String?,
    @SerializedName("current_page") val currentPage: Int?,
    @SerializedName("last_page") val lastPage: Int?,
)

data class AiraloToken(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("expires_in") val expiresIn: Long?,
    @SerializedName("token_type") val tokenType: String?,
)

data class AiraloCountry(
    val slug: String?,
    @SerializedName("country_code") val countryCode: String?,
    val title: String?,
    val operators: List<AiraloOperator>?,
)

data class AiraloOperator(
    val id: Long?,
    val title: String?,
    val type: String?,
    @SerializedName("plan_type") val planType: String?,
    @SerializedName("is_roaming") val isRoaming: Boolean?,
    val rechargeability: Boolean?,
    val packages: List<AiraloPackage>?,
)

data class AiraloPackage(
    val id: String?,
    val type: String?,
    val price: Double?,
    @SerializedName("net_price") val netPrice: Double?,
    val amount: Double?,
    val day: Int?,
    @SerializedName("is_unlimited") val isUnlimited: Boolean?,
    val title: String?,
    val data: String?,
)

data class AiraloOrder(
    val id: Long?,
    val code: String?,
    val currency: String?,
    @SerializedName("package_id") val packageId: String?,
    @SerializedName("package") val packageName: String?,
    val validity: Int?,
    val price: Double?,
    val sims: List<AiraloSim>?,
)

data class AiraloSim(
    val id: Long?,
    val iccid: String?,
    val lpa: String?,
    @SerializedName("matching_id") val matchingId: String?,
    val qrcode: String?,
    @SerializedName("qrcode_url") val qrcodeUrl: String?,
    @SerializedName("apn_type") val apnType: String?,
    @SerializedName("apn_value") val apnValue: String?,
)

data class AiraloUsage(
    val remaining: Double?,
    val total: Double?,
    @SerializedName("expired_at") val expiredAt: String?,
    @SerializedName("is_unlimited") val isUnlimited: Boolean?,
    val status: String?,
)

interface AiraloApi {
    @FormUrlEncoded
    @POST("v2/token")
    suspend fun token(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String,
    ): AiraloEnvelope<AiraloToken>

    @GET("v2/packages")
    suspend fun packages(
        @Header("Authorization") authorization: String,
        @Query("filter[type]") type: String?,
        @Query("filter[country]") country: String?,
        @Query("limit") limit: Int,
        @Query("page") page: Int,
    ): AiraloEnvelope<List<AiraloCountry>>

    @Multipart
    @POST("v2/orders")
    suspend fun order(
        @Header("Authorization") authorization: String,
        @Part("quantity") quantity: RequestBody,
        @Part("package_id") packageId: RequestBody,
        @Part("type") type: RequestBody,
        @Part("description") description: RequestBody,
    ): AiraloEnvelope<AiraloOrder>

    @GET("v2/sims/{iccid}/usage")
    suspend fun usage(
        @Header("Authorization") authorization: String,
        @Path("iccid") iccid: String,
    ): AiraloEnvelope<AiraloUsage>
}

/** Menyimpan access token Airalo (terenkripsi) agar tidak meminta token berulang kali. */
interface AiraloTokenCache {
    fun read(): Pair<String, Long>?
    fun write(token: String, expiresAtMillis: Long)
    fun clear()
}

class AiraloProvider(
    private val api: AiraloApi,
    private val clientId: String,
    private val clientSecret: String,
    private val tokenCache: AiraloTokenCache,
) : EsimProvider {

    override val id = ProviderId.AIRALO
    private val tokenLock = Mutex()

    private suspend fun bearer(): String = tokenLock.withLock {
        val now = System.currentTimeMillis()
        val cached = tokenCache.read()
        if (cached != null && cached.second > now + 60_000) {
            return@withLock "Bearer ${cached.first}"
        }
        val response = api.token(clientId, clientSecret, "client_credentials")
        val data = response.data ?: throw ProviderException("Airalo tidak mengembalikan data token.")
        val token = data.accessToken
        if (token.isNullOrBlank()) throw ProviderException("Airalo tidak mengembalikan access token.")
        // Dokumentasi menyebut token berlaku 24 jam; dibatasi 23 jam agar aman.
        val ttlSeconds = minOf(data.expiresIn ?: 86_400L, 82_800L).coerceAtLeast(300L)
        tokenCache.write(token, now + ttlSeconds * 1000)
        "Bearer $token"
    }

    /** Mengulang sekali dengan token baru bila token lama ditolak. */
    private suspend fun <T> authorized(block: suspend (String) -> T): T {
        return try {
            block(bearer())
        } catch (e: HttpException) {
            if (e.code() != 401) throw e
            tokenCache.clear()
            block(bearer())
        }
    }

    override suspend fun testConnection(): String {
        tokenCache.clear()
        bearer()
        return "Terhubung ke Airalo Partner API — access token diterima."
    }

    override suspend fun listPackages(scope: CatalogScope, countryCode: String): List<StorePackage> {
        val type = if (scope == CatalogScope.GLOBAL) "global" else "local"
        val country = if (scope == CatalogScope.COUNTRY) countryCode.uppercase() else null
        val result = ArrayList<StorePackage>()
        var page = 1
        while (page <= MAX_PAGES) {
            val response = authorized { auth -> api.packages(auth, type, country, PAGE_SIZE, page) }
            val countries = response.data.orEmpty()
            for (c in countries) {
                for (op in c.operators.orEmpty()) {
                    for (p in op.packages.orEmpty()) {
                        val packageId = p.id ?: continue
                        if (p.type != null && p.type != "sim") continue
                        val unlimited = p.isUnlimited == true
                        val retail = p.price ?: 0.0
                        result += StorePackage(
                            provider = ProviderId.AIRALO,
                            id = packageId,
                            title = p.title ?: p.data ?: packageId,
                            countryCode = c.countryCode.orEmpty(),
                            countryName = c.title.orEmpty(),
                            operatorName = op.title.orEmpty(),
                            networkTypes = if (op.isRoaming == true) "Roaming" else "",
                            coverage = emptyList(),
                            dataBytes = if (unlimited) 0L else ((p.amount ?: 0.0) * MB).toLong(),
                            unlimited = unlimited,
                            validityDays = p.day ?: 0,
                            price = p.netPrice ?: retail,
                            retailPrice = retail,
                            currency = "USD",
                            rawPrice = -1,
                        )
                    }
                }
            }
            val last = response.meta?.lastPage ?: 1
            if (countries.isEmpty() || page >= last) break
            page++
        }
        return result.sortedWith(compareBy({ it.countryName }, { it.price }))
    }

    override suspend fun purchase(pkg: StorePackage): PurchaseResult {
        val text = "text/plain".toMediaType()
        val response = authorized { auth ->
            api.order(
                authorization = auth,
                quantity = "1".toRequestBody(text),
                packageId = pkg.id.toRequestBody(text),
                type = "sim".toRequestBody(text),
                description = "eSIM Manager".toRequestBody(text),
            )
        }
        val order = response.data ?: throw ProviderException("Airalo tidak mengembalikan data pesanan.")
        val orderRef = order.code ?: order.id?.toString() ?: ""
        val esims = order.sims.orEmpty().map { sim ->
            val qr = sim.qrcode.orEmpty()
            val smdp = sim.lpa.orEmpty()
            val lpa: String = when {
                qr.startsWith("LPA:", ignoreCase = true) -> qr
                smdp.isNotBlank() -> "LPA:1\$" + smdp + "\$" + sim.matchingId.orEmpty()
                else -> ""
            }
            PurchasedEsim(
                iccid = sim.iccid.orEmpty(),
                lpaString = lpa,
                qrCodeUrl = sim.qrcodeUrl.orEmpty(),
                remoteRef = sim.iccid.orEmpty(),
                apn = sim.apnValue.orEmpty(),
                totalBytes = pkg.dataBytes,
                expiresAt = "",
                status = "NOT_ACTIVE",
            )
        }
        if (esims.isEmpty()) {
            throw ProviderException("Pesanan Airalo $orderRef dibuat tetapi tidak berisi profil eSIM.")
        }
        return PurchaseResult(orderRef, esims)
    }

    override suspend fun fetchOrder(orderRef: String): List<PurchasedEsim> = emptyList()

    override suspend fun usage(ref: RemoteRef): RemoteUsage {
        if (ref.iccid.isBlank()) throw ProviderException("ICCID profil tidak diketahui.")
        val response = authorized { auth -> api.usage(auth, ref.iccid) }
        val data = response.data ?: throw ProviderException("Airalo tidak mengembalikan data pemakaian.")
        val unlimited = data.isUnlimited == true
        val total = ((data.total ?: 0.0) * MB).toLong()
        val remaining = ((data.remaining ?: 0.0) * MB).toLong()
        return RemoteUsage(
            totalBytes = if (unlimited) 0 else total,
            usedBytes = if (unlimited) 0 else (total - remaining).coerceAtLeast(0),
            unlimited = unlimited,
            status = data.status.orEmpty(),
            expiresAt = data.expiredAt.orEmpty(),
        )
    }

    override suspend fun balance(): ProviderBalance? = null

    private companion object {
        const val MB = 1024.0 * 1024.0
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 6
    }
}
