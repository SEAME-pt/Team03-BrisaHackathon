package com.example.backgroundautomotiveapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.backgroundautomotiveapp.BalanceTopUpActivity
import com.example.backgroundautomotiveapp.R
import com.example.backgroundautomotiveapp.TollHistoryActivity

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val saldoButton = findViewById<Button>(R.id.btn_saldo_recargas)
        val historicoButton = findViewById<Button>(R.id.btn_historico_portagens)

        saldoButton.setOnClickListener {
            val intent = Intent(this, BalanceTopUpActivity::class.java)
            startActivity(intent)
        }

        historicoButton.setOnClickListener {
            val intent = Intent(this, TollHistoryActivity::class.java)
            startActivity(intent)
        }
    }
}
