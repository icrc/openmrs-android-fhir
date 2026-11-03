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

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.fhir.datacapture.validation.Invalid
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import timber.log.Timber

/**
 * A slim row of the pages of a paginated form, above its progress bar: the page on show is
 * highlighted and a tap on any other goes straight to it, instead of pressing Next / Previous
 * through every page in between. It takes the height of one line and scrolls sideways when the
 * pages do not fit.
 *
 * The form library has no call to go to a page. Its view model, which holds the pages and the page
 * on show, is internal, so this reads and sets it by reflection (the names are kept from shrinking,
 * see `proguard-rules.pro`). If the library changes and the lookup fails, the form is left exactly
 * as the library draws it, without the row.
 *
 * Going back is always allowed. Going forward first checks the whole form, as the submit button
 * does, and lands on the first page before the target with a required answer missing (its questions
 * marked) instead of on the target, so a form cannot be reviewed or saved with required answers
 * missing by jumping over the pages that hold them. The pages in between are not shown.
 */
internal fun QuestionnaireFragment.installPageBreadcrumbs(formView: View) {
  val root = formView as? ConstraintLayout ?: return
  if (root.findViewById<View>(R.id.form_page_breadcrumbs) != null) return
  val pager =
    try {
      FormPager(ViewModelProvider(this)[FormPager.viewModelClass()])
    } catch (e: Exception) {
      Timber.w(e, "No page breadcrumbs: the form library has changed")
      return
    }
  var jumping = false
  val questionnaire = arguments?.getString(QUESTIONNAIRE_ARGUMENT).orEmpty()
  val titles = pageTitles(questionnaire)
  val pageOfLinkId by lazy { pageOfLinkIds(questionnaire) }
  var shown by mutableStateOf(PagesShown())
  val rowHeight = (ROW_HEIGHT_DP * formView.resources.displayMetrics.density).toInt()
  val row =
    ComposeView(formView.context).apply {
      id = R.id.form_page_breadcrumbs
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      visibility = View.GONE
      setContent {
        OpenMrsTheme {
          PageBreadcrumbs(
            pages = shown.pages,
            current = shown.current,
            onSelect = { page ->
              // One jump at a time: the check of the form takes a moment.
              if (!jumping) {
                jumping = true
                viewLifecycleOwner.lifecycleScope.launch {
                  try {
                    if (!pager.goTo(page, pageOfLinkId)) {
                      AppMessage.show(
                        context,
                        R.string.questionnaire_required_answers_missing,
                        MessageKind.Error,
                        long = true,
                      )
                    }
                  } finally {
                    jumping = false
                  }
                }
              }
            },
          )
        }
      }
    }
  // The row sits in a band of top padding of the form's own layout, above everything the library
  // draws. It is not added to the library's title row: a row with a height there makes the library
  // lay its list out taller than the space it is given, so the last questions of a long page
  // cannot be scrolled into view. Padding keeps the list the size it is given.
  root.clipToPadding = false
  root.addView(
    row,
    ConstraintLayout.LayoutParams(0, rowHeight).apply {
      startToStart = ConstraintLayout.LayoutParams.PARENT_ID
      endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
      topToTop = ConstraintLayout.LayoutParams.PARENT_ID
      topMargin = -rowHeight
    },
  )
  var bandShown = false
  fun showBand(show: Boolean) {
    if (show == bandShown) return
    bandShown = show
    row.visibility = if (show) View.VISIBLE else View.GONE
    root.updatePadding(top = if (show) rowHeight else 0)
  }
  // A form of several pages has the band from the first layout; one changed later is laid out
  // again.
  showBand(titles.size > 1)
  viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
      pager.states.collect { state ->
        shown = pager.pagesShown(state, titles, getString(R.string.form_page_fallback_title))
        // The review page has its own title and Edit button at the top: no band there.
        showBand(shown.pages.size > 1)
      }
    }
  }
}

/** What the row draws: the pages that can be reached, and which one is on show. */
private class PagesShown(val pages: List<Page> = emptyList(), val current: Int = -1)

private class Page(val index: Int, val title: String)

/** The height of the row of breadcrumbs. */
private const val ROW_HEIGHT_DP = 40

/** How long a jump waits for the library to show the page it was set to. */
private const val PAGE_TIMEOUT_MS = 600L

/** The key of the questionnaire in the arguments of the form fragment (the library's own). */
private const val QUESTIONNAIRE_ARGUMENT = "questionnaire"

/** Reads the pages from the form library's view model and sets the page on show. */
private class FormPager(private val viewModel: ViewModel) {
  @Suppress("UNCHECKED_CAST")
  private val currentPage =
    viewModel.javaClass.getMethod("getCurrentPageIndexFlow").invoke(viewModel)
      as MutableStateFlow<Int?>

  @Suppress("UNCHECKED_CAST")
  val states: Flow<Any?> =
    viewModel.javaClass.methods
      .first { it.name.startsWith("getQuestionnaireStateFlow") && it.parameterTypes.isEmpty() }
      .invoke(viewModel) as Flow<Any?>

  private val mainHandler = Handler(Looper.getMainLooper())

  private val goToNextPage =
    viewModel.javaClass.methods.first {
      it.name.startsWith("goToNextPage") && it.parameterTypes.isEmpty()
    }

  private val validateForm =
    viewModel.javaClass.methods.first {
      it.name.startsWith("validateQuestionnaireAndUpdateUI") && it.parameterTypes.size == 1
    }

  /**
   * Goes to [page]; `false` when a required answer missing before it kept it on an earlier page.
   * Going back just sets the page. Going forward checks the whole form (what the submit button
   * checks, a call that does not build any page) and, if a page from the current one up to [page]
   * has answers missing, goes to the first of them instead and has the library mark its questions.
   * [pageOfLinkId] says which page each question is on.
   */
  suspend fun goTo(page: Int, pageOfLinkId: Map<String, Int>): Boolean {
    val from = currentPage.value ?: return false
    if (page <= from) {
      currentPage.value = page
      return true
    }
    val invalid = pagesWithAnswersMissing(pageOfLinkId) ?: return false
    val reachable = pagesShown(states.first(), emptyList(), "").pages.map { it.index }
    val blockedAt = reachable.firstOrNull { it in from until page && it in invalid }
    if (blockedAt == null) {
      currentPage.value = page
      return true
    }
    if (blockedAt != from) {
      currentPage.value = blockedAt
      // Next marks the questions of the page on show, so wait for it to be built.
      withTimeoutOrNull(PAGE_TIMEOUT_MS) { states.first { pageOf(it) == blockedAt } }
      pressNext()
    }
    return false
  }

  /**
   * The pages that hold a question with an answer missing or not valid, from the check of the whole
   * form; `null` if the check could not be made (then nothing is let through).
   */
  private suspend fun pagesWithAnswersMissing(pageOfLinkId: Map<String, Int>): Set<Int>? =
    try {
      val results =
        suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
          try {
            validateForm.invoke(viewModel, continuation)
          } catch (e: InvocationTargetException) {
            throw e.cause ?: e
          }
        }
          as Map<*, *>
      results
        .filter { (_, answers) -> (answers as? List<*>).orEmpty().any { it is Invalid } }
        .mapNotNull { (linkId, _) -> pageOfLinkId[linkId] }
        .toSet()
    } catch (e: Exception) {
      Timber.w(e, "Could not check the form before a jump")
      null
    }

  /**
   * Presses the library's Next as a tap would, from a message of the main thread. Its check of the
   * page relies on the form state being rebuilt at once, which does not happen when it is called
   * from inside a coroutine: the page would then be taken for not checked and never left.
   */
  private suspend fun pressNext(): Boolean = suspendCancellableCoroutine { continuation ->
    mainHandler.post {
      val pressed =
        try {
          goToNextPage.invoke(viewModel)
          true
        } catch (e: ReflectiveOperationException) {
          false
        }
      continuation.resume(pressed)
    }
  }

  private fun pageOf(state: Any?): Int? =
    paginationOf(state)?.let { call(it, "getCurrentPageIndex") as? Int }

  private fun paginationOf(state: Any?): Any? =
    state?.let { call(it, "getDisplayMode") }?.let { call(it, "getPagination") }

  /**
   * The pages of [state] (the library's `QuestionnaireState`) that are enabled and not hidden,
   * which are the ones its Next and Previous reach. Nothing while the form is not editing a
   * paginated questionnaire (the review page, a form with one page).
   */
  fun pagesShown(state: Any?, titles: List<String>, fallbackTitle: String): PagesShown {
    val pagination = paginationOf(state)
    if (pagination == null || call(pagination, "isPaginated") != true) return PagesShown()
    val pages =
      (call(pagination, "getPages") as? List<*>).orEmpty().mapNotNull { page ->
        page ?: return@mapNotNull null
        val reachable = call(page, "getEnabled") == true && call(page, "getHidden") != true
        val index = call(page, "getIndex") as? Int
        if (!reachable || index == null) return@mapNotNull null
        Page(index, label(index, titles.getOrNull(index), fallbackTitle))
      }
    return PagesShown(pages, pageOf(state) ?: -1)
  }

  /**
   * The text of a crumb: the title with the number of the page before it, as the library counts
   * them, unless the form numbered it already. Pages of one form can share a title.
   */
  private fun label(index: Int, title: String?, fallbackTitle: String): String =
    when {
      title.isNullOrBlank() -> "$fallbackTitle ${index + 1}"
      NUMBERED.containsMatchIn(title) -> title
      else -> "${index + 1} · $title"
    }

  /** The value of the getter [name], or `null` when the object has none. */
  private fun call(target: Any, name: String): Any? =
    try {
      target.javaClass.getMethod(name).invoke(target)
    } catch (e: NoSuchMethodException) {
      null
    }

  companion object {
    @Suppress("UNCHECKED_CAST")
    fun viewModelClass(): Class<ViewModel> =
      Class.forName("com.google.android.fhir.datacapture.QuestionnaireViewModel")
        as Class<ViewModel>
  }
}

/** The page (index among the top-level items) of every question of [questionnaireJson]. */
internal fun pageOfLinkIds(questionnaireJson: String): Map<String, Int> =
  try {
    val pages = JSONObject(questionnaireJson).optJSONArray("item") ?: JSONArray()
    val found = HashMap<String, Int>()
    fun collect(item: JSONObject, page: Int) {
      item.optString("linkId").takeIf { it.isNotEmpty() }?.let { found[it] = page }
      val children = item.optJSONArray("item") ?: return
      for (i in 0 until children.length()) collect(children.getJSONObject(i), page)
    }
    for (page in 0 until pages.length()) collect(pages.getJSONObject(page), page)
    found
  } catch (e: JSONException) {
    emptyMap()
  }

/**
 * The title of each page of [questionnaireJson]: the text of the page group, or else of the first
 * group inside it (the forms of this app put the title there, "3. Program Specifics"). Blank where
 * there is none.
 */
internal fun pageTitles(questionnaireJson: String): List<String> =
  try {
    val items = JSONObject(questionnaireJson).optJSONArray("item") ?: JSONArray()
    (0 until items.length()).map { titleOf(items.getJSONObject(it), Locale.getDefault().language) }
  } catch (e: JSONException) {
    emptyList()
  }

private fun titleOf(item: JSONObject, language: String): String {
  val own = localizedText(item, language)
  if (own.isNotBlank()) return own
  val children = item.optJSONArray("item") ?: return ""
  return if (children.length() > 0) titleOf(children.getJSONObject(0), language) else ""
}

/** The text of the item in [language] when it has a translation for it, markup removed. */
private fun localizedText(item: JSONObject, language: String): String {
  val translations = item.optJSONObject("_text")?.optJSONArray("extension")
  var text = item.optString("text")
  for (i in 0 until (translations?.length() ?: 0)) {
    val parts = translations?.getJSONObject(i)?.optJSONArray("extension") ?: continue
    val values =
      (0 until parts.length()).associate {
        parts.getJSONObject(it).let { p -> p.optString("url") to p }
      }
    if (values["lang"]?.optString("valueCode") == language) {
      values["content"]?.optString("valueString")?.takeIf { it.isNotBlank() }?.let { text = it }
    }
  }
  return text.replace(MARKUP, "").trim()
}

private val MARKUP = Regex("<[^>]*>")

/** A title that starts with its own number: "3. Program Specifics", "10.1 Somatic". */
private val NUMBERED = Regex("^\\d+(\\.\\d+)*[.)]?\\s")

@Composable
private fun PageBreadcrumbs(pages: List<Page>, current: Int, onSelect: (Int) -> Unit) {
  if (pages.size < 2) return
  val listState = rememberLazyListState()
  val position = pages.indexOfFirst { it.index == current }
  // Keeps the page on show in view, with the one before it for context.
  LaunchedEffect(position) {
    if (position >= 0) listState.animateScrollToItem((position - 1).coerceAtLeast(0))
  }
  LazyRow(
    state = listState,
    modifier = Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT_DP.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(0.dp),
    contentPadding = PaddingValues(horizontal = 12.dp),
  ) {
    itemsIndexed(pages, key = { _, page -> page.index }) { at, page ->
      if (at > 0) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.outline,
          modifier = Modifier.size(18.dp),
        )
      }
      Crumb(page.title, page.index == current, passed = page.index < current) {
        onSelect(page.index)
      }
    }
  }
}

@Composable
private fun Crumb(title: String, selected: Boolean, passed: Boolean, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier.heightIn(min = 32.dp)
        .background(
          if (selected) colors.secondaryContainer else Color.Transparent,
          RoundedCornerShape(16.dp),
        )
        .selectable(selected = selected, role = Role.Tab, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 4.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      color =
        when {
          selected -> colors.onSecondaryContainer
          passed -> colors.onSurface
          else -> colors.onSurfaceVariant
        },
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.widthIn(max = if (selected) 240.dp else 160.dp),
    )
  }
}
