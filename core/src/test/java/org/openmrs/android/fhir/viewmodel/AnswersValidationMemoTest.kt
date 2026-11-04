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

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AnswersValidationMemoTest {
  private val memo = AnswersValidationMemo()
  private val form = Any()
  private var checks = 0

  private suspend fun isValid(
    owner: String = "a",
    form: Any = this.form,
    answers: String = "{}",
    valid: Boolean = true,
  ) =
    memo.isValid(owner, form, answers) {
      checks++
      valid
    }

  @Test
  fun theSameAnswersAgainstTheSameFormAreCheckedOnce() = runTest {
    assertTrue(isValid())
    assertTrue(isValid())
    assertTrue(isValid())

    assertEquals(1, checks)
  }

  @Test
  fun anInvalidResultIsRememberedToo() = runTest {
    assertFalse(isValid(valid = false))
    assertFalse(isValid(valid = true))

    assertEquals(1, checks)
  }

  @Test
  fun changedAnswersAreCheckedAgain() = runTest {
    assertFalse(isValid(answers = "one", valid = false))
    assertTrue(isValid(answers = "two", valid = true))

    assertEquals(2, checks)
  }

  @Test
  fun aReplacedFormIsCheckedAgain() = runTest {
    isValid()
    isValid(form = Any())

    assertEquals(2, checks)
  }

  @Test
  fun everyOwnerKeepsItsOwnResult() = runTest {
    assertFalse(isValid(owner = "a", valid = false))
    assertTrue(isValid(owner = "b", valid = true))
    assertFalse(isValid(owner = "a", valid = true))
    assertTrue(isValid(owner = "b", valid = false))

    assertEquals(2, checks)
  }

  @Test
  fun clearingForgetsEverything() = runTest {
    isValid()
    memo.clear()
    isValid()

    assertEquals(2, checks)
  }
}
