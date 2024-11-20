package org.openmrs.android.fhir.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.LocationItemRecyclerViewAdapter
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.FragmentLocationBinding
import org.openmrs.android.fhir.viewmodel.LocationViewModel
import RestApiManager
import androidx.fragment.app.viewModels
import javax.inject.Inject
import kotlin.getValue

class LocationFragment : Fragment(R.layout.fragment_location) {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private val locationViewModel by viewModels<LocationViewModel> {viewModelFactory}

    private lateinit var restApiClient: RestApiManager
    private var _binding: FragmentLocationBinding? = null
    private lateinit var locationAdapter: LocationItemRecyclerViewAdapter
    private lateinit var favoriteLocationAdapter: LocationItemRecyclerViewAdapter

    private val binding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val actionBar = (requireActivity() as AppCompatActivity).supportActionBar
        (requireActivity().application as FhirApplication).appComponent.inject(this)

        lifecycleScope.launch {
            val savedLocationName = context?.applicationContext?.dataStore?.data?.first()?.get(PreferenceKeys.LOCATION_NAME)
            actionBar?.title = savedLocationName ?: requireContext().getString(R.string.select_a_location)

            val selectedLocationId = context?.applicationContext?.dataStore?.data?.first()?.get(PreferenceKeys.LOCATION_ID)

            selectedLocationId?.let { locationId ->
                if (::locationAdapter.isInitialized && ::favoriteLocationAdapter.isInitialized) {
                    locationAdapter.setSelectedLocation(locationId)
                    favoriteLocationAdapter.setSelectedLocation(locationId)
                }
            }
        }

        actionBar?.setDisplayHomeAsUpEnabled(true)
        restApiClient = FhirApplication.restApiClient(requireContext())
        locationViewModel.getLocations()
        locationViewModel.setFavoriteLocations(context?.applicationContext!!)

        val locationRecyclerView: RecyclerView = binding.locationRecyclerView
        val favoriteLocationRecyclerView: RecyclerView = binding.locationFavoriteRecylcerView
        locationAdapter = LocationItemRecyclerViewAdapter(this::onLocationItemClicked, false)
        favoriteLocationAdapter = LocationItemRecyclerViewAdapter(this::onFavoriteLocationItemClicked, true)
        locationRecyclerView.adapter = locationAdapter
        favoriteLocationRecyclerView.adapter = favoriteLocationAdapter

        binding.progressBar.visibility = View.VISIBLE

        locationViewModel.locations.observe(viewLifecycleOwner) {
            binding.progressBar.visibility = View.GONE
            if (::locationAdapter.isInitialized && ::favoriteLocationAdapter.isInitialized) {
                locationAdapter.submitList(locationViewModel.getLocationsListFiltered())
                favoriteLocationAdapter.submitList(locationViewModel.getFavoriteLocationsList())
            }
        }

        addSearchTextChangeListener()
        (activity as MainActivity).setDrawerEnabled(false)
    }

    private fun onLocationItemClicked(locationItem: LocationViewModel.LocationItem, isFavoriteClickedFlag: Boolean) {
        if (isFavoriteClickedFlag) {
            locationViewModel.favoriteLocationSet?.add(locationItem.resourceId)
            lifecycleScope.launch {
                context?.applicationContext?.dataStore?.edit { preferences ->
                    preferences[PreferenceKeys.FAVORITE_LOCATIONS] =
                        locationViewModel.favoriteLocationSet as Set<String>
                    if (::favoriteLocationAdapter.isInitialized && ::locationAdapter.isInitialized) {
                        favoriteLocationAdapter.submitList(locationViewModel.getFavoriteLocationsList())
                        locationAdapter.submitList(locationViewModel.getLocationsListFiltered())
                    }
                    Toast.makeText(context, "Location added to favorites.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            selectLocationItem(locationItem)
        }
    }

    private fun addSearchTextChangeListener() {
        binding.locationInputEditText.doOnTextChanged { text, _, _, _ ->
            if (::locationAdapter.isInitialized && ::favoriteLocationAdapter.isInitialized) {
                locationAdapter.submitList(locationViewModel.getLocationsListFiltered(text.toString()))
                favoriteLocationAdapter.submitList(locationViewModel.getFavoriteLocationsList(text.toString()))
            }
        }
    }

    private fun onFavoriteLocationItemClicked(locationItem: LocationViewModel.LocationItem, isFavoriteClickedFlag: Boolean) {
        if (isFavoriteClickedFlag) {
            locationViewModel.favoriteLocationSet?.remove(locationItem.resourceId)
            lifecycleScope.launch {
                context?.applicationContext?.dataStore?.edit { preferences ->
                    preferences[PreferenceKeys.FAVORITE_LOCATIONS] =
                        locationViewModel.favoriteLocationSet as Set<String>
                    if (::favoriteLocationAdapter.isInitialized && ::locationAdapter.isInitialized) {
                        favoriteLocationAdapter.submitList(locationViewModel.getFavoriteLocationsList())
                        locationAdapter.submitList(locationViewModel.getLocationsListFiltered())
                    }
                    Toast.makeText(context, "Location removed from favorites", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            selectLocationItem(locationItem)
        }
    }

    private fun selectLocationItem(locationItem: LocationViewModel.LocationItem) {
        lifecycleScope.launch {
            context?.applicationContext?.dataStore?.edit { preferences ->
                preferences[PreferenceKeys.LOCATION_ID] = locationItem.resourceId
                preferences[PreferenceKeys.LOCATION_NAME] = locationItem.name
            }
            (activity as MainActivity).updateLocationName(locationItem.name)

            (requireActivity() as AppCompatActivity).supportActionBar?.title = locationItem.name

            if (::locationAdapter.isInitialized && ::favoriteLocationAdapter.isInitialized) {
                locationAdapter.setSelectedLocation(locationItem.resourceId)
                favoriteLocationAdapter.setSelectedLocation(locationItem.resourceId)
            }
        }

        Toast.makeText(context, "Location Updated", Toast.LENGTH_SHORT).show()

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                NavHostFragment.findNavController(this).navigateUp()
                true
            }
            else -> false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
