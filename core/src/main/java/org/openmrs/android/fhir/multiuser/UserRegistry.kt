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
package org.openmrs.android.fhir.multiuser

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys

/**
 * Who uses this device. A device can be shared by several people, each signing in with their own
 * account; this is the small, always-in-memory record of them:
 * - the user who is signed in now (none for a guest, or after signing out),
 * - every user who has signed in, in order (the first one owns what was on the device before anyone
 *   else came),
 * - whose patient lists and live patients the stored values are (see [activate]),
 * - whether the patients present when the second user came have been attributed to the first.
 *
 * It is kept in a private preferences file: reading it costs nothing, so the rest of the app can
 * ask "is there more than one user?" on any path and, on a device with a single user, do nothing.
 */
object UserRegistry {
  private const val PREFS = "multi_user"
  private const val KEY_ACTIVE = "active"
  private const val KEY_KNOWN = "known"
  private const val KEY_STATE_OWNER = "state_owner"
  private const val KEY_BACKFILLED = "backfilled"
  private const val KEY_SHARING = "sharing"
  private const val KEY_FORGETTING_LOCATION = "forgetting_location"

  /** What belongs to one user and is swapped in and out of the shared preferences on sign-in. */
  private val PERSONAL_SET_KEYS: List<Preferences.Key<Set<String>>> =
    listOf(
      PreferenceKeys.SELECTED_PATIENT_LISTS,
      PreferenceKeys.LIVE_PATIENT_IDS,
      PreferenceKeys.LIVE_TEMPORARY_IDS,
    )

  /** The location the user works at is theirs too: another user may not have access to it. */
  private val PERSONAL_TEXT_KEYS: List<Preferences.Key<String>> =
    listOf(
      PreferenceKeys.LOCATION_ID,
      PreferenceKeys.LOCATION_NAME,
    )

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** The same person always gets the same name, whatever the case or spaces typed. */
  fun normalize(name: String?): String? = name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

  /** The signed-in user, or `null` for a guest or when nobody is signed in. */
  fun active(context: Context): String? = prefs(context).getString(KEY_ACTIVE, null)

  /** Everyone who has signed in on this device, the first one first. */
  fun knownUsers(context: Context): List<String> =
    prefs(context).getString(KEY_KNOWN, "").orEmpty().split('\n').filter { it.isNotEmpty() }

  /** More than one person has used the device: only then does anything need to be told apart. */
  fun isMultiUser(context: Context): Boolean = knownUsers(context).size >= 2

  /** Whoever used the device first, and so owns what was there before anyone else came. */
  fun firstUser(context: Context): String? = knownUsers(context).firstOrNull()

  /** The patients present when the second user came belong to the first (see [markBackfilled]). */
  fun isBackfilled(context: Context): Boolean = prefs(context).getBoolean(KEY_BACKFILLED, false)

  fun markBackfilled(context: Context) {
    prefs(context).edit().putBoolean(KEY_BACKFILLED, true).apply()
  }

  /**
   * [name] has signed in. Their own patient lists and live patients are put in place of the
   * previous user's (kept for when that user comes back); the very first user simply keeps what the
   * device already had.
   *
   * @return `true` when this is the second user of the device and the patients already on it have
   *   still to be attributed to the first one (see [UserDataLedger.attributeExistingPatients]).
   */
  suspend fun activate(context: Context, name: String?): Boolean {
    val user = normalize(name)
    if (user == null) {
      signOut(context)
      return false
    }
    val preferences = prefs(context)
    val known = knownUsers(context)
    val allKnown = if (user in known) known else known + user
    val owner = preferences.getString(KEY_STATE_OWNER, null)
    if (owner != user) {
      swapPersonalState(context, from = owner, to = user)
    }
    if (!isRememberingLocation(context, user)) {
      // Asked again after this login, like on the first one.
      context.applicationContext.dataStore.edit { stored ->
        PERSONAL_TEXT_KEYS.forEach { stored.remove(it) }
      }
    }
    preferences
      .edit()
      .putString(KEY_ACTIVE, user)
      .putString(KEY_STATE_OWNER, user)
      .putString(KEY_KNOWN, allKnown.joinToString("\n"))
      .apply()
    return allKnown.size >= 2 && !isBackfilled(context)
  }

  /** The users who let the others of the device see the patients they registered or downloaded. */
  fun sharingUsers(context: Context): Set<String> =
    prefs(context).getStringSet(KEY_SHARING, emptySet()).orEmpty().toSet()

  fun isSharing(context: Context, user: String?): Boolean =
    normalize(user)?.let { it in sharingUsers(context) } ?: false

  /** Off for everyone until a user turns it on for themselves. */
  fun setSharing(context: Context, user: String?, share: Boolean) {
    val name = normalize(user) ?: return
    val sharing = sharingUsers(context)
    prefs(context)
      .edit()
      .putStringSet(KEY_SHARING, if (share) sharing + name else sharing - name)
      .apply()
  }

  /**
   * Whether [user] goes straight back to the location they used last time (the default) or is asked
   * for the location after every login. Their choice, kept per user.
   */
  fun isRememberingLocation(context: Context, user: String?): Boolean {
    val name = normalize(user) ?: return true
    return name !in prefs(context).getStringSet(KEY_FORGETTING_LOCATION, emptySet()).orEmpty()
  }

  fun setRememberLocation(context: Context, user: String?, remember: Boolean) {
    val name = normalize(user) ?: return
    val forgetting =
      prefs(context).getStringSet(KEY_FORGETTING_LOCATION, emptySet()).orEmpty().toSet()
    prefs(context)
      .edit()
      .putStringSet(KEY_FORGETTING_LOCATION, if (remember) forgetting - name else forgetting + name)
      .apply()
  }

  /** Nobody is signed in any more (or a guest is). */
  fun signOut(context: Context) {
    prefs(context).edit().remove(KEY_ACTIVE).apply()
  }

  /** The device was wiped: nobody has used it yet. */
  fun reset(context: Context) {
    prefs(context).edit().clear().apply()
  }

  /**
   * Stores [from]'s values (when there is such a user) and loads [to]'s, or leaves them empty. The
   * rest of the app keeps reading the shared keys, so it needs no idea of who is who.
   */
  private suspend fun swapPersonalState(context: Context, from: String?, to: String) {
    // The first user of the device (nobody owned the values yet) keeps what is there.
    if (from == null) return
    val preferences = prefs(context)
    val editor = preferences.edit()
    context.applicationContext.dataStore.edit { stored ->
      PERSONAL_SET_KEYS.forEach { key ->
        val current = stored[key]
        if (current != null) {
          editor.putStringSet(stateKey(from, key), current)
        } else {
          editor.remove(stateKey(from, key))
        }
        val saved = preferences.getStringSet(stateKey(to, key), null)
        if (saved != null) stored[key] = saved.toSet() else stored.remove(key)
      }
      PERSONAL_TEXT_KEYS.forEach { key ->
        val current = stored[key]
        if (current != null) {
          editor.putString(stateKey(from, key), current)
        } else {
          editor.remove(stateKey(from, key))
        }
        val saved = preferences.getString(stateKey(to, key), null)
        if (saved != null) stored[key] = saved else stored.remove(key)
      }
    }
    editor.apply()
  }

  private fun stateKey(user: String, key: Preferences.Key<*>) = "state:$user:${key.name}"
}
