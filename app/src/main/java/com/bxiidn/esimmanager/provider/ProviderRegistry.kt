package com.bxiidn.esimmanager.provider

import com.bxiidn.esimmanager.core.SecureStore
import com.bxiidn.esimmanager.provider.airalo.AiraloApi
import com.bxiidn.esimmanager.provider.airalo.AiraloProvider
import com.bxiidn.esimmanager.provider.airalo.AiraloTokenCache
import com.bxiidn.esimmanager.provider.esimaccess.EsimAccessApi
import com.bxiidn.esimmanager.provider.esimaccess.EsimAccessProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class AiraloCredentials(val clientId: String, val clientSecret: String, val sandbox: Boolean)

/**
 * Menyimpan kredensial API penyedia (terenkripsi) dan membangun klien penyedia.
 * Tanpa kredensial, penyedia tidak tersedia — tidak ada katalog tiruan.
 */
class ProviderRegistry(
    private val store: SecureStore,
    private val http: OkHttpClient,
) {
    private val _configured = MutableStateFlow(readConfigured())
    val configured: StateFlow<List<ProviderId>> = _configured.asStateFlow()

    private var airalo: AiraloProvider? = null
    private var esimAccess: EsimAccessProvider? = null

    private fun readConfigured(): List<ProviderId> {
        val list = ArrayList<ProviderId>()
        if (esimAccessCode().isNotBlank()) list += ProviderId.ESIM_ACCESS
        val a = airaloCredentials()
        if (a.clientId.isNotBlank() && a.clientSecret.isNotBlank()) list += ProviderId.AIRALO
        return list
    }

    fun esimAccessCode(): String = store.get(KEY_EA_CODE)

    fun airaloCredentials(): AiraloCredentials = AiraloCredentials(
        clientId = store.get(KEY_AIRALO_ID),
        clientSecret = store.get(KEY_AIRALO_SECRET),
        sandbox = store.get(KEY_AIRALO_SANDBOX) == "1",
    )

    @Synchronized
    fun saveEsimAccess(accessCode: String) {
        store.put(KEY_EA_CODE, accessCode.trim())
        esimAccess = null
        _configured.value = readConfigured()
    }

    @Synchronized
    fun saveAiralo(clientId: String, clientSecret: String, sandbox: Boolean) {
        store.put(KEY_AIRALO_ID, clientId.trim())
        store.put(KEY_AIRALO_SECRET, clientSecret.trim())
        store.put(KEY_AIRALO_SANDBOX, if (sandbox) "1" else "0")
        store.remove(KEY_AIRALO_TOKEN, KEY_AIRALO_TOKEN_EXP)
        airalo = null
        _configured.value = readConfigured()
    }

    @Synchronized
    fun clear(id: ProviderId) {
        when (id) {
            ProviderId.ESIM_ACCESS -> {
                store.remove(KEY_EA_CODE)
                esimAccess = null
            }
            ProviderId.AIRALO -> {
                store.remove(KEY_AIRALO_ID, KEY_AIRALO_SECRET, KEY_AIRALO_SANDBOX, KEY_AIRALO_TOKEN, KEY_AIRALO_TOKEN_EXP)
                airalo = null
            }
        }
        _configured.value = readConfigured()
    }

    /** Null bila kredensial penyedia belum diisi. */
    @Synchronized
    fun provider(id: ProviderId): EsimProvider? = when (id) {
        ProviderId.ESIM_ACCESS -> {
            val code = esimAccessCode()
            if (code.isBlank()) {
                null
            } else {
                esimAccess ?: EsimAccessProvider(
                    api = retrofit(ESIM_ACCESS_URL).create(EsimAccessApi::class.java),
                    accessCode = code,
                ).also { esimAccess = it }
            }
        }
        ProviderId.AIRALO -> {
            val c = airaloCredentials()
            if (c.clientId.isBlank() || c.clientSecret.isBlank()) {
                null
            } else {
                airalo ?: AiraloProvider(
                    api = retrofit(if (c.sandbox) AIRALO_SANDBOX_URL else AIRALO_URL).create(AiraloApi::class.java),
                    clientId = c.clientId,
                    clientSecret = c.clientSecret,
                    tokenCache = tokenCache,
                ).also { airalo = it }
            }
        }
    }

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val tokenCache = object : AiraloTokenCache {
        override fun read(): Pair<String, Long>? {
            val token = store.get(KEY_AIRALO_TOKEN)
            val expiry = store.get(KEY_AIRALO_TOKEN_EXP).toLongOrNull() ?: 0L
            return if (token.isBlank() || expiry <= 0L) null else token to expiry
        }

        override fun write(token: String, expiresAtMillis: Long) {
            store.put(KEY_AIRALO_TOKEN, token)
            store.put(KEY_AIRALO_TOKEN_EXP, expiresAtMillis.toString())
        }

        override fun clear() {
            store.remove(KEY_AIRALO_TOKEN, KEY_AIRALO_TOKEN_EXP)
        }
    }

    private companion object {
        const val ESIM_ACCESS_URL = "https://api.esimaccess.com/"
        const val AIRALO_URL = "https://partners-api.airalo.com/"
        const val AIRALO_SANDBOX_URL = "https://sandbox-partners-api.airalo.com/"

        const val KEY_EA_CODE = "esimaccess_access_code"
        const val KEY_AIRALO_ID = "airalo_client_id"
        const val KEY_AIRALO_SECRET = "airalo_client_secret"
        const val KEY_AIRALO_SANDBOX = "airalo_sandbox"
        const val KEY_AIRALO_TOKEN = "airalo_token"
        const val KEY_AIRALO_TOKEN_EXP = "airalo_token_exp"
    }
}
