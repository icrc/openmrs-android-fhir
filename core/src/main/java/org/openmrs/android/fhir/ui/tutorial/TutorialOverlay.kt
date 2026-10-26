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
package org.openmrs.android.fhir.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.openmrs.android.fhir.R

private val HolePadding = 8.dp
private val HoleCorner = 20.dp
private val CalloutGap = 12.dp
private val CalloutMargin = 16.dp
private val TallCalloutBottom = 96.dp
private const val WAIT_FOR_TARGET_MILLIS = 4_000L
private const val SKIP_OPTIONAL_MILLIS = 1_200L

/**
 * The tour, drawn above the whole window (the app bar and the bottom bar included): the screen is
 * dimmed with a hole over the highlighted element, ringed by a pulsing outline, and a card says
 * what to do.
 *
 * Strictness: for a [TutorialAction.Tap] step four panels around the hole swallow every touch, so
 * the highlighted element is the only thing that can be pressed; for an
 * [TutorialAction.Acknowledge] step the whole screen is swallowed. The card, with "Skip tour" and
 * the close button, is always reachable, and Back closes the tour: the user is never trapped, not
 * even when the element never shows up (the tour ends after a few seconds).
 *
 * Put it once, over the window's content; it draws nothing while no tour runs.
 */
@Composable
fun TutorialOverlay() {
  val active = TutorialController.active ?: return
  val step = active.step
  val bounds = TutorialController.targets[step.targetKey]
  BackHandler { TutorialController.end() }

  var origin by remember { mutableStateOf(Offset.Zero) }
  Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
    if (bounds == null) {
      // The element is in a screen that is still opening: nothing can be touched meanwhile.
      Panel(Modifier.fillMaxSize())
      LaunchedEffect(active) {
        // An optional element is simply not here; any other is in a screen that is still opening.
        delay(if (step.optional) SKIP_OPTIONAL_MILLIS else WAIT_FOR_TARGET_MILLIS)
        if (TutorialController.targets[step.targetKey] == null) {
          if (step.optional) TutorialController.skipMissing() else TutorialController.end()
        }
      }
      return@Box
    }
    val density = LocalDensity.current
    val hole =
      with(density) {
        Rect(
          bounds.left - origin.x - HolePadding.toPx(),
          bounds.top - origin.y - HolePadding.toPx(),
          bounds.right - origin.x + HolePadding.toPx(),
          bounds.bottom - origin.y + HolePadding.toPx(),
        )
      }
    Spotlight(hole)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val width = maxWidth
      val height = maxHeight
      val holeTop = with(density) { hole.top.toDp() }
      val holeBottom = with(density) { hole.bottom.toDp() }
      val holeLeft = with(density) { hole.left.toDp() }
      val holeRight = with(density) { hole.right.toDp() }
      // The touch blockers. The hole is in screen coordinates, so they are laid out left to right
      // whatever the language: offsets are mirrored in a right-to-left one.
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if (step.action == TutorialAction.Tap) {
          Panel(Modifier.offset(0.dp, 0.dp).width(width).height(holeTop.coerceAtLeast(0.dp)))
          Panel(
            Modifier.offset(0.dp, holeBottom)
              .width(width)
              .height((height - holeBottom).coerceAtLeast(0.dp)),
          )
          Panel(
            Modifier.offset(0.dp, holeTop)
              .width(holeLeft.coerceAtLeast(0.dp))
              .height((holeBottom - holeTop).coerceAtLeast(0.dp)),
          )
          Panel(
            Modifier.offset(holeRight, holeTop)
              .width((width - holeRight).coerceAtLeast(0.dp))
              .height((holeBottom - holeTop).coerceAtLeast(0.dp)),
          )
        } else {
          Panel(Modifier.fillMaxSize())
        }
      }
      val below = (holeTop + holeBottom) / 2 < height / 2
      // An element that takes most of the screen (a form) leaves no room beside it: the card sits
      // over its lower part, clear of the buttons at the very bottom.
      val tall = holeBottom - holeTop > height * 0.6f
      Callout(
        active = active,
        modifier =
          if (tall) {
            Modifier.align(Alignment.BottomCenter).padding(bottom = TallCalloutBottom)
          } else if (below) {
            Modifier.align(Alignment.TopCenter).padding(top = holeBottom + CalloutGap)
          } else {
            Modifier.align(Alignment.BottomCenter)
              .padding(bottom = (height - holeTop + CalloutGap).coerceAtLeast(CalloutMargin))
          },
      )
    }
  }
}

/** Swallows every touch that lands on it. */
@Composable
private fun Panel(modifier: Modifier) {
  Box(
    modifier =
      modifier.pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
          }
        }
      },
  )
}

/** The dimmed screen with the hole and the pulsing outline around it. */
@Composable
private fun Spotlight(hole: Rect) {
  val ring = MaterialTheme.colorScheme.primary
  val pulse by
    rememberInfiniteTransition(label = "tutorial-pulse")
      .animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "tutorial-pulse-alpha",
      )
  Canvas(modifier = Modifier.fillMaxSize()) {
    val corner = HoleCorner.toPx()
    val path = Path().apply { addRoundRect(RoundRect(hole, CornerRadius(corner))) }
    clipPath(path, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.72f)) }
    // A soft glow, then the outline.
    drawRoundRect(
      color = ring.copy(alpha = 0.25f * pulse),
      topLeft = Offset(hole.left - 6.dp.toPx(), hole.top - 6.dp.toPx()),
      size = Size(hole.width + 12.dp.toPx(), hole.height + 12.dp.toPx()),
      cornerRadius = CornerRadius(corner + 6.dp.toPx()),
      style = Stroke(width = 8.dp.toPx()),
    )
    drawRoundRect(
      color = ring.copy(alpha = pulse),
      topLeft = hole.topLeft,
      size = hole.size,
      cornerRadius = CornerRadius(corner),
      style = Stroke(width = 3.dp.toPx()),
    )
  }
}

@Composable
private fun Callout(active: TutorialController.Active, modifier: Modifier) {
  val step = active.step
  val title = stringResource(step.title)
  Card(
    modifier =
      modifier.padding(horizontal = CalloutMargin).fillMaxWidth().semantics {
        paneTitle = title
        liveRegion = LiveRegionMode.Polite
      },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text =
            stringResource(R.string.tutorial_step_counter, active.index + 1, active.steps.size),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = TutorialController::end) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.tutorial_close),
          )
        }
      }
      Column(modifier = Modifier.padding(end = 12.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(step.body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
        )
        if (step.action == TutorialAction.Tap) {
          Text(
            text = stringResource(R.string.tutorial_tap_to_continue),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = TutorialController::end) {
          Text(text = stringResource(R.string.tutorial_skip))
        }
        if (step.action == TutorialAction.Acknowledge) {
          Button(onClick = TutorialController::acknowledge) {
            Text(text = stringResource(R.string.tutorial_next))
          }
        }
      }
    }
  }
}
