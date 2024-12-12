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
package org.openmrs.android.fhir.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.lifecycle.ViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BasicLoginActivityViewModel @Inject constructor(private val applicationContext: Context) :
  ViewModel() {

  private val sharedPreferences: SharedPreferences =
    applicationContext.getSharedPreferences("AppPrefs", Application.MODE_PRIVATE)
  private val MAX_FAILED_ATTEMPTS = 5
  private val LOCKOUT_TIME_MINUTES = 5L

  fun handleLogin(context: Context, username: String, password: String) {
    if (isLockedOut()) {
      val waitTime = getRemainingLockoutTime()
      Toast.makeText(
          context,
          "Too many failed attempts. Try again in $waitTime minutes.",
          Toast.LENGTH_SHORT,
        )
        .show()
      return
    }

    if (validateCredentials(username, password)) {
      resetFailedAttempts()
      saveSessionDuration()
      Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
    } else {
      incrementFailedAttempts()
      if (getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
        lockout()
        Toast.makeText(
            context,
            "Too many failed attempts. Locked out for $LOCKOUT_TIME_MINUTES minutes.",
            Toast.LENGTH_SHORT,
          )
          .show()
      } else {
        Toast.makeText(context, "Invalid credentials. Try again.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun validateCredentials(username: String, password: String): Boolean {
    // Replace with actual validation logic
    return username == "admin" && password == "password123"
  }

  private fun incrementFailedAttempts() {
    val attempts = getFailedAttempts() + 1
    sharedPreferences.edit().putInt("failed_attempts", attempts).apply()
  }

  private fun getFailedAttempts(): Int {
    return sharedPreferences.getInt("failed_attempts", 0)
  }

  private fun resetFailedAttempts() {
    sharedPreferences.edit().putInt("failed_attempts", 0).apply()
  }

  private fun lockout() {
    val lockoutEndTime =
      System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(LOCKOUT_TIME_MINUTES)
    sharedPreferences.edit().putLong("lockout_end_time", lockoutEndTime).apply()
  }

  private fun isLockedOut(): Boolean {
    val lockoutEndTime = sharedPreferences.getLong("lockout_end_time", 0L)
    return System.currentTimeMillis() < lockoutEndTime
  }

  private fun getRemainingLockoutTime(): Long {
    val lockoutEndTime = sharedPreferences.getLong("lockout_end_time", 0L)
    return TimeUnit.MILLISECONDS.toMinutes(lockoutEndTime - System.currentTimeMillis())
  }

  private fun saveSessionDuration() {
    val sessionDuration = 2 * 60 * 60 * 1000L // Default to 2 hours
    sharedPreferences
      .edit()
      .putLong("session_end_time", System.currentTimeMillis() + sessionDuration)
      .apply()
  }
}
