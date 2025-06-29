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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Worker-compatible permission checker that only checks permission status without requesting
 * permissions (which requires UI interaction)
 */
class PermissionChecker(private val context: Context) {

  fun checkNotificationPermissionStatus(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      // Permission not required for older versions
      true
    }
  }

  fun checkNotificationPermissionStatus(callback: (isGranted: Boolean) -> Unit) {
    callback(checkNotificationPermissionStatus())
  }

  fun isNotificationPermissionRequired(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
  }
}

/**
 * Activity-based permission helper that can both check and request permissions This should only be
 * used in UI contexts (Activities/Fragments)
 */
class PermissionHelper(
  private val activity: AppCompatActivity,
  private val permissionChecker: PermissionChecker = PermissionChecker(activity),
) {

  private var onPermissionResult: ((Boolean) -> Unit)? = null

  private val permissionLauncher: ActivityResultLauncher<String> =
    activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
      onPermissionResult?.invoke(isGranted)
    }

  fun checkAndRequestNotificationPermission(callback: (Boolean) -> Unit) {
    onPermissionResult = callback

    if (permissionChecker.isNotificationPermissionRequired()) {
      when {
        permissionChecker.checkNotificationPermissionStatus() -> {
          callback(true)
        }
        else -> {
          permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      }
    } else {
      callback(true) // Permission not required for older versions
    }
  }

  fun checkNotificationPermissionStatus(callback: (isGranted: Boolean) -> Unit) {
    permissionChecker.checkNotificationPermissionStatus(callback)
  }

  fun checkNotificationPermissionStatus(): Boolean {
    return permissionChecker.checkNotificationPermissionStatus()
  }
}

@Singleton
class PermissionCheckerFactory @Inject constructor() {

  fun create(context: Context): PermissionChecker {
    return PermissionChecker(context)
  }
}

@Singleton
class PermissionHelperFactory
@Inject
constructor(
  private val permissionCheckerFactory: PermissionCheckerFactory,
) {

  fun create(activity: AppCompatActivity): PermissionHelper {
    val permissionChecker = permissionCheckerFactory.create(activity)
    return PermissionHelper(activity, permissionChecker)
  }
}

/** Extension functions for easier usage */
fun Context.createPermissionChecker(): PermissionChecker {
  return PermissionChecker(this)
}

fun AppCompatActivity.createPermissionHelper(): PermissionHelper {
  return PermissionHelper(this)
}
