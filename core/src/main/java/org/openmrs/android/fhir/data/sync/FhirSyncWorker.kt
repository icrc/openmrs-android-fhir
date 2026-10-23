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
package org.openmrs.android.fhir.data.sync

import android.content.Context
import androidx.work.WorkerParameters
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.LocalChange
import com.google.android.fhir.db.LocalChangeResourceReference
import com.google.android.fhir.sync.DownloadWorkManager
import com.google.android.fhir.sync.download.DownloadRequest
import com.google.android.fhir.sync.upload.SyncUploadProgress
import com.google.android.fhir.sync.upload.UploadRequestResult
import com.google.android.fhir.sync.upload.UploadStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.openmrs.android.fhir.FhirApplication

class FhirSyncWorker(appContext: Context, workerParams: WorkerParameters) :
  BaseFhirSyncWorker(appContext, workerParams) {

  override fun getDownloadWorkManager(): DownloadWorkManager {
    return TimestampBasedDownloadWorkManagerImpl(
      FhirApplication.dataStore(applicationContext),
      applicationContext,
    )
  }
}

class FirstFhirSyncWorker(appContext: Context, workerParams: WorkerParameters) :
  BaseFhirSyncWorker(appContext, workerParams) {

  override fun getDownloadWorkManager(): DownloadWorkManager =
    PreSyncDownloadWorkManagerImpl(applicationContext)
}

class LocationFhirSyncWorker(appContext: Context, workerParams: WorkerParameters) :
  BaseFhirSyncWorker(appContext, workerParams) {

  override fun getDownloadWorkManager(): DownloadWorkManager =
    LocationDownloadWorkManagerImpl(applicationContext)
}

/**
 * Downloads and sends nothing (the Sync page's "Download"): the upload phase of the sync does
 * nothing, so what waits on the device stays there.
 */
class DownloadOnlyFhirSyncWorker(appContext: Context, workerParams: WorkerParameters) :
  BaseFhirSyncWorker(appContext, workerParams) {

  override fun getDownloadWorkManager(): DownloadWorkManager {
    return TimestampBasedDownloadWorkManagerImpl(
      FhirApplication.dataStore(applicationContext),
      applicationContext,
    )
  }

  override fun getFhirEngine(): FhirEngine = SkipUploadFhirEngine(super.getFhirEngine())
}

/** The app's engine with an upload phase that has nothing to send. */
private class SkipUploadFhirEngine(private val delegate: FhirEngine) : FhirEngine by delegate {
  override suspend fun syncUpload(
    uploadStrategy: UploadStrategy,
    upload:
      suspend (List<LocalChange>, List<LocalChangeResourceReference>) -> Flow<UploadRequestResult>,
  ): Flow<SyncUploadProgress> = emptyFlow()
}

/** Sends what is waiting on the device and downloads nothing: the live upload ([LiveSync]). */
class UploadFhirSyncWorker(appContext: Context, workerParams: WorkerParameters) :
  BaseFhirSyncWorker(appContext, workerParams) {

  override fun getDownloadWorkManager(): DownloadWorkManager = NoDownloadWorkManager
}

private object NoDownloadWorkManager : DownloadWorkManager {
  override suspend fun getNextRequest(): DownloadRequest? = null

  override suspend fun getSummaryRequestUrls(): Map<ResourceType, String> = emptyMap()

  override suspend fun processResponse(response: Resource): Collection<Resource> = emptyList()
}
