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

class ReferenceUrlResolver(val context: Context) : UrlResolver {

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
