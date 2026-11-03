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
package org.openmrs.android.fhir.data.transfer

import android.content.Context

/**
 * The encounters that came in through an import and have not been checked for their provider yet
 * (see `OpenMRSHelper.useOwnProviderWhereTheOriginalIsNotVisible`). Only these are looked at before
 * an upload: what a user records on their own device has their own provider, and needs no check.
 * With nothing imported the set is empty and the check costs one preference read.
 */
object ImportedEncounters {
  private const val PREFS_NAME = "imported_encounters"
  private const val KEY_IDS = "ids"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun ids(context: Context): Set<String> =
    prefs(context).getStringSet(KEY_IDS, emptySet()).orEmpty().toSet()

  fun add(context: Context, ids: Collection<String>) {
    if (ids.isEmpty()) return
    prefs(context).edit().putStringSet(KEY_IDS, ids(context) + ids).apply()
  }

  fun remove(context: Context, ids: Collection<String>) {
    if (ids.isEmpty()) return
    prefs(context).edit().putStringSet(KEY_IDS, ids(context) - ids.toSet()).apply()
  }
}
