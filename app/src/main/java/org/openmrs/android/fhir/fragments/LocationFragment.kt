package org.openmrs.android.fhir.fragments

import LocationAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.google.android.fhir.FhirEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.FragmentLocationBinding
import org.openmrs.android.fhir.viewmodel.LocationViewModel

class LocationFragment: Fragment(R.layout.fragment_location) {
    private lateinit var fhirEngine: FhirEngine
    private lateinit var locationViewModel: LocationViewModel
    private var _binding: FragmentLocationBinding? = null

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
            title = requireContext().getString(R.string.select_location)
            setDisplayHomeAsUpEnabled(true)
        }
        lifecycleScope.launch {
            val selectedLocation = context?.applicationContext?.dataStore?.data?.first()?.get(PreferenceKeys.LOCATION_NAME)
            if (selectedLocation != null) {
                binding.selectedLocationTv.text = selectedLocation
            }
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
        locationViewModel.locations.observe(viewLifecycleOwner) {
            setupLocationSpinner(it)
        }
        (activity as MainActivity).setDrawerEnabled(false)
    }

    private fun setupLocationSpinner(locations: List<LocationViewModel.LocationItem>) {

        val autoCompleteTextView: AutoCompleteTextView = requireView().findViewById(R.id.autoCompleteTextView)
        val adapter = LocationAdapter(requireContext(), locations)
        autoCompleteTextView.setAdapter(adapter)

        autoCompleteTextView.setOnItemClickListener { parent, _, position, _ ->
            val selectedLocation = parent.getItemAtPosition(position) as LocationViewModel.LocationItem
            lifecycleScope.launch {
                context?.applicationContext?.dataStore?.edit { preferences ->
                    preferences[PreferenceKeys.LOCATION_ID] = selectedLocation.resourceId
                    preferences[PreferenceKeys.LOCATION_NAME] = selectedLocation.name
                }
                binding.selectedLocationTv.text = selectedLocation.name
                Toast.makeText(context, "Location Updated", Toast.LENGTH_SHORT).show()
            }
        }

        autoCompleteTextView.setOnClickListener {
            if (!autoCompleteTextView.isPopupShowing) {
                autoCompleteTextView.showDropDown()
            }
        }

        // Show dropdown on focus
        autoCompleteTextView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                autoCompleteTextView.showDropDown()
            }
        }
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