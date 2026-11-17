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
package org.openmrs.android.fhir.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.data.remote.isServerReachable
import org.openmrs.android.fhir.extensions.composeView
import org.openmrs.android.fhir.extensions.limitToReadableWidth
import org.openmrs.android.fhir.patientlists.MemberPending
import org.openmrs.android.fhir.patientlists.PatientListExport
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.screens.PatientListDetailScreen
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.PatientListDetailViewModel
import org.openmrs.android.fhir.viewmodel.PatientListMessage
import timber.log.Timber

class PatientListDetailFragment : Fragment() {
  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<PatientListDetailViewModel> { viewModelFactory }
  private val mainActivityViewModel by
    activityViewModels<MainActivityViewModel> { viewModelFactory }

  private val listId: String
    get() = requireArguments().getString(ARG_LIST_ID).orEmpty()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: android.view.LayoutInflater,
    container: android.view.ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    (requireActivity().application as FhirApplication).appComponent.inject(this)
    viewModel.start(listId)
    return composeView {
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      val activityState by mainActivityViewModel.uiState.collectAsStateWithLifecycle()
      PatientListDetailScreen(
        state = state,
        online =
          activityState.connectivityState.isServerReachable() &&
            !GuestSession.isActive(requireContext()),
        onQueryChange = viewModel::onQueryChange,
        onRefresh = viewModel::refresh,
        onEdit = viewModel::edit,
        onDelete = { viewModel.delete { findNavController().navigateUp() } },
        onUndoDelete = viewModel::undoDelete,
        onDownloadedChange = viewModel::setDownloaded,
        onAddPatients = {
          findNavController()
            .navigate(
              R.id.action_patientListDetailFragment_to_patientListPickerFragment,
              bundleOf(ARG_LIST_ID to listId),
            )
        },
        onStartSession = {
          findNavController()
            .navigate(
              R.id.action_patientListDetailFragment_to_createEncounterFragment,
              bundleOf(
                "patient_id" to "",
                "is_group_encounter" to true,
                "patient_list_id" to listId,
              ),
            )
        },
        onRemoveMember = { viewModel.removeMember(it.patientId) },
        onUndoMember = { viewModel.undoMember(it.patientId) },
        onSendNow = viewModel::sendNow,
        onDiscard = viewModel::discardChanges,
        onExport = ::exportList,
        onOpenCompatibility = { findNavController().navigate(R.id.serverCompatibilityFragment) },
      )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    view.limitToReadableWidth()
    val actionBar = (requireActivity() as AppCompatActivity).supportActionBar
    actionBar?.apply {
      title = getString(R.string.select_patient_lists)
      setDisplayHomeAsUpEnabled(true)
    }
    mainActivityViewModel.setDrawerEnabled(false)
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          viewModel.uiState.collect { state ->
            state.patientList?.name?.let { actionBar?.title = it }
            if (state.gone) {
              AppMessage.show(
                requireContext(),
                getString(R.string.plist_gone),
                MessageKind.Error,
                long = true,
              )
              findNavController().navigateUp()
            }
          }
        }
        launch { viewModel.messages.collect { showPatientListMessage(requireContext(), it) } }
      }
    }
  }

  private fun exportList() {
    viewLifecycleOwner.lifecycleScope.launch {
      val context = requireContext()
      val name = viewModel.uiState.value.patientList?.name ?: return@launch
      val members = viewModel.membersForExport()
      if (members.none { it.pending != MemberPending.REMOVE }) {
        AppMessage.show(context, getString(R.string.plist_export_empty), MessageKind.Info)
        return@launch
      }
      val csv =
        PatientListExport.csv(
          members,
          getString(R.string.plist_export_columns).split("|"),
          getString(R.string.plist_export_status_in),
          getString(R.string.plist_member_will_add),
        )
      val file =
        try {
          withContext(Dispatchers.IO) {
            val folder = File(context.cacheDir, EXPORT_FOLDER).apply { mkdirs() }
            folder.listFiles()?.forEach { it.delete() }
            File(
                folder,
                PatientListExport.fileName(
                  name,
                  SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date()),
                ),
              )
              .also { it.writeText(csv, Charsets.UTF_8) }
          }
        } catch (e: IOException) {
          Timber.w(e, "Could not write the patient list")
          AppMessage.show(context, getString(R.string.plist_export_failed), MessageKind.Error)
          return@launch
        }
      AuditLog.record(
        AuditAction.DATA_EXPORTED,
        AuditOutcome.SUCCESS,
        "kind" to "patient_list",
        "list" to listId,
        "patients" to members.count { it.pending != MemberPending.REMOVE },
      )
      val uri =
        FileProvider.getUriForFile(
          context,
          "${context.applicationContext.packageName}.provider",
          file,
        )
      val send =
        Intent(Intent.ACTION_SEND).apply {
          type = "text/csv"
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_SUBJECT, getString(R.string.plist_export_subject, name))
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      startActivity(Intent.createChooser(send, getString(R.string.plist_export_chooser)))
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean =
    when (item.itemId) {
      android.R.id.home -> {
        NavHostFragment.findNavController(this).navigateUp()
        true
      }
      else -> false
    }

  companion object {
    const val ARG_LIST_ID = "listId"
    private const val EXPORT_FOLDER = "patient-list-exports"
  }
}

internal fun showPatientListMessage(context: Context, message: PatientListMessage) {
  val text =
    if (message.plural) {
      context.resources.getQuantityString(message.res, message.count, message.count)
    } else {
      context.getString(message.res)
    }
  AppMessage.show(
    context,
    text,
    if (message.isError) MessageKind.Error else MessageKind.Success,
    long = message.isError,
  )
}
