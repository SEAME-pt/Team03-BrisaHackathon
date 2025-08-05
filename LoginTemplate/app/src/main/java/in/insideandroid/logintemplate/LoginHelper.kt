package `in`.insideandroid.logintemplate

import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object LoginHelper {

    fun loginUser(context: Context, scope: CoroutineScope, email: String, password: String) {
//        if (email.isEmpty() || password.isEmpty()) {
//            Toast.makeText(context, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
//            return
//        }

        scope.launch {
            val token = performLoginManually(email, password)
            if (token != null) {
                Toast.makeText(context, "Login bem-sucedido! Token: $token", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Falha no login", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun performLoginManually(email: String, password: String): String? {
        // Coloque aqui sua lógica de requisição HTTP como estava no seu método original
        return null // Apenas para compilar; substitua com seu código real
    }
}
