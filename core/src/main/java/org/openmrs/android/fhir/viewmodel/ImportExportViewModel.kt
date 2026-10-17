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

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.data.transfer.DataTransfer
import org.openmrs.android.fhir.data.transfer.ExportResult
import org.openmrs.android.fhir.data.transfer.ImportResult
import org.openmrs.android.fhir.data.transfer.InvalidTransferFileException
import org.openmrs.android.fhir.data.transfer.WrongPinException
import org.openmrs.android.fhir.ui.components.MessageKind
import timber.log.Timber

/**
 * @property pending how many resources wait to be uploaded; `null` while it is being counted
 * @property exported the file the last export wrote, ready to share or save
 * @property imported what the last import did
 * @property importFile a file chosen to import, waiting for its PIN
 * @property wrongPin the PIN just entered did not open [importFile]
 */
data class ImportExportUiState(
  val pending: Int? = null,
  val busy: Boolean = false,
  val exported: ExportResult? = null,
  val imported: ImportResult? = null,
  val importFile: Uri? = null,
  val wrongPin: Boolean = false,
)

data class ImportExportMessage(
  @StringRes val resId: Int,
  val kind: MessageKind,
  val args: List<Any> = emptyList(),
)

class ImportExportViewModel
@Inject
constructor(
  private val context: Context,
  private val transfer: DataTransfer,
) : ViewModel() {
  private val _uiState = MutableStateFlow(ImportExportUiState())
  val uiState: StateFlow<ImportExportUiState> = _uiState

  private val _messages = MutableSharedFlow<ImportExportMessage>(extraBufferCapacity = 4)
  val messages: SharedFlow<ImportExportMessage> = _messages

  init {
    refreshPending()
  }

  fun refreshPending() {
    viewModelScope.launch {
      val count = runCatching { transfer.pendingCount() }.getOrDefault(0)
      _uiState.update { it.copy(pending = count) }
    }
  }

  fun export(pin: String) {
    if (_uiState.value.busy) return
    _uiState.update { it.copy(busy = true, exported = null) }
    viewModelScope.launch {
      try {
        val result = transfer.export(pin)
        _uiState.update { it.copy(exported = result) }
      } catch (e: Exception) {
        Timber.e(e, "Export failed")
        AuditLog.failure(AuditAction.DATA_EXPORTED, "error" to e.javaClass.simpleName)
        say(R.string.export_failed, MessageKind.Error)
      } finally {
        _uiState.update { it.copy(busy = false) }
      }
    }
  }

  /** A file was picked; its PIN is asked next. */
  fun fileChosen(uri: Uri) {
    _uiState.update { it.copy(importFile = uri, wrongPin = false) }
  }

  fun cancelImport() {
    _uiState.update { it.copy(importFile = null, wrongPin = false) }
  }

  fun import(pin: String) {
    val uri = _uiState.value.importFile ?: return
    if (_uiState.value.busy) return
    _uiState.update { it.copy(busy = true, wrongPin = false) }
    viewModelScope.launch {
      try {
        val bytes = readFile(uri)
        val result = transfer.import(bytes, pin)
        _uiState.update { it.copy(importFile = null, imported = result) }
        refreshPending()
      } catch (e: WrongPinException) {
        AuditLog.failure(AuditAction.DATA_IMPORTED, "reason" to "wrong_pin")
        _uiState.update { it.copy(wrongPin = true) }
      } catch (e: InvalidTransferFileException) {
        AuditLog.failure(AuditAction.DATA_IMPORTED, "reason" to "invalid_file")
        _uiState.update { it.copy(importFile = null) }
        say(R.string.import_invalid_file, MessageKind.Error)
      } catch (e: Exception) {
        Timber.e(e, "Import failed")
        AuditLog.failure(AuditAction.DATA_IMPORTED, "error" to e.javaClass.simpleName)
        _uiState.update { it.copy(importFile = null) }
        say(R.string.import_failed, MessageKind.Error)
      } finally {
        _uiState.update { it.copy(busy = false) }
      }
    }
  }

  /** Copies the exported file to where the user chose to save it. */
  fun saveExportTo(uri: Uri) {
    val file = _uiState.value.exported?.file ?: return
    viewModelScope.launch {
      val saved = runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
          } != null
        }
      }
      say(
        if (saved.getOrDefault(false)) R.string.export_saved else R.string.export_failed,
        if (saved.getOrDefault(false)) MessageKind.Success else MessageKind.Error,
      )
    }
  }

  private suspend fun readFile(uri: Uri): ByteArray =
    withContext(Dispatchers.IO) {
      context.contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readNBytesUpTo(DataTransfer.MAX_IMPORT_BYTES)
        bytes ?: throw InvalidTransferFileException()
      }
        ?: throw InvalidTransferFileException()
    }

  /** The bytes of the stream, or `null` if it is longer than [limit]. */
  private fun java.io.InputStream.readNBytesUpTo(limit: Long): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
      val read = read(buffer)
      if (read < 0) break
      total += read
      if (total > limit) return null
      out.write(buffer, 0, read)
    }
    return out.toByteArray()
  }

  private fun say(@StringRes resId: Int, kind: MessageKind) {
    _messages.tryEmit(ImportExportMessage(resId, kind))
  }
}
