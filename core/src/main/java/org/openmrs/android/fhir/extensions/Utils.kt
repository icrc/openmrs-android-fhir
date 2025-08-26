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

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Base64
import android.view.View
import ca.uhn.fhir.model.api.TemporalPrecisionEnum
import com.google.android.fhir.datacapture.extensions.allItems
import com.google.android.material.snackbar.Snackbar
import com.squareup.moshi.Moshi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.QuestionnaireResponse

inline fun <reified T> T.toJson(): String {
  return Moshi.Builder().build().adapter(T::class.java).toJson(this)
}

inline fun <reified T> String.fromJson(): T? {
  return Moshi.Builder().build().adapter(T::class.java).fromJson(this)
}

fun showSnackBar(
  activity: Activity,
  message: String,
  action: String? = null,
  actionListener: View.OnClickListener? = null,
  duration: Int = Snackbar.LENGTH_SHORT,
) {
  val snackBar =
    Snackbar.make(activity.findViewById(android.R.id.content), message, duration)
      .setBackgroundTint(Color.GRAY)
      .setTextColor(Color.WHITE)
  if (action != null && actionListener != null) {
    snackBar.setAction(action, actionListener)
  }
  snackBar.show()
}

fun saveToFile(context: Context, fileName: String, content: String): File? {
  return try {
    val fileDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
    if (fileDir != null && !fileDir.exists()) {
      fileDir.mkdirs()
    }
    val file = File(fileDir, fileName)
    FileOutputStream(file).use { outputStream -> outputStream.write(content.toByteArray()) }
    file
  } catch (e: IOException) {
    e.printStackTrace()
    null
  }
}

fun ByteArray.encodeToString(): String {
  return Base64.encodeToString(this, Base64.DEFAULT)
}

fun String.decodeToByteArray(): ByteArray {
  return Base64.decode(this, Base64.DEFAULT)
}

fun isInternetAvailable(context: Context): Boolean {
  val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  val network = connectivityManager.activeNetwork ?: return false
  val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
  return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

val Int.minutesInMillis: Long
  get() = this * 60 * 1000L

val Int.hoursInMillis: Long
  get() = this * 60 * 60 * 1000L

fun generateUuid(): String {
  return UUID.randomUUID().toString()
}

fun convertDateTimeAnswersToDate(response: QuestionnaireResponse) {
  response.allItems.forEach { item ->
    item.answer.forEach { answer ->
      if (answer.value is DateTimeType) {
        val date = (answer.value as DateTimeType).value
        answer.value = DateType(date)
      }
    }
  }
}

fun convertDateAnswersToDateTime(response: QuestionnaireResponse) {
  response.allItems.forEach { item ->
    item.answer.forEach { answer ->
      if (answer.value is DateType) {
        val date = (answer.value as DateType).value
        answer.value = DateTimeType(date)
      }
    }
  }
}

fun nowUtcDateTime(): DateTimeType =
  DateTimeType(Date(), TemporalPrecisionEnum.SECOND, TimeZone.getTimeZone("UTC"))

fun nowLocalDateTime(): DateTimeType =
  DateTimeType(Date(), TemporalPrecisionEnum.SECOND, TimeZone.getDefault())

/** Format a Date in the device's local time zone. */
fun Date.toLocalString(
  pattern: String = "dd MM yyyy",
  zone: ZoneId = ZoneId.systemDefault(),
  locale: Locale = Locale.getDefault(),
): String {
  val fmt = DateTimeFormatter.ofPattern(pattern, locale).withZone(zone)
  return fmt.format(this.toInstant())
}
