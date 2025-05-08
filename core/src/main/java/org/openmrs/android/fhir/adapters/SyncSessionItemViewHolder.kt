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
package org.openmrs.android.fhir.adapters

import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.data.database.model.SyncStatus
import org.openmrs.android.fhir.databinding.ItemSyncSessionBinding

class SyncSessionItemViewHolder(
  private val binding: ItemSyncSessionBinding,
  private val onDeleteSession: (SyncSession) -> Unit,
) : RecyclerView.ViewHolder(binding.rootCard) {

  fun bind(session: SyncSession) {
    // Start time
    binding.tvSyncStartTime.text =
      "${itemView.context.getString(R.string.start_time)}: ${session.startTime}"

    // Progress
    val progressText =
      "${itemView.context.getString(R.string.downloaded)} ${session.downloadedPatients}/${session.totalPatientsToDownload} | " +
        "${itemView.context.getString(R.string.uploaded)} ${session.uploadedPatients}/${session.totalPatientsToUpload}"
    binding.tvSyncProgress.text = progressText

    // Completion time
    binding.tvSyncEndTime.text =
      if (session.completionTime.isNullOrEmpty()) {
        itemView.context.getString(R.string.in_progress)
      } else {
        "${itemView.context.getString(R.string.completed_at)}: ${session.completionTime}"
      }

    // Status Indicator (color)
    val colorRes =
      when (session.status) {
        SyncStatus.ONGOING -> R.color.orange
        SyncStatus.COMPLETED -> R.color.tertiary_green_80
        SyncStatus.COMPLETED_WITH_ERRORS -> R.color.error_red_40
      }
    binding.statusIndicator.setBackgroundColor(itemView.context.getColor(colorRes))

    // Error Section
    if (session.status == SyncStatus.COMPLETED_WITH_ERRORS && session.errors.isNotEmpty()) {
      binding.errorSection.isVisible = true
      binding.tvTotalErrors.text =
        "${itemView.context.getString(R.string.errors)}: ${session.errors.size}"

      val errorDetails = session.errors.joinToString("\n") { error -> "- $error" }
      binding.tvErrorDetails.text = errorDetails
    } else {
      binding.errorSection.isVisible = false
    }

    // Delete Button
    if (session.status == SyncStatus.ONGOING) {
      binding.btnDeleteSyncSession.isVisible = false
    } else {
      binding.btnDeleteSyncSession.setOnClickListener {
        onDeleteSession(session) // Callback to delete the session
      }
    }
  }
}
