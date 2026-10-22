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
package org.openmrs.android.fhir.extensions

import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.updatePadding
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

fun Context.resolveActionBarHeightPx(): Int {
  val typedValue = TypedValue()
  val resolved =
    theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typedValue, true) ||
      theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
  return if (resolved) {
    TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
  } else {
    0
  }
}

/**
 * Bottom edge, in pixels, of the app bar overlay (status bar + network banner + toolbar). Published
 * by the main activity so screens drawn under the bar can pad by what is really there instead of
 * assuming a toolbar height.
 */
object AppBarInsets {
  val heightPx = MutableStateFlow(0)
}

/**
 * Keeps a screen that is still an XML view below the app bar overlay (status bar, toolbar and the
 * connection strip), whatever height they have on this device: the top padding follows the height
 * the main activity measures, so the strip never covers the first lines of the screen.
 */
fun View.keepBelowAppBar(owner: LifecycleOwner, extra: Dp = 4.dp) {
  val extraPx = (resources.displayMetrics.density * extra.value).toInt()
  owner.lifecycleScope.launch {
    AppBarInsets.heightPx.collect { barBottomPx -> updatePadding(top = barBottomPx + extraPx) }
  }
}

/** Height of the app bar overlay; falls back to status bar + toolbar until it is measured. */
@Composable
fun rememberAppBarHeight(): Dp {
  val context = LocalContext.current
  val density = LocalDensity.current
  val measuredPx by AppBarInsets.heightPx.collectAsState()
  val actionBarHeightPx = remember { context.resolveActionBarHeightPx() }
  val fallback =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
      with(density) { actionBarHeightPx.toDp() }
  return if (measuredPx > 0) with(density) { measuredPx.toDp() } else fallback
}

@Composable fun statusBarAndActionBarTopInset(): Dp = rememberAppBarHeight() + 16.dp

/**
 * Content padding for screens drawn under the app bar: 16dp gutters plus the app bar height on top.
 * [extraTop] adds screen specific spacing below the bar.
 */
@Composable
fun rememberActionBarContentPadding(extraTop: Dp = 0.dp): PaddingValues {
  val topInset = rememberAppBarHeight() + extraTop
  return PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 16.dp + topInset)
}
