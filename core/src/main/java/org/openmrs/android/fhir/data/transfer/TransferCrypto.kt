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

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The file is not one this app exported (wrong header, cut short). */
class InvalidTransferFileException : Exception("Not an export file")

/** The PIN does not open the file. */
class WrongPinException : Exception("Wrong PIN")

/**
 * Basic protection for an export file: the 4-digit PIN is stretched into an AES-256 key (PBKDF2,
 * random salt) and the data sealed with AES-GCM, which also detects a wrong PIN or a damaged file.
 *
 * File layout: `"OMRS"` + version (1 byte) + salt (16) + IV (12) + ciphertext with the GCM tag.
 *
 * A 4-digit PIN only allows 10,000 combinations, so this keeps the data from being casually read in
 * a mail attachment or a chat, it is not protection against someone set on breaking it. The
 * stretching makes each guess cost a noticeable fraction of a second.
 */
object TransferCrypto {
  private val MAGIC = "OMRS".toByteArray(Charsets.US_ASCII)
  private const val VERSION: Byte = 1
  private const val SALT_BYTES = 16
  private const val IV_BYTES = 12
  private const val ITERATIONS = 60_000
  private const val KEY_BITS = 256
  private const val TAG_BITS = 128
  private val HEADER_BYTES = MAGIC.size + 1 + SALT_BYTES + IV_BYTES
  private val random = SecureRandom()

  fun encrypt(plain: ByteArray, pin: String): ByteArray {
    val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
    val iv = ByteArray(IV_BYTES).also(random::nextBytes)
    val cipher =
      Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, key(pin, salt), GCMParameterSpec(TAG_BITS, iv))
      }
    return MAGIC + VERSION + salt + iv + cipher.doFinal(plain)
  }

  /** @throws InvalidTransferFileException, WrongPinException */
  fun decrypt(file: ByteArray, pin: String): ByteArray {
    if (
      file.size <= HEADER_BYTES ||
        !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) ||
        file[MAGIC.size] != VERSION
    ) {
      throw InvalidTransferFileException()
    }
    val saltStart = MAGIC.size + 1
    val salt = file.copyOfRange(saltStart, saltStart + SALT_BYTES)
    val iv = file.copyOfRange(saltStart + SALT_BYTES, HEADER_BYTES)
    val cipher =
      Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, key(pin, salt), GCMParameterSpec(TAG_BITS, iv))
      }
    return try {
      cipher.doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
    } catch (e: AEADBadTagException) {
      throw WrongPinException()
    } catch (e: GeneralSecurityException) {
      // Some providers report a failed tag check as a plain BadPaddingException.
      throw WrongPinException()
    }
  }

  private fun key(pin: String, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
    val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    spec.clearPassword()
    return SecretKeySpec(bytes, "AES")
  }
}
