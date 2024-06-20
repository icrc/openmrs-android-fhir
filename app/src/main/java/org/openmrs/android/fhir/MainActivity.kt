/*
 * Copyright 2022-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openmrs.android.fhir

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.fhir.sync.CurrentSyncJobStatus
import kotlinx.coroutines.flow.first
import org.openmrs.android.fhir.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.viewmodel.MainActivityViewModel
import timber.log.Timber

const val MAX_RESOURCE_COUNT = 20

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private lateinit var drawerToggle: ActionBarDrawerToggle
  private val viewModel: MainActivityViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    initActionBar()
    initNavigationDrawer()
    observeLastSyncTime()
    observeSyncState()
    viewModel.updateLastSyncTimestamp()
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
      val selectedLocationName = applicationContext?.dataStore?.data?.first()?.get(
        PreferenceKeys.LOCATION_NAME)
      if (selectedLocationName != null) {
        binding.navigationView.menu.findItem(R.id.menu_current_location).title = selectedLocationName
      }
    }
  }

  fun updateLocationName(locationName: String) {
    binding.navigationView.menu.findItem(R.id.menu_current_location).title = locationName
  }

  private fun onNavigationItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.menu_sync -> {
        viewModel.triggerOneTimeSync()
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
