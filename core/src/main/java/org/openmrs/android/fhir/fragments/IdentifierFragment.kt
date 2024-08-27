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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.MainActivity
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.adapters.IdentifierTypeRecyclerViewAdapter
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.model.IdentifierType
import org.openmrs.android.fhir.databinding.FragmentIdentifierBinding

class IdentifierFragment: Fragment(R.layout.fragment_identifier) {
    private var _binding: FragmentIdentifierBinding? = null
    private lateinit var identifierAdapter: IdentifierTypeRecyclerViewAdapter
    private lateinit var selectedIdentifierTypes: MutableSet<String>
    private lateinit var identifierTypes: MutableList<IdentifierType>
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
            title = requireContext().getString(R.string.select_identifier_types)
            setDisplayHomeAsUpEnabled(true)
        }
        runBlocking{
            binding.progressBar.visibility = View.VISIBLE
            identifierTypes = context?.applicationContext?.let { FhirApplication.roomDatabase(it).dao().getAllIdentifierTypes().toMutableList()} ?: mutableListOf()
            if (identifierTypes.isEmpty()) {
                context?.applicationContext?.let {
                    IdentifierTypeManager.fetchIdentifiers(it)
                }
            }
            selectedIdentifierTypes = context?.dataStore?.data?.first()?.get(PreferenceKeys.SELECTED_IDENTIFIER_TYPES)?.toMutableSet() ?: mutableSetOf()
            val identifierRecyclerView: RecyclerView = binding.identifierRecyclerView
            identifierAdapter = IdentifierTypeRecyclerViewAdapter(this@IdentifierFragment::onIdentifierTypeItemClicked, selectedIdentifierTypes)
            identifierRecyclerView.adapter = identifierAdapter
            identifierAdapter.submitList(identifierTypes)
            binding.progressBar.visibility = View.GONE
        }
        (activity as MainActivity).setDrawerEnabled(false)

        addSearchTextChangeListener()
    }

    private fun addSearchTextChangeListener() {
        binding.identifierInputEditText.doOnTextChanged { text, _, _, _ ->
            if (::identifierAdapter.isInitialized && text != null) {
                identifierAdapter.submitList(identifierTypes.filter{
                    it.display?.contains(text) ?: true
                })
            }
        }
    }

    private fun onIdentifierTypeItemClicked(identifierTypeItem: IdentifierType, isSelected: Boolean) {
        if(!identifierTypeItem.required){
            lifecycleScope.launch {
                if(isSelected){
                    context?.applicationContext?.dataStore?.edit { preferences ->
                        selectedIdentifierTypes.remove(identifierTypeItem.uuid)
                        preferences[PreferenceKeys.SELECTED_IDENTIFIER_TYPES] = selectedIdentifierTypes
                        Toast.makeText(context, "Identifier removed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    lifecycleScope.launch {
                        selectedIdentifierTypes.add(identifierTypeItem.uuid)
                        context?.applicationContext?.dataStore?.edit { preferences ->
                            preferences[PreferenceKeys.SELECTED_IDENTIFIER_TYPES] = selectedIdentifierTypes
                            Toast.makeText(context, "Identifier added", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                if (::identifierAdapter.isInitialized) {
                    identifierAdapter.notifyDataSetChanged()
                    identifierAdapter.submitList(identifierTypes)
                }
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