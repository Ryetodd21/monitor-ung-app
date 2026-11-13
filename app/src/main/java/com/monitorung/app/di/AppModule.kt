package com.monitorung.app.di

import androidx.core.content.edit
import android.content.Context
import androidx.room.Room
import com.monitorung.app.BuildConfig
import com.monitorung.app.data.local.MonitorUNGDatabase
import com.monitorung.app.data.local.dao.*
import com.monitorung.app.data.lock.UnlockTracker
import com.monitorung.app.data.push.MqttPushClient
import com.monitorung.app.data.remote.TokenAuthenticator
import com.monitorung.app.data.remote.api.MonitorUNGApi
import com.monitorung.app.data.repository.*
import com.monitorung.app.data.snapshot.UsageSnapshotStore
import com.monitorung.app.domain.repository.*
import com.monitorung.app.service.BatteryBasedSotManager
import com.monitorung.app.service.DataSyncManager
import com.monitorung.app.service.NotificationScheduler
import com.monitorung.app.service.SmartMonitoringManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.GsonBuilder
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val PREFS_NAME = "monitor_encrypted_prefs"
private const val KEY_DB_PASSPHRASE = "db_passphrase"
private const val PASSPHRASE_LENGTH = 32

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // ── Certificate pins for the production API host ─────────────────────────
    // The cert pin only enforces when the request URL hostname matches `PROD_HOST`
    // EXACTLY — OkHttp's CertificatePinner does not wildcard. So `PROD_HOST` MUST
    // equal the hostname in `BuildConfig.API_BASE_URL` (the prod flavor sets
    // `https://monitorterop.my.id`, so this constant is `monitorterop.my.id`).
    //
    // Pin format: "sha256/<base64-of-SHA256-of-SPKI-DER>" — NOT the hex fingerprint
    // of the certificate. The two are different; using hex here makes every
    // handshake fail with `SSLPeerUnverifiedException: Certificate pinning failure!`
    // because OkHttp base64-decodes the suffix before comparing.
    //
    // Dev flavor: pinning disabled so engineers can debug with proxies.
    // Prod flavor: pinning ENABLED — never release without valid pins.
    //
    // HOW TO GET THE PINS (one-liner, copy-paste):
    //   openssl s_client -connect monitorterop.my.id:443 \
    //     -servername monitorterop.my.id </dev/null 2>/dev/null \
    //     | openssl x509 -noout -pubkey \
    //     | openssl pkey -pubin -outform der \
    //     | openssl dgst -sha256 -binary \
    //     | base64 \
    //     | awk '{print "sha256/" $0}'
    //
    // For the intermediate cert (acts as a long-lived backup across LE rotation
    // cycles — LE intermediates last years):
    //   openssl s_client -connect monitorterop.my.id:443 \
    //     -servername monitorterop.my.id -showcerts </dev/null 2>/dev/null \
    //     | awk '/-----BEGIN CERTIFICATE-----/{c++} c==2{print} /-----END CERTIFICATE-----/{if(c==2){exit}}' \
    //     | openssl x509 -noout -pubkey \
    //     | openssl pkey -pubin -outform der \
    //     | openssl dgst -sha256 -binary \
    //     | base64 \
    //     | awk '{print "sha256/" $0}'
    //
    // Rotation: update BACKUP first → deploy → test → update PRIMARY → deploy final.
    // Verified 2026-09-28 against live cert (issued 2026-09-23, expires 2026-12-22).
    private const val PROD_HOST = "monitorterop.my.id"
    // Primary: current leaf cert SPKI hash (rotates every ~90 days via LE).
    private const val PROD_PIN_PRIMARY = "sha256/eCowd58ZQwpQdkruvpUSQINacevQi+YSG64gB2qX8Rg="
    // Backup: Let's Encrypt YE2 intermediate SPKI hash (stable across leaf rotations).
    private const val PROD_PIN_BACKUP  = "sha256/s/tdAOmUzd8syaTuqfgGvFcn6DzA5Cmb+Vby1ST+U3Y="

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenAuthenticator: TokenAuthenticator): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            if (BuildConfig.DEBUG) android.util.Log.d("OkHttp", message)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            // Automatic 401 → refresh-token → retry. See TokenAuthenticator.kt.
            .authenticator(tokenAuthenticator)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // Certificate pinning: enabled for prod flavor only.
        // Dev flavor has this disabled so debugging with proxies works.
        if (BuildConfig.CERTIFICATE_PINNING_ENABLED) {
            val certificatePinner = CertificatePinner.Builder()
                .add(PROD_HOST, PROD_PIN_PRIMARY)
                .add(PROD_HOST, PROD_PIN_BACKUP)
                .build()
            builder.certificatePinner(certificatePinner)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        // Use a Gson instance that supports `null` Kotlin types and reflects
        // generic types faithfully. Without `serializeNulls()` and a configured
        // GsonBuilder, R8-stripped generic type parameters on suspend functions
        // (e.g. `Response<Map<String, String>>`, `Response<List<X>>`) can crash
        // with `java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType`
        // when Retrofit tries to recover the inner parameterized type via reflection.
        val gson = GsonBuilder()
            .serializeNulls()
            .setStrictness(com.google.gson.Strictness.LENIENT)
            .create()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideMonitorUNGApi(retrofit: Retrofit): MonitorUNGApi {
        return retrofit.create(MonitorUNGApi::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MonitorUNGDatabase {
        val encryptedPrefs = com.monitorung.app.util.SecurePrefs.get(context, PREFS_NAME)

        // Get or generate passphrase — stored encrypted via EncryptedSharedPreferences (backed by Android Keystore)
        val passphrase: ByteArray = if (encryptedPrefs.contains(KEY_DB_PASSPHRASE)) {
            android.util.Base64.decode(
                encryptedPrefs.getString(KEY_DB_PASSPHRASE, null),
                android.util.Base64.NO_WRAP
            )
        } else {
            val random = ByteArray(PASSPHRASE_LENGTH)
            SecureRandom().nextBytes(random)
            encryptedPrefs.edit {
                putString(KEY_DB_PASSPHRASE, android.util.Base64.encodeToString(random, android.util.Base64.NO_WRAP))
            }
            random
        }

        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(
            context,
            MonitorUNGDatabase::class.java,
            "monitor_ung.db"
        )
            .openHelperFactory(factory)
            // CRITICAL: never fall back to destructive recreate.
            //
            // `fallbackToDestructiveMigration()` silently drops ALL tables
            // (including `pending_logs` containing encrypted usage/location
            // rows that have not yet been synced to the server). Once the
            // schema version is bumped, those rows would be gone for good
            // before the next sync Window could upload them.
            //
            // Instead we declare the upgrade path explicitly via Migration
            // objects. Unsupported downgrades must fail instead of deleting pending data.
            .addMigrations(*MonitorUNGDatabase.MIGRATIONS)
            .build()
    }

    @Provides
    fun provideAppUsageLogDao(db: MonitorUNGDatabase): AppUsageLogDao = db.appUsageLogDao()

    @Provides
    fun provideLocationLogDao(db: MonitorUNGDatabase): LocationLogDao = db.locationLogDao()

    @Provides
    fun provideScheduleDao(db: MonitorUNGDatabase): ScheduleDao = db.scheduleDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository

    @Binds
    @Singleton
    abstract fun bindCampusConfigRepository(impl: CampusConfigRepositoryImpl): CampusConfigRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRequestRepository(impl: ScheduleRequestRepositoryImpl): ScheduleRequestRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSmartMonitoringManager(
        @ApplicationContext context: Context,
        scheduleRepository: ScheduleRepository,
        campusConfigRepository: CampusConfigRepository,
        authRepository: AuthRepository,
        appUsageLogDao: AppUsageLogDao,
        locationLogDao: LocationLogDao,
        unlockTracker: UnlockTracker,
        snapshotStore: UsageSnapshotStore,
        mqttPushClient: MqttPushClient,
    ): SmartMonitoringManager = SmartMonitoringManager(
        context, scheduleRepository, campusConfigRepository,
        authRepository, appUsageLogDao, locationLogDao, unlockTracker, snapshotStore, mqttPushClient
    )

    @Provides
    @Singleton
    fun provideNotificationScheduler(
        @ApplicationContext context: Context
    ): NotificationScheduler = NotificationScheduler(context)

    @Provides
    @Singleton
    fun provideDataSyncManager(
        @ApplicationContext context: Context,
        api: MonitorUNGApi,
        authRepository: AuthRepository,
        appUsageLogDao: AppUsageLogDao,
        locationLogDao: LocationLogDao,
        notificationScheduler: NotificationScheduler,
    ): DataSyncManager = DataSyncManager(
        context, api, authRepository,
        appUsageLogDao, locationLogDao, notificationScheduler
    )

    @Provides
    @Singleton
    fun provideBatteryBasedSotManager(
        @ApplicationContext context: Context,
        appUsageLogDao: AppUsageLogDao,
        authRepository: AuthRepository,
        unlockTracker: UnlockTracker,
        snapshotStore: UsageSnapshotStore,
    ): BatteryBasedSotManager = BatteryBasedSotManager(
        context, appUsageLogDao, authRepository, unlockTracker, snapshotStore
    )
}
