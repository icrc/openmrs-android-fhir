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
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.fhir.sync.CurrentSyncJobStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.ActivityMainBinding
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import timber.log.Timber

const val MAX_RESOURCE_COUNT = 20

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private lateinit var drawerToggle: ActionBarDrawerToggle

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<MainActivityViewModel> { viewModelFactory }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (this.application as FhirApplication).appComponent.inject(this)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    initActionBar()
    initNavigationDrawer()
    observeLastSyncTime()
    observeSyncState()
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
}
