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

import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class ApiHelperTest {
  private fun errorOf(code: Int, body: String = "") =
    Response.error<String>(code, body.toResponseBody("application/json".toMediaType()))

  @Test
  fun aBodyIsASuccess() {
    val result = executeApiHelper { Response.success("ok") }

    assertEquals(ApiResponse.Success("ok"), result)
  }

  @Test
  fun anEmptySuccessIsAServerError() {
    val result = executeApiHelper<String> { Response.success(null) }

    assertIs<ApiResponse.ServerError>(result)
  }

  @Test
  fun aMissingResourceIsAServerError() {
    assertIs<ApiResponse.ServerError>(executeApiHelper { errorOf(404) })
  }

  @Test
  fun anInternalServerErrorKeepsTheStandardMessage() {
    val result = executeApiHelper { errorOf(500) }

    assertEquals(ApiResponse.ServerError(UNEXPECTED_INTERNAL_SERVER), result)
  }

  @Test
  fun aServerThatCannotBeFoundIsNoConnection() {
    val result = executeApiHelper<String> { throw UnknownHostException("no such host") }

    assertEquals(ApiResponse.NoInternetConnection, result)
  }

  @Test
  fun anyOtherFailureIsAServerError() {
    val result = executeApiHelper<String> { throw IllegalStateException("boom") }

    assertEquals(ApiResponse.ServerError(UNEXPECTED_INTERNAL_SERVER), result)
  }
}
