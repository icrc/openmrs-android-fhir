package org.openmrs.android.fhir.fragments

import android.content.Context
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.IdentifierAdapter
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.databinding.FragmentIdentifierBinding

class IdentifierFragment: Fragment(R.layout.fragment_identifier) {
    private var _binding: FragmentIdentifierBinding? = null

    private val binding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentIdentifierBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.apply {
            title = requireContext().getString(R.string.select_identifier)
            setDisplayHomeAsUpEnabled(true)
        }
        runBlocking{
            val identifiers = context?.applicationContext?.getSharedPreferences(PreferenceKeys.IDENTIFIERS, Context.MODE_PRIVATE)?.getStringSet(PreferenceKeys.IDENTIFIERS,
                mutableSetOf()
            )
            if (identifiers.isNullOrEmpty()) {
                context?.applicationContext?.let {
                    IdentifierManager.fetchIdentifiers(it)
                }
            }

            val selectedIdentifier = context?.applicationContext?.dataStore?.data?.first()?.get(PreferenceKeys.IDENTIFIER_NAME)
            if (selectedIdentifier != null) {
                binding.selectedIdentifierTv.text = selectedIdentifier
            }
        }
        setupIdentifierSpinner()
        (activity as MainActivity).setDrawerEnabled(false)
    }

    private fun setupIdentifierSpinner() {
        val autoCompleteTextView: AutoCompleteTextView = requireView().findViewById(R.id.autoCompleteTextView)

        lifecycleScope.launch {
            val identifiers = context?.applicationContext?.getSharedPreferences(PreferenceKeys.IDENTIFIERS, Context.MODE_PRIVATE)?.getStringSet(PreferenceKeys.IDENTIFIERS,
                mutableSetOf()
            )
            val result = mutableListOf<IdentifierManager.IdentifierItem>()
            identifiers?.forEach { identifier ->
                result.add(
                    IdentifierManager.IdentifierItem(identifier.substringAfter(","), identifier.substringBefore(","))
                )
            }
            val adapter = IdentifierAdapter(requireContext(), result.toList())
            autoCompleteTextView.setAdapter(adapter)
        }


        autoCompleteTextView.setOnItemClickListener { parent, _, position, _ ->
            val selectedIdentifier = parent.getItemAtPosition(position) as IdentifierManager.IdentifierItem
            lifecycleScope.launch {
                context?.applicationContext?.dataStore?.edit { preferences ->
                    preferences[PreferenceKeys.IDENTIFIER_ID] = selectedIdentifier.uuid
                    preferences[PreferenceKeys.IDENTIFIER_NAME] = selectedIdentifier.display
                }
                binding.selectedIdentifierTv.text = selectedIdentifier.display
                Toast.makeText(context, "Identifier Updated", Toast.LENGTH_SHORT).show()
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