package com.salimmeshref.securemessenger.data.local.prefrences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.core.content.edit

class SecurePreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAuthToken(token: String) {
        encryptedPrefs.edit() { putString(KEY_AUTH_TOKEN, token) }
    }

    fun getAuthToken(): String? {
        return encryptedPrefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun savePrivateKeyAlias(alias: String) {
        encryptedPrefs.edit() { putString(KEY_PRIVATE_KEY_ALIAS, alias) }
    }


    fun getPrivateKeyAlias(): String? {
        return encryptedPrefs.getString(KEY_PRIVATE_KEY_ALIAS, null)
    }

    //use this method to clear all stored data (e.g., on logout)
    fun clearAll() {
        encryptedPrefs.edit() { clear() }
    }


    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_PRIVATE_KEY_ALIAS = "private_key_alias"
    }
}