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

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.hl7.fhir.r4.model.Meta
import org.hl7.fhir.r4.model.Narrative
import org.hl7.fhir.r4.model.Questionnaire
import org.junit.Test

/** Which forms are the same on the device and on the server, and which copy a form is read from. */
class FormUpdatesTest {
  private val parser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
  private val assets = File("src/main/assets/$BUNDLED_QUESTIONNAIRES_DIR")

  private fun versions(
    app: String? = "a",
    held: String? = null,
    server: String? = "a",
    usable: Boolean = true,
    checked: Boolean = true,
  ) = FormVersions("id", "Form", app, held, server, null, usable, checked).status()

  @Test
  fun aFormTheServerSaysTheSameAsIsUpToDate() {
    val status = versions(app = "a", server = "a")

    assertEquals(FormState.UP_TO_DATE, status.state)
    assertEquals(FormSource.APP, status.inUse)
  }

  @Test
  fun aFormTheServerSaysDifferentlyHasAnUpdate() {
    assertEquals(FormState.UPDATE_AVAILABLE, versions(app = "a", server = "b").state)
  }

  @Test
  fun theCopyTakenFromTheServerIsTheOneComparedAndTheOneInUse() {
    val taken = versions(app = "a", held = "b", server = "b")
    val serverChanged = versions(app = "a", held = "b", server = "c")

    assertEquals(FormState.UP_TO_DATE, taken.state)
    assertEquals(FormSource.SERVER, taken.inUse)
    assertEquals(FormState.UPDATE_AVAILABLE, serverChanged.state)
    assertEquals(FormSource.SERVER, serverChanged.inUse)
  }

  @Test
  fun aFormOnlyTheServerHasIsNew() {
    val status = versions(app = null, held = null, server = "x")

    assertEquals(FormState.NEW_ON_SERVER, status.state)
    assertEquals(FormSource.NONE, status.inUse)
  }

  @Test
  fun aFormOnlyTheDeviceHasIsNotOnTheServer() {
    assertEquals(FormState.NOT_ON_SERVER, versions(app = "a", server = null).state)
    assertEquals(FormState.NOT_ON_SERVER, versions(app = null, held = "b", server = null).state)
  }

  @Test
  fun aServerCopyThatCannotBeReadIsNotOffered() {
    assertEquals(FormState.NOT_USABLE, versions(app = "a", server = "b", usable = false).state)
  }

  @Test
  fun beforeTheServerIsAskedNothingIsSaidAboutIt() {
    val status = versions(app = "a", server = null, checked = false)

    assertEquals(FormState.NOT_CHECKED, status.state)
    assertEquals(FormSource.APP, status.inUse)
  }

  @Test
  fun theServersFormsAreReadFromTheEngineOnlyWhenTheyAreInUse() {
    assertTrue(usesStoredForm(embeddedOnly = false, accepted = emptySet(), id = "a"))
    assertTrue(usesStoredForm(embeddedOnly = true, accepted = setOf("a"), id = "a"))
    assertEquals(false, usesStoredForm(embeddedOnly = true, accepted = setOf("a"), id = "b"))
    assertEquals(false, usesStoredForm(embeddedOnly = true, accepted = emptySet(), id = "a"))
  }

  @Test
  fun theCopyOfTheAppAndTheSameFormWrittenByTheServerHaveTheSameFingerprint() {
    val files = assets.listFiles { file -> file.extension == "json" }.orEmpty()
    assertTrue(files.size >= 20, "no forms found: is the test running in core/?")

    files.forEach { file ->
      val app =
        parser.parseResource(Questionnaire::class.java, expandCompactQuestionnaire(file.readText()))
      // What a server hands out: the same form written out and read back, with its own bookkeeping.
      val served =
        parser.parseResource(Questionnaire::class.java, parser.encodeResourceToString(app)).also {
          it.meta = Meta().setVersionId("7").setLastUpdated(java.util.Date())
          it.text = Narrative().also { text -> text.divAsString = "<div>generated</div>" }
          it.id = "Questionnaire/${app.idElement.idPart}/_history/7"
        }

      assertEquals(app.fingerprint(parser), served.fingerprint(parser), file.name)
    }
  }

  @Test
  fun aChangedQuestionChangesTheFingerprint() {
    val file = assets.listFiles { f -> f.extension == "json" }!!.first()
    val app =
      parser.parseResource(Questionnaire::class.java, expandCompactQuestionnaire(file.readText()))
    val changed = app.copy().also { it.item.first().item.first().text = "Another question" }
    val renamed = app.copy().also { it.title = "Another title" }

    assertNotEquals(app.fingerprint(parser), changed.fingerprint(parser))
    assertNotEquals(app.fingerprint(parser), renamed.fingerprint(parser))
  }
}
