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
import java.io.File
import java.io.FileWriter
import java.io.IOException
import org.openmrs.android.fhir.extensions.FileLoggingTree.Companion.APPLICATION_LOG_FILE_NAME
import timber.log.Timber

class FileLoggingTree(context: Context, private val maxFileSize: Long) : Timber.Tree() {

  private val logFile: File = File(context.filesDir, APPLICATION_LOG_FILE_NAME)

  override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    writeLogToFile("$priority/$tag: $message")
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
      val trimmedContent = StringBuilder()
      BufferedReader(logFile.reader()).useLines { lines ->
        val allLines = lines.toList()
        val deleteCount = (allLines.size * 0.1).toInt()
        val startIndex = deleteCount
        for (i in startIndex until allLines.size) {
          trimmedContent.appendLine(allLines[i])
        }
      }
      // Overwrite the file with the trimmed content
      FileWriter(logFile, false).use { writer -> writer.write(trimmedContent.toString()) }
    } catch (e: IOException) {
      Log.e("FileLoggingTree", "Failed to trim log file", e)
    }
  }

  companion object {
    const val APPLICATION_LOG_FILE_NAME = "app_logs.txt"
  }
}

fun getApplicationLogs(context: Context): File {
  val logFile = File(context.filesDir, APPLICATION_LOG_FILE_NAME)
  return logFile
}
