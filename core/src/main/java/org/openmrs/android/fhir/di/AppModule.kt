package org.openmrs.android.fhir.di

import android.content.Context
import androidx.room.Room
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineProvider
import dagger.Module
import dagger.Provides
import org.openmrs.android.fhir.data.database.AppDatabase
import javax.inject.Singleton

@Module
object AppModule {

    @JvmStatic
    @Singleton
    @Provides
    fun provideAppFhirEngine(context: Context): FhirEngine {
        return FhirEngineProvider.getInstance(context)
    }

    @JvmStatic
    @Singleton
    @Provides
    fun provideDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java, "openmrs_android_fhir"
        ).build()
    }

}