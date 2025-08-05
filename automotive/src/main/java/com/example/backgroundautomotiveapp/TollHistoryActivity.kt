package com.example.backgroundautomotiveapp
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.backgroundautomotiveapp.ui.TollHistoryAdapter
import com.example.backgroundautomotiveapp.util.SecureStorage
import com.example.backgroundautomotiveapp.util.TripsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class TollHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TollHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("calling activity toll history")
        setContentView(R.layout.activity_toll_history)

        recyclerView = findViewById(R.id.recycler_toll_history)
        recyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val authToken = SecureStorage.getAuthToken(applicationContext) ?: return@launch
            val trips = TripsHelper.fetchTrips(applicationContext, authToken)

            withContext(Dispatchers.Main) {
                adapter = TollHistoryAdapter(trips) { trip ->
                    val intent = Intent(this@TollHistoryActivity, TripDetailActivity::class.java)
                    intent.putExtra("trip_number", trip.tripNumber.toString())
                    startActivity(intent)
                }
               recyclerView.adapter = adapter
            }
        }
    }
}

class TripDetailActivity {

}
