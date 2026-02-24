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

interface SettingsRepository {
  suspend fun getNetworkStatusVisible(): Boolean

  suspend fun getNotificationsEnabled(): Boolean

  suspend fun getTokenExpiryDelayMinutes(): Int

  suspend fun getPeriodicSyncDelayMinutes(): Int

  suspend fun setNetworkStatusVisible(enabled: Boolean)

  suspend fun setNotificationsEnabled(enabled: Boolean)

  suspend fun setTokenExpiryDelayMinutes(minutes: Int)

  suspend fun setPeriodicSyncDelayMinutes(minutes: Int)
}

class DemoSettingsRepository
@Inject
constructor(
  context: Context,
) : SettingsRepository {
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

  private companion object {
    const val MILLIS_PER_MINUTE = 60000L
  }
}
