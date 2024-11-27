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

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.Period
import org.openmrs.android.fhir.databinding.PatientListItemViewBinding
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

class PatientItemViewHolder(binding: PatientListItemViewBinding) :
  RecyclerView.ViewHolder(binding.root) {
  private val statusView: ImageView = binding.status
  private val nameView: TextView = binding.name
  private val ageView: TextView = binding.fieldName

  fun bindTo(
    patientItem: PatientListViewModel.PatientItem,
    onItemClicked: (PatientListViewModel.PatientItem) -> Unit,
  ) {
    this.nameView.text = patientItem.name
    this.ageView.text = getFormattedAge(patientItem) + "," + patientItem.gender[0].uppercase()
    this.itemView.setOnClickListener { onItemClicked(patientItem) }
    if (patientItem.isSynced != null && patientItem.isSynced!!) {
      statusView.visibility = View.GONE
    } else {
      statusView.visibility = View.VISIBLE
    }
  }

  private fun getFormattedAge(
    patientItem: PatientListViewModel.PatientItem,
  ): String {
    if (patientItem.dob == null) return ""
    return Period.between(patientItem.dob, LocalDate.now()).let {
      when {
        it.years > 0 -> it.years.toString()
        it.months > 0 -> it.months.toString() + " months"
        else -> it.days.toString() + " months"
      }
    }
  }
}
