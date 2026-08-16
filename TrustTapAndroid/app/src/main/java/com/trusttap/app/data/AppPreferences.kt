package com.trusttap.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.trustTapDataStore by preferencesDataStore(name = "trusttap_preferences")

class AppPreferences(private val context: Context) {

    val baseUrl: Flow<String> = context.trustTapDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { preferences -> preferences[BASE_URL] ?: DEFAULT_BASE_URL }

    val hasCompletedOnboarding: Flow<Boolean> = context.trustTapDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { preferences -> preferences[ONBOARDING_COMPLETE] ?: false }

    suspend fun setBaseUrl(value: String) {
        context.trustTapDataStore.edit { preferences ->
            preferences[BASE_URL] = value
        }
    }

    suspend fun completeOnboarding() {
        context.trustTapDataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETE] = true
        }
    }

    companion object {
        /** Default for the real phone setup described in the project brief. */
        const val DEFAULT_BASE_URL = "http://192.168.1.70:8000/"
        const val EMULATOR_BASE_URL = "http://10.0.2.2:8000/"

        private val BASE_URL = stringPreferencesKey("base_url")
        private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }
}
