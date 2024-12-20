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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.openmrs.android.fhir.data.database.model.SyncSession
import org.openmrs.android.fhir.databinding.ItemSyncSessionBinding

class SyncSessionsAdapter(
  private val onDeleteSession: (SyncSession) -> Unit,
) : ListAdapter<SyncSession, SyncSessionItemViewHolder>(SyncSessionDiffCallback()) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SyncSessionItemViewHolder {
    val binding =
      ItemSyncSessionBinding.inflate(
        LayoutInflater.from(parent.context),
        parent,
        false,
      )
    return SyncSessionItemViewHolder(binding, onDeleteSession)
  }

  override fun onBindViewHolder(holder: SyncSessionItemViewHolder, position: Int) {
    val session = getItem(position)
    holder.bind(session)
  }
}

class SyncSessionDiffCallback : DiffUtil.ItemCallback<SyncSession>() {
  override fun areItemsTheSame(oldItem: SyncSession, newItem: SyncSession): Boolean {
    return oldItem.id == newItem.id
  }

  override fun areContentsTheSame(oldItem: SyncSession, newItem: SyncSession): Boolean {
    return oldItem == newItem
  }
}
