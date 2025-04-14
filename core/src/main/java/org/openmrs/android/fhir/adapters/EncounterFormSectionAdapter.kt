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
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.data.database.model.FormItem
import org.openmrs.android.fhir.data.database.model.FormSectionItem

class CreateFormSectionAdapter(
  private val formSectionItems: List<FormSectionItem>,
  private val formClickListener: (String) -> Unit,
) : RecyclerView.Adapter<CreateFormSectionAdapter.FormSectionViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormSectionViewHolder {
    val view =
      LayoutInflater.from(parent.context).inflate(R.layout.form_section_item, parent, false)
    return FormSectionViewHolder(view)
  }

  override fun onBindViewHolder(holder: FormSectionViewHolder, position: Int) {
    val formSection = formSectionItems[position]
    holder.bind(formSection, formClickListener)
  }

  override fun getItemCount(): Int = formSectionItems.size

  class FormSectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val sectionNameTextView: TextView = itemView.findViewById(R.id.tv_section_name)
    private val formsRecyclerView: RecyclerView = itemView.findViewById(R.id.rv_forms)

    fun bind(formSectionItem: FormSectionItem, formClickListener: (String) -> Unit) {
      sectionNameTextView.text = formSectionItem.name
      formsRecyclerView.layoutManager = LinearLayoutManager(itemView.context)
      formsRecyclerView.adapter = FormAdapter(formSectionItem.forms, formClickListener)
    }
  }
}

class FormAdapter(
  private val forms: List<FormItem>,
  private val formClickListener: (String) -> Unit,
) : RecyclerView.Adapter<FormAdapter.FormViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormViewHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.form_item, parent, false)
    return FormViewHolder(view)
  }

  override fun onBindViewHolder(holder: FormViewHolder, position: Int) {
    val form = forms[position]
    holder.bind(form, formClickListener)
  }

  override fun getItemCount(): Int = forms.size

  class FormViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val formNameTextView: TextView = itemView.findViewById(R.id.tv_form_name)

    fun bind(formItem: FormItem, formClickListener: (String) -> Unit) {
      // Format the form name for display (remove prefix, replace dots with spaces, etc.)
      val displayName = formItem.name

      formNameTextView.text = displayName

      itemView.setOnClickListener { formClickListener(formItem.questionnaireId) }
    }
  }
}
