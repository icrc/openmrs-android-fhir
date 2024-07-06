package org.openmrs.android.fhir.adapters

import IdentifierManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
class IdentifierAdapter(context: Context, private val identifiers: List<IdentifierManager.IdentifierItem>) :
    ArrayAdapter<IdentifierManager.IdentifierItem>(context, 0, identifiers), Filterable {

    private var filteredIdentifiers: List<IdentifierManager.IdentifierItem> = identifiers

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val identifier = getItem(position)
        val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_dropdown_item_1line, parent, false)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = identifier?.display
        return view
    }

    override fun getItem(position: Int): IdentifierManager.IdentifierItem {
        return filteredIdentifiers[position]
    }

    override fun getCount(): Int {
        return filteredIdentifiers.size
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val suggestions = if (constraint.isNullOrEmpty()) {
                    identifiers
                } else {
                    identifiers.filter { it.display.contains(constraint, ignoreCase = true) }
                }
                results.values = suggestions
                results.count = suggestions.size
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredIdentifiers = if (results?.values is List<*>) {
                    results.values as List<IdentifierManager.IdentifierItem>
                } else {
                    emptyList()
                }
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as IdentifierManager.IdentifierItem).display
            }
        }
    }
}