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
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.datastore.preferences.core.edit
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.work.WorkManager
import com.google.android.fhir.FhirEngine
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.extensions.BiometricUtils
import org.openmrs.android.fhir.extensions.NotificationHelper
import org.openmrs.android.fhir.extensions.PermissionHelper
import org.openmrs.android.fhir.extensions.PermissionHelperFactory
import org.openmrs.android.fhir.extensions.UncaughtExceptionHandler
import org.openmrs.android.fhir.extensions.checkAndDeleteLogFile
import org.openmrs.android.fhir.extensions.getApplicationLogs
import org.openmrs.android.fhir.extensions.saveToFile
import org.openmrs.android.fhir.extensions.showSnackBar
import org.openmrs.android.fhir.ui.screens.DrawerContent
import org.openmrs.android.fhir.ui.screens.DrawerItem
import org.openmrs.android.fhir.ui.screens.MainActivityOverlays
import org.openmrs.android.fhir.ui.screens.MainActivityScaffold
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.SyncInfoViewModel
import timber.log.Timber

class MainActivity : AppCompatActivity() {
  private lateinit var authStateManager: AuthStateManager
  private var tokenExpiryHandler: Handler? = null
  private var tokenCheckRunnable: Runnable? = null
  private lateinit var loginRepository: LoginRepository
  private lateinit var demoDataStore: DemoDataStore

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var viewModelSavedStateFactory: ViewModelSavedStateFactory

  @Inject lateinit var fhirEngine: FhirEngine

  @Inject lateinit var database: AppDatabase

  @Inject lateinit var apiManager: ApiManager

  @Inject lateinit var notificationHelper: NotificationHelper

  private lateinit var permissionHelper: PermissionHelper

  @Inject lateinit var permissionHelperFactory: PermissionHelperFactory

  private val viewModel by viewModels<MainActivityViewModel> { viewModelSavedStateFactory }
  private val syncInfoViewModel by viewModels<SyncInfoViewModel> { viewModelFactory }

  private val navHostFragmentId: Int = R.id.main_nav_host_container
  private var drawerLayout: DrawerLayout? = null
  private var toolbar: MaterialToolbar? = null
  private var overlayComposeView: ComposeView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Thread.setDefaultUncaughtExceptionHandler(
      UncaughtExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler()),
    )
    (this.application as FhirApplication).appComponent.inject(this)
    permissionHelper = permissionHelperFactory.create(this)
    loginRepository = LoginRepository.getInstance(applicationContext)
    authStateManager = AuthStateManager.getInstance(applicationContext)
    tokenExpiryHandler = Handler(Looper.getMainLooper())
    demoDataStore = DemoDataStore(this)
    setContentView(R.layout.activity_main)
    ensureNavHost(navHostFragmentId)
    drawerLayout = findViewById(R.id.drawer)
    findViewById<ComposeView>(R.id.top_bar_compose_container).setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      MainActivityScaffold(
        networkStatusText = uiState.networkStatusText,
        isNetworkStatusVisible = uiState.isNetworkStatusVisible,
        onToolbarReady = ::configureToolbar,
      )
    }
    findViewById<ComposeView>(R.id.drawer_compose_container).setContent {
      val activityContext = LocalContext.current
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      val drawerItems =
        androidx.compose.runtime.remember(uiState.locationMenuTitle, uiState.versionMenuTitle) {
          buildDrawerItems(
            activityContext,
            uiState.locationMenuTitle,
            uiState.versionMenuTitle,
          )
        }
      DrawerContent(
        lastSyncText = uiState.lastSyncText,
        drawerItems = drawerItems,
        onDrawerItemSelected = ::onNavigationItemSelected,
      )
    }
    overlayComposeView = findViewById(R.id.overlay_compose_container)
    overlayComposeView?.setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      MainActivityOverlays(
        isSyncTasksVisible = uiState.isSyncTasksVisible,
        syncProgressState = uiState.syncProgressState,
        syncHeaderText = uiState.syncHeaderText,
        showSyncCloseButton = uiState.showSyncCloseButton,
        onCloseSyncTasks = ::hideSyncTasksScreen,
        isLoading = uiState.loading,
      )
    }
    updateOverlayVisibility()
    registerBackHandler()
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiEvents.collect { event ->
          when (event) {
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.OpenDrawer ->
              openNavigationDrawer()
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.TriggerSync -> onSyncPress()
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.ShowSyncTasks ->
              showSyncTasksScreen()
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.HideSyncTasks ->
              hideSyncTasksScreen()
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.SyncStarted -> showSyncStarted()
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.SyncCompleted ->
              showSnackBar(this@MainActivity, getString(R.string.sync_completed))
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.SyncFailed ->
              showSnackBar(this@MainActivity, getString(R.string.sync_failed))
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.ShowContinueSyncDialog ->
              showContinueSyncDialog(event.fetchIdentifiers)
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.ShowTokenExpiredDialog ->
              showTokenExpiredDialog(event.connectivityState)
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.ShowLogoutDialog ->
              showLogoutWarningDialog(event.connectivityState)
          }
        }
      }
    }
    //    lifecycleScope.launch {
    // viewModel.initPeriodicSyncWorker(demoDataStore.getPeriodicSyncDelay()) } TODO: Discuss on
    // periodic sync
    observeNetworkConnection(this)
    observeSyncState()
    viewModel.updateLastSyncTimestamp()
    viewModel.triggerIdentifierTypeSync()
  }

  override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
      if (!viewModel.hasHandledPostLoginNavigation()) {
        val selectedLocationId =
          applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID]
        if (selectedLocationId == null) {
          showSyncTasksScreen(
            headerTextResId = R.string.get_started,
            showCloseButton = false,
          )
          waitForNavController()?.let { navController ->
            if (navController.currentDestination?.id != R.id.locationFragment) {
              val bundle = Bundle().apply { putBoolean("from_login", true) }
              navController.navigate(R.id.locationFragment, bundle)
              viewModel.setHasHandledPostLoginNavigation(true)
            }
          }
        }
      }
    }
    currentFocus?.let { hideKeyboard(this@MainActivity, it) }
  }

  private fun hideKeyboard(context: Activity, focusView: View) {
    val inputMethodManager = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(focusView.windowToken, 0)
  }

  private fun registerBackHandler() {
    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            drawerLayout?.closeDrawer(GravityCompat.START)
            return
          }
          lifecycleScope.launch {
            val navController = waitForNavController()
            val handled = navController?.popBackStack() ?: false
            if (!handled) {
              isEnabled = false
              onBackPressedDispatcher.onBackPressed()
              isEnabled = true
            }
          }
        }
      },
    )
  }

  fun setDrawerEnabled(enabled: Boolean) {
    viewModel.setDrawerEnabled(enabled)
    drawerLayout?.setDrawerLockMode(
      if (enabled) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
      GravityCompat.START,
    )
    updateToolbarNavigationIcon()
  }

  fun openNavigationDrawer() {
    drawerLayout?.openDrawer(GravityCompat.START)
    viewModel.updateLastSyncTimestamp()
  }

  private fun closeNavigationDrawer() {
    drawerLayout?.closeDrawer(GravityCompat.START)
  }

  private fun configureToolbar(toolbar: MaterialToolbar) {
    if (this.toolbar === toolbar) {
      return
    }
    this.toolbar = toolbar
    setSupportActionBar(toolbar)
    updateToolbarNavigationIcon()
    toolbar.setNavigationOnClickListener {
      if (viewModel.uiState.value.drawerEnabled) {
        openNavigationDrawer()
      } else {
        onBackPressedDispatcher.onBackPressed()
      }
    }
  }

  private fun updateToolbarNavigationIcon() {
    val drawable =
      DrawerArrowDrawable(this).apply {
        progress = if (viewModel.uiState.value.drawerEnabled) 0f else 1f
      }
    toolbar?.navigationIcon = drawable
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
  }

  private fun buildDrawerItems(
    context: Context,
    locationMenuTitle: String,
    versionMenuTitle: String,
  ): List<DrawerItem> {
    return listOf(
      DrawerItem(
        id = R.id.menu_sync,
        iconRes = R.drawable.cloud_sync_24px,
        title = context.getString(R.string.sync_menu),
        testTag = "DrawerItemSync",
      ),
      DrawerItem(
        id = R.id.menu_current_location,
        iconRes = R.drawable.ic_location,
        title =
          if (locationMenuTitle.isNotBlank()) {
            locationMenuTitle
          } else {
            context.getString(R.string.no_location_selected)
          },
        testTag = "DrawerItemLocation",
      ),
      DrawerItem(
        id = R.id.menu_select_identifier,
        iconRes = R.drawable.baseline_tag_24,
        title = context.getString(R.string.select_identifier_types),
        testTag = "DrawerItemIdentifier",
      ),
      DrawerItem(
        id = R.id.menu_settings,
        iconRes = R.drawable.baseline_settings_24,
        title = context.getString(R.string.settings),
        testTag = "DrawerItemSettings",
      ),
      DrawerItem(
        id = R.id.menu_diagnostics,
        iconRes = R.drawable.ic_diagnostics,
        title = context.getString(R.string.send_diagnostics),
        testTag = "DrawerItemDiagnostics",
      ),
      DrawerItem(
        id = R.id.menu_logout,
        iconRes = R.drawable.ic_logout,
        title = context.getString(R.string.logout),
        testTag = "DrawerItemLogout",
      ),
      DrawerItem(
        id = R.id.menu_version,
        iconRes = R.drawable.ic_version_24,
        title =
          if (versionMenuTitle.isNotBlank()) {
            versionMenuTitle
          } else {
            context.getString(R.string.version)
          },
        testTag = "DrawerItemVersion",
        enabled = false,
      ),
    )
  }

  private fun ensureNavHost(containerId: Int) {
    val existing = supportFragmentManager.findFragmentById(containerId) as? NavHostFragment
    if (existing == null) {
      supportFragmentManager
        .beginTransaction()
        .replace(containerId, NavHostFragment.create(R.navigation.reference_nav_graph))
        .commit()
    }
  }

  private fun getNavController(): NavController? {
    val navHost = supportFragmentManager.findFragmentById(navHostFragmentId) as? NavHostFragment
    return navHost?.navController
  }

  private suspend fun waitForNavController(maxAttempts: Int = 50): NavController? {
    repeat(maxAttempts) {
      val controller = getNavController()
      if (controller != null) {
        return controller
      }
      delay(50)
    }
    return null
  }

  fun updateLocationName(locationName: String) {
    viewModel.updateLocationName(locationName)
  }

  private fun onNavigationItemSelected(itemId: Int) {
    when (itemId) {
      R.id.menu_sync -> {
        onSyncPress()
      }
      R.id.menu_logout -> {
        val connectivityState = viewModel.networkStatus.value
        viewModel.requestLogoutDialog(connectivityState)
      }
      R.id.menu_settings -> {
        getNavController()?.navigate(R.id.settings_fragment)
      }
      R.id.menu_diagnostics -> {
        showSendDiagnosticReportDialog()
      }
      R.id.menu_select_identifier -> {
        getNavController()?.navigate(R.id.identifierFragment)
      }
      R.id.menu_current_location -> {
        getNavController()?.navigate(R.id.locationFragment)
      }
    }
    closeNavigationDrawer()
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
              applicationContext.dataStore.edit { preferences ->
                preferences.remove(PreferenceKeys.LOCATION_ID)
                preferences.remove(PreferenceKeys.LOCATION_NAME)
              }
              showSnackBar(
                this@MainActivity,
                getString(R.string.location_unassigned_select_location),
              )
              getNavController()?.navigate(R.id.locationFragment)
            }
          }
        }*/
        checkNotificationPermission()
        val fetchIdentifiers = applicationContext.resources.getBoolean(R.bool.fetch_identifiers)
        viewModel.triggerOneTimeSync(applicationContext, fetchIdentifiers)
        closeNavigationDrawer()
      }
      isTokenExpired() && connectivityState.isServerReachable() -> {
        viewModel.requestTokenExpiredDialog(connectivityState)
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

  private fun checkNotificationPermission() {
    permissionHelper.checkAndRequestNotificationPermission {}
  }

  @SuppressLint("MissingPermission")
  private fun showSyncStarted() {
    permissionHelper.checkNotificationPermissionStatus { isGranted ->
      if (isGranted) {
        notificationHelper.showSyncStarted()
      }
    }
    showSnackBar(this@MainActivity, getString(R.string.sync_started))
  }

  private fun showContinueSyncDialog(fetchIdentifiers: Boolean) {
    AlertDialog.Builder(this)
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

  fun showSyncTasksScreen(
    headerTextResId: Int = R.string.syncing_patient_data,
    showCloseButton: Boolean = true,
  ) {
    viewModel.showSyncTasksScreen(headerTextResId, showCloseButton)
    updateOverlayVisibility()
  }

  fun hideSyncTasksScreen() {
    viewModel.hideSyncTasksScreen()
    updateOverlayVisibility()
  }

  fun updateSyncProgress(current: Int, total: Int) {
    viewModel.updateSyncProgress(current, total)
  }

  private fun observeSyncState() {
    lifecycleScope.launch {
      viewModel.syncProgress.observeForever { viewModel.handleSyncWorkInfos(it) }

      viewModel.pollPeriodicSyncJobStatus?.collect {
        Timber.d("observerSyncState: pollState Got status $it")
        //        handleCurrentSyncJobStatus(it.currentSyncJobStatus)
      }
    }
  }

  private suspend fun monitorTokenExpiry() {
    val tokenExpiryDelay = withContext(Dispatchers.IO) { demoDataStore.getTokenExpiryDelay() }
    tokenCheckRunnable =
      object : Runnable {
        override fun run() {
          if (isTokenExpired()) {
            val connectivityState = viewModel.networkStatus.value
            if (connectivityState.isServerReachable()) {
              viewModel.requestTokenExpiredDialog(connectivityState)
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

  private fun showTokenExpiredDialog(connectivityState: ServerConnectivityState) {
    if (!connectivityState.isServerReachable()) {
      viewModel.onTokenExpiredDialogDismissed()
      return
    }
    AlertDialog.Builder(this)
      .setTitle(getString(R.string.login_expired))
      .setMessage(getString(R.string.session_expired))
      .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
        lifecycleScope.launch { loginRepository.refreshAccessToken() }
        viewModel.setStopSync(false)
        viewModel.onTokenExpiredDialogDismissed()
        dialog.dismiss()
      }
      .setNegativeButton(getString(R.string.no)) { dialog, _ ->
        lifecycleScope.launch { scheduleDialogForLater() }
        viewModel.setStopSync(false)
        viewModel.onTokenExpiredDialogDismissed()
        dialog.dismiss()
      }
      .setNeutralButton(getString(R.string.no_and_don_t_ask_me_again)) { dialog, _ ->
        viewModel.cancelPeriodicSyncWorker(applicationContext)
        viewModel.setStopSync(true)
        showSnackBar(this@MainActivity, getString(R.string.sync_terminated))
        viewModel.onTokenExpiredDialogDismissed()
        dialog.dismiss()
      }
      .setCancelable(false)
      .show()
  }

  private suspend fun scheduleDialogForLater() {
    tokenExpiryHandler?.postDelayed(
      {
        val connectivityState = viewModel.networkStatus.value
        viewModel.requestTokenExpiredDialog(connectivityState)
      },
      demoDataStore.getPeriodicSyncDelay(),
    ) // 15 minutes in milliseconds
  }

  private fun observeNetworkConnection(context: Context) {
    lifecycleScope.launch {
      viewModel.networkStatus.collect { connectivityState ->
        if (connectivityState is ServerConnectivityState.ServerConnected) {
          monitorTokenExpiry()
        }
        val fetchIdentifiers = applicationContext.resources.getBoolean(R.bool.fetch_identifiers)
        viewModel.handleConnectivityState(connectivityState, fetchIdentifiers)
      }
    }
  }

  private fun updateOverlayVisibility() {
    val shouldShow = viewModel.uiState.value.isSyncTasksVisible || viewModel.uiState.value.loading
    overlayComposeView?.isVisible = shouldShow
  }

  @VisibleForTesting
  fun updateNetworkStatusBannerTextForTest(text: String) {
    viewModel.updateNetworkStatusBannerTextForTest(text)
  }

  @VisibleForTesting
  fun updateLastSyncTextForTest(text: String) {
    viewModel.updateLastSyncTextForTest(text)
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
    viewModel.setLoading(true)
    updateOverlayVisibility()
    showSnackBar(
      this@MainActivity,
      getString(R.string.logging_out),
      duration = Snackbar.LENGTH_LONG,
    )

    lifecycleScope
      .launch(Dispatchers.Default) { clearAppData() }
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
