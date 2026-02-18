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
package org.openmrs.android.fhir.viewmodel

import com.google.android.fhir.sync.CurrentSyncJobStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.openmrs.android.fhir.data.SettingsRepository
import org.openmrs.android.fhir.data.sync.InitialSyncRunner

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadSettingsPopulatesUiState() = runTest {
    val repository =
      FakeSettingsRepository(
        networkStatusVisible = false,
        notificationsEnabled = false,
        tokenDelayMinutes = 4,
        periodicDelayMinutes = 25,
      )
    val viewModel =
      SettingsViewModel(
        settingsRepository = repository,
        initialSyncRunner = FakeInitialSyncRunner(),
      )

    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isNetworkStatusVisible)
    assertFalse(state.isNotificationsEnabled)
    assertEquals(4, state.tokenCheckDelayMinutes)
    assertEquals(25, state.periodicSyncDelayMinutes)
  }

  @Test
  fun savePersistsUpdatedValuesAndEmitsEvent() = runTest {
    val repository = FakeSettingsRepository()
    val viewModel =
      SettingsViewModel(
        settingsRepository = repository,
        initialSyncRunner = FakeInitialSyncRunner(),
      )

    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onNetworkStatusToggle(true)
    viewModel.onNotificationsToggle(true)
    viewModel.onTokenDelaySelected(10)
    viewModel.onPeriodicSyncDelaySelected(30)

    val event = async { viewModel.events.first() }

    viewModel.onSaveClicked()

    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(true, repository.networkStatusVisible)
    assertEquals(true, repository.notificationsEnabled)
    assertEquals(10, repository.tokenDelayMinutes)
    assertEquals(30, repository.periodicDelayMinutes)
    assertEquals(SettingsEvent.SettingsSaved, event.await())
  }

  private class FakeSettingsRepository(
    var networkStatusVisible: Boolean = true,
    var notificationsEnabled: Boolean = true,
    var tokenDelayMinutes: Int = 1,
    var periodicDelayMinutes: Int = 15,
  ) : SettingsRepository {
    override suspend fun getNetworkStatusVisible(): Boolean = networkStatusVisible

    override suspend fun getNotificationsEnabled(): Boolean = notificationsEnabled

    override suspend fun getTokenExpiryDelayMinutes(): Int = tokenDelayMinutes

    override suspend fun getPeriodicSyncDelayMinutes(): Int = periodicDelayMinutes

    override suspend fun setNetworkStatusVisible(enabled: Boolean) {
      networkStatusVisible = enabled
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
      notificationsEnabled = enabled
    }

    override suspend fun setTokenExpiryDelayMinutes(minutes: Int) {
      tokenDelayMinutes = minutes
    }

    override suspend fun setPeriodicSyncDelayMinutes(minutes: Int) {
      periodicDelayMinutes = minutes
    }
  }

  private class FakeInitialSyncRunner : InitialSyncRunner {
    override suspend fun runInitialSync(): Flow<CurrentSyncJobStatus> = emptyFlow()
  }
}
