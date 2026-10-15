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
package org.openmrs.android.fhir.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors the Material 3 scheme has no role for: the dark action buttons used across the app and the
 * status colors of the sync UI.
 */
@Immutable
data class AppColors(
  /** Background of the app's primary action buttons (dark grey in light mode). */
  val actionButton: Color,
  /** Content (text/icon) shown on [actionButton]. */
  val onActionButton: Color,
  val success: Color,
  /** Spinner / progress indicator color. */
  val progress: Color,
  val warning: Color,
  /** Soft highlight: selected rows and the "sync in progress" card. */
  val highlightContainer: Color,
  /** Soft background of neutral, informational cards. */
  val neutralContainer: Color,
)

// OpenMRS 3 (Carbon) palette: teal brand colors, neutral grey layers, Carbon support colors.
private val LightAppColors =
  AppColors(
    actionButton = Color(0xFF005D5D),
    onActionButton = Color.White,
    success = Color(0xFF198038),
    progress = Color(0xFF007D79),
    warning = Color(0xFFF1C21B),
    highlightContainer = Color(0xFFD9FBFB),
    neutralContainer = Color(0xFFF4F4F4),
  )

private val DarkAppColors =
  AppColors(
    actionButton = Color(0xFF08BDBA),
    onActionButton = Color(0xFF161616),
    success = Color(0xFF42BE65),
    progress = Color(0xFF08BDBA),
    warning = Color(0xFFF1C21B),
    highlightContainer = Color(0xFF004144),
    neutralContainer = Color(0xFF262626),
  )

private val LightColorScheme: ColorScheme =
  lightColorScheme(
    primary = Color(0xFF005D5D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9FBFB),
    onPrimaryContainer = Color(0xFF004144),
    secondary = Color(0xFF007D79),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9FBFB),
    onSecondaryContainer = Color(0xFF004144),
    tertiary = Color(0xFF198038),
    background = Color(0xFFF4F4F4),
    onBackground = Color(0xFF161616),
    surface = Color.White,
    onSurface = Color(0xFF161616),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF525252),
    outline = Color(0xFF8D8D8D),
    outlineVariant = Color(0xFFE0E0E0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF4F4F4),
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    error = Color(0xFFDA1E28),
    onError = Color.White,
  )

private val DarkColorScheme: ColorScheme =
  darkColorScheme(
    primary = Color(0xFF08BDBA),
    onPrimary = Color(0xFF003A3A),
    primaryContainer = Color(0xFF005D5D),
    onPrimaryContainer = Color(0xFFD9FBFB),
    secondary = Color(0xFF3DDBD9),
    onSecondary = Color(0xFF003A3A),
    secondaryContainer = Color(0xFF004144),
    onSecondaryContainer = Color(0xFFD9FBFB),
    tertiary = Color(0xFF42BE65),
    onTertiary = Color(0xFF022D0D),
    background = Color(0xFF161616),
    onBackground = Color(0xFFF4F4F4),
    surface = Color(0xFF262626),
    onSurface = Color(0xFFF4F4F4),
    surfaceVariant = Color(0xFF393939),
    onSurfaceVariant = Color(0xFFC6C6C6),
    outline = Color(0xFF8D8D8D),
    outlineVariant = Color(0xFF393939),
    surfaceContainerLowest = Color(0xFF161616),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = Color(0xFF262626),
    surfaceContainerHigh = Color(0xFF303030),
    surfaceContainerHighest = Color(0xFF393939),
    error = Color(0xFFFA4D56),
    onError = Color(0xFF161616),
  )

private val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Access to the app-specific colors: `OpenMrsTheme.colors.actionButton`. */
object OpenMrsTheme {
  val colors: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

/**
 * The single Compose theme for the app. Every entry point (activities, fragments, list items) wraps
 * its content in this instead of a bare [MaterialTheme], so colors follow the system light/dark
 * setting consistently with the XML screens, which already use a DayNight theme.
 */
@Composable
fun OpenMrsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  CompositionLocalProvider(
    LocalAppColors provides if (darkTheme) DarkAppColors else LightAppColors,
  ) {
    MaterialTheme(
      colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
      content = content,
    )
  }
}
