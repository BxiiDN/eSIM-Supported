package com.bxiidn.esimmanager.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ProbeResult(
    val reachable: Boolean,
    val latencyMs: Long,
    val endpoint: String,
    val detail: String,
    val checkedAt: Long,
)

/** Uji akses internet nyata: permintaan HTTP ke endpoint "generate_204". */
class InternetProbe(http: OkHttpClient) {
    private val client = http.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val endpoints = listOf(
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
    )

    suspend fun run(): ProbeResult = withContext(Dispatchers.IO) {
        var lastError = "Tidak ada respons"
        for (url in endpoints) {
            val host = url.substringAfter("://").substringBefore('/')
            try {
                val started = System.nanoTime()
                client.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use { response ->
                    val elapsed = (System.nanoTime() - started) / 1_000_000
                    if (response.code == 204 || response.isSuccessful) {
                        return@withContext ProbeResult(true, elapsed, host, "HTTP ${response.code}", System.currentTimeMillis())
                    }
                    lastError = "HTTP ${response.code} dari $host (kemungkinan portal login)"
                }
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message ?: "gagal terhubung"}"
            }
        }
        ProbeResult(false, 0, "", lastError, System.currentTimeMillis())
    }
}
