package org.openmrs.android.fhir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.fhir.FhirEngineProvider
import com.google.android.fhir.datacapture.UrlResolver
import com.google.android.fhir.db.ResourceNotFoundException
import com.google.android.fhir.get
import org.hl7.fhir.r4.model.Binary
import org.hl7.fhir.r4.model.ResourceType
import timber.log.Timber

class ReferenceUrlResolver (val context: Context) : UrlResolver {

  private val fhirEngine = FhirEngineProvider.getInstance(context)

  override suspend fun resolveBitmapUrl(url: String): Bitmap? {
    val logicalId = getLogicalIdFromFhirUrl(url, ResourceType.Binary)
    return try {
      val binary = fhirEngine.get<Binary>(logicalId)
      BitmapFactory.decodeByteArray(binary.data, 0, binary.data.size)
    } catch (e: ResourceNotFoundException) {
      Timber.e(e)
      null
    }
  }
}

/**
 * Returns the logical id of a FHIR Resource URL e.g
 * 1. "https://hapi.fhir.org/baseR4/Binary/1234" returns "1234".
 * 2. "https://hapi.fhir.org/baseR4/Binary/1234/_history/2" returns "1234".
 */
private fun getLogicalIdFromFhirUrl(url: String, resourceType: ResourceType): String {
  return url.substringAfter("${resourceType.name}/").substringBefore("/")
}