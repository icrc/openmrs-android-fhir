/*
* BSD 3-Clause License
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice, this
*    list of conditions and the following disclaimer.
*
* 2. Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*
* 3. Neither the name of the copyright holder nor the names of its
*    contributors may be used to endorse or promote products derived from
*    this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
* FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
* DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
* SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
* CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.openmrs.android.fhir.di

import android.content.Context
import androidx.room.Room
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineProvider
import dagger.Module
import dagger.Provides
import javax.inject.Singleton
import org.openmrs.android.fhir.data.IdentifierTypeManager
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.remote.Api
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.extensions.NotificationHelper
import org.openmrs.android.fhir.extensions.PermissionChecker
import org.openmrs.android.helpers.OpenMRSHelper

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
        AppDatabase::class.java,
        "openmrs_android_fhir",
      )
      .build()
  }

  @JvmStatic
  @Singleton
  @Provides
  fun provideAppApi(context: Context): Api {
    return ApiManager(context)
  }

  @JvmStatic
  @Singleton
  @Provides
  fun provideIdentifierTypeManager(
    context: Context,
    database: AppDatabase,
    apiManager: ApiManager,
  ): IdentifierTypeManager {
    return IdentifierTypeManager(context, database, apiManager)
  }

  @Provides
  @Singleton
  fun provideOpenMRSHelper(
    context: Context,
    fhirEngine: FhirEngine,
  ): OpenMRSHelper {
    return OpenMRSHelper(fhirEngine, context)
  }

  @Provides
  @Singleton
  fun provideNotificationHelper(context: Context): NotificationHelper {
    return NotificationHelper(context)
  }

  @Provides
  @Singleton
  fun providePermissionChecker(context: Context): PermissionChecker {
    return PermissionChecker(context)
  }
}
