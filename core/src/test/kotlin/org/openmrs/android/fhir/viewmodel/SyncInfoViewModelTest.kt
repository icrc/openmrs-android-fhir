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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.database.Dao
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.data.database.model.SyncStatus

@OptIn(ExperimentalCoroutinesApi::class)
class SyncInfoViewModelTest {

  @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

  private val dispatcher = StandardTestDispatcher()
  private val dao: Dao = mock(Dao::class.java)
  private val database: AppDatabase = mock(AppDatabase::class.java)

  private lateinit var viewModel: SyncInfoViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    whenever(database.dao()).thenReturn(dao)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun syncSessions_exposesDaoFlow() =
    runTest(dispatcher) {
      val sessions =
        listOf(
          SyncSession(
            id = 1,
            startTime = "10:00",
            downloadedPatients = 1,
            totalPatientsToDownload = 2,
            uploadedPatients = 0,
            totalPatientsToUpload = 1,
            completionTime = null,
            status = SyncStatus.ONGOING,
          ),
        )
      whenever(dao.getAllSyncSessions()).thenReturn(flowOf(sessions))

      viewModel = SyncInfoViewModel(database)

      val liveDataValue = viewModel.syncSessions.getOrAwaitValue { advanceUntilIdle() }

      assertEquals(sessions, liveDataValue)
    }

  @Test
  fun deleteSyncSession_delegatesToDao() =
    runTest(dispatcher) {
      whenever(dao.getAllSyncSessions()).thenReturn(emptyFlow())
      val session =
        SyncSession(
          id = 2,
          startTime = "11:00",
          downloadedPatients = 0,
          totalPatientsToDownload = 0,
          uploadedPatients = 0,
          totalPatientsToUpload = 0,
          completionTime = null,
          status = SyncStatus.COMPLETED,
        )
      viewModel = SyncInfoViewModel(database)

      viewModel.deleteSyncSession(session)
      advanceUntilIdle()

      verify(dao).deleteSyncSession(session.id)
    }

  @Test
  fun clearAllSyncSessions_callsDao() =
    runTest(dispatcher) {
      whenever(dao.getAllSyncSessions()).thenReturn(emptyFlow())
      viewModel = SyncInfoViewModel(database)

      viewModel.clearAllSyncSessions()
      advanceUntilIdle()

      verify(dao).clearAllSyncSessionsExceptOngoing()
    }
}

private fun <T> androidx.lifecycle.LiveData<T>.getOrAwaitValue(
  time: Long = 2,
  timeUnit: TimeUnit = TimeUnit.SECONDS,
  afterObserve: () -> Unit = {},
): T? {
  var data: T? = null
  val latch = CountDownLatch(1)
  val observer =
    androidx.lifecycle.Observer<T> {
      data = it
      latch.countDown()
    }
  observeForever(observer)

  try {
    afterObserve()
    if (!latch.await(time, timeUnit)) {
      throw AssertionError("LiveData value was never set.")
    }
  } finally {
    removeObserver(observer)
  }

  return data
}
