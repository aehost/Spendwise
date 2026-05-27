package com.spendwise.app.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.correctionDataStore by preferencesDataStore("category_corrections")

/**
 * Persistent store for user-supplied merchant → category corrections.
 *
 * These overrides are checked BEFORE the four-tier rule engine in [MerchantMatcher],
 * giving the app a self-learning capability: once the user corrects a category the
 * app will remember it forever.
 *
 * Storage: DataStore<Preferences> (JSON-encoded map in a single key).
 */
@Singleton
class CategoryCorrectionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val KEY_CORRECTIONS = stringPreferencesKey("corrections_json")

    /** Hot state-flow of all saved corrections: merchant-key (lowercased) → categorySlug */
    val corrections = context.correctionDataStore.data
        .map { prefs ->
            val json = prefs[KEY_CORRECTIONS] ?: "{}"
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * Persist a correction. Fire-and-forget; safe to call on any dispatcher.
     * @param merchant  raw merchant string (will be lowercased + trimmed as key)
     * @param categorySlug  the correct category slug chosen by the user
     */
    fun saveCorrection(merchant: String, categorySlug: String) {
        scope.launch {
            context.correctionDataStore.edit { prefs ->
                val current = corrections.value.toMutableMap()
                current[merchant.trim().lowercase()] = categorySlug
                prefs[KEY_CORRECTIONS] = gson.toJson(current)
            }
        }
    }

    /** Remove a previously saved correction (e.g. user re-corrects). */
    fun removeCorrection(merchant: String) {
        scope.launch {
            context.correctionDataStore.edit { prefs ->
                val current = corrections.value.toMutableMap()
                current.remove(merchant.trim().lowercase())
                prefs[KEY_CORRECTIONS] = gson.toJson(current)
            }
        }
    }

    /** Synchronous snapshot for use inside the merchant matcher. */
    fun getSnapshot(): Map<String, String> = corrections.value
}
