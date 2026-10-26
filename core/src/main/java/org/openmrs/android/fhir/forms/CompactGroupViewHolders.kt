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
package org.openmrs.android.fhir.forms

import android.view.View
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.fhir.datacapture.QuestionnaireItemViewHolderFactoryMatchersProviderFactory
import com.google.android.fhir.datacapture.views.QuestionnaireViewItem
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemAndroidViewHolderDelegate
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemAndroidViewHolderFactory
import org.hl7.fhir.r4.model.Questionnaire
import org.openmrs.android.fhir.R

/**
 * Tag of the form items provider below: forms ask for it with
 * `QuestionnaireFragment.Builder.setCustomQuestionnaireItemViewHolderFactoryMatchersProvider`.
 */
const val COMPACT_GROUPS_PROVIDER = "openmrs-compact-groups"

/**
 * Forms that follow the OpenMRS conventions wrap every question in two groups that carry no text
 * (they only hold the `definition` the answers are extracted with). The form library draws a header
 * view for every group, even one that shows nothing, and that view is among the most expensive ones
 * to build: a form of a hundred questions inflated and measured two hundred of them while it opened
 * and again while it was scrolled. A group without a title draws nothing, so it gets a view that
 * does nothing. Its children are separate items of the form and are untouched, as is what is
 * extracted from the answers: only how a group with nothing to show is drawn changes.
 */
private object TitlelessGroupViewHolderFactory :
  QuestionnaireItemAndroidViewHolderFactory(R.layout.questionnaire_titleless_group) {
  override fun getQuestionnaireItemViewHolderDelegate() =
    object : QuestionnaireItemAndroidViewHolderDelegate {
      override lateinit var questionnaireViewItem: QuestionnaireViewItem

      override fun init(itemView: View) = Unit

      override fun bind(questionnaireViewItem: QuestionnaireViewItem) = Unit

      override fun setReadOnly(isReadOnly: Boolean) = Unit
    }
}

/** A group that shows nothing: no title, no prefix, not repeated and not drawn in a special way. */
internal fun Questionnaire.QuestionnaireItemComponent.isTitlelessGroup(): Boolean =
  type == Questionnaire.QuestionnaireItemType.GROUP &&
    !repeats &&
    !hasText() &&
    !hasPrefix() &&
    extension.none { it.url.endsWith("/questionnaire-itemControl") }

object CompactGroupsProviderFactory : QuestionnaireItemViewHolderFactoryMatchersProviderFactory {
  private val provider =
    object : QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatchersProvider() {
      override fun get() =
        listOf(
          QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatcher(
            TitlelessGroupViewHolderFactory,
          ) {
            it.isTitlelessGroup()
          },
        )
    }

  override fun get(
    provider: String,
  ): QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatchersProvider = this.provider
}
