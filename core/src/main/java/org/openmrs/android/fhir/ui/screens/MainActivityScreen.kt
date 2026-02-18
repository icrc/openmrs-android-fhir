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
package org.openmrs.android.fhir.ui.screens

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.appbar.MaterialToolbar
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.components.DrawerHeader
import org.openmrs.android.fhir.ui.components.NetworkStatusBanner

@Stable
data class DrawerItem(
  val id: Int,
  @DrawableRes val iconRes: Int,
  val title: String,
  val testTag: String,
  val enabled: Boolean = true,
)

@Stable
data class SyncProgressState(
  val current: Int,
  val total: Int,
)

@Composable
fun MainActivityScaffold(
  networkStatusText: String,
  isNetworkStatusVisible: Boolean,
  onToolbarReady: (MaterialToolbar) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth().statusBarsPadding()) {
    if (isNetworkStatusVisible) {
      NetworkStatusBanner(text = networkStatusText)
    }
    ToolbarHost(onToolbarReady = onToolbarReady)
  }
}

@Composable
fun DrawerContent(
  lastSyncText: String,
  drawerItems: List<DrawerItem>,
  onDrawerItemSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .background(colorResource(id = R.color.white))
        .verticalScroll(rememberScrollState())
        .padding(bottom = 16.dp),
  ) {
    DrawerHeader(label = stringResource(R.string.last_sync), lastSyncValue = lastSyncText)
    Divider()
    drawerItems.forEach { item ->
      NavigationDrawerItem(
        modifier =
          Modifier.fillMaxWidth().alpha(if (item.enabled) 1f else 0.6f).testTag(item.testTag),
        label = { Text(text = item.title) },
        icon = { IconResource(iconRes = item.iconRes) },
        selected = false,
        onClick = { if (item.enabled) onDrawerItemSelected(item.id) },
        colors =
          NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
          ),
      )
    }
  }
}

@Composable
fun MainActivityOverlays(
  syncProgressState: SyncProgressState,
  syncHeaderText: String,
  showSyncCloseButton: Boolean,
  isSyncTasksVisible: Boolean,
  isLoading: Boolean,
  onCloseSyncTasks: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize()) {
    if (isSyncTasksVisible) {
      SyncTasksOverlay(
        syncProgressState = syncProgressState,
        headerText = syncHeaderText,
        showCloseButton = showSyncCloseButton,
        onClose = onCloseSyncTasks,
      )
    }
    if (isLoading) {
      LoadingOverlay()
    }
  }
}

@Composable
private fun IconResource(@DrawableRes iconRes: Int) {
  Image(
    painter = painterResource(iconRes),
    contentDescription = null,
    modifier = Modifier.size(24.dp),
  )
}

@Composable
private fun ToolbarHost(onToolbarReady: (MaterialToolbar) -> Unit) {
  val context = LocalContext.current
  val toolbarState: MutableState<MaterialToolbar?> = remember { mutableStateOf(null) }
  val toolbarColor = colorResource(id = R.color.white)
  AndroidView(
    modifier = Modifier.fillMaxWidth().testTag("MainToolbar"),
    factory = {
      MaterialToolbar(context).apply {
        id = R.id.toolbar
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
          )
        setBackgroundColor(toolbarColor.toArgb())
      }
    },
    update = { toolbar ->
      if (toolbarState.value != toolbar) {
        toolbarState.value = toolbar
        onToolbarReady(toolbar)
      }
    },
  )
}

@Composable
private fun SyncTasksOverlay(
  syncProgressState: SyncProgressState,
  headerText: String,
  showCloseButton: Boolean,
  onClose: () -> Unit,
) {
  val progressFraction =
    if (syncProgressState.total > 0) {
      syncProgressState.current.toFloat() / syncProgressState.total.toFloat()
    } else {
      0f
    }
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(colorResource(id = R.color.primaryContainer_blue_90))
        .testTag("SyncTasksOverlay"),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      modifier = Modifier.padding(24.dp),
      shape = MaterialTheme.shapes.large,
      border = BorderStroke(1.dp, Color(0xFFE0E7FF)),
      colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
      Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = headerText,
          fontSize = 18.sp,
          color = Color(0xFF666666),
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
          progress = { progressFraction },
          modifier = Modifier.fillMaxWidth().height(4.dp),
          color = Color(0xFF1976D2),
          trackColor = Color(0xFFE0E0E0),
          strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
          text = stringResource(id = R.string.syncing_resources),
          fontSize = 32.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF333333),
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text =
            stringResource(
              id = R.string.this_can_take_up_to_10_minutes_depending_on_your_internet_connection,
            ),
          fontSize = 16.sp,
          color = Color(0xFF666666),
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          SyncChecklistItem(text = stringResource(id = R.string.stay_connected_to_wi_fi_or_data))
          SyncChecklistItem(
            text =
              stringResource(
                id = R.string.leave_the_device_in_a_location_with_good_internet_reception,
              ),
          )
          SyncChecklistItem(
            text =
              stringResource(
                id = R.string.you_can_do_other_things_on_the_device_while_it_syncs,
              ),
          )
        }
        Spacer(modifier = Modifier.height(32.dp))
        if (showCloseButton) {
          Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.dashboard_cardview_textcolor),
                contentColor = Color.White,
              ),
            shape = MaterialTheme.shapes.extraSmall,
            contentPadding = PaddingValues(vertical = 12.dp),
          ) {
            Text(text = stringResource(id = R.string.close_this_window), fontSize = 12.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun SyncChecklistItem(text: String) {
  Row(modifier = Modifier.fillMaxWidth()) {
    Image(
      painter = painterResource(id = R.drawable.ic_check_circle),
      contentDescription = null,
      modifier = Modifier.size(24.dp),
    )
    Spacer(modifier = Modifier.width(16.dp))
    Text(text = text, fontSize = 16.sp, color = Color(0xFF333333))
  }
}

@Composable
private fun LoadingOverlay() {
  Surface(
    modifier = Modifier.fillMaxSize().testTag("MainLoadingOverlay"),
    color = Color.White,
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF4285F4))
    }
  }
}
