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
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

/** UI Controller helper class to display list of observations. */
class ObservationItemRecyclerViewAdapter :
  ListAdapter<PatientListViewModel.ObservationItem, ObservationItemViewHolder>(
    ObservationItemDiffCallback(),
  ) {

  class ObservationItemDiffCallback :
    DiffUtil.ItemCallback<PatientListViewModel.ObservationItem>() {
    override fun areItemsTheSame(
      oldItem: PatientListViewModel.ObservationItem,
      newItem: PatientListViewModel.ObservationItem,
    ): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(
      oldItem: PatientListViewModel.ObservationItem,
      newItem: PatientListViewModel.ObservationItem,
    ): Boolean = oldItem.id == newItem.id
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ObservationItemViewHolder {
    val composeView =
      ComposeView(parent.context).apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
          )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      }
    return ObservationItemViewHolder(composeView)
  }

  override fun onBindViewHolder(holder: ObservationItemViewHolder, position: Int) {
    val item = currentList[position]
    holder.bindTo(item)
  }
}
