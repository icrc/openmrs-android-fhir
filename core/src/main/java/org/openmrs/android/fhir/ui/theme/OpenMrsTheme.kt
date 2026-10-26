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
import org.openmrs.android.fhir.extensions.isAppInDarkTheme
import org.openmrs.android.fhir.extensions.isColorBlindPalette

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
    highlightContainer = Color(0xFFE6F1F0),
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
    primaryContainer = Color(0xFFE6F1F0),
    onPrimaryContainer = Color(0xFF00403F),
    secondary = Color(0xFF007D79),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F1F0),
    onSecondaryContainer = Color(0xFF00403F),
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

// Color-blind palette (Okabe-Ito based): blue and orange instead of green and red, and the
// neighbouring states differ in lightness as well as in hue.
private val ColorBlindLightAppColors =
  AppColors(
    actionButton = Color(0xFF00508C),
    onActionButton = Color.White,
    success = Color(0xFF0072B2),
    progress = Color(0xFF0072B2),
    warning = Color(0xFFE69F00),
    highlightContainer = Color(0xFFD6E8F7),
    neutralContainer = Color(0xFFF4F4F4),
  )

private val ColorBlindDarkAppColors =
  AppColors(
    actionButton = Color(0xFF56B4E9),
    onActionButton = Color(0xFF00263F),
    success = Color(0xFF56B4E9),
    progress = Color(0xFF56B4E9),
    warning = Color(0xFFF0E442),
    highlightContainer = Color(0xFF0B3552),
    neutralContainer = Color(0xFF262626),
  )

private val ColorBlindLightColorScheme: ColorScheme =
  LightColorScheme.copy(
    primary = Color(0xFF00508C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8F7),
    onPrimaryContainer = Color(0xFF00294A),
    secondary = Color(0xFF0072B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E8F7),
    onSecondaryContainer = Color(0xFF00294A),
    tertiary = Color(0xFF8A4B00),
    onSurfaceVariant = Color(0xFF3F3F3F),
    outline = Color(0xFF6F6F6F),
    error = Color(0xFFB34700),
    onError = Color.White,
    errorContainer = Color(0xFFFFE0CC),
    onErrorContainer = Color(0xFF4A1D00),
  )

private val ColorBlindDarkColorScheme: ColorScheme =
  DarkColorScheme.copy(
    primary = Color(0xFF7CC4F2),
    onPrimary = Color(0xFF00263F),
    primaryContainer = Color(0xFF00508C),
    onPrimaryContainer = Color(0xFFD6E8F7),
    secondary = Color(0xFF56B4E9),
    onSecondary = Color(0xFF00263F),
    secondaryContainer = Color(0xFF0B3552),
    onSecondaryContainer = Color(0xFFD6E8F7),
    tertiary = Color(0xFFF0A030),
    onTertiary = Color(0xFF2E1800),
    onSurfaceVariant = Color(0xFFDADADA),
    outline = Color(0xFFA8A8A8),
    error = Color(0xFFFFA060),
    onError = Color(0xFF3A1500),
    errorContainer = Color(0xFF7A3000),
    onErrorContainer = Color(0xFFFFE0CC),
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
fun OpenMrsTheme(
  darkTheme: Boolean = isAppInDarkTheme(),
  colorBlind: Boolean = isColorBlindPalette(),
  content: @Composable () -> Unit,
) {
  val appColors =
    when {
      colorBlind && darkTheme -> ColorBlindDarkAppColors
      colorBlind -> ColorBlindLightAppColors
      darkTheme -> DarkAppColors
      else -> LightAppColors
    }
  val colorScheme =
    when {
      colorBlind && darkTheme -> ColorBlindDarkColorScheme
      colorBlind -> ColorBlindLightColorScheme
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }
  CompositionLocalProvider(LocalAppColors provides appColors) {
    MaterialTheme(colorScheme = colorScheme, content = content)
  }
}
