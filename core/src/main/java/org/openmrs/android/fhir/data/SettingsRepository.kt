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
package org.openmrs.android.fhir.data

import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.openmrs.android.fhir.DemoDataStore
import org.openmrs.android.fhir.data.sync.SyncMode
import org.openmrs.android.fhir.multiuser.UserRegistry

interface SettingsRepository {
  suspend fun getNetworkStatusVisible(): Boolean

  suspend fun getNotificationsEnabled(): Boolean

  suspend fun getTokenExpiryDelayMinutes(): Int

  suspend fun getPeriodicSyncDelayMinutes(): Int

  suspend fun setNetworkStatusVisible(enabled: Boolean)

  suspend fun setNotificationsEnabled(enabled: Boolean)

  suspend fun setTokenExpiryDelayMinutes(minutes: Int)

  suspend fun setPeriodicSyncDelayMinutes(minutes: Int)

  suspend fun getAutoSyncEnabled(): Boolean = true

  suspend fun setAutoSyncEnabled(enabled: Boolean) {}

  /** What the automatic sync does: [SyncMode.UPLOAD_ONLY] or [SyncMode.BOTH] (the default). */
  suspend fun getAutoSyncMode(): SyncMode = SyncMode.BOTH

  suspend fun setAutoSyncMode(mode: SyncMode) {}

  suspend fun getLiveModeEnabled(): Boolean = false

  suspend fun setLiveModeEnabled(enabled: Boolean) {}

  suspend fun getKeepLivePatients(): Boolean = true

  suspend fun setKeepLivePatients(keep: Boolean) {}

  suspend fun getAutomaticVisits(): Boolean = true

  suspend fun setAutomaticVisits(automatic: Boolean) {}

  /** The user signed in now (none for a guest): the setting below is theirs. */
  suspend fun getSignedInUser(): String? = null

  /** Whether the signed-in user lets the other users of the device see the patients they have. */
  suspend fun getShareMyData(): Boolean = false

  suspend fun setShareMyData(share: Boolean) {}

  /** Whether the signed-in user goes back to the location they used last, after login. */
  suspend fun getRememberLocation(): Boolean = true

  suspend fun setRememberLocation(remember: Boolean) {}
}

class DemoSettingsRepository
@Inject
constructor(
  context: Context,
) : SettingsRepository {
  private val appContext = context.applicationContext
  private val dataStore = DemoDataStore(context)

  override suspend fun getNetworkStatusVisible(): Boolean {
    return dataStore.getCheckNetworkConnectivityFlow().first()
  }

  override suspend fun getNotificationsEnabled(): Boolean {
    return dataStore.getNotificationsEnabledFlow().first()
  }

  override suspend fun getTokenExpiryDelayMinutes(): Int {
    return (dataStore.getTokenExpiryDelay() / MILLIS_PER_MINUTE).toInt()
  }

  override suspend fun getPeriodicSyncDelayMinutes(): Int {
    return (dataStore.getPeriodicSyncDelay() / MILLIS_PER_MINUTE).toInt()
  }

  override suspend fun setNetworkStatusVisible(enabled: Boolean) {
    dataStore.setCheckNetworkConnectivity(enabled)
  }

  override suspend fun setNotificationsEnabled(enabled: Boolean) {
    dataStore.setNotificationsEnabled(enabled)
  }

  override suspend fun setTokenExpiryDelayMinutes(minutes: Int) {
    dataStore.saveTokenExpiryDelay(minutes.toString())
  }

  override suspend fun setPeriodicSyncDelayMinutes(minutes: Int) {
    dataStore.savePeriodicSyncDelay(minutes.toString())
  }

  override suspend fun getAutoSyncEnabled(): Boolean {
    return dataStore.getAutoSyncEnabledFlow().first()
  }

  override suspend fun setAutoSyncEnabled(enabled: Boolean) {
    dataStore.setAutoSyncEnabled(enabled)
  }

  override suspend fun getAutoSyncMode(): SyncMode = dataStore.getAutoSyncModeFlow().first()

  override suspend fun setAutoSyncMode(mode: SyncMode) {
    dataStore.setAutoSyncMode(mode)
  }

  override suspend fun getLiveModeEnabled(): Boolean {
    return dataStore.getLiveModeEnabledFlow().first()
  }

  override suspend fun setLiveModeEnabled(enabled: Boolean) {
    dataStore.setLiveModeEnabled(enabled)
  }

  override suspend fun getKeepLivePatients(): Boolean {
    return dataStore.getKeepLivePatientsFlow().first()
  }

  override suspend fun setKeepLivePatients(keep: Boolean) {
    dataStore.setKeepLivePatients(keep)
  }

  override suspend fun getAutomaticVisits(): Boolean {
    return dataStore.getAutomaticVisitsFlow().first()
  }

  override suspend fun setAutomaticVisits(automatic: Boolean) {
    dataStore.setAutomaticVisits(automatic)
  }

  override suspend fun getSignedInUser(): String? = UserRegistry.active(appContext)

  override suspend fun getShareMyData(): Boolean =
    UserRegistry.isSharing(appContext, UserRegistry.active(appContext))

  override suspend fun setShareMyData(share: Boolean) {
    UserRegistry.setSharing(appContext, UserRegistry.active(appContext), share)
  }

  override suspend fun getRememberLocation(): Boolean =
    UserRegistry.isRememberingLocation(appContext, UserRegistry.active(appContext))

  override suspend fun setRememberLocation(remember: Boolean) {
    UserRegistry.setRememberLocation(appContext, UserRegistry.active(appContext), remember)
  }

  private companion object {
    const val MILLIS_PER_MINUTE = 60000L
  }
}
