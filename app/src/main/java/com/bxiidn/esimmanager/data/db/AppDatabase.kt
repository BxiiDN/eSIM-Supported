package com.bxiidn.esimmanager.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

object ProfileStatus {
    /** Disimpan di aplikasi, belum dipasang ke perangkat mana pun dari aplikasi ini. */
    const val SAVED = "SAVED"

    /** Sudah dibeli, penyedia belum selesai mengalokasikan profil. */
    const val PENDING = "PENDING"

    /** Berhasil diunduh ke eUICC perangkat ini melalui EuiccManager. */
    const val INSTALLED = "INSTALLED"
}

object ProfileSource {
    const val QR = "QR"
    const val IMAGE = "IMAGE"
    const val MANUAL = "MANUAL"
    const val STORE = "STORE"
}

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val carrier: String = "",
    val country: String = "",
    val smdpAddress: String = "",
    /** String LPA lengkap, terenkripsi (AES-GCM, Android Keystore). */
    val activationCodeEnc: String = "",
    val confirmationCodeEnc: String = "",
    val confirmationCodeRequired: Boolean = false,
    val iccid: String = "",
    val source: String = ProfileSource.MANUAL,
    val status: String = ProfileStatus.SAVED,
    val providerId: String = "",
    val remoteRef: String = "",
    val orderRef: String = "",
    val packageTitle: String = "",
    val dataTotalBytes: Long = 0,
    val dataUsedBytes: Long = 0,
    val unlimited: Boolean = false,
    val validityDays: Int = 0,
    val expiresAt: String = "",
    val remoteStatus: String = "",
    val qrCodeUrl: String = "",
    val apn: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastSyncedAt: Long = 0,
)

@Entity(tableName = "usage_snapshots", indices = [Index("profileId")])
data class UsageSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val profileLabel: String,
    val timestamp: Long,
    val usedBytes: Long,
    val totalBytes: Long,
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: String,
    val orderRef: String,
    val packageTitle: String,
    val countryName: String,
    val price: Double,
    val currency: String,
    val createdAt: Long,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observe(id: Long): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(snapshot: UsageSnapshotEntity): Long

    @Query("SELECT * FROM usage_snapshots WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT 60")
    fun observeFor(profileId: Long): Flow<List<UsageSnapshotEntity>>

    @Query("SELECT * FROM usage_snapshots ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<UsageSnapshotEntity>>

    @Query("DELETE FROM usage_snapshots WHERE profileId = :profileId")
    suspend fun deleteFor(profileId: Long)
}

@Dao
interface OrderDao {
    @Insert
    suspend fun insert(order: OrderEntity): Long

    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OrderEntity>>
}

@Database(
    entities = [ProfileEntity::class, UsageSnapshotEntity::class, OrderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun usageDao(): UsageDao
    abstract fun orderDao(): OrderDao
}
