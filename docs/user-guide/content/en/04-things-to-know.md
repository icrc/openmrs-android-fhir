# Things that confuse people {#things-to-know}

The questions below come up again and again. Each one says what you see, why, and what to do.

## "I recorded it, but is it on the server?" {#tk-sent}

Everything you record is saved **on the tablet first**. It reaches the server at the next sync (or a
moment later with live mode on). Until then it exists only on the tablet.

**What to do:** before you log out, delete patient data, or hand the tablet to someone else, look at
the home screen. It must say {{home_summary_uploaded}}. If it says {{home_summary_waiting}}, tap
{{nav_sync}} first.

## "The first download is taking forever" {#tk-first-download}

The first time, the app downloads your patients with everything recorded for them. It cannot be
skipped and it can take several minutes.

**What to do:** stay where the signal is good, keep the tablet awake, and wait for the home screen.
Later syncs only bring what changed.

## "I cannot find a patient" {#tk-find-patient}

The search looks **only at the patients on the tablet**. A patient is on the tablet if they are in a
patient list that you ticked, or if you registered them here.

**What to do:**

1. Check the spelling, or search by identifier or phone number.
2. Open {{patient_lists_header}} and check that the patient's list is ticked. With **no list ticked,
   no patient is downloaded**.
3. Check your location: the lists you see depend on it.
4. With live mode on, the search also asks the server (see [Sync and live mode](#live-mode)).

## "Register or identify later?" {#tk-duplicate}

Registering a patient who is already on the server creates a **duplicate**.

**What to do:** search first. If you have no connection and cannot check, tap
{{identify_later_action}} instead of registering, write a note that will help to find the patient
(name, village, phone), and record the visit as usual. When you are connected, tap
{{identify_banner_identify}} on that patient to find the real one; everything you recorded moves to them.

> **Warning:** a patient to identify later is **not sent** when you sync, until you identify them.
> The sync page then says **Action needed** instead of {{home_summary_uploaded}}. That is not an error:
> it means something waits for you.

## "I work without an account" {#tk-guest}

<!-- auth: sso -->
If your organisation allows it, you can tap {{continue_without_login}} on the login page. You can
register patients and fill in forms, offline. When you want to sync, the app asks you to sign in with
single sign-on, and what you recorded is kept and sent.
<!-- /auth -->
<!-- auth: basic -->
If your organisation allows it, you can tap {{continue_without_login}} on the login page. You can
register patients and fill in forms, offline. When you want to sync, the app asks you to sign in with
your user name and password, and what you recorded is kept and sent.
<!-- /auth -->
<!-- auth: both -->
If your organisation allows it, you can tap {{continue_without_login}} on the login page. You can
register patients and fill in forms, offline. When you want to sync, the app asks you to sign in, and
what you recorded is kept and sent.
<!-- /auth -->

## "The app asked me to sign in again" {#tk-session}

<!-- auth: sso -->
Your single sign-on session ended. The app renews it by itself most of the time, but when it cannot
(for example after a long time offline) a strip **Session expired. Sign in** appears under the title.
Tap {{sign_in}}. Your work stays on the tablet and is sent after you sign in. You never lose data
because a session ended.
<!-- /auth -->
<!-- auth: basic -->
The server no longer accepts your login, for example after a long time offline. A strip
**Session expired. Sign in** appears under the title. Tap {{sign_in}}. Your work stays on the tablet
and is sent after you sign in. You never lose data because a session ended.
<!-- /auth -->
<!-- auth: both -->
The server no longer accepts your login, for example after a long time offline. A strip
**Session expired. Sign in** appears under the title. Tap {{sign_in}}. Your work stays on the tablet
and is sent after you sign in. You never lose data because a session ended.
<!-- /auth -->

## "Offline login stopped working" {#tk-offline-login}

Offline login unlocks the app with your fingerprint, face or screen lock. It needs a lock on the
tablet. If you change or remove the fingerprint or the screen lock, the app turns offline login off
for your safety.

**What to do:** sign in with your account, then turn {{offline_login_title}} on again in {{settings}}.

## "What does log out do to my data?" {#tk-logout}

By default **your data stays on the tablet** when you log out, so the next person to sign in finds
it. They only see what they registered or downloaded themselves, unless you allow it in {{settings}}.
The question at log out says exactly what will happen.

{{delete_patient_data_menu}} in the menu is different: it removes the patients from the tablet, and
**what was never sent is lost for good**. Check {{home_summary_uploaded}} first.

## "My patient list changes are not on the server yet" {#tk-list-changes}

You can create a patient list, rename it, and add or remove patients. Like everything else, it is saved
on the tablet first and marked **Not sent yet** until the next sync. Tap {{plists_send_now}} on the
patient lists screen to send it at once. A patient who was registered on the tablet and not uploaded
yet can be added to a list, but the add waits until the patient is uploaded.

## "A patient is missing in my group session" {#tk-group}

A group session starts from **one patient list**: you choose the list, then the form, then who
attends. The patients of that list are ticked; untick the ones who are absent. A patient who is not
in the list can be added with {{plist_add_patients}}, and joins the list when the session starts. A
patient who is not on the tablet cannot be added: choose their list first, or register them.

## "I see forms that my colleague does not" {#tk-forms}

Your organisation decides which forms exist and how they are grouped. You can also make your own
sections and favourites in {{settings}}. The forms on your tablet are the ones bundled in the app; they
can be refreshed from the server in the advanced settings, but only your organisation needs to do that.
