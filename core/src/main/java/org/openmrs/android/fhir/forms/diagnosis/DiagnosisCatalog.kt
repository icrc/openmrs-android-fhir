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
package org.openmrs.android.fhir.forms.diagnosis

import android.content.Context
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * A diagnosis of the dictionary: an OpenMRS concept. The server finds it by its [uuid]; the [icd]
 * codes are what the user searches by and sees next to the name, the server does not take them.
 */
class Diagnosis(
  val uuid: String,
  val name: String,
  val icd: List<String>,
  /** The name in other languages (language tag in lower case, as `es` or `pt-br`, to the name). */
  val translations: Map<String, String> = emptyMap(),
) {
  /** The code of the answer saved in a form. */
  val code: String
    get() = uuid

  /** The ICD-10 code shown next to the name. */
  val icdLabel: String
    get() = icd.firstOrNull().orEmpty()

  /** The name in the language of the user (a language tag, as `es` or `pt-BR`), or the default. */
  fun nameIn(languageTag: String): String {
    if (translations.isEmpty()) return name
    val tag = languageTag.lowercase(Locale.ROOT).replace('_', '-')
    return translations[tag] ?: translations[tag.substringBefore('-')] ?: name
  }
}

/**
 * [text] as the search compares it: lower case and without accents or other marks, so that
 * "hipertension" finds "Hipertensión", "aigue" finds "aiguë", and an Arabic name is found without
 * its vowel marks. Plain ASCII, which is nearly all of the English names, is only lower-cased.
 */
internal fun foldForSearch(text: String): String {
  val lower = text.lowercase(Locale.ROOT)
  // Most names (the English ones, the Russian ones...) have nothing to fold, and unicode
  // normalization is the slow part: it is only done for a string that has a letter that needs it.
  if (lower.none { needsFolding(it) }) return lower
  val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
  return buildString(decomposed.length) {
    decomposed.forEach {
      if (Character.getType(it) != Character.NON_SPACING_MARK.toInt()) append(it)
    }
  }
}

/**
 * Whether [c] (already lower case) is a letter that has an accent or a mark to take off. Not the
 * letters of the ASCII range and Latin-1 up to the accented ones, the Russian letters but "й", and
 * the basic Arabic letters (not the alefs with a hamza): those are left as they are.
 */
private fun needsFolding(c: Char): Boolean =
  when (c.code) {
    in 0..0xBF -> false
    0x439 -> true // й is и with a mark
    in 0x430..0x44F -> false
    in 0x627..0x64A -> false
    else -> true
  }

/** What a search found: the best [items] (at most the limit asked for) and how many matched. */
class DiagnosisSearchResult(val items: List<Diagnosis>, val total: Int)

/** The file is not a CSV of diagnoses this app can use. */
class InvalidDiagnosisCatalogException(message: String) : Exception(message)

/**
 * A list of diagnoses a form can offer, read from a CSV bundled with the app (see
 * [DiagnosisCatalogs]). Built for a long list (more than 40,000 concepts): everything the search
 * looks at is lower-cased once, and a search is one pass.
 */
class DiagnosisCatalog(val id: String, val diagnoses: List<Diagnosis>) {
  /** What the search looks at, one entry for each diagnosis, in the order of the list. */
  private class SearchIndex(
    /** The codes as typed and without the dot, lower-cased. */
    val codes: Array<String>,
    /** The names without accents, lower-cased. */
    val names: Array<String>,
    /** The names in the other languages, as the names are. */
    val translated: Array<List<String>>,
  )

  /**
   * Built when it is first needed (see [prepare]): the list can be shown and its diagnoses found by
   * their uuid before it is ready, and it takes a good part of the time a list takes to load.
   */
  private val searchIndex by
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
      SearchIndex(
        codes = Array(diagnoses.size) { index -> searchableCodes(diagnoses[index]) },
        names = Array(diagnoses.size) { index -> foldForSearch(diagnoses[index].name) },
        translated =
          Array(diagnoses.size) { index ->
            diagnoses[index].translations.values.map { foldForSearch(it) }
          },
      )
    }

  private val byUuid = HashMap<String, Diagnosis>(diagnoses.size * 2)

  init {
    diagnoses.forEach { byUuid.putIfAbsent(it.uuid.lowercase(Locale.ROOT), it) }
  }

  val size: Int
    get() = diagnoses.size

  /**
   * Builds what the search needs, so the first search does not wait for it. A search made before
   * that waits for it to be done.
   */
  fun prepare() {
    searchIndex
  }

  /** The diagnosis of a concept uuid, as in an answer or in a condition on the server. */
  fun find(uuid: String): Diagnosis? = byUuid[uuid.lowercase(Locale.ROOT)]

  /**
   * The diagnoses whose code or name (in any language, accents ignored) contains every word of
   * [query], best first: an ICD-10 code that is the text, a code that starts with it, a name that
   * starts with it, a word of the name that starts with it, then the rest, each in the order of the
   * list (by name). With no text, the start of the list.
   */
  fun search(query: String, limit: Int = DEFAULT_LIMIT): DiagnosisSearchResult {
    val words = foldForSearch(query).split(' ', ',', ';').filter { it.isNotBlank() }
    if (words.isEmpty()) {
      return DiagnosisSearchResult(diagnoses.take(limit), diagnoses.size)
    }
    val ready = searchIndex
    val codes = ready.codes
    val names = ready.names
    val translated = ready.translated
    val first = words.first()
    val phrase = words.joinToString(" ")
    val matches = LongArray(minOf(diagnoses.size, MAX_COUNTED))
    var count = 0
    var total = 0
    for (index in diagnoses.indices) {
      val code = codes[index]
      val name = names[index]
      val others = translated[index]
      val matched =
        words.all { word ->
          code.contains(word) || name.contains(word) || others.any { it.contains(word) }
        }
      if (!matched) continue
      total++
      if (count == matches.size) continue
      var score =
        when {
          codeIs(code, first) -> 0
          codeStartsWith(code, first) -> 1
          else -> nameScore(name, phrase, first)
        }
      if (score > 2) {
        for (other in others) score = minOf(score, nameScore(other, phrase, first))
      }
      // The score in the high bits, the position in the list in the low ones: one sort.
      matches[count++] = (score.toLong() shl 32) or index.toLong()
    }
    matches.sort(0, count)
    val items = ArrayList<Diagnosis>(minOf(limit, count))
    for (i in 0 until minOf(limit, count)) {
      items.add(diagnoses[(matches[i] and 0xFFFFFFFFL).toInt()])
    }
    return DiagnosisSearchResult(items, total)
  }

  private fun nameScore(name: String, phrase: String, first: String): Int =
    when {
      name == phrase -> 2
      name.startsWith(phrase) -> 3
      name.contains(" $phrase") || name.contains("($phrase") -> 4
      name.startsWith(first) -> 5
      name.contains(" $first") || name.contains("($first") || name.contains("[$first") -> 6
      else -> 7
    }

  private fun codeIs(codes: String, text: String): Boolean =
    codes == text ||
      codes.startsWith("$text ") ||
      codes.contains(" $text ") ||
      codes.endsWith(" $text")

  private fun codeStartsWith(codes: String, text: String): Boolean =
    codes.startsWith(text) || codes.contains(" $text")

  companion object {
    const val DEFAULT_LIMIT = 100

    /** More matches than this are counted, but only this many are ranked. */
    private const val MAX_COUNTED = 20_000

    /** The codes as typed (`J20.9`) and without the dot (`J209`), lower-cased, for the search. */
    private fun searchableCodes(diagnosis: Diagnosis): String {
      if (diagnosis.icd.isEmpty()) return ""
      val builder = StringBuilder()
      diagnosis.icd.forEach { builder.append(it.lowercase(Locale.ROOT)).append(' ') }
      diagnosis.icd.forEach { code ->
        if ('.' in code) builder.append(code.lowercase(Locale.ROOT).replace(".", "")).append(' ')
      }
      return builder.toString().trimEnd()
    }
  }
}

/**
 * The lists of diagnoses of the app. A list is a CSV in `assets/diagnoses/<id>.csv` (see
 * [DiagnosisCsv] for the columns); a build puts its own file there, and the one of the app module
 * wins over the one of this library. A list is read off the main thread and kept; a processed copy
 * of it is kept in the private cache of the app (see [DiagnosisCatalogCache]), so that the next
 * start reads that instead of the CSV, which takes a small part of the time.
 */
object DiagnosisCatalogs {
  /** The list a picker uses when its form does not name one: `assets/diagnoses/diagnoses.csv`. */
  const val DEFAULT_ID = "diagnoses"

  private const val ASSETS_DIRECTORY = "diagnoses"
  private val SAFE_ID = Regex("[A-Za-z0-9._-]{1,64}")
  private val cache = ConcurrentHashMap<String, DiagnosisCatalog>()
  private val loading = Mutex()

  /** Writes the processed copies, apart from everything else. */
  private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** The list, or null when the app has no such file (or it is not a list of diagnoses). */
  suspend fun get(context: Context, id: String): DiagnosisCatalog? {
    cache[id]?.let {
      return it
    }
    if (!SAFE_ID.matches(id)) return null
    // One read at a time: the form warms the list up while its picker may already ask for it.
    return loading.withLock {
      cache[id]
        ?: withContext(Dispatchers.IO) { load(context.applicationContext, id) }
          ?.also { cache[id] = it }
    }
  }

  /** The list if it is already in memory: no reading, for the places that cannot wait. */
  fun cached(id: String): DiagnosisCatalog? = cache[id]

  /**
   * Lets go of the lists in memory, which is the most the feature holds (tens of megabytes), when
   * the system is short of it. What is open keeps its list until it is closed; the next ask reads
   * the list again, from the processed copy.
   */
  fun release() {
    cache.clear()
  }

  private fun load(context: Context, id: String): DiagnosisCatalog? {
    val started = System.nanoTime()
    val key = cacheKey(context, id)
    val copy = File(context.cacheDir, "diagnosis-list-$id.bin")
    if (key != null) {
      DiagnosisCatalogCache.read(copy, key)?.let { diagnoses ->
        return DiagnosisCatalog(id, diagnoses).also {
          writer.launch { it.prepare() }
          report(id, "cache", started, it)
        }
      }
    }
    val catalog =
      runCatching {
          context.assets.open("$ASSETS_DIRECTORY/$id.csv").bufferedReader(Charsets.UTF_8).use {
            DiagnosisCsv.parse(it, id)
          }
        }
        .onFailure { Timber.w(it, "The list of diagnoses %s cannot be read", id) }
        .getOrNull()
        ?: return null
    report(id, "csv", started, catalog)
    writer.launch { catalog.prepare() }
    if (key != null) {
      writer.launch {
        runCatching { DiagnosisCatalogCache.write(copy, key, catalog.diagnoses) }
          .onFailure { Timber.w(it, "The list of diagnoses %s cannot be cached", id) }
      }
    }
    return catalog
  }

  /**
   * Which CSV a processed copy was made from: the list and the last time the app was installed or
   * updated, the only time its CSV changes. Null when that is not known: there is no copy then.
   */
  private fun cacheKey(context: Context, id: String): String? =
    runCatching {
        "$id/${context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime}"
      }
      .getOrNull()

  private fun report(id: String, source: String, started: Long, catalog: DiagnosisCatalog) {
    val millis = (System.nanoTime() - started) / 1_000_000
    Timber.i(
      "The list of diagnoses %s: %d read from the %s in %d ms",
      id,
      catalog.size,
      source,
      millis,
    )
  }

  internal val LANGUAGE_TAG = Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*")
}
