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
package org.openmrs.android.fhir.extensions

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec
import org.openmrs.android.fhir.EncryptionHelper
import org.openmrs.android.fhir.extensions.FileLoggingTree.Companion.APPLICATION_LOG_FILE_NAME
import timber.log.Timber

class FileLoggingTree(context: Context, private val maxFileSize: Long, password: String) :
  Timber.Tree() {

  private val logFile: File = File(context.filesDir, APPLICATION_LOG_FILE_NAME)
  private val secretKey =
    SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(password.toByteArray()), "AES")

  override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    writeLogToFile(encrypt("$priority/$tag: $message"))
  }

  private fun encrypt(log: String): String {
    val (cipherText, iv) = EncryptionHelper.encrypt(log, secretKey)
    return "${iv.encodeToString()}:$cipherText"
  }

  private fun writeLogToFile(log: String) {
    try {
      if (!logFile.exists()) {
        logFile.createNewFile()
      }

      // Check if the file size exceeds the maximum threshold
      if (logFile.length() > maxFileSize) {
        trimLogFile()
      }

      // Append the log to the file
      FileWriter(logFile, true).use { writer -> writer.appendLine(log) }
    } catch (e: IOException) {
      Log.e("FileLoggingTree", "Failed to write log to file", e)
    }
  }

  private fun trimLogFile() {
    try {
      val tempFile = File("${logFile.absolutePath}.tmp")

      // First pass: count total lines
      val totalLines =
        BufferedReader(logFile.reader()).use { reader -> reader.useLines { it.count() } }

      val deleteCount = (totalLines * 0.1).toInt().coerceAtLeast(1)

      // Second pass: copy lines we want to keep
      BufferedReader(logFile.reader()).use { reader ->
        BufferedWriter(tempFile.writer()).use { writer ->
          reader.useLines { lines ->
            lines.drop(deleteCount).forEach { line ->
              writer.write(line)
              writer.newLine()
            }
          }
        }
      }

      // Atomic replace
      if (!tempFile.renameTo(logFile)) {
        tempFile.delete()
        throw IOException("Failed to replace log file")
      }
    } catch (e: IOException) {
      Log.e("FileLoggingTree", "Failed to trim log file", e)
    }
  }

  companion object {
    const val APPLICATION_LOG_FILE_NAME = "app_logs.txt"
  }
}

fun checkAndDeleteLogFile(context: Context) {
  val logFile = File(context.filesDir, APPLICATION_LOG_FILE_NAME)
  if (logFile.exists()) {
    logFile.delete()
  }
}

fun getApplicationLogs(context: Context, password: String): File? {
  val encryptedFile = File(context.filesDir, APPLICATION_LOG_FILE_NAME)
  if (!encryptedFile.exists()) return null

  val decryptedFile = File(context.cacheDir, "app_logs_decrypted.txt")
  val secretKey =
    SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(password.toByteArray()), "AES")

  try {
    FileWriter(decryptedFile, false).use { writer ->
      encryptedFile.forEachLine { line ->
        try {
          val parts = line.split(":", limit = 2)
          if (parts.size == 2) {
            val decrypted =
              EncryptionHelper.decrypt(
                parts[1],
                secretKey,
                parts[0].decodeToByteArray(),
              )
            writer.appendLine(decrypted)
          }
        } catch (e: Exception) {
          Log.e("FileLoggingTree", "Failed to decrypt log line", e)
        }
      }
      writer.flush()
    }
  } catch (e: IOException) {
    Log.e("FileLoggingTree", "Failed to create decrypted log file", e)
    return null
  }
  return decryptedFile.takeIf { it.length() > 0 }
}
