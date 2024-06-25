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
import com.google.android.fhir.FhirEngine
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.LocationItemRecyclerViewAdapter
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.FragmentLocationBinding
import org.openmrs.android.fhir.viewmodel.LocationViewModel

class LocationFragment: Fragment(R.layout.fragment_location) {
    private lateinit var fhirEngine: FhirEngine
    private lateinit var locationViewModel: LocationViewModel
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
        (requireActivity() as AppCompatActivity).supportActionBar?.apply {
            title = requireContext().getString(R.string.select_a_location)
            setDisplayHomeAsUpEnabled(true)
        }
        fhirEngine = FhirApplication.fhirEngine(requireContext())
        locationViewModel =
            ViewModelProvider(
                this,
                LocationViewModel.LocationViewModelFactory(
                    requireActivity().application,
                    fhirEngine
                )
            )[LocationViewModel::class.java]
        locationViewModel.setFavoriteLocations(context?.applicationContext!!)
        val locationRecyclerView: RecyclerView = binding.locationRecyclerView
        val favoriteLocationRecyclerView: RecyclerView = binding.locationFavoriteRecylcerView
        locationAdapter = LocationItemRecyclerViewAdapter(this::onLocationItemClicked, false)
        favoriteLocationAdapter = LocationItemRecyclerViewAdapter(this::onFavoriteLocationItemClicked, true)
        locationRecyclerView.adapter = locationAdapter
        favoriteLocationRecyclerView.adapter = favoriteLocationAdapter
        locationViewModel.locations.observe(viewLifecycleOwner) {
            locationAdapter.submitList(locationViewModel.getLocationsListFiltered())
            favoriteLocationAdapter.submitList(locationViewModel.getFavoriteLocationsList())
        }
        addSearchTextChangeListener()
        (activity as MainActivity).setDrawerEnabled(false)
    }

    private fun onLocationItemClicked(locationItem: LocationViewModel.LocationItem, isFavoriteClickedFlag: Boolean) {
        if(isFavoriteClickedFlag) {
            locationViewModel.favoriteLocationSet?.add(locationItem.resourceId)
            lifecycleScope.launch {
                context?.applicationContext?.dataStore?.edit { preferences ->
                    preferences[PreferenceKeys.FAVORITE_LOCATIONS] =
                        locationViewModel.favoriteLocationSet as Set<String>
                    favoriteLocationAdapter.submitList(locationViewModel.getFavoriteLocationsList())
                    locationAdapter.submitList(locationViewModel.getLocationsListFiltered())
                    Toast.makeText(context, "Location added to favorites.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            selectLocationItem(locationItem)
        }
    }

    private fun addSearchTextChangeListener(){
        binding.locationInputEditText.doOnTextChanged { text, _, _, _ ->
            locationAdapter.submitList(locationViewModel.getLocationsListFiltered(text.toString()))
            favoriteLocationAdapter.submitList(locationViewModel.getFavoriteLocationsList(text.toString()))
        }
    }

    private fun onFavoriteLocationItemClicked(locationItem: LocationViewModel.LocationItem, isFavoriteClickedFlag: Boolean) {
        if(isFavoriteClickedFlag) {
            locationViewModel.favoriteLocationSet?.remove(locationItem.resourceId)
            lifecycleScope.launch {
                context?.applicationContext?.dataStore?.edit { preferences ->
                    preferences[PreferenceKeys.FAVORITE_LOCATIONS] =
                        locationViewModel.favoriteLocationSet as Set<String>
                    favoriteLocationAdapter.submitList(locationViewModel.getFavoriteLocationsList())
                    locationAdapter.submitList(locationViewModel.getLocationsListFiltered())
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
            Toast.makeText(context, "Location Updated", Toast.LENGTH_SHORT).show()
        }
        this.context?.let { PatientIdentifierManager.initialize(it) }



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