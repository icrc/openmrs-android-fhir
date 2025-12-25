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
package org.openmrs.android.fhir.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

object ListContainerTestTags {
  const val ObservationList = "ObservationList"
  const val PatientList = "PatientList"
  const val EmptyState = "ListEmptyState"
  const val LoadingIndicator = "ListLoadingIndicator"
  const val RefreshIndicator = "ListRefreshIndicator"
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T> SwipeRefreshListContainer(
  items: List<T>,
  isLoading: Boolean,
  isRefreshing: Boolean,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
  listModifier: Modifier = Modifier,
  listTestTag: String,
  emptyContent: @Composable (() -> Unit) = {
    Text(
      modifier = Modifier.testTag(ListContainerTestTags.EmptyState),
      text = "No items available",
      style = MaterialTheme.typography.bodyMedium,
    )
  },
  contentPadding: PaddingValues = PaddingValues(0.dp),
  itemKey: ((T) -> Any)? = null,
  itemContent: @Composable (T) -> Unit,
) {
  val refreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = onRefresh)

  Box(modifier = modifier.fillMaxSize().pullRefresh(refreshState)) {
    if (items.isEmpty() && !isLoading) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { emptyContent() }
    } else {
      LazyColumn(
        modifier = listModifier.fillMaxSize().testTag(listTestTag),
        contentPadding = contentPadding,
      ) {
        if (itemKey == null) {
          items(items) { item -> itemContent(item) }
        } else {
          items(items, key = itemKey) { item -> itemContent(item) }
        }
      }
    }

    if (isLoading) {
      CircularProgressIndicator(
        modifier = Modifier.align(Alignment.Center).testTag(ListContainerTestTags.LoadingIndicator),
      )
    }

    PullRefreshIndicator(
      refreshing = isRefreshing,
      state = refreshState,
      modifier =
        Modifier.align(Alignment.TopCenter).testTag(ListContainerTestTags.RefreshIndicator),
    )
  }
}

@Composable
fun PatientListContainerScreen(
  patients: List<PatientListViewModel.PatientItem>,
  isLoading: Boolean,
  isRefreshing: Boolean,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
  emptyContent: @Composable (() -> Unit) = {
    Text(
      modifier = Modifier.testTag(ListContainerTestTags.EmptyState),
      text = "No patients found",
      style = MaterialTheme.typography.bodyMedium,
    )
  },
  itemContent: @Composable (PatientListViewModel.PatientItem) -> Unit,
) {
  SwipeRefreshListContainer(
    items = patients,
    isLoading = isLoading,
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier,
    listTestTag = ListContainerTestTags.PatientList,
    emptyContent = emptyContent,
    itemKey = { it.resourceId },
    itemContent = itemContent,
  )
}

@Composable
fun ObservationListContainerScreen(
  observations: List<PatientListViewModel.ObservationItem>,
  isLoading: Boolean,
  isRefreshing: Boolean,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
  emptyContent: @Composable (() -> Unit) = {
    Text(
      modifier = Modifier.testTag(ListContainerTestTags.EmptyState),
      text = "No observations found",
      style = MaterialTheme.typography.bodyMedium,
    )
  },
  itemContent: @Composable (PatientListViewModel.ObservationItem) -> Unit,
) {
  SwipeRefreshListContainer(
    items = observations,
    isLoading = isLoading,
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier,
    listModifier = Modifier.padding(horizontal = 16.dp),
    listTestTag = ListContainerTestTags.ObservationList,
    emptyContent = emptyContent,
    itemKey = { it.id },
    itemContent = itemContent,
  )
}
