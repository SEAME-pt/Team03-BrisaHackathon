package com.example.backgroundautomotiveapp.util // Or your preferred package

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStorage {
    private const val PREFERENCE_FILE_NAME = "secure_app_prefs" // Changed name slightly
    private const val KEY_AUTH_TOKEN = "auth_token"

    private fun getEncryptedSharedPreferences(context: Context): EncryptedSharedPreferences {
        val masterKeyAlias = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFERENCE_FILE_NAME,
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences // Cast needed for some versions
    }

    fun saveAuthToken(context: Context, token: String) {
        getEncryptedSharedPreferences(context).edit().putString(KEY_AUTH_TOKEN, token).apply()
        Log.d("SecureStorage", "AuthToken saved successfully.")
    }

    fun getAuthToken(context: Context): String? {
        val token = getEncryptedSharedPreferences(context).getString(KEY_AUTH_TOKEN, null)
        Log.d("SecureStorage", "Retrieved AuthToken: ${token != null}") // Log if token was found
        return token
    }

    fun clearAuthToken(context: Context) {
        getEncryptedSharedPreferences(context).edit().remove(KEY_AUTH_TOKEN).apply()
        Log.d("SecureStorage", "AuthToken cleared.")
    }
}
