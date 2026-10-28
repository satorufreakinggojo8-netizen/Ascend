package com.cadence.fit

import android.app.Application
import android.content.Context
import com.cadence.fit.data.BackupService
import com.cadence.fit.data.CadenceDatabase
import com.cadence.fit.data.CadenceRepository
import com.cadence.fit.data.PreferencesStore
import com.cadence.fit.data.SharedPreferencesStore
import com.cadence.fit.domain.core.SystemClock
import com.cadence.fit.domain.sleep.SleepRepository
import com.cadence.fit.integrations.healthconnect.AndroidHealthConnectGateway
import com.cadence.fit.integrations.healthconnect.HealthConnectSleepDataSource

/**
 * Manual dependency wiring. The app is small enough that a DI framework would add
 * more indirection than it removes, and every dependency is a plain constructor
 * argument, which keeps the layers swappable in tests.
 */
class AppContainer(context: Context) {

    private val database = CadenceDatabase(context)

    val preferences = PreferencesStore(
        SharedPreferencesStore(
            context.getSharedPreferences(PreferencesStore.FILE_NAME, Context.MODE_PRIVATE),
        ),
    )

    val repository = CadenceRepository(
        database = database,
        preferences = preferences,
        appVersion = BuildConfig.VERSION_NAME,
    )

    private val sleepGateway = AndroidHealthConnectGateway(context)

    val sleepRepository = SleepRepository(
        source = HealthConnectSleepDataSource(sleepGateway),
        stateStore = preferences,
        clock = SystemClock,
    )

    val backupService = BackupService(context)

    fun close() {
        database.close()
    }
}

class CadenceApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTerminate() {
        container.close()
        super.onTerminate()
    }
}
