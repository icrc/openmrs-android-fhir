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

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object EncryptionHelper {

  private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
  private const val GCM_TAG_LENGTH = 16

  fun encrypt(data: String, secretKey: SecretKey): Pair<String, ByteArray> {
    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val iv = cipher.iv // Save this IV for decryption
    val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
    return Pair(Base64.encodeToString(encryptedBytes, Base64.DEFAULT), iv)
  }

  fun decrypt(encryptedData: String, secretKey: SecretKey, iv: ByteArray): String {
    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
    val ivSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
    val decryptedBytes = cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT))
    return String(decryptedBytes, Charsets.UTF_8)
  }
}
