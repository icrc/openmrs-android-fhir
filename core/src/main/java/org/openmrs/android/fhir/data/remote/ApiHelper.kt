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
import org.greenrobot.eventbus.EventBus
import org.openmrs.android.fhir.data.remote.model.Error
import org.openmrs.android.fhir.extensions.fromJson
import retrofit2.Response

const val UNEXPECTED_INTERNAL_SERVER = "Unexpected internal server error."

inline fun <T> executeApiHelper(responseMethod: () -> Response<T>): ApiResponse<T> {
  return try {
    val response = responseMethod.invoke()
    when (response.code()) {
      in 200..300 -> {
        val responseBody = response.body()
        if (responseBody != null) {
          ApiResponse.Success(responseBody)
        } else {
          ApiResponse.ServerError("The application has encountered an unknown error.")
        }
      }
      400,
      401, -> {
        response
          .errorBody()
          ?.string()
          ?.takeIf { it.isNotEmpty() }
          ?.fromJson<Error>()
          ?.let {
            ApiResponse.ApiError(
              it.errorDescription
                ?: it.errorMessage ?: "The application has encountered an unknown error.",
            )
          }
          ?: run {
            EventBus.getDefault().post(UnauthorizedAccess)
            ApiResponse.UnauthorizedAccess(
              "You are unauthorized to access the requested resource. Please log in.",
            )
          }
      }
      404 ->
        ApiResponse.ServerError(
          "We could not find the resource you requested. Please refer to the documentation for the list of resources.",
        )
      500 -> ApiResponse.ServerError(UNEXPECTED_INTERNAL_SERVER)
      else -> ApiResponse.ServerError(UNEXPECTED_INTERNAL_SERVER)
    }
  } catch (exception: Exception) {
    exception.printStackTrace()
    when (exception) {
      is UnknownHostException -> ApiResponse.NoInternetConnection
      else -> ApiResponse.ServerError(UNEXPECTED_INTERNAL_SERVER)
    }
  }
}

object UnauthorizedAccess
