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

import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Makes this element something a tour can point at: `Modifier.tutorialTarget("home.patients")`.
 *
 * It announces where the element is (so a step can highlight it) and watches, without consuming
 * anything, whether it is tapped, so the element keeps working exactly as before and the tour sees
 * what the user did. Put it before the element's own `clickable`. It costs nothing when no tour
 * runs.
 */
fun Modifier.tutorialTarget(key: String): Modifier = composed {
  DisposableEffect(key) { onDispose { TutorialController.unregister(key) } }
  // A step on an element that is scrolled out of sight scrolls it into view.
  val requester = remember { BringIntoViewRequester() }
  val isCurrent = TutorialController.active?.step?.targetKey == key
  LaunchedEffect(isCurrent) { if (isCurrent) requester.bringIntoView() }
  this.bringIntoViewRequester(requester)
    .onGloballyPositioned { TutorialController.register(key, it.boundsInWindow()) }
    .pointerInput(key) {
      awaitEachGesture {
        // The Initial pass comes before the element's own handlers and nothing is consumed here.
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
        if (up != null) TutorialController.onTargetTapped(key)
      }
    }
}

/**
 * Makes an Android view (not a composable) something a tour can point at, for a screen that is
 * still XML (a form, a toolbar button): its place is read now and does not follow the view.
 */
fun registerViewTarget(view: View, key: String) {
  val location = IntArray(2)
  view.getLocationInWindow(location)
  TutorialController.register(
    key,
    Rect(
      location[0].toFloat(),
      location[1].toFloat(),
      (location[0] + view.width).toFloat(),
      (location[1] + view.height).toFloat(),
    ),
  )
}

/** The elements the tours point at. */
object TutorialTargets {
  const val HOME_PATIENTS = "home.patients"
  const val PATIENT_SEARCH = "patients.search"
  const val PATIENT_REGISTER = "patients.register"
  const val HOME_STATUS = "home.status"
  const val HOME_REGISTER = "home.register"
  const val HOME_LISTS = "home.lists"
  const val HOME_GROUP = "home.group"
  const val HOME_SYNC = "home.sync"
  const val HOME_IMPORT_EXPORT = "home.importexport"
  const val HOME_SUMMARY = "home.summary"
  const val HOME_MENU = "home.menu"
  const val HOME_SESSION = "home.session"
  const val PATIENT_SORT = "patients.sort"
  const val SETTINGS_SEARCH = "settings.search"
  const val PATIENT_LIST = "patients.list"
  const val PATIENT_IDENTIFY_LATER = "patients.identifylater"
  const val DETAILS_OVERVIEW = "details.overview"
  const val DETAILS_ENCOUNTERS = "details.encounters"
  const val DETAILS_NEW_ENCOUNTER = "details.newencounter"
  const val SYNC_STATUS = "sync.status"
  const val SYNC_IDENTIFY = "sync.identify"
  const val SYNC_MODE = "sync.mode"
  const val SYNC_NOW = "sync.now"
  const val SYNC_TABS = "sync.tabs"
  const val IE_EXPORT = "importexport.export"
  const val IE_IMPORT = "importexport.import"
  const val SETTINGS_LANGUAGE = "settings.language"
  const val SETTINGS_APPEARANCE = "settings.appearance"
  const val SETTINGS_SYNC = "settings.sync"
  const val SETTINGS_LIVE = "settings.live"
  const val SETTINGS_SHARED = "settings.shared"
  const val SETTINGS_SECURITY = "settings.security"
  const val LISTS_SEARCH = "lists.search"
  const val LISTS_LIST = "lists.list"
  const val LOCATION_SEARCH = "location.search"
  const val LOCATION_LIST = "location.list"
  const val FORMS_SEARCH = "forms.search"
  const val FORMS_LIST = "forms.list"
  const val FORM_CONTAINER = "form.container"
  const val FORM_TABS = "form.tabs"
  const val LOGIN_USERNAME = "login.username"
  const val LOGIN_SSO = "login.sso"
  const val LOGIN_GUEST = "login.guest"
  const val LOGIN_SHARE = "login.share"
  const val LOGIN_SERVER = "login.server"
}
