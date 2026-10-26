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
package org.openmrs.android.fhir.forms

import android.content.Context
import ca.uhn.fhir.context.FhirContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.openmrs.android.fhir.config.AppConfig
import timber.log.Timber

/**
 * Pays, in the background, the one-time cost of the form engine.
 *
 * The first time a data-capture form evaluates an expression the library builds its FHIRPath
 * engine, which parses every FHIR R4 structure definition (several seconds). Done lazily that
 * happens on the main thread while the user waits for the first form to open; initializing the same
 * class early on a low-priority thread makes the first form as fast as the following ones.
 */
object FormEngineWarmUp {
  private val started = AtomicBoolean(false)

  fun start(context: Context) {
    if (!started.compareAndSet(false, true)) return
    val appContext = context.applicationContext
    thread(name = "form-engine-warm-up", priority = Thread.MIN_PRIORITY) {
      val startedAt = System.currentTimeMillis()
      try {
        Class.forName(FHIR_PATH_UTIL_CLASS)
        // The registration form is the one opened most: have it parsed (it is kept for reuse).
        appContext.bundledQuestionnaire(
          AppConfig.get(appContext).registrationForm,
          FhirContext.forR4Cached().newJsonParser(),
        )
        Timber.i("Form engine ready in %d ms", System.currentTimeMillis() - startedAt)
      } catch (e: Throwable) {
        // Only an optimization: the form initializes it on first use if this fails.
        Timber.w(e, "Could not warm up the form engine")
      }
    }
  }

  private const val FHIR_PATH_UTIL_CLASS =
    "com.google.android.fhir.datacapture.fhirpath.FhirPathUtilKt"
}
