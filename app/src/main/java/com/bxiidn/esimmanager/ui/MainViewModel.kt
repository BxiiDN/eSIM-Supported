package com.bxiidn.esimmanager.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bxiidn.esimmanager.EsimApp
import com.bxiidn.esimmanager.data.NewProfileInput
import com.bxiidn.esimmanager.data.db.ProfileEntity
import com.bxiidn.esimmanager.data.db.UsageSnapshotEntity
import com.bxiidn.esimmanager.device.DeviceReport
import com.bxiidn.esimmanager.device.EuiccEvent
import com.bxiidn.esimmanager.device.EuiccEvents
import com.bxiidn.esimmanager.device.EuiccStart
import com.bxiidn.esimmanager.device.SystemSubscriptions
import com.bxiidn.esimmanager.net.BootUsage
import com.bxiidn.esimmanager.net.DailyUsage
import com.bxiidn.esimmanager.net.NetworkSnapshot
import com.bxiidn.esimmanager.net.ProbeResult
import com.bxiidn.esimmanager.provider.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UsageState(
    val hasAccess: Boolean = false,
    val daily: List<DailyUsage> = emptyList(),
    val boot: BootUsage = BootUsage(0, 0),
    val loading: Boolean = false,
)

data class ProbeState(
    val running: Boolean = false,
    val result: ProbeResult? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as EsimApp).container

    val network: StateFlow<NetworkSnapshot> = container.connectivity.snapshot
    val euiccEvent: StateFlow<EuiccEvent?> = EuiccEvents.latest

    val profiles: StateFlow<List<ProfileEntity>> = container.profiles.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentSnapshots: StateFlow<List<UsageSnapshotEntity>> = container.profiles.observeRecentUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _report = MutableStateFlow(container.capabilities.build())
    val report: StateFlow<DeviceReport> = _report.asStateFlow()

    private val _systemSubs = MutableStateFlow(container.capabilities.systemSubscriptions())
    val systemSubs: StateFlow<SystemSubscriptions> = _systemSubs.asStateFlow()

    private val _usage = MutableStateFlow(UsageState())
    val usage: StateFlow<UsageState> = _usage.asStateFlow()

    private val _probe = MutableStateFlow(ProbeState())
    val probe: StateFlow<ProbeState> = _probe.asStateFlow()

    private val _busy = MutableStateFlow<Set<Long>>(emptySet())
    val busyProfiles: StateFlow<Set<Long>> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshUsage()
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun refreshAll() {
        refreshDevice()
        refreshUsage()
        container.connectivity.refresh()
    }

    fun refreshDevice() {
        _report.value = container.capabilities.build()
        _systemSubs.value = container.capabilities.systemSubscriptions()
        container.connectivity.refresh()
    }

    fun refreshNetwork() = container.connectivity.refresh()

    fun refreshUsage() {
        viewModelScope.launch {
            _usage.update { it.copy(loading = true) }
            val state = withContext(Dispatchers.IO) {
                val access = container.dataUsage.hasUsageAccess()
                UsageState(
                    hasAccess = access,
                    daily = if (access) container.dataUsage.daily(7) else emptyList(),
                    boot = container.dataUsage.sinceBoot(),
                    loading = false,
                )
            }
            _usage.value = state
        }
    }

    fun runProbe() {
        if (_probe.value.running) return
        viewModelScope.launch {
            _probe.update { it.copy(running = true) }
            val result = container.probe.run()
            _probe.value = ProbeState(running = false, result = result)
        }
    }

    // ---- Profil -------------------------------------------------------------

    fun profile(id: Long): Flow<ProfileEntity?> = container.profiles.observe(id)

    fun usageHistory(id: Long): Flow<List<UsageSnapshotEntity>> = container.profiles.observeUsage(id)

    fun revealActivationCode(profile: ProfileEntity): String = container.profiles.revealActivationCode(profile)

    fun revealConfirmationCode(profile: ProfileEntity): String = container.profiles.revealConfirmationCode(profile)

    fun addProfile(input: NewProfileInput, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = container.profiles.add(input)
            _message.value = "Profil disimpan."
            onSaved(id)
        }
    }

    fun updateProfile(id: Long, label: String, carrier: String, country: String, notes: String) {
        viewModelScope.launch {
            container.profiles.updateMeta(id, label, carrier, country, notes)
            _message.value = "Perubahan disimpan."
        }
    }

    fun deleteProfile(id: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            container.profiles.delete(id)
            _message.value = "Profil dihapus dari aplikasi."
            onDeleted()
        }
    }

    /** Sinkronisasi pemakaian / alokasi profil dengan API penyedia. */
    fun syncProfile(id: Long) {
        if (id in _busy.value) return
        viewModelScope.launch {
            _busy.update { it + id }
            _message.value = try {
                withContext(Dispatchers.IO) { container.profiles.syncRemote(id) }
            } catch (e: Exception) {
                e.userMessage()
            }
            _busy.update { it - id }
        }
    }

    // ---- EuiccManager -------------------------------------------------------

    /** Meminta LPA sistem mengunduh profil; hasilnya datang lewat [euiccEvent]. */
    fun installOnDevice(profile: ProfileEntity) {
        val code = container.profiles.revealActivationCode(profile)
        when (val start = container.euicc.download(code, profile.id)) {
            is EuiccStart.Started -> _message.value = "Permintaan unduh dikirim ke sistem. Menunggu hasil…"
            is EuiccStart.Unavailable -> _message.value = start.reason
        }
    }

    fun deleteSystemSubscription(subscriptionId: Int) {
        when (val start = container.euicc.delete(subscriptionId)) {
            is EuiccStart.Started -> _message.value = "Permintaan hapus dikirim ke sistem. Menunggu hasil…"
            is EuiccStart.Unavailable -> _message.value = start.reason
        }
    }

    fun resolveEuicc(activity: Activity, event: EuiccEvent) {
        EuiccEvents.consume()
        if (!container.euicc.resolve(activity, event)) {
            _message.value = "Tidak dapat membuka layar persetujuan sistem."
        }
    }

    fun dismissEuiccEvent() {
        EuiccEvents.consume()
        refreshDevice()
    }

    fun openSystemProvisioning(activity: Activity, profile: ProfileEntity) {
        val code = container.profiles.revealActivationCode(profile)
        if (!container.euicc.openSystemProvisioning(activity, code)) {
            _message.value = "Penyiapan eSIM sistem tidak tersedia di perangkat ini."
        }
    }

    fun openSystemEsimSettings(activity: Activity) {
        if (!container.euicc.openSystemEsimSettings(activity)) {
            _message.value = "Setelan eSIM sistem tidak dapat dibuka."
        }
    }
}
