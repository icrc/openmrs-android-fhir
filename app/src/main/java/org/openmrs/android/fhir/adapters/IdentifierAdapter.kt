package org.openmrs.android.fhir.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
class IdentifierAdapter(context: Context, private val identifiers: List<IdentifierManager.IdentifierItem>) :
    ArrayAdapter<IdentifierManager.IdentifierItem>(context, 0, identifiers) {


    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val identifier = getItem(position)
        val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_dropdown_item_1line, parent, false)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = identifier.display
        return view
    }

    override fun getItem(position: Int): IdentifierManager.IdentifierItem {
        return identifiers[position]
    }

    override fun getCount(): Int {
        return identifiers.size
    }
}