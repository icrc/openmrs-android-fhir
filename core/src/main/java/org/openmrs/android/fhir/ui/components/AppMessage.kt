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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme

/** What a message is about; it picks the icon and the accent colour. */
enum class MessageKind {
  Success,
  Error,
  Info,
}

/**
 * The one way the app tells the user something short ("Patient saved", "Sync completed"). It is
 * drawn like the rest of the app (a rounded card with a coloured icon, the look of the sync popup)
 * instead of the system toast or the grey snackbar, slides in above the bottom bars, disappears by
 * itself or on a tap, and replaces the message still showing.
 *
 * It lives in the activity's content, so a message for a dialog has to be given the dialog's
 * content as [container]. When the context is not an activity, or the activity is going away, the
 * plain system toast is used so the message is not lost.
 */
object AppMessage {
  private const val SHORT_MS = 2800L
  private const val LONG_MS = 4800L
  private val handler = Handler(Looper.getMainLooper())
  private val TAG = AppMessage::class.java.name

  fun show(
    context: Context?,
    text: CharSequence,
    kind: MessageKind = MessageKind.Info,
    long: Boolean = false,
    container: ViewGroup? = null,
  ) {
    if (context == null) return
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.post { show(context, text, kind, long, container) }
      return
    }
    val activity = context.findActivity()
    val host = container ?: activity?.findViewById<ViewGroup>(android.R.id.content)
    if (activity == null || host == null || activity.isFinishing || activity.isDestroyed) {
      Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
      return
    }
    host.findViewWithTag<View>(TAG)?.let { host.removeView(it) }
    val view =
      ComposeView(activity).apply {
        tag = TAG
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
          OpenMrsTheme {
            MessageCard(
              text = text.toString(),
              kind = kind,
              durationMs = if (long) LONG_MS else SHORT_MS,
              onGone = { host.removeView(this) },
            )
          }
        }
      }
    host.addView(
      view,
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM,
      ),
    )
  }

  fun show(
    context: Context?,
    @StringRes resId: Int,
    kind: MessageKind = MessageKind.Info,
    long: Boolean = false,
    container: ViewGroup? = null,
  ) {
    if (context != null) show(context, context.getString(resId), kind, long, container)
  }
}

/** `context.showMessage(R.string.patient_is_saved, MessageKind.Success)`. */
fun Context.showMessage(
  @StringRes resId: Int,
  kind: MessageKind = MessageKind.Info,
  long: Boolean = false,
) = AppMessage.show(this, resId, kind, long)

fun Context.showMessage(
  text: CharSequence,
  kind: MessageKind = MessageKind.Info,
  long: Boolean = false,
) = AppMessage.show(this, text, kind, long)

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

private const val EXIT_MS = 320L

/** Keeps the message above the bottom navigation bar and the next/submit bar of the forms. */
private val BOTTOM_CLEARANCE = 96.dp

@Composable
private fun MessageCard(text: String, kind: MessageKind, durationMs: Long, onGone: () -> Unit) {
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    visible = true
    delay(durationMs)
    visible = false
    delay(EXIT_MS)
    onGone()
  }
  val (accent, icon) =
    when (kind) {
      MessageKind.Success -> OpenMrsTheme.colors.success to R.drawable.ic_check_circle
      MessageKind.Error -> MaterialTheme.colorScheme.error to R.drawable.ic_error
      MessageKind.Info -> OpenMrsTheme.colors.progress to R.drawable.ic_info
    }
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(start = 16.dp, end = 16.dp, bottom = BOTTOM_CLEARANCE),
    contentAlignment = Alignment.BottomCenter,
  ) {
    AnimatedVisibility(
      visible = visible,
      enter = fadeIn() + slideInVertically { it / 2 },
      exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
      Surface(
        modifier =
          Modifier.widthIn(max = 480.dp)
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .clickable { visible = false },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
      ) {
        Row(
          modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              painter = painterResource(id = icon),
              contentDescription = null,
              tint = accent,
              modifier = Modifier.size(22.dp),
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
