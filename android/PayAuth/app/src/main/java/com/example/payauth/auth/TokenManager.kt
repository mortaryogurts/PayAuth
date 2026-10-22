package com.example.payauth.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context
): TokenStore {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun saveTokens(accessToken: String, refreshToken: String) =
        prefs.edit().putString(KEY_TOKEN, accessToken).putString("refresh", refreshToken).apply()

    override fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    override fun getRefreshToken(): String? = prefs.getString("refresh", null)

    override fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).remove("refresh").apply()
    }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
    }
}