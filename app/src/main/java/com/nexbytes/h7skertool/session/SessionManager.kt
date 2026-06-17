package com.nexbytes.h7skertool.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "h7sker_session")

class SessionManager(private val context: Context) {

    companion object {
        private val KEY_VERIFIED = booleanPreferencesKey("verified")
        private val KEY_CLIENT_URL = stringPreferencesKey("client_url")
        private val KEY_USERNAME = stringPreferencesKey("username")
    }

    val isVerified: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_VERIFIED] ?: false
    }

    val clientUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CLIENT_URL] ?: ""
    }

    val username: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_USERNAME] ?: ""
    }

    suspend fun setVerified(verified: Boolean, username: String = "") {
        context.dataStore.edit { prefs ->
            prefs[KEY_VERIFIED] = verified
            if (username.isNotEmpty()) prefs[KEY_USERNAME] = username
        }
    }

    suspend fun setClientUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CLIENT_URL] = url.trimEnd('/')
        }
    }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs[KEY_VERIFIED] = false
            prefs[KEY_USERNAME] = ""
        }
    }

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }
}
