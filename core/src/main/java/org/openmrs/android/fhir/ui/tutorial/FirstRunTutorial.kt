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

import android.content.Context
import org.openmrs.android.fhir.R

/**
 * The tour that runs once, on the first launch after the app was installed.
 *
 * Whether it is still to run is a flag in this app's own `SharedPreferences` file: no server, no
 * account, nothing leaves the device. The app does not back its data up (`allowBackup=false`), so
 * uninstalling and installing again starts from scratch and the tour runs again, while an update of
 * the app keeps the flag.
 */
object FirstRunTutorial {
  private const val PREFS = "first_run_tutorial"
  private const val KEY_PENDING = "pending"

  val steps =
    listOf(
      TutorialStep(
        targetKey = TutorialTargets.HOME_PATIENTS,
        title = R.string.tutorial_home_patients_title,
        body = R.string.tutorial_home_patients_body,
        action = TutorialAction.Tap,
      ),
      TutorialStep(
        targetKey = TutorialTargets.PATIENT_SEARCH,
        title = R.string.tutorial_patients_search_title,
        body = R.string.tutorial_patients_search_body,
        action = TutorialAction.Acknowledge,
      ),
      TutorialStep(
        targetKey = TutorialTargets.PATIENT_REGISTER,
        title = R.string.tutorial_patients_register_title,
        body = R.string.tutorial_patients_register_body,
        action = TutorialAction.Tap,
      ),
    )

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /**
   * Called when the app starts. The first time ever it decides: a fresh installation is to get the
   * tour, an update of an app that was already in use (installed before the tour existed) is not.
   */
  fun init(context: Context) {
    val prefs = prefs(context)
    if (prefs.contains(KEY_PENDING)) return
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    prefs.edit().putBoolean(KEY_PENDING, info.firstInstallTime == info.lastUpdateTime).apply()
  }

  fun isPending(context: Context): Boolean = prefs(context).getBoolean(KEY_PENDING, false)

  /**
   * Starts the tour if it has not run yet. It is marked as done as it starts, so it never comes
   * back, whether the user finishes it, skips it or closes it.
   */
  fun startIfPending(context: Context) {
    if (!isPending(context) || TutorialController.isActive) return
    prefs(context).edit().putBoolean(KEY_PENDING, false).apply()
    TutorialController.start(steps)
  }
}
