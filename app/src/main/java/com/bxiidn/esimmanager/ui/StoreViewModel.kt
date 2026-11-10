package com.bxiidn.esimmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bxiidn.esimmanager.EsimApp
import com.bxiidn.esimmanager.data.db.OrderEntity
import com.bxiidn.esimmanager.provider.AiraloCredentials
import com.bxiidn.esimmanager.provider.CatalogScope
import com.bxiidn.esimmanager.provider.ProviderBalance
import com.bxiidn.esimmanager.provider.ProviderId
import com.bxiidn.esimmanager.provider.StorePackage
import com.bxiidn.esimmanager.provider.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PurchaseOutcome(
    val success: Boolean,
    val title: String,
    val message: String,
    val profileId: Long = -1,
)

data class StoreState(
    val provider: ProviderId? = null,
    val scope: CatalogScope = CatalogScope.COUNTRY,
    val countryCode: String = "",
    val loading: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: String? = null,
    val packages: List<StorePackage> = emptyList(),
    val balance: ProviderBalance? = null,
    val purchasing: Boolean = false,
    val outcome: PurchaseOutcome? = null,
)

data class CredentialTest(
    val provider: ProviderId,
    val running: Boolean,
    val success: Boolean = false,
    val message: String = "",
)

class StoreViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as EsimApp).container
    private val registry = container.providers

    val configured: StateFlow<List<ProviderId>> = registry.configured

    val orders: StateFlow<List<OrderEntity>> = container.profiles.observeOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(StoreState(provider = registry.configured.value.firstOrNull()))
    val state: StateFlow<StoreState> = _state.asStateFlow()

    private val _test = MutableStateFlow<CredentialTest?>(null)
    val test: StateFlow<CredentialTest?> = _test.asStateFlow()

    private var loadJob: Job? = null

    /** Memastikan penyedia terpilih masih terkonfigurasi (dipanggil saat layar Store dibuka). */
    fun ensureProvider() {
        val available = registry.configured.value
        val current = _state.value.provider
        if (current == null || current !in available) {
            _state.value = StoreState(provider = available.firstOrNull())
        }
        if (_state.value.provider != null && _state.value.balance == null) refreshBalance()
    }

    fun selectProvider(id: ProviderId) {
        if (_state.value.provider == id) return
        loadJob?.cancel()
        _state.value = StoreState(provider = id, scope = _state.value.scope, countryCode = _state.value.countryCode)
        refreshBalance()
        if (_state.value.scope == CatalogScope.GLOBAL || _state.value.countryCode.isNotBlank()) load()
    }

    fun selectScope(scope: CatalogScope) {
        if (_state.value.scope == scope) return
        loadJob?.cancel()
        _state.update { it.copy(scope = scope, packages = emptyList(), loadedOnce = false, error = null, loading = false) }
        if (scope == CatalogScope.GLOBAL || _state.value.countryCode.isNotBlank()) load()
    }

    fun selectCountry(code: String) {
        _state.update { it.copy(countryCode = code, scope = CatalogScope.COUNTRY) }
        load()
    }

    /** Memuat katalog dari API penyedia. */
    fun load() {
        val snapshot = _state.value
        val providerId = snapshot.provider ?: return
        val provider = registry.provider(providerId) ?: return
        if (snapshot.scope == CatalogScope.COUNTRY && snapshot.countryCode.isBlank()) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val list = withContext(Dispatchers.IO) {
                    provider.listPackages(snapshot.scope, snapshot.countryCode).distinctBy { it.id }
                }
                _state.update { it.copy(loading = false, loadedOnce = true, packages = list, error = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadedOnce = true, packages = emptyList(), error = e.userMessage()) }
            }
        }
    }

    fun refreshBalance() {
        val providerId = _state.value.provider ?: return
        val provider = registry.provider(providerId) ?: return
        viewModelScope.launch {
            val balance = try {
                withContext(Dispatchers.IO) { provider.balance() }
            } catch (e: Exception) {
                null
            }
            _state.update { if (it.provider == providerId) it.copy(balance = balance) else it }
        }
    }

    /** Pembelian nyata melalui API penyedia; profil hasilnya disimpan terenkripsi. */
    fun purchase(pkg: StorePackage) {
        if (_state.value.purchasing) return
        val provider = registry.provider(pkg.provider) ?: return
        viewModelScope.launch {
            _state.update { it.copy(purchasing = true, outcome = null) }
            val outcome = try {
                val result = withContext(Dispatchers.IO) { provider.purchase(pkg) }
                val ids = container.profiles.savePurchase(pkg, result)
                if (result.esims.isEmpty()) {
                    PurchaseOutcome(
                        success = true,
                        title = "Pesanan dibuat",
                        message = "Pesanan ${result.orderRef} berhasil dibuat di ${pkg.provider.label}, tetapi penyedia masih " +
                            "mengalokasikan profil eSIM. Buka profil ini lalu tekan Sinkronkan beberapa saat lagi.",
                        profileId = ids.firstOrNull() ?: -1,
                    )
                } else {
                    PurchaseOutcome(
                        success = true,
                        title = "Pembelian berhasil",
                        message = "Pesanan ${result.orderRef} selesai. Profil eSIM tersimpan terenkripsi di aplikasi ini " +
                            "dan belum terpasang di perangkat mana pun.",
                        profileId = ids.firstOrNull() ?: -1,
                    )
                }
            } catch (e: Exception) {
                PurchaseOutcome(success = false, title = "Pembelian gagal", message = e.userMessage())
            }
            _state.update { it.copy(purchasing = false, outcome = outcome) }
            refreshBalance()
        }
    }

    fun consumeOutcome() {
        _state.update { it.copy(outcome = null) }
    }

    // ---- Kredensial ---------------------------------------------------------

    fun esimAccessCode(): String = registry.esimAccessCode()

    fun airaloCredentials(): AiraloCredentials = registry.airaloCredentials()

    fun saveEsimAccess(accessCode: String) {
        registry.saveEsimAccess(accessCode)
        testConnection(ProviderId.ESIM_ACCESS)
    }

    fun saveAiralo(clientId: String, clientSecret: String, sandbox: Boolean) {
        registry.saveAiralo(clientId, clientSecret, sandbox)
        testConnection(ProviderId.AIRALO)
    }

    fun clearCredentials(id: ProviderId) {
        registry.clear(id)
        _test.value = null
        if (_state.value.provider == id) {
            _state.value = StoreState(provider = registry.configured.value.firstOrNull())
        }
    }

    fun testConnection(id: ProviderId) {
        val provider = registry.provider(id)
        if (provider == null) {
            _test.value = CredentialTest(id, running = false, success = false, message = "Kredensial belum lengkap.")
            return
        }
        viewModelScope.launch {
            _test.value = CredentialTest(id, running = true)
            _test.value = try {
                val message = withContext(Dispatchers.IO) { provider.testConnection() }
                CredentialTest(id, running = false, success = true, message = message)
            } catch (e: Exception) {
                CredentialTest(id, running = false, success = false, message = e.userMessage())
            }
        }
    }
}
