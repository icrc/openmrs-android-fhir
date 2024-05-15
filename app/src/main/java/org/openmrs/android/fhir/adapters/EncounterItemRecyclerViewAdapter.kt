import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.openmrs.android.fhir.databinding.EncounterListItemBinding
import org.openmrs.android.fhir.viewmodel.EncounterItemViewHolder
import org.openmrs.android.fhir.viewmodel.PatientListViewModel

class EncounterItemRecyclerViewAdapter :
        ListAdapter<PatientListViewModel.EncounterItem, EncounterItemViewHolder>(
                EncounterItemDiffCallback()
        ) {

  class EncounterItemDiffCallback :
          DiffUtil.ItemCallback<PatientListViewModel.EncounterItem>() {
    override fun areItemsTheSame(
            oldItem: PatientListViewModel.EncounterItem,
            newItem: PatientListViewModel.EncounterItem,
    ): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(
            oldItem: PatientListViewModel.EncounterItem,
            newItem: PatientListViewModel.EncounterItem,
    ): Boolean = oldItem.id == newItem.id
  }

  override fun onCreateViewHolder(
          parent: ViewGroup,
          viewType: Int
  ): EncounterItemViewHolder {
    return EncounterItemViewHolder(
            EncounterListItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
            )
    )
  }

  override fun onBindViewHolder(holder: EncounterItemViewHolder, position: Int) {
    val item = currentList[position]
    holder.bindTo(item)
  }
}
