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
package org.openmrs.android.fhir.data.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.openmrs.android.fhir.SplashActivity
import org.openmrs.android.fhir.config.AppConfig

/**
 * The OpenMRS server the app talks to. It defaults to the build configuration, but the login screen
 * can point the app at another server (an IP address or a domain); the choice is kept on the
 * device.
 *
 * All server URLs are derived from one base such as `http://192.168.1.5:8080/openmrs`.
 */
object ServerSettings {
  private const val PREFS_NAME = "server_settings"
  private const val KEY_SERVER_URL = "server_url"
  private const val FHIR_PATH = "/ws/fhir2/R4/"
  private const val REST_PATH = "/ws/rest/v1/"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  /** The server the user chose on this device, or `null` when the build default is used. */
  fun override(context: Context): String? =
    prefs(context).getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() }

  fun setOverride(context: Context, baseUrl: String?) {
    val editor = prefs(context).edit()
    if (baseUrl.isNullOrBlank()) {
      editor.remove(KEY_SERVER_URL)
    } else {
      editor.putString(KEY_SERVER_URL, baseUrl)
    }
    // commit(), not apply(): the app is restarted right after and must not lose the choice.
    editor.commit()
  }

  /** The server from the build configuration, as a base URL. */
  fun defaultUrl(context: Context): String = AppConfig.get(context).serverUrl

  /** The server in use right now. */
  fun activeUrl(context: Context): String = override(context) ?: defaultUrl(context)

  fun fhirBaseUrl(context: Context): String = activeUrl(context) + FHIR_PATH

  fun restUrl(context: Context): String = activeUrl(context) + REST_PATH

  fun checkServerUrl(context: Context): String = activeUrl(context) + REST_PATH + "session"

  /**
   * Turns what the user typed into a base URL, or `null` when it cannot be a server address.
   * `192.168.1.5:8080` becomes `http://192.168.1.5:8080/openmrs`; a pasted REST or FHIR address is
   * cut back to the server.
   */
  fun normalize(input: String): String? {
    var text = input.trim()
    if (text.isEmpty()) return null
    if (!text.contains("://")) text = "http://$text"
    text = text.substringBefore("/ws/").trimEnd('/')
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
    return if (uri.rawPath.isNullOrEmpty()) "$text/openmrs" else text
  }

  /**
   * Restarts the app so every component picks up the server in use. It starts again at [target]
   * (the splash screen, which decides where to go; the login page when the server was changed from
   * there, so the user stays on it), with the [extras] the screen had.
   */
  fun restartApp(
    context: Context,
    target: Class<out Activity> = SplashActivity::class.java,
    extras: Bundle? = null,
  ) {
    val intent =
      Intent(context, target)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    extras?.let { intent.putExtras(it) }
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
  }

  sealed interface TestResult {
    data class Success(val millis: Long) : TestResult

    data class Failure(val reason: Reason, val code: Int? = null) : TestResult
  }

  enum class Reason {
    InvalidAddress,
    ServerNotFound,
    TimedOut,
    CertificateProblem,
    CleartextNotAllowed,
    UnexpectedAnswer,
    Unreachable,
  }

  /** Asks the server for its public session endpoint, which needs no login. */
  suspend fun testConnection(baseUrl: String): TestResult =
    withContext(Dispatchers.IO) {
      val request =
        runCatching {
            Request.Builder().url(baseUrl.trimEnd('/') + REST_PATH + "session").get().build()
          }
          .getOrNull()
          ?: return@withContext TestResult.Failure(Reason.InvalidAddress)
      val client =
        OkHttpClient.Builder()
          .connectTimeout(8, TimeUnit.SECONDS)
          .readTimeout(8, TimeUnit.SECONDS)
          .followRedirects(false)
          .build()
      val started = System.currentTimeMillis()
      try {
        client.newCall(request).execute().use { response ->
          // 401/403 still prove an OpenMRS server answered.
          if (response.isSuccessful || response.code == 401 || response.code == 403) {
            TestResult.Success(System.currentTimeMillis() - started)
          } else {
            TestResult.Failure(Reason.UnexpectedAnswer, response.code)
          }
        }
      } catch (e: UnknownHostException) {
        TestResult.Failure(Reason.ServerNotFound)
      } catch (e: SocketTimeoutException) {
        TestResult.Failure(Reason.TimedOut)
      } catch (e: SSLException) {
        TestResult.Failure(Reason.CertificateProblem)
      } catch (e: IOException) {
        if (e.message?.contains("cleartext", ignoreCase = true) == true) {
          TestResult.Failure(Reason.CleartextNotAllowed)
        } else {
          TestResult.Failure(Reason.Unreachable)
        }
      }
    }
}
