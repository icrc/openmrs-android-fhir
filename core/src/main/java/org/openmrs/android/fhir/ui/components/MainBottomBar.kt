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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.openmrs.android.fhir.R

/** The destinations offered in the bottom navigation bar. */
enum class TopLevelDestination(
  /** Navigation graph destination the item opens. */
  val destinationId: Int,
  @StringRes val labelRes: Int,
  val icon: ImageVector,
  val testTag: String,
) {
  Home(R.id.home_fragment, R.string.nav_home, Icons.Default.Home, "BottomNavHome"),
  Patients(R.id.patient_list, R.string.nav_patients, Icons.Default.People, "BottomNavPatients"),
  GroupSession(
    R.id.createEncounterFragment,
    R.string.nav_group_session,
    Icons.Default.Groups,
    "BottomNavGroupSession",
  ),
  Sync(R.id.syncInfoFragment, R.string.nav_sync, Icons.Default.Sync, "BottomNavSync"),
}

/**
 * Which bottom-bar item is selected for the screen the user is on, or `null` when the bar should be
 * hidden (forms, patient details, login flows).
 *
 * The unsynced-resources screen is shown under Sync. The encounter-creation screen is only a
 * top-level screen in its group-session flavour; for a single patient it is a form.
 */
fun topLevelDestinationFor(destinationId: Int, isGroupEncounter: Boolean): TopLevelDestination? =
  when (destinationId) {
    R.id.home_fragment -> TopLevelDestination.Home
    R.id.patient_list -> TopLevelDestination.Patients
    R.id.createEncounterFragment -> if (isGroupEncounter) TopLevelDestination.GroupSession else null
    R.id.syncInfoFragment,
    R.id.unsyncedResourcesFragment, -> TopLevelDestination.Sync
    else -> null
  }

@Composable
fun MainBottomBar(
  selected: TopLevelDestination,
  onDestinationSelected: (TopLevelDestination) -> Unit,
  modifier: Modifier = Modifier,
) {
  NavigationBar(
    modifier = modifier.fillMaxWidth().testTag("MainBottomBar"),
    containerColor = MaterialTheme.colorScheme.surface,
    // The activity already pads its content for the system bars and the keyboard.
    windowInsets = WindowInsets(0, 0, 0, 0),
  ) {
    TopLevelDestination.entries.forEach { destination ->
      NavigationBarItem(
        modifier = Modifier.testTag(destination.testTag),
        selected = destination == selected,
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
          ),
        onClick = { onDestinationSelected(destination) },
        icon = { Icon(imageVector = destination.icon, contentDescription = null) },
        label = { Text(text = stringResource(id = destination.labelRes)) },
      )
    }
  }
}
