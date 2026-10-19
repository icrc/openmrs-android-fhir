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

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.datastore.preferences.core.edit
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.work.WorkManager
import com.google.android.fhir.FhirEngine
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.auth.AuthMethod
import org.openmrs.android.fhir.auth.AuthStateManager
import org.openmrs.android.fhir.auth.GuestSession
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.config.AppConfig
import org.openmrs.android.fhir.data.OpenMRSHelper
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.AppDatabase
import org.openmrs.android.fhir.data.diagnostics.DiagnosticsBuilder
import org.openmrs.android.fhir.data.diagnostics.DiagnosticsEstimate
import org.openmrs.android.fhir.data.diagnostics.DiagnosticsOption
import org.openmrs.android.fhir.data.diagnostics.DiagnosticsRange
import org.openmrs.android.fhir.data.diagnostics.DiagnosticsReport
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ServerConnectivityState
import org.openmrs.android.fhir.data.remote.ServerSettings
import org.openmrs.android.fhir.data.remote.isServerReachable
import org.openmrs.android.fhir.data.transfer.DataTransfer
import org.openmrs.android.fhir.di.ViewModelSavedStateFactory
import org.openmrs.android.fhir.extensions.BiometricUtils
import org.openmrs.android.fhir.extensions.ClearDataOnLogoutStore
import org.openmrs.android.fhir.extensions.FormEngineWarmUp
import org.openmrs.android.fhir.extensions.NotificationHelper
import org.openmrs.android.fhir.extensions.PermissionHelper
import org.openmrs.android.fhir.extensions.PermissionHelperFactory
import org.openmrs.android.fhir.extensions.UncaughtExceptionHandler
import org.openmrs.android.fhir.extensions.checkAndDeleteLogFile
import org.openmrs.android.fhir.fragments.CreateEncountersFragmentArgs
import org.openmrs.android.fhir.multiuser.UserRegistry
import org.openmrs.android.fhir.ui.components.AppMessage
import org.openmrs.android.fhir.ui.components.DiagnosticsDialog
import org.openmrs.android.fhir.ui.components.MainBottomBar
import org.openmrs.android.fhir.ui.components.MessageKind
import org.openmrs.android.fhir.ui.components.TopLevelDestination
import org.openmrs.android.fhir.ui.components.topLevelDestinationFor
import org.openmrs.android.fhir.ui.screens.DrawerContent
import org.openmrs.android.fhir.ui.screens.DrawerItem
import org.openmrs.android.fhir.ui.screens.MainActivityOverlays
import org.openmrs.android.fhir.ui.screens.MainActivityScaffold
import org.openmrs.android.fhir.ui.theme.OpenMrsTheme
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.SyncInfoViewModel
import timber.log.Timber

/** Blur radius, in pixels, of the app behind the sync popup. */
private const val BACKGROUND_BLUR_RADIUS = 18f

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

  @Inject lateinit var diagnosticsBuilder: DiagnosticsBuilder

  @Inject lateinit var dataTransfer: DataTransfer

  private val diagnosticsVisible = androidx.compose.runtime.mutableStateOf(false)
  private val diagnosticsBusy = androidx.compose.runtime.mutableStateOf(false)
  private val diagnosticsEstimate =
    androidx.compose.runtime.mutableStateOf<DiagnosticsEstimate?>(null)

  /** The report waiting for the user to choose where to save it. */
  private var diagnosticsToSave: DiagnosticsReport? = null

  private val saveDiagnosticsLauncher =
    registerForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
      val report = diagnosticsToSave
      diagnosticsToSave = null
      if (uri == null || report == null) return@registerForActivityResult
      lifecycleScope.launch {
        try {
          withContext(Dispatchers.IO) {
            val output =
              contentResolver.openOutputStream(uri) ?: throw java.io.IOException("No output")
            output.use { out -> report.file.inputStream().use { it.copyTo(out) } }
          }
          AppMessage.show(this@MainActivity, R.string.diag_saved, MessageKind.Success)
        } catch (e: kotlinx.coroutines.CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.e(e, "Saving the diagnostics failed")
          AppMessage.show(this@MainActivity, R.string.diag_save_failed, MessageKind.Error)
        }
      }
    }

  private lateinit var permissionHelper: PermissionHelper

  @Inject lateinit var permissionHelperFactory: PermissionHelperFactory

  private val viewModel by viewModels<MainActivityViewModel> { viewModelSavedStateFactory }
  private val syncInfoViewModel by viewModels<SyncInfoViewModel> { viewModelFactory }

  private val navHostFragmentId: Int = R.id.main_nav_host_container

  /** True while the user works without an account ("Continue without signing in"). */
  private val isGuest = androidx.compose.runtime.mutableStateOf(false)

  private val signInLauncher =
    registerForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
      if (result.resultCode != RESULT_OK) return@registerForActivityResult
      if (isGuest.value) onSignedInFromGuest() else onSessionRenewed()
    }
  private var drawerLayout: DrawerLayout? = null
  private var toolbar: MaterialToolbar? = null
  private var overlayComposeView: ComposeView? = null
  private val bottomBarSelection = MutableStateFlow<TopLevelDestination?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    // Same window mode on every API level: the compose app bar already pads for the status bar.
    // The app bar is dark or brand-colored behind the status bar: light icons on it.
    enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
    super.onCreate(savedInstanceState)
    FormEngineWarmUp.start(this)
    Thread.setDefaultUncaughtExceptionHandler(
      UncaughtExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler()),
    )
    (this.application as FhirApplication).appComponent.inject(this)
    permissionHelper = permissionHelperFactory.create(this)
    loginRepository = LoginRepository.getInstance(applicationContext)
    authStateManager = AuthStateManager.getInstance(applicationContext)
    isGuest.value = GuestSession.isActive(this)
    // Who is signed in (the provider of what is created here) is read from the server when it is
    // not
    // saved, and what was created without it is corrected: nothing is uploaded that way.
    if (!isGuest.value) {
      lifecycleScope.launch(Dispatchers.IO) {
        try {
          OpenMRSHelper(fhirEngine, applicationContext).ensureIdentity()
        } catch (e: kotlinx.coroutines.CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w(e, "Could not check who is signed in")
        }
      }
    }
    tokenExpiryHandler = Handler(Looper.getMainLooper())
    demoDataStore = DemoDataStore(this)
    setContentView(R.layout.activity_main)
    ensureNavHost(navHostFragmentId)
    drawerLayout = findViewById(R.id.drawer)
    // Edge-to-edge: keep the screens and the bottom bar clear of the navigation bar and keyboard.
    findViewById<View>(R.id.main_content_container).let { content ->
      ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
        val bottom =
          insets
            .getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime())
            .bottom
        view.updatePadding(bottom = bottom)
        insets
      }
    }
    findViewById<ComposeView>(R.id.top_bar_compose_container).setContent {
      OpenMrsTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        MainActivityScaffold(
          networkStatusText = uiState.networkStatusText,
          isNetworkStatusVisible = uiState.isNetworkStatusVisible,
          connectivityState = uiState.connectivityState,
          isSyncing = uiState.isSyncing,
          syncProgress = uiState.syncProgressState,
          nextSyncMinutes = uiState.autoSyncCountdownMinutes,
          liveStatus = uiState.liveStatus,
          onDelaySync = { minutes ->
            viewModel.delayAutoSync(minutes)
            AuditLog.record(
              AuditAction.SYNC,
              AuditOutcome.SUCCESS,
              "state" to "delayed",
              "minutes" to minutes,
            )
          },
          onTurnOffAutoSync = {
            viewModel.disableAutoSync()
            AuditLog.record(
              AuditAction.SETTINGS_CHANGED,
              AuditOutcome.SUCCESS,
              "changed" to listOf("auto_sync=false"),
            )
            AppMessage.show(this, R.string.auto_sync_turned_off, MessageKind.Info, long = true)
          },
          onResetAutoSyncTimer = {
            // Back to the full interval of the settings.
            viewModel.restartAutoSync()
            AuditLog.record(AuditAction.SYNC, AuditOutcome.SUCCESS, "state" to "timer_reset")
          },
          onToolbarReady = ::configureToolbar,
        )
      }
    }
    // The side menu is composed the first time the drawer is pulled, not while the app starts.
    val drawerComposeView = findViewById<ComposeView>(R.id.drawer_compose_container)
    var isDrawerContentSet = false
    val setDrawerContent = {
      if (!isDrawerContentSet) {
        isDrawerContentSet = true
        drawerComposeView.setContent {
          OpenMrsTheme {
            val activityContext = LocalContext.current
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val drawerItems =
              androidx.compose.runtime.remember(
                uiState.locationMenuTitle,
                uiState.versionMenuTitle,
                isGuest.value,
              ) {
                buildDrawerItems(
                  activityContext,
                  uiState.locationMenuTitle,
                  uiState.versionMenuTitle,
                  isGuest.value,
                )
              }
            val signedInName by
              androidx.compose.runtime
                .remember { activityContext.dataStore.data.map { it[PreferenceKeys.USER_NAME] } }
                .collectAsStateWithLifecycle(initialValue = null)
            val accountText =
              if (isGuest.value) {
                getString(R.string.drawer_guest_account)
              } else {
                signedInName?.takeIf { it.isNotBlank() } ?: AuditLog.user
              }
            DrawerContent(
              accountText = accountText,
              lastSyncText = uiState.lastSyncText,
              drawerItems = drawerItems,
              onDrawerItemSelected = ::onNavigationItemSelected,
            )
          }
        }
      }
    }
    drawerLayout?.addDrawerListener(
      object : DrawerLayout.SimpleDrawerListener() {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) = setDrawerContent()
      },
    )
    findViewById<ComposeView>(R.id.bottom_bar_compose_container).setContent {
      OpenMrsTheme {
        val selected by bottomBarSelection.collectAsStateWithLifecycle()
        selected?.let { MainBottomBar(selected = it, onDestinationSelected = ::onTopLevelSelected) }
      }
    }
    lifecycleScope.launch {
      waitForNavController()?.addOnDestinationChangedListener { _, destination, arguments ->
        // A screen that wants a subtitle sets it once it is created, after this.
        supportActionBar?.subtitle = null
        bottomBarSelection.value =
          topLevelDestinationFor(
            destinationId = destination.id,
            isGroupEncounter = arguments?.getBoolean("is_group_encounter", false) == true,
          )
      }
    }
    overlayComposeView = findViewById(R.id.overlay_compose_container)
    overlayComposeView?.setContent {
      OpenMrsTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        MainActivityOverlays(
          isSyncTasksVisible = uiState.isSyncTasksVisible,
          isSyncMinimized = uiState.isSyncMinimized,
          syncProgressState = uiState.syncProgressState,
          syncHeaderText = uiState.syncHeaderText,
          showSyncCloseButton = uiState.showSyncCloseButton,
          onMinimizeSyncTasks = viewModel::minimizeSyncTasks,
          onExpandSyncTasks = viewModel::expandSyncTasks,
          isLoading = uiState.loading,
        )
      }
    }
    updateOverlayVisibility()
    // The send-diagnostics choices: a dialog window, so this view takes no room.
    addContentView(
      ComposeView(this).apply {
        setContent {
          OpenMrsTheme {
            if (diagnosticsVisible.value) {
              DiagnosticsDialog(
                busy = diagnosticsBusy.value,
                estimate = diagnosticsEstimate.value,
                onSend = { options, range -> sendDiagnostics(options, range, download = false) },
                onDownload = { options, range -> sendDiagnostics(options, range, download = true) },
                onDismiss = { diagnosticsVisible.value = false },
              )
            }
          }
        }
      },
      android.view.ViewGroup.LayoutParams(
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
      ),
    )
    registerBackHandler()
    // Screens call setDisplayHomeAsUpEnabled while they start, which puts the plain back arrow back
    // on the toolbar; once a screen is resumed, draw the icon the drawer state asks for.
    supportFragmentManager.registerFragmentLifecycleCallbacks(
      object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
          if (f is NavHostFragment) return
          updateToolbarNavigationIcon()
        }
      },
      true,
    )
    // Fragments switch the drawer on and off through the ViewModel, so the toolbar icon (menu vs.
    // back arrow) and the drawer's swipe lock must follow that state rather than the Activity
    // method alone, which they never call.
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState
          .map { it.drawerEnabled }
          .distinctUntilChanged()
          .collect { enabled ->
            drawerLayout?.setDrawerLockMode(
              if (enabled) {
                DrawerLayout.LOCK_MODE_UNLOCKED
              } else {
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED
              },
              GravityCompat.START,
            )
            updateToolbarNavigationIcon()
          }
      }
    }
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState
          .map { it.isSessionPromptPending }
          .distinctUntilChanged()
          .collect { pending -> if (pending) showSessionExpiredDialog() }
      }
    }
    // While the sync popup is open the app behind it is blurred (where the system supports it).
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState
          .map { it.isSyncTasksVisible && !(it.isSyncMinimized && it.showSyncCloseButton) }
          .distinctUntilChanged()
          .collect { blurred -> setBackgroundBlurred(blurred) }
      }
    }
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiEvents.collect { event ->
          when (event) {
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.OpenDrawer ->
              openNavigationDrawer()
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.TriggerSync -> onSyncPress()
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.ShowIdentifierTypesRequired ->
              showIdentifierTypesRequiredDialog()
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.ShowLocationRequired ->
              sendToLocationSelection()
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.ShowSyncTasks ->
              showSyncTasksScreen()
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.HideSyncTasks ->
              hideSyncTasksScreen()
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.SyncStarted -> showSyncStarted()
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.SyncCompleted ->
              AppMessage.show(this@MainActivity, R.string.sync_completed, MessageKind.Success)
            is org.openmrs.android.fhir.viewmodel.MainActivityEvent.SyncFailed ->
              AppMessage.show(this@MainActivity, R.string.sync_failed, MessageKind.Error)
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
    viewModel.refreshLocalizedTexts()
    observeNetworkConnection(this)
    observeSyncState()
    viewModel.updateLastSyncTimestamp()
    if (!isGuest.value) viewModel.triggerIdentifierTypeSync()
  }

  override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
      if (!isGuest.value && !viewModel.hasHandledPostLoginNavigation()) {
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
          val state = viewModel.uiState.value
          if (state.isSyncTasksVisible && !state.isSyncMinimized && state.showSyncCloseButton) {
            viewModel.minimizeSyncTasks()
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

  private fun onTopLevelSelected(destination: TopLevelDestination) {
    val navController = getNavController() ?: return
    if (destination == bottomBarSelection.value) return
    val options =
      NavOptions.Builder().setLaunchSingleTop(true).setPopUpTo(R.id.home_fragment, false).build()
    when (destination) {
      TopLevelDestination.Home -> navController.popBackStack(R.id.home_fragment, false)
      TopLevelDestination.GroupSession ->
        navController.navigate(
          destination.destinationId,
          CreateEncountersFragmentArgs(patientId = "", isGroupEncounter = true).toBundle(),
          options,
        )
      else -> navController.navigate(destination.destinationId, null, options)
    }
  }

  private fun updateToolbarNavigationIcon() {
    val drawable =
      DrawerArrowDrawable(this).apply {
        progress = if (viewModel.uiState.value.drawerEnabled) 0f else 1f
        // The color of the bar's icons (its own theme): white on the brand color.
        toolbar?.let {
          color =
            com.google.android.material.color.MaterialColors.getColor(
              it,
              androidx.appcompat.R.attr.colorControlNormal,
            )
        }
      }
    // Through the action bar, not the toolbar: enabling "home as up" makes it draw its own default
    // arrow over anything set on the toolbar directly.
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setHomeAsUpIndicator(drawable)
    }
  }

  private fun buildDrawerItems(
    context: Context,
    locationMenuTitle: String,
    versionMenuTitle: String,
    guest: Boolean = false,
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
      .let { items ->
        if (!guest) {
          items
        } else {
          // No server session: no location setup, and "sign in" replaces logout.
          items
            .filter { it.id != R.id.menu_current_location && it.id != R.id.menu_logout }
            .plus(
              DrawerItem(
                id = R.id.menu_sign_in,
                iconRes = R.drawable.ic_logout,
                title = context.getString(R.string.sign_in),
                testTag = "DrawerItemSignIn",
              ),
            )
        }
      }
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
      R.id.menu_current_location -> {
        getNavController()?.navigate(R.id.locationFragment)
      }
      R.id.menu_sign_in -> launchSignIn()
    }
    closeNavigationDrawer()
  }

  /*
   * Before triggering sync, it checks if the user is assigned to the location.
   * If not redirects user to the select location screen first and starts the sync.
   */
  fun onSyncPress() {
    if (isGuest.value) {
      promptSignInToSync()
      return
    }
    lifecycleScope.launch {
      // Nothing is uploaded before a location is chosen: it is stamped on what the user entered.
      if (applicationContext.dataStore.data.first()[PreferenceKeys.LOCATION_ID].isNullOrBlank()) {
        sendToLocationSelection()
        return@launch
      }
      syncWithLocation()
    }
  }

  private fun sendToLocationSelection() {
    closeNavigationDrawer()
    AppMessage.show(this, R.string.please_select_a_location_first, MessageKind.Info)
    getNavController()?.let { navController ->
      if (navController.currentDestination?.id != R.id.locationFragment) {
        navController.navigate(
          R.id.locationFragment,
          Bundle().apply { putBoolean("from_login", true) },
        )
      }
    }
  }

  private fun syncWithLocation() {
    val connectivityState = viewModel.networkStatus.value
    when {
      !isTokenExpired() && connectivityState.isServerReachable() -> {
        checkNotificationPermission()
        val fetchIdentifiers = AppConfig.get(applicationContext).fetchIdentifiers
        viewModel.triggerOneTimeSync(applicationContext, fetchIdentifiers)
        closeNavigationDrawer()
      }
      isTokenExpired() && connectivityState.isServerReachable() -> {
        viewModel.requestTokenExpiredDialog(connectivityState)
      }
      connectivityState is ServerConnectivityState.InternetOnly -> {
        AppMessage.show(
          this@MainActivity,
          R.string.sync_server_unreachable_message,
          MessageKind.Error,
        )
      }
      connectivityState is ServerConnectivityState.Offline -> {
        AppMessage.show(this@MainActivity, R.string.sync_device_offline_message, MessageKind.Error)
      }
    }
  }

  /** Guest patients cannot be uploaded without an identifier; let the user pick the types. */
  private fun showIdentifierTypesRequiredDialog() {
    MaterialAlertDialogBuilder(this)
      .setTitle(getString(R.string.identifier_types_required_title))
      .setMessage(getString(R.string.identifier_types_required_message))
      .setPositiveButton(getString(R.string.select_identifier_types)) { dialog, _ ->
        dialog.dismiss()
        getNavController()?.navigate(R.id.identifierFragment)
      }
      .setNegativeButton(getString(android.R.string.cancel)) { dialog, _ -> dialog.dismiss() }
      .show()
  }

  /** The guest has work to upload: ask them to sign in; what they entered is kept. */
  private fun promptSignInToSync() {
    closeNavigationDrawer()
    MaterialAlertDialogBuilder(this)
      .setTitle(getString(R.string.sign_in_to_sync_title))
      .setMessage(getString(R.string.sign_in_to_sync_message))
      .setPositiveButton(getString(R.string.sign_in)) { dialog, _ ->
        dialog.dismiss()
        launchSignIn()
      }
      .setNegativeButton(getString(android.R.string.cancel)) { dialog, _ -> dialog.dismiss() }
      .show()
  }

  private fun launchSignIn() {
    signInLauncher.launch(
      Intent(this, LoginActivity::class.java).putExtra(GuestSession.EXTRA_SIGN_IN_FROM_GUEST, true),
    )
  }

  /**
   * Back from the login screen with a session. The location screen is shown next (onResume), and
   * the first sync gives the patients registered as a guest their identifiers.
   */
  private fun onSignedInFromGuest() {
    isGuest.value = false
    viewModel.restartAutoSync()
    viewModel.setHasHandledPostLoginNavigation(false)
    viewModel.triggerIdentifierTypeSync()
  }

  /** Signed in again after the session had expired: what was waiting to be synced goes now. */
  private fun onSessionRenewed() {
    viewModel.restartAutoSync()
    onSyncPress()
  }

  private var sessionExpiredDialog: AlertDialog? = null

  /** The automatic sync found the session no longer valid: ask the user to sign in again. */
  private fun showSessionExpiredDialog() {
    if (sessionExpiredDialog?.isShowing == true) return
    sessionExpiredDialog =
      MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.login_expired))
        .setMessage(getString(R.string.auto_sync_session_expired_message))
        .setPositiveButton(getString(R.string.sign_in)) { dialog, _ ->
          dialog.dismiss()
          launchSignIn()
        }
        .setNegativeButton(getString(R.string.later)) { dialog, _ -> dialog.dismiss() }
        .setOnDismissListener { viewModel.onSessionPromptHandled() }
        .show()
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
    AppMessage.show(this@MainActivity, R.string.sync_started, MessageKind.Info)
  }

  private fun showContinueSyncDialog(fetchIdentifiers: Boolean) {
    MaterialAlertDialogBuilder(this)
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
    viewModel.syncProgress.observe(this@MainActivity) { viewModel.handleSyncWorkInfos(it) }

    lifecycleScope.launch {
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
    MaterialAlertDialogBuilder(this)
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
        AppMessage.show(this@MainActivity, R.string.sync_terminated, MessageKind.Info)
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
        // No session as a guest: no token to watch and no sync to resume.
        if (isGuest.value) return@collect
        if (connectivityState is ServerConnectivityState.ServerConnected) {
          monitorTokenExpiry()
        }
        val fetchIdentifiers = AppConfig.get(applicationContext).fetchIdentifiers
        viewModel.handleConnectivityState(connectivityState, fetchIdentifiers)
      }
    }
  }

  private var blurAnimator: ValueAnimator? = null
  private var backgroundBlur = 0f

  /**
   * Fades a blur in or out on what is behind the sync popup; API 31+, the scrim covers the rest.
   */
  private fun setBackgroundBlurred(blurred: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val targets =
      listOfNotNull<View>(
        findViewById(R.id.main_content_container),
        findViewById(R.id.top_bar_compose_container),
      )
    blurAnimator?.cancel()
    val to = if (blurred) BACKGROUND_BLUR_RADIUS else 0f
    blurAnimator =
      ValueAnimator.ofFloat(backgroundBlur, to).apply {
        duration = 220
        addUpdateListener {
          backgroundBlur = it.animatedValue as Float
          val effect =
            if (backgroundBlur < 0.5f) {
              null
            } else {
              RenderEffect.createBlurEffect(backgroundBlur, backgroundBlur, Shader.TileMode.CLAMP)
            }
          targets.forEach { view -> view.setRenderEffect(effect) }
        }
        start()
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

  /**
   * Says what logging out does with the data, as set in Settings: deleted (and then, in red, how
   * many resources were never uploaded and are lost with it) or kept.
   */
  private suspend fun showLogoutConfirmation() {
    val clearData = ClearDataOnLogoutStore.get(this)
    val pending =
      try {
        dataTransfer.pendingCount()
      } catch (e: Exception) {
        0
      }
    val message = android.text.SpannableStringBuilder()
    message.append(
      getString(if (clearData) R.string.logout_message_delete else R.string.logout_message_keep),
    )
    if (pending > 0) {
      message.append("\n\n")
      val start = message.length
      message.append(
        resources.getQuantityString(
          if (clearData) R.plurals.logout_pending_lost else R.plurals.logout_pending_kept,
          pending,
          pending,
        ),
      )
      message.setSpan(
        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
        start,
        message.length,
        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
      if (clearData) {
        message.setSpan(
          android.text.style.ForegroundColorSpan(
            com.google.android.material.color.MaterialColors.getColor(
              this,
              androidx.appcompat.R.attr.colorError,
              android.graphics.Color.RED,
            ),
          ),
          start,
          message.length,
          android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
      }
    }
    MaterialAlertDialogBuilder(this)
      .setTitle(getString(R.string.logout))
      .setMessage(message)
      .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
        dialog.dismiss()
        navigateToLogin()
      }
      .setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
      .setCancelable(false)
      .show()
  }

  private fun showLogoutWarningDialog(connectivityState: ServerConnectivityState) {
    when (connectivityState) {
      ServerConnectivityState.ServerConnected -> lifecycleScope.launch { showLogoutConfirmation() }
      ServerConnectivityState.InternetOnly ->
        MaterialAlertDialogBuilder(this)
          .setTitle(getString(R.string.logout))
          .setMessage(getString(R.string.logout_unavailable_internet_only_message))
          .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
          .setCancelable(true)
          .show()
      ServerConnectivityState.Offline ->
        MaterialAlertDialogBuilder(this)
          .setTitle(getString(R.string.logout))
          .setMessage(getString(R.string.logout_unavailable_offline_message))
          .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
          .setCancelable(true)
          .show()
    }
  }

  /**
   * Settings > "Log out and delete all data": leaves the session (guest or signed in) and removes
   * everything stored on this device, whatever the connection. Without a connection a single
   * sign-on session cannot be ended at the provider, so only the local session is dropped.
   */
  fun logoutAndDeleteAllData() {
    AuditLog.record(
      AuditAction.LOGOUT,
      AuditOutcome.SUCCESS,
      "method" to authStateManager.getAuthMethod().name.lowercase(),
      "dataDeleted" to true,
    )
    AuditLog.setUser(null)
    UserRegistry.signOut(this)
    val wasGuest = GuestSession.isActive(this)
    val canEndProviderSession =
      !wasGuest &&
        authStateManager.getAuthMethod() == AuthMethod.OPENID &&
        viewModel.uiState.value.connectivityState != ServerConnectivityState.Offline
    viewModel.setLoading(true)
    updateOverlayVisibility()
    AppMessage.show(this, R.string.logging_out, MessageKind.Info, long = true)
    lifecycleScope.launch {
      withContext(Dispatchers.Default) { clearAppData() }
      if (wasGuest) GuestSession.end(this@MainActivity)
      if (canEndProviderSession) {
        val loginIntent = Intent(this@MainActivity, LoginActivity::class.java)
        handleAuthNavigation(
          createPendingIntent(loginIntent, 0),
          createPendingIntent(loginIntent, 1),
        )
      } else {
        withContext(Dispatchers.IO) {
          authStateManager.clearAuthDataStore()
          applicationContext.dataStore.edit { preferences -> preferences.clear() }
        }
        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
        finish()
      }
    }
  }

  /**
   * Settings > Change server. The app restarts on the new server. A signed-in user is signed out
   * first, so their password is not sent to a server they never gave it to, and what was chosen on
   * the previous server (location, patient lists) is dropped. Data on the device stays.
   */
  fun changeServer(url: String?) {
    AuditLog.record(
      AuditAction.SETTINGS_CHANGED,
      AuditOutcome.SUCCESS,
      "changed" to listOf("server"),
    )
    val wasGuest = GuestSession.isActive(this)
    viewModel.setLoading(true)
    updateOverlayVisibility()
    lifecycleScope.launch {
      withContext(Dispatchers.IO) {
        ServerSettings.setOverride(this@MainActivity, url)
        if (!wasGuest) {
          val workManager = WorkManager.getInstance(this@MainActivity)
          workManager.cancelAllWork()
          workManager.pruneWork()
          authStateManager.resetBasicAuthCredentials()
          BiometricUtils.deleteBiometricKey(applicationContext)
          clearSessionPreferences(clearData = true)
        }
        UserRegistry.forgetPersonalState(this@MainActivity)
      }
      if (!wasGuest) {
        AuditLog.setUser(null)
        UserRegistry.signOut(this@MainActivity)
      }
      ServerSettings.restartApp(this@MainActivity)
    }
  }

  private fun navigateToLogin() {
    val clearData = ClearDataOnLogoutStore.get(this)
    AuditLog.record(
      AuditAction.LOGOUT,
      AuditOutcome.SUCCESS,
      "method" to authStateManager.getAuthMethod().name.lowercase(),
      "dataDeleted" to clearData,
    )
    AuditLog.setUser(null)
    UserRegistry.signOut(this)
    val loginIntent = Intent(this, LoginActivity::class.java)

    val pendingIntentSuccess = createPendingIntent(loginIntent, 0)
    val pendingIntentCancel = createPendingIntent(loginIntent, 1)
    viewModel.setLoading(true)
    updateOverlayVisibility()
    AppMessage.show(this@MainActivity, R.string.logging_out, MessageKind.Info, long = true)

    lifecycleScope
      .launch(Dispatchers.Default) { if (clearData) clearAppData() else endSessionKeepingData() }
      .invokeOnCompletion {
        handleAuthNavigation(pendingIntentSuccess, pendingIntentCancel, clearData)
      }
  }

  /** Logging out with "delete data" off: the session goes, the patients and drafts stay. */
  private suspend fun endSessionKeepingData() {
    val workManager = WorkManager.getInstance(this@MainActivity)
    workManager.cancelAllWork()
    workManager.pruneWork()
    authStateManager.resetBasicAuthCredentials()
    BiometricUtils.deleteBiometricKey(applicationContext)
  }

  /**
   * The session's preferences go; when the data stays, so do the choices that belong to it (the
   * location, the patient lists, the identifier types), or the kept data would not match them.
   */
  private suspend fun clearSessionPreferences(clearData: Boolean) {
    val kept = if (clearData) null else applicationContext.dataStore.data.first()
    authStateManager.clearAuthDataStore()
    applicationContext.dataStore.edit { preferences -> preferences.clear() }
    if (kept != null) {
      applicationContext.dataStore.edit { preferences ->
        kept[PreferenceKeys.LOCATION_ID]?.let { preferences[PreferenceKeys.LOCATION_ID] = it }
        kept[PreferenceKeys.LOCATION_NAME]?.let { preferences[PreferenceKeys.LOCATION_NAME] = it }
        kept[PreferenceKeys.SELECTED_PATIENT_LISTS]?.let {
          preferences[PreferenceKeys.SELECTED_PATIENT_LISTS] = it
        }
        kept[PreferenceKeys.FAVORITE_LOCATIONS]?.let {
          preferences[PreferenceKeys.FAVORITE_LOCATIONS] = it
        }
        kept[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]?.let {
          preferences[PreferenceKeys.SELECTED_IDENTIFIER_TYPES] = it
        }
      }
    }
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
    // Nothing of any user is left on the device.
    UserRegistry.reset(applicationContext)
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
        if (
          dir == applicationContext.filesDir &&
            (file.name == "datastore" || file.name == AuditLog.DIRECTORY)
        ) {
          return@forEach
        }
        file.deleteRecursively()
      }
    }
  }

  private fun handleAuthNavigation(
    pendingIntentSuccess: PendingIntent,
    pendingIntentCancel: PendingIntent,
    clearData: Boolean = true,
  ) {
    when (authStateManager.getAuthMethod()) {
      AuthMethod.BASIC -> {
        lifecycleScope.launch(Dispatchers.IO) { clearSessionPreferences(clearData) }
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
      }
      AuthMethod.OPENID -> {
        authStateManager.endSessionRequest(pendingIntentSuccess, pendingIntentCancel)
        lifecycleScope.launch(Dispatchers.IO) { clearSessionPreferences(clearData) }
      }
    }
  }

  private fun showSendDiagnosticReportDialog() {
    diagnosticsEstimate.value = null
    diagnosticsVisible.value = true
    lifecycleScope.launch {
      diagnosticsEstimate.value =
        try {
          diagnosticsBuilder.estimate()
        } catch (e: kotlinx.coroutines.CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w(e, "Diagnostics estimate failed")
          null
        }
    }
  }

  /**
   * Builds the report the user chose on a background thread, then hands it to the mail app, or
   * (with [download]) asks where to save it as a file.
   */
  private fun sendDiagnostics(
    options: Set<DiagnosticsOption>,
    range: DiagnosticsRange,
    download: Boolean,
  ) {
    if (diagnosticsBusy.value) return
    diagnosticsBusy.value = true
    lifecycleScope.launch {
      try {
        val report =
          diagnosticsBuilder.build(
            options,
            range,
            viewModel.lastSyncTimestampLiveData.value?.toString(),
          )
        AuditLog.record(
          AuditAction.DIAGNOSTICS_SENT,
          AuditOutcome.SUCCESS,
          "included" to report.included.map { it.name },
          "range" to range.name,
          "delivery" to if (download) "download" else "email",
        )
        diagnosticsVisible.value = false
        if (download) {
          diagnosticsToSave = report
          val stamp =
            java.text
              .SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
              .format(java.util.Date())
          saveDiagnosticsLauncher.launch("openmrs-diagnostics-$stamp.zip")
          return@launch
        }
        val emailIntent =
          Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConfig.get(this@MainActivity).supportEmail))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diagnostic_report_subject))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.diagnostic_report_body))
            putExtra(
              Intent.EXTRA_STREAM,
              FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.provider",
                report.file,
              ),
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        startActivity(
          Intent.createChooser(emailIntent, getString(R.string.send_email_chooser_title)),
        )
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Preparing the diagnostics failed")
        AuditLog.failure(AuditAction.DIAGNOSTICS_SENT, "error" to e.javaClass.simpleName)
        AppMessage.show(this@MainActivity, R.string.diag_failed, MessageKind.Error)
      } finally {
        diagnosticsBusy.value = false
      }
    }
  }
}
