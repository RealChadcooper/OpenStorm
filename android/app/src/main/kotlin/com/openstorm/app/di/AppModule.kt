package com.openstorm.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.openstorm.app.BuildConfig
import com.openstorm.core.data.local.OpenStormDatabase
import com.openstorm.core.data.local.dao.AlertDao
import com.openstorm.core.data.local.dao.RadarStationDao
import com.openstorm.core.data.remote.OpenStormApi
import com.openstorm.core.data.repository.AlertRepositoryImpl
import com.openstorm.core.data.repository.PreferencesRepositoryImpl
import com.openstorm.core.data.repository.RadarRepositoryImpl
import com.openstorm.core.domain.repository.AlertRepository
import com.openstorm.core.domain.repository.PreferencesRepository
import com.openstorm.core.domain.repository.RadarRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL + "/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideOpenStormApi(retrofit: Retrofit): OpenStormApi =
        retrofit.create(OpenStormApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OpenStormDatabase =
        Room.databaseBuilder(context, OpenStormDatabase::class.java, "openstorm.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRadarStationDao(db: OpenStormDatabase): RadarStationDao = db.radarStationDao()

    @Provides
    fun provideAlertDao(db: OpenStormDatabase): AlertDao = db.alertDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("openstorm_prefs")
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRadarRepository(impl: RadarRepositoryImpl): RadarRepository

    @Binds
    @Singleton
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository
}
