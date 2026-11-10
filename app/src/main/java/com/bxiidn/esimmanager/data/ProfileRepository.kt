package com.bxiidn.esimmanager.data

import com.bxiidn.esimmanager.core.SecureCipher
import com.bxiidn.esimmanager.data.db.AppDatabase
import com.bxiidn.esimmanager.data.db.OrderEntity
import com.bxiidn.esimmanager.data.db.ProfileEntity
import com.bxiidn.esimmanager.data.db.ProfileSource
import com.bxiidn.esimmanager.data.db.ProfileStatus
import com.bxiidn.esimmanager.data.db.UsageSnapshotEntity
import com.bxiidn.esimmanager.lpa.LpaActivationCode
import com.bxiidn.esimmanager.provider.ProviderException
import com.bxiidn.esimmanager.provider.ProviderId
import com.bxiidn.esimmanager.provider.ProviderRegistry
import com.bxiidn.esimmanager.provider.PurchaseResult
import com.bxiidn.esimmanager.provider.PurchasedEsim
import com.bxiidn.esimmanager.provider.RemoteRef
import com.bxiidn.esimmanager.provider.StorePackage
import kotlinx.coroutines.flow.Flow

data class NewProfileInput(
    val code: LpaActivationCode,
    val label: String,
    val carrier: String,
    val country: String,
    val confirmationCode: String,
    val notes: String,
    val source: String,
)

/** Penyimpanan profil eSIM (Room) dengan kode aktivasi terenkripsi. */
class ProfileRepository(
    db: AppDatabase,
    private val cipher: SecureCipher,
    private val providers: ProviderRegistry,
) {
    private val profileDao = db.profileDao()
    private val usageDao = db.usageDao()
    private val orderDao = db.orderDao()

    fun observeAll(): Flow<List<ProfileEntity>> = profileDao.observeAll()
    fun observe(id: Long): Flow<ProfileEntity?> = profileDao.observe(id)
    fun observeUsage(profileId: Long): Flow<List<UsageSnapshotEntity>> = usageDao.observeFor(profileId)
    fun observeRecentUsage(): Flow<List<UsageSnapshotEntity>> = usageDao.observeRecent(30)
    fun observeOrders(): Flow<List<OrderEntity>> = orderDao.observeAll()

    fun revealActivationCode(profile: ProfileEntity): String = cipher.decrypt(profile.activationCodeEnc)
    fun revealConfirmationCode(profile: ProfileEntity): String = cipher.decrypt(profile.confirmationCodeEnc)

    suspend fun get(id: Long): ProfileEntity? = profileDao.get(id)

    suspend fun add(input: NewProfileInput): Long {
        val now = System.currentTimeMillis()
        return profileDao.insert(
            ProfileEntity(
                label = input.label.trim().ifBlank { input.carrier.trim().ifBlank { input.code.smdpAddress } },
                carrier = input.carrier.trim(),
                country = input.country.trim(),
                smdpAddress = input.code.smdpAddress,
                activationCodeEnc = cipher.encrypt(input.code.toLpaString()),
                confirmationCodeEnc = cipher.encrypt(input.confirmationCode.trim()),
                confirmationCodeRequired = input.code.confirmationCodeRequired,
                source = input.source,
                status = ProfileStatus.SAVED,
                notes = input.notes.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun ProfileEntity.withPurchased(esim: PurchasedEsim, now: Long): ProfileEntity {
        val smdp = when (val parsed = LpaActivationCode.parse(esim.lpaString)) {
            is LpaActivationCode.ParseResult.Ok -> parsed.code.smdpAddress
            is LpaActivationCode.ParseResult.Error -> ""
        }
        return copy(
            smdpAddress = smdp,
            activationCodeEnc = cipher.encrypt(esim.lpaString),
            iccid = esim.iccid,
            status = ProfileStatus.SAVED,
            remoteRef = esim.remoteRef,
            dataTotalBytes = if (esim.totalBytes > 0) esim.totalBytes else dataTotalBytes,
            expiresAt = esim.expiresAt,
            remoteStatus = esim.status,
            qrCodeUrl = esim.qrCodeUrl,
            apn = esim.apn,
            updatedAt = now,
        )
    }

    /** Mencatat pesanan dan menyimpan profil hasil pembelian. Mengembalikan id profil. */
    suspend fun savePurchase(pkg: StorePackage, result: PurchaseResult): List<Long> {
        val now = System.currentTimeMillis()
        orderDao.insert(
            OrderEntity(
                providerId = pkg.provider.name,
                orderRef = result.orderRef,
                packageTitle = pkg.title,
                countryName = pkg.countryName,
                price = pkg.price,
                currency = pkg.currency,
                createdAt = now,
            ),
        )
        val base = ProfileEntity(
            label = pkg.title,
            carrier = pkg.operatorName,
            country = pkg.countryCode.ifBlank { pkg.countryName },
            source = ProfileSource.STORE,
            status = ProfileStatus.PENDING,
            providerId = pkg.provider.name,
            orderRef = result.orderRef,
            packageTitle = pkg.title,
            dataTotalBytes = pkg.dataBytes,
            unlimited = pkg.unlimited,
            validityDays = pkg.validityDays,
            createdAt = now,
            updatedAt = now,
        )
        if (result.esims.isEmpty()) return listOf(profileDao.insert(base))
        return result.esims.map { profileDao.insert(base.withPurchased(it, now)) }
    }

    /**
     * Sinkronisasi dengan penyedia: mengambil profil yang alokasinya tertunda,
     * atau memperbarui pemakaian data dan mencatatnya ke riwayat.
     */
    suspend fun syncRemote(id: Long): String {
        val profile = profileDao.get(id) ?: throw ProviderException("Profil tidak ditemukan.")
        val providerId = runCatching { ProviderId.valueOf(profile.providerId) }.getOrNull()
            ?: throw ProviderException("Profil ini tidak terhubung ke penyedia mana pun.")
        val provider = providers.provider(providerId)
            ?: throw ProviderException("Kredensial ${providerId.label} belum diatur di Pengaturan.")
        val now = System.currentTimeMillis()

        if (profile.status == ProfileStatus.PENDING) {
            val esims = provider.fetchOrder(profile.orderRef)
            val first = esims.firstOrNull()
                ?: throw ProviderException("Penyedia belum selesai mengalokasikan profil. Coba lagi sebentar.")
            profileDao.update(profile.withPurchased(first, now).copy(lastSyncedAt = now))
            return "Profil eSIM diterima dari ${providerId.label}."
        }

        val usage = provider.usage(RemoteRef(profile.iccid, profile.remoteRef, profile.orderRef))
        profileDao.update(
            profile.copy(
                dataTotalBytes = if (usage.totalBytes > 0) usage.totalBytes else profile.dataTotalBytes,
                dataUsedBytes = usage.usedBytes,
                unlimited = usage.unlimited,
                expiresAt = usage.expiresAt.ifBlank { profile.expiresAt },
                remoteStatus = usage.status.ifBlank { profile.remoteStatus },
                updatedAt = now,
                lastSyncedAt = now,
            ),
        )
        usageDao.insert(
            UsageSnapshotEntity(
                profileId = profile.id,
                profileLabel = profile.label,
                timestamp = now,
                usedBytes = usage.usedBytes,
                totalBytes = if (usage.totalBytes > 0) usage.totalBytes else profile.dataTotalBytes,
            ),
        )
        return "Pemakaian data diperbarui."
    }

    suspend fun updateMeta(id: Long, label: String, carrier: String, country: String, notes: String) {
        val profile = profileDao.get(id) ?: return
        profileDao.update(
            profile.copy(
                label = label.trim().ifBlank { profile.label },
                carrier = carrier.trim(),
                country = country.trim(),
                notes = notes.trim(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun markInstalled(id: Long) {
        val profile = profileDao.get(id) ?: return
        profileDao.update(profile.copy(status = ProfileStatus.INSTALLED, updatedAt = System.currentTimeMillis()))
    }

    /** Menghapus catatan profil dari aplikasi ini saja (tidak menyentuh eUICC maupun penyedia). */
    suspend fun delete(id: Long) {
        usageDao.deleteFor(id)
        profileDao.delete(id)
    }
}
