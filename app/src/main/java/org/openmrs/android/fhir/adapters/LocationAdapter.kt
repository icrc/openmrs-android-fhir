import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import org.openmrs.android.fhir.viewmodel.LocationViewModel
class LocationAdapter(context: Context, private val locations: List<LocationViewModel.LocationItem>) :
    ArrayAdapter<LocationViewModel.LocationItem>(context, 0, locations), Filterable {

    private var filteredLocations: List<LocationViewModel.LocationItem> = locations

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val location = getItem(position)
        val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_dropdown_item_1line, parent, false)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = location?.name
        return view
    }

    override fun getItem(position: Int): LocationViewModel.LocationItem {
        return filteredLocations[position]
    }

    override fun getCount(): Int {
        return filteredLocations.size
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val suggestions = if (constraint.isNullOrEmpty()) {
                    locations
                } else {
                    locations.filter { it.name.contains(constraint, ignoreCase = true) }
                }
                results.values = suggestions
                results.count = suggestions.size
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredLocations = if (results?.values is List<*>) {
                    results.values as List<LocationViewModel.LocationItem>
                } else {
                    emptyList()
                }
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as LocationViewModel.LocationItem).name
            }
        }
    }
}
