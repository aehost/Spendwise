package com.spendwise.app.data.location

import android.util.Log
import com.spendwise.app.data.local.preferences.CategoryCorrectionStore
import com.spendwise.app.data.remote.api.IntelligenceApi
import com.spendwise.app.data.remote.dto.ClassifyMerchantRequest
import com.spendwise.app.domain.merchant.MerchantDatabase.MatchType
import com.spendwise.app.domain.merchant.MerchantDatabase.MerchantTag
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

/**
 * Tier 5 classifier: resolves merchants that all local tiers failed to classify
 * by sending {merchant name + GPS coordinates} to the backend, which calls the
 * Google Places Nearby Search API and returns the place's category.
 *
 * Only fires when:
 *  1. All local tiers (0-4) returned confidence < 0.7 or "other"
 *  2. Location permission is granted
 *  3. A fresh location is available (from [LocationHelper] cache or a new fix)
 *
 * Results are saved to [CategoryCorrectionStore] so the same merchant is never
 * looked up via location again — the local Tier 0 cache handles it next time.
 *
 * Battery cost: one network call when invoked; location is fetched via
 * [LocationHelper] (cell-tower triangulation, ~0.05 mAh).
 */
class LocationMerchantClassifier @Inject constructor(
    private val locationHelper: LocationHelper,
    private val intelligenceApi: IntelligenceApi,
    private val correctionStore: CategoryCorrectionStore
) {
    companion object {
        private const val TAG = "LocationClassifier"
    }

    /**
     * Tries to classify [merchant] using GPS + backend Places lookup.
     * Returns null if location is unavailable or the backend has no match.
     */
    suspend fun classify(merchant: String, smsBody: String? = null): MerchantTag? {
        val loc = locationHelper.getLocation() ?: return null

        return try {
            val resp = intelligenceApi.classifyMerchant(
                ClassifyMerchantRequest(
                    merchant  = merchant,
                    smsBody   = smsBody,
                    latitude  = loc.latitude,
                    longitude = loc.longitude,
                    accuracy  = loc.accuracy.toDouble()
                )
            )

            val result = resp.body()?.data ?: return null
            if (result.categorySlug == "other" || result.confidence < 0.65) return null

            Log.d(TAG, "Location-classified '$merchant' → ${result.categorySlug} (${result.confidence})")

            // Persist so Tier 0 handles it locally next time (no more location calls)
            correctionStore.saveCorrection(merchant, result.categorySlug)

            MerchantTag(
                categorySlug = result.categorySlug,
                displayName  = result.displayName ?: merchant,
                subCategory  = result.subCategory,
                confidence   = result.confidence.toFloat(),
                matchType    = MatchType.KEYWORD
            )
        } catch (e: Exception) {
            Log.w(TAG, "Location classification failed for '$merchant'", e)
            null
        }
    }
}
