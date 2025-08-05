package com.example.backgroundautomotiveapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.backgroundautomotiveapp.R
import com.example.backgroundautomotiveapp.util.SecureStorage
import com.example.backgroundautomotiveapp.util.Trip
import com.example.backgroundautomotiveapp.util.TripsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TollHistoryAdapter(
    private val tripList: List<Trip>,
    private val onTripClick: (Trip) -> Unit
) : RecyclerView.Adapter<TollHistoryAdapter.TollViewHolder>() {

    private val tripLocationCache = mutableMapOf<Int, String>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    class TollViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        val textTripNumber: TextView = itemView.findViewById(R.id.text_trip_number)
        val textDate: TextView = itemView.findViewById(R.id.text_date)
        val textLicense: TextView = itemView.findViewById(R.id.text_license)
        val textLocation: TextView = itemView.findViewById(R.id.text_location)
        val textCost: TextView = itemView.findViewById(R.id.text_cost)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TollViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_toll_trip, parent, false)
        return TollViewHolder(view)
    }

    override fun onBindViewHolder(holder: TollViewHolder, position: Int) {
        val trip = tripList[position]

        // Formata data da viagem
        val date = Date(trip.startDate)
        val format = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val formattedDate = format.format(date)

        // Preenche dados básicos
//        holder.textTripNumber.text = "Viagem #${trip.tripNumber}"
        holder.textDate.text = formattedDate
        holder.textLicense.text = trip.licensePlate.value
        holder.textCost.text = "€${"%.2f".format(trip.totalCost)}"

        // Verifica se já temos os nomes dos pedágios armazenados
        val cached = tripLocationCache[trip.tripNumber]
        if (cached != null) {
            holder.textLocation.text = cached
        } else {
            holder.textLocation.text = "Carregando..."

            coroutineScope.launch {
                val context = holder.itemView.context
                val authToken = SecureStorage.getAuthToken(context) ?: return@launch

                val locationText = TripsHelper.fetchTripDetail(trip.tripNumber, authToken)
                    ?: "Indisponível"

                // Salva no cache
                tripLocationCache[trip.tripNumber] = locationText

                // Garante que ainda está na posição correta (evita sobrescrever item reciclado)
                if (holder.adapterPosition == position) {
                    holder.textLocation.text = locationText
                }
            }
        }

        holder.itemView.setOnClickListener {
            onTripClick(trip)
        }
    }

    override fun getItemCount(): Int = tripList.size
}
