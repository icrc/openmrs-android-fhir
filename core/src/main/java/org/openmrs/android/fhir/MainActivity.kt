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

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.sync.CurrentSyncJobStatus
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmrs.android.fhir.auth.AuthStateManager
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.ActivityMainBinding
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel.Companion.PREFS_NAME
import timber.log.Timber

const val MAX_RESOURCE_COUNT = 20

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private lateinit var drawerToggle: ActionBarDrawerToggle
  private lateinit var authStateManager: AuthStateManager
  private var tokenExpiryHandler: Handler? = null
  private var tokenCheckRunnable: Runnable? = null
  private var isDialogShowing = false
  private lateinit var loginRepository: LoginRepository

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var fhirEngine: FhirEngine

  private val viewModel by viewModels<MainActivityViewModel> { viewModelFactory }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (this.application as FhirApplication).appComponent.inject(this)
    binding = ActivityMainBinding.inflate(layoutInflater)
    loginRepository = LoginRepository.getInstance(applicationContext)
    authStateManager = AuthStateManager.getInstance(applicationContext)
    setContentView(binding.root)
    tokenExpiryHandler = Handler(Looper.getMainLooper())

    initActionBar()
    initNavigationDrawer()
    observeLastSyncTime()
    observeSyncState()
    observeNetworkConnection()
    viewModel.updateLastSyncTimestamp()
    viewModel.triggerIdentifierTypeSync(applicationContext)
  }

  override fun onResume() {
    super.onResume()
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

  private fun initNavigationDrawer() {
    binding.navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected)
    drawerToggle = ActionBarDrawerToggle(this, binding.drawer, R.string.open, R.string.close)
    binding.drawer.addDrawerListener(drawerToggle)
    drawerToggle.syncState()
    setLocationInDrawer()
  }

  private fun setLocationInDrawer() {
    lifecycleScope.launch {
      binding.navigationView.menu.findItem(R.id.menu_current_location).title =
        applicationContext?.dataStore?.data?.first()?.get(PreferenceKeys.LOCATION_NAME)
          ?: getString(R.string.no_location_selected)
    }
  }

  fun updateLocationName(locationName: String) {
    binding.navigationView.menu.findItem(R.id.menu_current_location).title = locationName
  }

  private fun onNavigationItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.menu_sync -> {
        viewModel.triggerOneTimeSync(applicationContext)
        binding.drawer.closeDrawer(GravityCompat.START)
        return false
      }
      R.id.menu_logout -> {
        showLogoutWarningDialog()
      }
    }
    binding.drawer.closeDrawer(GravityCompat.START)
    return false
  }

  private fun showToast(message: String) {
    Timber.i(message)
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
  }

  private fun observeSyncState() {
    lifecycleScope.launch {
      viewModel.pollState.collect {
        Timber.d("observerSyncState: pollState Got status $it")
        when (it) {
          is CurrentSyncJobStatus.Enqueued -> showToast("Sync: started")
          is CurrentSyncJobStatus.Running -> showToast("Sync: in progress")
          is CurrentSyncJobStatus.Succeeded -> {
            showToast("Sync: succeeded at ${it.timestamp}")
            viewModel.updateLastSyncTimestamp()
          }
          is CurrentSyncJobStatus.Failed -> {
            showToast("Sync: failed at ${it.timestamp}")
            viewModel.updateLastSyncTimestamp()
          }
          else -> showToast("Sync: unknown state.")
        }
      }
    }
  }

  private fun observeLastSyncTime() {
    viewModel.lastSyncTimestampLiveData.observe(this) {
      binding.navigationView.getHeaderView(0).findViewById<TextView>(R.id.last_sync_tv).text = it
    }
  }

  private suspend fun monitorTokenExpiry() {
    val isServerAvailable = withContext(Dispatchers.IO) { viewModel.isServerAvailable() }
    tokenCheckRunnable =
      object : Runnable {
        override fun run() {
          val authState = authStateManager.current
          val expirationTime = authState.accessTokenExpirationTime
          if (expirationTime != null && System.currentTimeMillis() > expirationTime) {
            if (isServerAvailable) {
              showTokenExpiredDialog()
              tokenExpiryHandler?.removeCallbacks(this)
            }
          } else {
            tokenExpiryHandler?.postDelayed(this, 60 * 1000)
          }
        }
      }

    // Start the first check immediately
    tokenExpiryHandler?.post(tokenCheckRunnable!!)
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
          scheduleDialogForLater()
          viewModel.setStopSync(false)
          dialog.dismiss()
        }
        .setNeutralButton(getString(R.string.no_and_don_t_ask_me_again)) { dialog, _ ->
          isDialogShowing = true
          viewModel.cancelPeriodicSyncWorker(applicationContext)
          viewModel.setStopSync(true)
          Toast.makeText(this, "Sync: Terminated", Toast.LENGTH_SHORT).show()
          dialog.dismiss()
        }
        .setCancelable(false)
        .show()
    }
  }

  private fun scheduleDialogForLater() {
    tokenExpiryHandler?.postDelayed(
      {
        showTokenExpiredDialog() // Re-show the dialog after 15 minutes
      },
      15 * 60 * 1000,
    ) // 15 minutes in milliseconds
  }

  private fun observeNetworkConnection() {
    lifecycleScope.launch {
      viewModel.networkStatus.collect { isNetworkAvailable ->
        if (isNetworkAvailable) {
          binding.offlineFlag.tvNetworkStatus.text = getString(R.string.online)
          viewModel.triggerOneTimeSync(applicationContext)
          monitorTokenExpiry()
        } else {
          binding.offlineFlag.tvNetworkStatus.text = getString(R.string.offline)
        }
      }
    }
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

  private fun showLogoutWarningDialog() {
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
  }

  private fun navigateToLogin() {
    val intent = Intent(this, LoginActivity::class.java)
    val pendingIntentSuccess =
      PendingIntent.getActivity(
        this,
        0,
        intent,
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val pendingIntentCancel =
      PendingIntent.getActivity(
        this,
        1,
        intent,
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    lifecycleScope
      .launch(Dispatchers.IO) {
        WorkManager.getInstance(this@MainActivity).cancelAllWork()
        fhirEngine.clearDatabase()
        clearPreferences()
      }
      .invokeOnCompletion {
        authStateManager.endSessionRequest(pendingIntentSuccess, pendingIntentCancel)
      }
  }

  private fun clearPreferences() {
    val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    sharedPreferences.edit().clear().apply()
  }
}
