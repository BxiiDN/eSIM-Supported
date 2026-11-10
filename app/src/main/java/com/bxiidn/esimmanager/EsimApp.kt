package com.bxiidn.esimmanager

import android.app.Application
import androidx.room.Room
import com.bxiidn.esimmanager.core.SecureCipher
import com.bxiidn.esimmanager.core.SecureStore
import com.bxiidn.esimmanager.data.ProfileRepository
import com.bxiidn.esimmanager.data.db.AppDatabase
import com.bxiidn.esimmanager.device.DeviceCapabilities
import com.bxiidn.esimmanager.device.EuiccController
import com.bxiidn.esimmanager.net.ConnectivityMonitor
import com.bxiidn.esimmanager.net.DataUsageReader
import com.bxiidn.esimmanager.net.InternetProbe
import com.bxiidn.esimmanager.provider.ProviderRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class EsimApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Dependensi tingkat aplikasi (DI manual, tanpa framework). */
class AppContainer(app: Application) {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val cipher = SecureCipher()
    val secureStore = SecureStore(app, cipher)

    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "esim_manager.db").build()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    val providers = ProviderRegistry(secureStore, http)
    val profiles = ProfileRepository(db, cipher, providers)
    val capabilities = DeviceCapabilities(app)
    val euicc = EuiccController(app)
    val connectivity = ConnectivityMonitor(app)
    val dataUsage = DataUsageReader(app)
    val probe = InternetProbe(http)
}
