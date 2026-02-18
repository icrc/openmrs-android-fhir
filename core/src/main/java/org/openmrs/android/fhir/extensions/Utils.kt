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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.IntegerType
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ServerConnectivityState

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
    val fileDir = File(context.filesDir, "Documents")
    if (!fileDir.exists()) {
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
  return Base64.encodeToString(this, Base64.NO_WRAP)
}

fun String.decodeToByteArray(): ByteArray {
  return Base64.decode(this, Base64.NO_WRAP)
}

suspend fun Context.getServerConnectivityState(apiManager: ApiManager): ServerConnectivityState {
  val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  val network = connectivityManager.activeNetwork ?: return ServerConnectivityState.Offline
  val networkCapabilities =
    connectivityManager.getNetworkCapabilities(network) ?: return ServerConnectivityState.Offline
  if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
    return ServerConnectivityState.Offline
  }

  val timeoutMillis = FhirApplication.serverConnectivityTimeoutMillis(this)
  val isServerReachable =
    withContext(Dispatchers.IO) {
      val url = FhirApplication.checkServerUrl(this@getServerConnectivityState)
      withTimeoutOrNull(timeoutMillis) { apiManager.checkServerConnection(url) } ?: false
    }

  return if (isServerReachable) {
    ServerConnectivityState.ServerConnected
  } else {
    ServerConnectivityState.InternetOnly
  }
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
        val calendar: Calendar =
          Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
        answer.value = DateType(calendar)
      }
    }
  }
}

fun convertQuantityObsToIntegerObs(obs: Observation): Observation {
  if (obs.hasValueQuantity() && obs.valueQuantity.hasValue()) {
    val intValue = obs.valueQuantity.value.toInt()
    obs.value = IntegerType(intValue)
  }
  return obs
}

fun convertDateAnswersToUtcDateTime(response: QuestionnaireResponse) {
  response.allItems.forEach { item ->
    item.answer.forEach { answer ->
      if (answer.value is DateType) {
        val date = (answer.value as DateType).value
        answer.value = DateTimeType(date, TemporalPrecisionEnum.SECOND, TimeZone.getTimeZone("UTC"))
      }
    }
  }
}

fun nowUtcDateTime(): DateTimeType =
  DateTimeType(Date(), TemporalPrecisionEnum.SECOND, TimeZone.getTimeZone("UTC"))

fun nowLocalDateTime(): DateTimeType =
  DateTimeType(Date(), TemporalPrecisionEnum.SECOND, TimeZone.getDefault())

fun utcDateToLocalDate(date: Date): Date {
  return Date(date.time + TimeZone.getDefault().getOffset(date.time))
}

/** Format a Date in the device's local time zone. */
fun Date.toLocalString(
  pattern: String = "dd MM yyyy",
  zone: ZoneId = ZoneId.systemDefault(),
  locale: Locale = Locale.getDefault(),
): String {
  val fmt = DateTimeFormatter.ofPattern(pattern, locale).withZone(zone)
  return fmt.format(this.toInstant())
}

fun findItemByLinkId(
  items: List<QuestionnaireItemComponent>?,
  linkId: String,
): QuestionnaireItemComponent? {
  if (items == null) return null
  items.forEach { item ->
    if (item.linkId == linkId) {
      return item
    }
    val nested = findItemByLinkId(item.item, linkId)
    if (nested != null) {
      return nested
    }
  }
  return null
}

private const val QUESTIONNAIRE_ITEM_CONTROL_URL =
  "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl"
private const val QUESTIONNAIRE_ITEM_CONTROL_SYSTEM =
  "http://hl7.org/fhir/questionnaire-item-control"
private const val QUESTIONNAIRE_ITEM_CONTROL_PAGE = "page"
private const val PAGE_SPACER_TEXT = "&#8203;<br><br><br><br><br><br><br>"

fun Questionnaire.ensurePageGroupsHaveTrailingSpacer() {
  item.forEach { questionnaireItem -> questionnaireItem.ensurePageGroupsHaveTrailingSpacer() }
}

private fun QuestionnaireItemComponent.ensurePageGroupsHaveTrailingSpacer() {
  item.forEach { childItem -> childItem.ensurePageGroupsHaveTrailingSpacer() }

  if (!isPageGroupItem()) {
    return
  }

  val existingSpacerIndex = item.indexOfFirst { it.isPageSpacerItem() }
  if (existingSpacerIndex != -1) {
    val spacerItem = item.removeAt(existingSpacerIndex)
    item.add(spacerItem)
    return
  }

  item.add(
    QuestionnaireItemComponent().apply {
      linkId = generateUuid()
      type = Questionnaire.QuestionnaireItemType.DISPLAY
      text = PAGE_SPACER_TEXT
    },
  )
}

private fun QuestionnaireItemComponent.isPageGroupItem(): Boolean {
  return extension.any { extension ->
    extension.url == QUESTIONNAIRE_ITEM_CONTROL_URL &&
      (extension.value as? CodeableConcept)?.coding?.any { coding ->
        coding.system == QUESTIONNAIRE_ITEM_CONTROL_SYSTEM &&
          coding.code == QUESTIONNAIRE_ITEM_CONTROL_PAGE
      } == true
  }
}

private fun QuestionnaireItemComponent.isPageSpacerItem(): Boolean {
  return type == Questionnaire.QuestionnaireItemType.DISPLAY && text == PAGE_SPACER_TEXT
}
