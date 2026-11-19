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
package org.openmrs.android.fhir.patientlists

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

/** The attribute types the server last said patient lists can have, kept for working offline. */
interface AttributeTypeStore {
  fun names(): List<String>

  fun save(names: List<String>)

  companion object {
    fun memory(): AttributeTypeStore =
      object : AttributeTypeStore {
        private var kept = emptyList<String>()

        override fun names() = kept

        override fun save(names: List<String>) {
          kept = names
        }
      }
  }
}

class StoredAttributeTypes(context: Context) : AttributeTypeStore {
  private val prefs =
    context.applicationContext.getSharedPreferences("patient_list_attribute_types", 0)

  override fun names(): List<String> =
    try {
      val array = JSONArray(prefs.getString(KEY, null) ?: "[]")
      (0 until array.length()).map { array.getString(it) }
    } catch (e: JSONException) {
      emptyList()
    }

  override fun save(names: List<String>) {
    prefs.edit().putString(KEY, JSONArray(names).toString()).apply()
  }

  private companion object {
    const val KEY = "names"
  }
}
