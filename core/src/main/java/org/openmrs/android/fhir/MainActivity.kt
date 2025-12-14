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
package org.openmrs.android.fhir

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.datastore.preferences.core.edit
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.fhir.FhirEngine
import com.google.android.material.snackbar.Snackbar
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.openmrs.android.fhir.auth.AuthMethod
import org.openmrs.android.fhir.auth.AuthStateManager
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.database.model.SyncStatus
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ServerConnectivityState
import org.openmrs.android.fhir.data.remote.isServerReachable
import org.openmrs.android.fhir.databinding.ActivityMainBinding
import org.openmrs.android.fhir.extensions.BiometricUtils
import org.openmrs.android.fhir.extensions.NotificationHelper
import org.openmrs.android.fhir.extensions.PermissionHelper
import org.openmrs.android.fhir.extensions.PermissionHelperFactory
import org.openmrs.android.fhir.extensions.UncaughtExceptionHandler
import org.openmrs.android.fhir.extensions.checkAndDeleteLogFile
import org.openmrs.android.fhir.extensions.getApplicationLogs
import org.openmrs.android.fhir.extensions.saveToFile
import org.openmrs.android.fhir.extensions.showSnackBar
import org.openmrs.android.fhir.ui.components.DrawerHeader
import org.openmrs.android.fhir.ui.components.NetworkStatusBanner
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.SyncInfoViewModel
import org.openmrs.android.fhir.worker.SyncInfoDatabaseWriterWorker
import timber.log.Timber

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private lateinit var drawerToggle: ActionBarDrawerToggle
  private lateinit var authStateManager: AuthStateManager
  private var tokenExpiryHandler: Handler? = null
  private var tokenCheckRunnable: Runnable? = null
  private var isDialogShowing = false
  private lateinit var loginRepository: LoginRepository
  private lateinit var demoDataStore: DemoDataStore

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var fhirEngine: FhirEngine

  @Inject lateinit var database: AppDatabase

  @Inject lateinit var apiManager: ApiManager

  @Inject lateinit var notificationHelper: NotificationHelper

  private lateinit var permissionHelper: PermissionHelper

  @Inject lateinit var permissionHelperFactory: PermissionHelperFactory

  private val viewModel by viewModels<MainActivityViewModel> { viewModelFactory }
  private val syncInfoViewModel by viewModels<SyncInfoViewModel> { viewModelFactory }

  private var networkStatusText by mutableStateOf("")
  private var lastSyncText by mutableStateOf("")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Thread.setDefaultUncaughtExceptionHandler(
      UncaughtExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler()),
    )
    (this.application as FhirApplication).appComponent.inject(this)
    permissionHelper = permissionHelperFactory.create(this)
    binding = ActivityMainBinding.inflate(layoutInflater)
    loginRepository = LoginRepository.getInstance(applicationContext)
    authStateManager = AuthStateManager.getInstance(applicationContext)
    setContentView(binding.root)
    tokenExpiryHandler = Handler(Looper.getMainLooper())
    demoDataStore = DemoDataStore(this)
    //    lifecycleScope.launch {
    // viewModel.initPeriodicSyncWorker(demoDataStore.getPeriodicSyncDelay()) } TODO: Discuss on
    // periodic sync
    setupNetworkStatusBanner()
    initActionBar()
    initNavigationDrawer()
    observeNetworkConnection(this)
    observeLastSyncTime()
    observeSyncState()
    viewModel.updateLastSyncTimestamp()
    viewModel.triggerIdentifierTypeSync()
    observeSettings()
  }

  override fun onResume() {
    super.onResume()
    // If the user has not selected a location, redirects to the "select location" screen.
    runBlocking {
      val selectedLocationId = applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID]
      if (selectedLocationId == null) {
        val bundle = Bundle().apply { putBoolean("from_login", true) }
        binding.navHostFragment.findNavController().navigate(R.id.locationFragment, bundle)
      }
    }
    // getting Root View that gets focus
    val rootView = (findViewById<View>(android.R.id.content) as ViewGroup).getChildAt(0)
    rootView.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
      if (hasFocus) {
        hideKeyboard(this@MainActivity)
      }
    }
  }

  private fun hideKeyboard(context: Activity) {
    val inputMethodManager = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(context.currentFocus!!.windowToken, 0)
  }

  override fun onBackPressed() {
    if (binding.drawer.isDrawerOpen(GravityCompat.START)) {
      binding.drawer.closeDrawer(GravityCompat.START)
      return
    }
    super.onBackPressed()
  }

  fun setDrawerEnabled(enabled: Boolean) {
    val lockMode =
      if (enabled) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
    binding.drawer.setDrawerLockMode(lockMode)
    drawerToggle.isDrawerIndicatorEnabled = enabled
  }

  fun openNavigationDrawer() {
    binding.drawer.openDrawer(GravityCompat.START)
    viewModel.updateLastSyncTimestamp()
  }

  private fun initActionBar() {
    val toolbar = binding.toolbar
    setSupportActionBar(toolbar)
  }

  private fun setupNetworkStatusBanner() {
    networkStatusText = getString(R.string.offline)
    binding.networkStatusBanner.apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent { NetworkStatusBanner(text = networkStatusText) }
    }
  }

  private fun setupDrawerHeader() {
    val headerView = ComposeView(this)
    headerView.setViewCompositionStrategy(
      ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
    )
    headerView.setContent {
      DrawerHeader(label = getString(R.string.last_sync), lastSyncValue = lastSyncText)
    }
    binding.navigationView.addHeaderView(headerView)
  }

  private fun initNavigationDrawer() {
    binding.navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected)
    drawerToggle = ActionBarDrawerToggle(this, binding.drawer, R.string.open, R.string.close)
    binding.drawer.addDrawerListener(drawerToggle)
    drawerToggle.syncState()
    setupDrawerHeader()
    setLocationInDrawer()
  }

  private fun checkNotificationPermission() {
    permissionHelper.checkAndRequestNotificationPermission {}
  }

  private fun setLocationInDrawer() {
    lifecycleScope.launch {
      binding.navigationView.menu.findItem(R.id.menu_current_location).title =
        applicationContext?.dataStore?.data?.first()?.get(PreferenceKeys.LOCATION_NAME)
          ?: getString(R.string.no_location_selected)

      val versionName =
        try {
          val packageInfo = packageManager.getPackageInfo(packageName, 0)
          packageInfo.versionName
        } catch (_: Exception) {
          "N/A"
        }

      binding.navigationView.menu.findItem(R.id.menu_version).title = "App Version: $versionName"
    }
  }

  fun updateLocationName(locationName: String) {
    binding.navigationView.menu.findItem(R.id.menu_current_location).title = locationName
  }

  private fun onNavigationItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.menu_sync -> {
        onSyncPress()
      }
      R.id.menu_logout -> {
        val connectivityState = viewModel.networkStatus.value
        showLogoutWarningDialog(connectivityState)
      }
      R.id.menu_settings -> {
        findNavController(R.id.nav_host_fragment).navigate(R.id.settings_fragment)
      }
      R.id.menu_diagnostics -> {
        showSendDiagnosticReportDialog()
      }
      R.id.menu_select_identifier -> {
        findNavController(R.id.nav_host_fragment).navigate(R.id.identifierFragment)
      }
      R.id.menu_current_location -> {
        findNavController(R.id.nav_host_fragment).navigate(R.id.locationFragment)
      }
    }
    binding.drawer.closeDrawer(GravityCompat.START)
    return false
  }

  /*
   * Before triggering sync, it checks if the user is assigned to the location.
   * If not redirects user to the select location screen first and starts the sync.
   */
  fun onSyncPress() {
    val connectivityState = viewModel.networkStatus.value
    when {
      !isTokenExpired() && connectivityState.isServerReachable() -> {
        /*runBlocking {
          val selectedLocationId =
            applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID]
          if (selectedLocationId != null) {
            apiManager.setLocationSession(SessionLocation(selectedLocationId))
            val isCurrentLocationValid =
              viewModel.checkLocationIdAndPurgeUnassignedLocations(
                applicationContext,
                selectedLocationId,
              )
            if (!isCurrentLocationValid) {
              applicationContext?.dataStore?.edit { preferences ->
                preferences.remove(PreferenceKeys.LOCATION_ID)
                preferences.remove(PreferenceKeys.LOCATION_NAME)
              }
              showSnackBar(
                this@MainActivity,
                getString(R.string.location_unassigned_select_location),
              )
              binding.navHostFragment.findNavController().navigate(R.id.locationFragment)
            }
          }
        }*/
        checkNotificationPermission()
        val fetchIdentifiers = applicationContext.resources.getBoolean(R.bool.fetch_identifiers)
        viewModel.triggerOneTimeSync(applicationContext, fetchIdentifiers)
        binding.drawer.closeDrawer(GravityCompat.START)
      }
      isTokenExpired() && connectivityState.isServerReachable() -> {
        showTokenExpiredDialog()
      }
      connectivityState is ServerConnectivityState.InternetOnly -> {
        showSnackBar(
          this@MainActivity,
          getString(R.string.sync_server_unreachable_message),
        )
      }
      connectivityState is ServerConnectivityState.Offline -> {
        showSnackBar(this@MainActivity, getString(R.string.sync_device_offline_message))
      }
    }
  }

  fun showSyncTasksScreen() {
    binding.syncTasksContainer.visibility = View.VISIBLE
    binding.coordinatorLayoutContainer.visibility = View.GONE
    binding.btnSyncTasks.setOnClickListener { hideSyncTasksScreen() }
  }

  fun hideSyncTasksScreen() {
    binding.syncTasksContainer.visibility = View.GONE
    binding.coordinatorLayoutContainer.visibility = View.VISIBLE
  }

  private fun observeSyncState() {
    lifecycleScope.launch {
      viewModel.syncProgress.observeForever { handleSyncStatus(it) }

      viewModel.inProgressSyncSession.observe(this@MainActivity) {
        if (it != null) {
          binding.syncProgressBar.max = it.totalPatientsToDownload + it.totalPatientsToUpload
          binding.syncProgressBar.progress = it.uploadedPatients + it.downloadedPatients
        }
      }

      viewModel.pollPeriodicSyncJobStatus?.collect {
        Timber.d("observerSyncState: pollState Got status $it")
        //        handleCurrentSyncJobStatus(it.currentSyncJobStatus)
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun handleSyncStatus(workInfos: List<WorkInfo>) {
    val workInfo = workInfos.firstOrNull()

    if (workInfo == null) {
      return
    }

    when {
      workInfo.state == WorkInfo.State.RUNNING -> {
        val progress = workInfo.progress

        if (progress.getString(SyncInfoDatabaseWriterWorker.PROGRESS_STATUS) == "STARTED") {
          permissionHelper.checkNotificationPermissionStatus { isGranted ->
            if (isGranted) {
              notificationHelper.showSyncStarted()
            }
          }
          showSyncTasksScreen()
          showSnackBar(this@MainActivity, getString(R.string.sync_started))
        }
      }
      workInfo.state == WorkInfo.State.SUCCEEDED -> {
        hideSyncTasksScreen()
        viewModel.updateLastSyncTimestamp()
        showSnackBar(this@MainActivity, getString(R.string.sync_completed))
        viewModel.setIsSyncing(false)
      }
      workInfo.state == WorkInfo.State.FAILED -> {
        hideSyncTasksScreen()
        viewModel.updateLastSyncTimestamp()
        viewModel.setIsSyncing(false)
        showSnackBar(this@MainActivity, getString(R.string.sync_failed))
      }
    }
  }

  private fun observeLastSyncTime() {
    viewModel.lastSyncTimestampLiveData.observe(this) { timestamp -> lastSyncText = timestamp }
  }

  private suspend fun monitorTokenExpiry() {
    val tokenExpiryDelay = withContext(Dispatchers.IO) { demoDataStore.getTokenExpiryDelay() }
    tokenCheckRunnable =
      object : Runnable {
        override fun run() {
          if (isTokenExpired()) {
            val connectivityState = viewModel.networkStatus.value
            if (connectivityState.isServerReachable()) {
              showTokenExpiredDialog()
              tokenExpiryHandler?.removeCallbacks(this)
            }
          } else {
            tokenExpiryHandler?.postDelayed(this, tokenExpiryDelay)
          }
        }
      }
    tokenCheckRunnable?.let { tokenExpiryHandler?.post(it) }
  }

  private fun isTokenExpired(): Boolean {
    val authState = authStateManager.current
    val expirationTime = authState.accessTokenExpirationTime
    return expirationTime != null && System.currentTimeMillis() > expirationTime
  }

  private fun showTokenExpiredDialog() {
    if (!isDialogShowing) {
      isDialogShowing = true
      AlertDialog.Builder(this)
        .setTitle(getString(R.string.login_expired))
        .setMessage(getString(R.string.session_expired))
        .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
          isDialogShowing = false
          lifecycleScope.launch { loginRepository.refreshAccessToken() }
          viewModel.setStopSync(false)
          dialog.dismiss()
        }
        .setNegativeButton(getString(R.string.no)) { dialog, _ ->
          isDialogShowing = false
          lifecycleScope.launch { scheduleDialogForLater() }
          viewModel.setStopSync(false)
          dialog.dismiss()
        }
        .setNeutralButton(getString(R.string.no_and_don_t_ask_me_again)) { dialog, _ ->
          isDialogShowing = true
          viewModel.cancelPeriodicSyncWorker(applicationContext)
          viewModel.setStopSync(true)
          showSnackBar(this@MainActivity, getString(R.string.sync_terminated))
          dialog.dismiss()
        }
        .setCancelable(false)
        .show()
    }
  }

  private suspend fun scheduleDialogForLater() {
    tokenExpiryHandler?.postDelayed(
      {
        showTokenExpiredDialog() // Re-show the dialog after 15 minutes
      },
      demoDataStore.getPeriodicSyncDelay(),
    ) // 15 minutes in milliseconds
  }

  private fun observeNetworkConnection(context: Context) {
    lifecycleScope.launch {
      var previousState: ServerConnectivityState? = null
      viewModel.networkStatus.collect { connectivityState ->
        val previous = previousState
        previousState = connectivityState
        when (connectivityState) {
          ServerConnectivityState.ServerConnected -> {
            networkStatusText = getString(R.string.network_status_connected_to_server)
            if (
              viewModel.isSyncing.value == true &&
                previous != ServerConnectivityState.ServerConnected
            ) {
              val fetchIdentifiers =
                applicationContext.resources.getBoolean(R.bool.fetch_identifiers)
              AlertDialog.Builder(context)
                .setTitle(getString(R.string.connection_restored))
                .setMessage(getString(R.string.do_you_want_to_continue_sync))
                .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                  dialog.dismiss()
                  viewModel.triggerOneTimeSync(applicationContext, fetchIdentifiers)
                }
                .setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
                .setCancelable(false)
                .show()
            }
            monitorTokenExpiry()
          }
          ServerConnectivityState.InternetOnly -> {
            networkStatusText = getString(R.string.network_status_server_unreachable)
          }
          ServerConnectivityState.Offline -> {
            networkStatusText = getString(R.string.offline)
          }
        }
      }
    }
  }

  @VisibleForTesting
  fun updateNetworkStatusBannerTextForTest(text: String) {
    networkStatusText = text
  }

  @VisibleForTesting
  fun updateLastSyncTextForTest(text: String) {
    lastSyncText = text
  }

  override fun onDestroy() {
    super.onDestroy()
    tokenExpiryHandler?.removeCallbacksAndMessages(null)
  }

  override fun onStart() {
    super.onStart()
    viewModel.registerNetworkCallback()
  }

  override fun onStop() {
    super.onStop()
    viewModel.unregisterNetworkCallback()
  }

  private fun showLogoutWarningDialog(connectivityState: ServerConnectivityState) {
    when (connectivityState) {
      ServerConnectivityState.ServerConnected ->
        AlertDialog.Builder(this)
          .setTitle(getString(R.string.logout))
          .setMessage(getString(R.string.logout_message))
          .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
            dialog.dismiss()
            navigateToLogin()
          }
          .setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
          .setCancelable(false)
          .show()
      ServerConnectivityState.InternetOnly ->
        AlertDialog.Builder(this)
          .setTitle(getString(R.string.logout))
          .setMessage(getString(R.string.logout_unavailable_internet_only_message))
          .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
          .setCancelable(true)
          .show()
      ServerConnectivityState.Offline ->
        AlertDialog.Builder(this)
          .setTitle(getString(R.string.logout))
          .setMessage(getString(R.string.logout_unavailable_offline_message))
          .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
          .setCancelable(true)
          .show()
    }
  }

  private fun navigateToLogin() {
    val loginIntent = Intent(this, LoginActivity::class.java)

    val pendingIntentSuccess = createPendingIntent(loginIntent, 0)
    val pendingIntentCancel = createPendingIntent(loginIntent, 1)
    binding.loadingLayout.setBackgroundColor(getColor(R.color.white))
    binding.progressBar.visibility = View.VISIBLE
    showSnackBar(
      this@MainActivity,
      getString(R.string.logging_out),
      duration = Snackbar.LENGTH_LONG,
    )

    lifecycleScope
      .launch(Dispatchers.IO) { clearAppData() }
      .invokeOnCompletion { handleAuthNavigation(pendingIntentSuccess, pendingIntentCancel) }
  }

  private fun createPendingIntent(intent: Intent, requestCode: Int): PendingIntent {
    return PendingIntent.getActivity(
      this,
      requestCode,
      intent,
      PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
  }

  /*
   * Clears app data except AuthStateManager's datastore
   */
  private suspend fun clearAppData() {
    val workManager = WorkManager.getInstance(this@MainActivity)
    workManager.cancelAllWork()
    workManager.pruneWork()
    fhirEngine.clearDatabase()
    demoDataStore.clearAll()
    database.clearAllTables()
    authStateManager.resetBasicAuthCredentials()
    BiometricUtils.deleteBiometricKey(applicationContext)
    checkAndDeleteLogFile(applicationContext)
    clearApplicationFiles()
  }

  private fun clearApplicationFiles() {
    val dirs =
      listOf(
        applicationContext.filesDir,
        applicationContext.cacheDir,
        applicationContext.getExternalFilesDir(null),
      )
    dirs.forEach { dir ->
      dir?.listFiles()?.forEach { file ->
        if (dir == applicationContext.filesDir && file.name == "datastore") {
          return@forEach
        }
        file.deleteRecursively()
      }
    }
  }

  private fun handleAuthNavigation(
    pendingIntentSuccess: PendingIntent,
    pendingIntentCancel: PendingIntent,
  ) {
    when (authStateManager.getAuthMethod()) {
      AuthMethod.BASIC -> {
        lifecycleScope.launch(Dispatchers.IO) {
          authStateManager.clearAuthDataStore()
          applicationContext.dataStore.edit { preferences -> preferences.clear() }
        }
        startActivity(Intent(this, BasicLoginActivity::class.java))
        finish()
      }
      AuthMethod.OPENID -> {
        authStateManager.endSessionRequest(pendingIntentSuccess, pendingIntentCancel)
        lifecycleScope.launch(Dispatchers.IO) {
          authStateManager.clearAuthDataStore()
          applicationContext.dataStore.edit { preferences -> preferences.clear() }
        }
      }
    }
  }

  private fun observeSettings() {
    lifecycleScope.launch {
      demoDataStore.getCheckNetworkConnectivityFlow().collect { isCheckNetworkConnectivityEnabled ->
        if (isCheckNetworkConnectivityEnabled) {
          binding.networkStatusBanner.visibility = View.VISIBLE
        } else {
          binding.networkStatusBanner.visibility = View.GONE
        }
      }
    }
  }

  private fun showSendDiagnosticReportDialog() {
    AlertDialog.Builder(this)
      .setTitle(getString(R.string.send_diagnostics))
      .setMessage(getString(R.string.diagnostics_message))
      .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
        dialog.dismiss()
        runBlocking { sendDiagnosticsEmail() }
      }
      .setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
      .setCancelable(false)
      .show()
  }

  private suspend fun sendDiagnosticsEmail() {
    val password = getString(R.string.diagnostics_password)
    val zipFile = createDiagnosticZip(password)
    val emailIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.support_email)))
        putExtra(Intent.EXTRA_SUBJECT, "Diagnostic Report")
        putExtra(Intent.EXTRA_TEXT, "Attached is the diagnostic report.")
        putExtra(
          Intent.EXTRA_STREAM,
          FileProvider.getUriForFile(
            applicationContext,
            "${applicationContext.packageName}.provider",
            zipFile,
          ),
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    startActivity(Intent.createChooser(emailIntent, "Send Email"))
  }

  private suspend fun createDiagnosticZip(password: String): File {
    val zipFile = File(applicationContext.cacheDir, "diagnostics.zip")
    val zip = ZipFile(zipFile, password.toCharArray())
    val params =
      ZipParameters().apply {
        isEncryptFiles = true
        encryptionMethod = EncryptionMethod.AES
        aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
      }
    val logFile = getApplicationLogs(applicationContext, password)
    listOfNotNull(logFile, getSyncInfoFile()).forEach { file -> zip.addFile(file, params) }
    logFile?.delete()
    return zipFile
  }

  private suspend fun getSyncInfoFile(): File? {
    return withContext(Dispatchers.IO) {
      try {
        // Fetch all sync session data
        val syncSessions = syncInfoViewModel.getAllSyncSessions()

        // Prepare file content
        val fileContent = StringBuilder()
        fileContent.append("Sync Session Information\n")
        fileContent.append("========================\n\n")

        fileContent.append(
          "FHIR Server URL: ${applicationContext.getString(R.string.fhir_base_url)}\n\n",
        )

        viewModel.lastSyncTimestampLiveData.value?.let {
          fileContent.append("Last sync: ${it}\n\n")
        }

        if (syncSessions.isEmpty()) {
          fileContent.append("No Sync Info Available\n")
        } else {
          for (session in syncSessions) {
            fileContent.append("Start Time: ${session.startTime}\n")
            fileContent.append(
              "Downloaded Resources: ${session.downloadedPatients}/${session.totalPatientsToDownload}\n",
            )
            fileContent.append(
              "Uploaded Resources: ${session.uploadedPatients}/${session.totalPatientsToUpload}\n",
            )
            fileContent.append("Completion Time: ${session.completionTime ?: "In Progress"}\n")
            fileContent.append("Status: ${session.status}\n")

            if (session.status == SyncStatus.COMPLETED_WITH_ERRORS && session.errors.isNotEmpty()) {
              fileContent.append("Errors:\n")
              session.errors.forEach { error -> fileContent.append("- $error\n") }
            }
            fileContent.append("\n")
          }
          fileContent.append("========================\n\n")
        }

        // Save the content to a file
        saveToFile(applicationContext, "SyncInfo.txt", fileContent.toString())
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    }
  }
}
