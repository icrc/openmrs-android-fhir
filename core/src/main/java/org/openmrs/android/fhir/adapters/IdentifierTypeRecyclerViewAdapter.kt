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

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.openmrs.android.fhir.data.database.model.IdentifierType

class IdentifierTypeRecyclerViewAdapter(
  private val onItemClicked: (IdentifierType, Boolean) -> Unit,
  private val selectedIdentifierId: MutableSet<String>,
) : ListAdapter<IdentifierType, IdentifierTypeViewHolder>(IdentifierTypeDiffCallback()) {

  class IdentifierTypeDiffCallback : DiffUtil.ItemCallback<IdentifierType>() {
    override fun areItemsTheSame(
      oldItem: IdentifierType,
      newItem: IdentifierType,
    ): Boolean = oldItem.uuid == newItem.uuid

    override fun areContentsTheSame(
      oldItem: IdentifierType,
      newItem: IdentifierType,
    ): Boolean = oldItem == newItem
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IdentifierTypeViewHolder {
    val composeView =
      ComposeView(parent.context).apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
          )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      }
    return IdentifierTypeViewHolder(composeView)
  }

  override fun onBindViewHolder(holder: IdentifierTypeViewHolder, position: Int) {
    val item = getItem(position)
    val isSelected = selectedIdentifierId.contains(item.uuid)
    holder.bindTo(item, onItemClicked, isSelected)
  }
}
