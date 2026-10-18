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

import android.os.Build
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.appbar.MaterialToolbar
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.remote.ServerConnectivityState
import org.openmrs.android.fhir.extensions.rememberAppBarHeight
import org.openmrs.android.fhir.ui.components.AutoSyncDelayDialog
import org.openmrs.android.fhir.ui.components.DrawerHeader
import org.openmrs.android.fhir.ui.components.NetworkStatusBanner
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

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
) {
  /**
   * How far the sync is, or `null` when that is not known. The total the sync library reports only
   * counts what the main searches match (the patients), while the download also brings their
   * encounters and observations, so [current] passes it long before the sync is done. From then on
   * only the amount received so far can be shown honestly, not a percentage.
   */
  val fraction: Float?
    get() = if (total > 0 && current < total) current.toFloat() / total else null
}

@Composable
fun MainActivityScaffold(
  networkStatusText: String,
  isNetworkStatusVisible: Boolean,
  onToolbarReady: (MaterialToolbar) -> Unit,
  modifier: Modifier = Modifier,
  connectivityState: ServerConnectivityState = ServerConnectivityState.ServerConnected,
  isSyncing: Boolean = false,
  syncProgress: SyncProgressState = SyncProgressState(0, 0),
  nextSyncMinutes: Int? = null,
  onDelaySync: (minutes: Int) -> Unit = {},
  onTurnOffAutoSync: () -> Unit = {},
  onResetAutoSyncTimer: () -> Unit = {},
) {
  var showDelayDialog by rememberSaveable { mutableStateOf(false) }
  // One bar: the surface color runs up behind the status bar, with the connection strip and the
  // toolbar inside it and a soft shadow underneath.
  // The drawer lays this view out at full height; only the bar itself may take space and paint.
  Surface(
    modifier = modifier.fillMaxWidth().wrapContentHeight(Alignment.Top),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 3.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
      if (isNetworkStatusVisible) {
        val fraction =
          if (syncProgress.total > 0) {
            (syncProgress.current.toFloat() / syncProgress.total).coerceIn(0f, 1f)
          } else {
            null
          }
        val syncing = stringResource(id = R.string.syncing)
        NetworkStatusBanner(
          text = networkStatusText,
          state = connectivityState,
          syncText =
            when {
              !isSyncing -> null
              fraction == null -> syncing
              else ->
                "$syncing · ${stringResource(id = R.string.sync_percent, (fraction * 100).toInt())}"
            },
          syncProgress = fraction,
          nextSyncText =
            nextSyncMinutes?.let {
              if (it >= 60) {
                stringResource(id = R.string.next_sync_in_hours, it / 60, it % 60)
              } else {
                stringResource(id = R.string.next_sync_in_minutes, it)
              }
            },
          onDelayClick = if (nextSyncMinutes != null) ({ showDelayDialog = true }) else null,
        )
      }
      ToolbarHost(onToolbarReady = onToolbarReady)
    }
  }
  if (showDelayDialog) {
    AutoSyncDelayDialog(
      onDelay = {
        showDelayDialog = false
        onDelaySync(it)
      },
      onTurnOff = {
        showDelayDialog = false
        onTurnOffAutoSync()
      },
      onResetTimer = {
        showDelayDialog = false
        onResetAutoSyncTimer()
      },
      onDismiss = { showDelayDialog = false },
    )
  }
}

/** Items grouped so related actions sit together; the version is a quiet footer. */
private val DrawerGroups =
  listOf(
    listOf(R.id.menu_sync, R.id.menu_current_location),
    listOf(R.id.menu_settings, R.id.menu_diagnostics),
    listOf(R.id.menu_logout, R.id.menu_sign_in),
  )

@Composable
fun DrawerContent(
  lastSyncText: String,
  drawerItems: List<DrawerItem>,
  onDrawerItemSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val itemsById = remember(drawerItems) { drawerItems.associateBy { it.id } }
  val versionItem = itemsById[R.id.menu_version]

  Column(
    modifier = modifier.width(300.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface),
  ) {
    DrawerHeader(label = stringResource(R.string.last_sync), lastSyncValue = lastSyncText)

    Column(
      modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
    ) {
      DrawerGroups.forEachIndexed { index, ids ->
        if (index > 0) {
          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
          )
        }
        ids
          .mapNotNull { itemsById[it] }
          .forEach { item -> DrawerEntry(item = item, onClick = { onDrawerItemSelected(item.id) }) }
      }
    }

    if (versionItem != null) {
      Text(
        text = versionItem.title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
          Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag(versionItem.testTag),
      )
    }
  }
}

@Composable
private fun DrawerEntry(item: DrawerItem, onClick: () -> Unit) {
  val isLogout = item.id == R.id.menu_logout
  val contentColor =
    if (isLogout) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
  val iconColor =
    if (isLogout) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
  NavigationDrawerItem(
    modifier =
      Modifier.padding(horizontal = 12.dp)
        .fillMaxWidth()
        .alpha(if (item.enabled) 1f else 0.6f)
        .testTag(item.testTag),
    label = {
      Text(
        text = item.title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 2,
      )
    },
    icon = {
      Icon(
        painter = painterResource(item.iconRes),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
      )
    },
    selected = false,
    onClick = { if (item.enabled) onClick() },
    shape = RoundedCornerShape(12.dp),
    colors =
      NavigationDrawerItemDefaults.colors(
        unselectedContainerColor = Color.Transparent,
        unselectedIconColor = iconColor,
        unselectedTextColor = contentColor,
      ),
  )
}

@Composable
fun MainActivityOverlays(
  syncProgressState: SyncProgressState,
  syncHeaderText: String,
  showSyncCloseButton: Boolean,
  isSyncTasksVisible: Boolean,
  isSyncMinimized: Boolean,
  isLoading: Boolean,
  onMinimizeSyncTasks: () -> Unit,
  onExpandSyncTasks: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize()) {
    SyncPopup(
      visible = isSyncTasksVisible,
      // The blocking first sync cannot be tucked away: it is the only way forward.
      minimized = isSyncMinimized && showSyncCloseButton,
      canMinimize = showSyncCloseButton,
      progress = syncProgressState,
      headerText = syncHeaderText,
      onMinimize = onMinimizeSyncTasks,
      onExpand = onExpandSyncTasks,
    )
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

private val TITLE_APPEARANCE =
  com.google.android.material.R.style.TextAppearance_Material3_TitleLarge

@Composable
private fun ToolbarHost(onToolbarReady: (MaterialToolbar) -> Unit) {
  val context = LocalContext.current
  val toolbarState: MutableState<MaterialToolbar?> = remember { mutableStateOf(null) }
  val titleColor = MaterialTheme.colorScheme.onSurface.toArgb()
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
        // The bar's surface is drawn by the composable around it.
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setTitleTextAppearance(context, TITLE_APPEARANCE)
      }
    },
    update = { toolbar ->
      toolbar.setTitleTextColor(titleColor)
      if (toolbarState.value != toolbar) {
        toolbarState.value = toolbar
        onToolbarReady(toolbar)
      }
    },
  )
}

/**
 * The sync progress as a popup over a dimmed (and, where the system can, blurred) app, so what is
 * behind stays recognisable. It can be minimised to a small pill under the app bar that leaves the
 * app usable; tapping the pill brings the popup back.
 */
@Composable
private fun BoxScope.SyncPopup(
  visible: Boolean,
  minimized: Boolean,
  canMinimize: Boolean,
  progress: SyncProgressState,
  headerText: String,
  onMinimize: () -> Unit,
  onExpand: () -> Unit,
) {
  // The activity blurs the content itself where it can; elsewhere the scrim has to do more.
  val scrimAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.2f else 0.5f
  val noRipple = remember { MutableInteractionSource() }

  AnimatedVisibility(
    visible = visible && !minimized,
    enter = fadeIn(),
    exit = fadeOut(),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Color.Black.copy(alpha = scrimAlpha))
          .clickable(interactionSource = noRipple, indication = null) {
            if (canMinimize) onMinimize()
          }
          .testTag("SyncTasksOverlay"),
      contentAlignment = Alignment.Center,
    ) {
      AnimatedVisibility(
        visible = visible && !minimized,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
      ) {
        SyncPopupCard(
          progress = progress,
          headerText = headerText,
          canMinimize = canMinimize,
          onMinimize = onMinimize,
          modifier =
            Modifier.padding(24.dp).clickable(interactionSource = noRipple, indication = null) {},
        )
      }
    }
  }

  AnimatedVisibility(
    visible = visible && minimized,
    modifier = Modifier.align(Alignment.TopCenter),
    enter = fadeIn() + slideInVertically { -it },
    exit = fadeOut() + slideOutVertically { -it },
  ) {
    SyncPill(
      progress = progress,
      onExpand = onExpand,
      modifier = Modifier.padding(top = rememberAppBarHeight() + 8.dp),
    )
  }
}

@Composable
private fun SyncPopupCard(
  progress: SyncProgressState,
  headerText: String,
  canMinimize: Boolean,
  onMinimize: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val fraction = progress.fraction
  Surface(
    modifier = modifier.widthIn(max = 400.dp).fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 12.dp,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      SyncBadge()
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = headerText,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text =
          stringResource(
            id = R.string.this_can_take_up_to_10_minutes_depending_on_your_internet_connection,
          ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(20.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        val barModifier = Modifier.weight(1f).height(8.dp).clip(CircleShape)
        if (fraction != null) {
          LinearProgressIndicator(
            progress = { fraction },
            modifier = barModifier,
            color = OpenMrsTheme.colors.progress,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = stringResource(id = R.string.sync_percent, (fraction * 100).toInt()),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
        } else {
          LinearProgressIndicator(
            modifier = barModifier,
            color = OpenMrsTheme.colors.progress,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
          )
        }
      }
      if (fraction == null && progress.current > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(id = R.string.sync_items_so_far, progress.current),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("SyncItemsSoFar"),
        )
      }
      Spacer(modifier = Modifier.height(20.dp))
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SyncTip(text = stringResource(id = R.string.stay_connected_to_wi_fi_or_data))
        SyncTip(
          text =
            stringResource(
              id = R.string.leave_the_device_in_a_location_with_good_internet_reception,
            ),
        )
        SyncTip(
          text = stringResource(id = R.string.you_can_do_other_things_on_the_device_while_it_syncs),
        )
      }
      if (canMinimize) {
        Spacer(modifier = Modifier.height(24.dp))
        FilledTonalButton(
          onClick = onMinimize,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("SyncMinimizeButton"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(id = R.string.sync_minimize))
        }
      }
    }
  }
}

/** The turning sync icon; the rotation is applied while drawing, so nothing recomposes. */
@Composable
private fun SyncBadge() {
  val turn = rememberInfiniteTransition(label = "sync-turn")
  val angle by
    turn.animateFloat(
      initialValue = 0f,
      targetValue = -360f,
      animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing)),
      label = "sync-angle",
    )
  Box(
    modifier =
      Modifier.size(64.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painter = painterResource(id = R.drawable.ic_baseline_sync_24),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = angle },
    )
  }
}

@Composable
private fun SyncTip(text: String) {
  Row(verticalAlignment = Alignment.Top) {
    Icon(
      painter = painterResource(id = R.drawable.ic_check_circle),
      contentDescription = null,
      tint = OpenMrsTheme.colors.success,
      modifier = Modifier.padding(top = 1.dp).size(18.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/** What is left of the sync once minimised: a small pill with the progress, tap to expand. */
@Composable
private fun SyncPill(
  progress: SyncProgressState,
  onExpand: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val fraction = progress.fraction
  val label =
    if (fraction != null) {
      "${stringResource(id = R.string.syncing)} · ${stringResource(id = R.string.sync_percent, (fraction * 100).toInt())}"
    } else if (progress.current > 0) {
      "${stringResource(id = R.string.syncing)} · ${progress.current}"
    } else {
      stringResource(id = R.string.syncing)
    }
  Surface(
    onClick = onExpand,
    modifier = modifier.testTag("SyncMinimizedPill"),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shadowElevation = 6.dp,
  ) {
    Row(
      modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (fraction != null) {
        CircularProgressIndicator(
          progress = { fraction },
          modifier = Modifier.size(18.dp),
          strokeWidth = 2.5.dp,
          color = OpenMrsTheme.colors.progress,
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
      } else {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          strokeWidth = 2.5.dp,
          color = OpenMrsTheme.colors.progress,
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.width(6.dp))
      Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = stringResource(id = R.string.sync_show_progress),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun LoadingOverlay() {
  Surface(
    modifier = Modifier.fillMaxSize().testTag("MainLoadingOverlay"),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      androidx.compose.material3.CircularProgressIndicator(color = OpenMrsTheme.colors.progress)
    }
  }
}
