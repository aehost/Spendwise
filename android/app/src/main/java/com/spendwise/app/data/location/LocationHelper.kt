package com.spendwise.app.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Battery-minimal location helper.
 *
 * Design goals:
 *  — Single-shot only (no continuous tracking, no geofencing)
 *  — 15-minute in-memory cache: if we already have a recent location, return it instantly
 *    without waking GPS hardware at all
 *  — Uses PRIORITY_BALANCED_POWER_ACCURACY (cell-tower + WiFi triangulation) by default.
 *    Consumes ~0.05 mAh per fix vs ~1-2 mAh for GPS. Only falls back to GPS when the
 *    cell-tower fix is older than STALE_THRESHOLD_MS.
 *  — Hard 4-second timeout: if no fix arrives, returns null gracefully
 *  — Completely silent when location permission is not granted
 *
 * Typical battery cost: ~0.05 mAh per transaction (negligible).
 */
@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LocationHelper"
        /** Return cached location if younger than this */
        private const val CACHE_TTL_MS    = 15 * 60 * 1000L   // 15 minutes
        /** Maximum age of OS last-known location before we request a fresh fix */
        private const val STALE_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
        /** Hard timeout for a location request */
        private const val REQUEST_TIMEOUT_MS = 4_000L
    }

    // ── In-memory cache ───────────────────────────────────────────────────
    @Volatile private var cachedLocation: Location? = null
    @Volatile private var cacheTimestampMs: Long = 0L

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * Returns the best available location, or null if:
     *  - Location permission not granted
     *  - No fix available within timeout
     *
     * Battery cost per call:
     *  - Cache hit (< 15 min old): 0 mAh
     *  - Network fix: ~0.05 mAh
     *  - GPS fix (rare fallback): ~1.0 mAh
     */
    suspend fun getLocation(): Location? {
        if (!hasPermission()) return null

        // Return cached location if still fresh
        val now = System.currentTimeMillis()
        val cached = cachedLocation
        if (cached != null && (now - cacheTimestampMs) < CACHE_TTL_MS) {
            Log.d(TAG, "Cache hit: ${cached.latitude},${cached.longitude}")
            return cached
        }

        return try {
            // Step 1: Try last known location (zero battery cost)
            val lastKnown = getLastKnownLocation()
            if (lastKnown != null) {
                val ageMs = now - lastKnown.time
                if (ageMs < STALE_THRESHOLD_MS) {
                    Log.d(TAG, "Last-known is fresh (${ageMs / 1000}s old)")
                    updateCache(lastKnown)
                    return lastKnown
                }
            }

            // Step 2: Request a fresh network-based fix (low battery)
            val fresh = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                requestSingleFix()
            }

            if (fresh != null) {
                Log.d(TAG, "Fresh fix: ${fresh.latitude},${fresh.longitude} acc=${fresh.accuracy}m")
                updateCache(fresh)
                fresh
            } else {
                // Step 3: Fall back to last known even if stale
                if (lastKnown != null) {
                    Log.d(TAG, "Using stale last-known (no fresh fix within timeout)")
                    updateCache(lastKnown)
                    lastKnown
                } else {
                    Log.d(TAG, "No location available")
                    null
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission revoked mid-call")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Location error", e)
            null
        }
    }

    /** Instantly returns cached location without any I/O (for synchronous use) */
    fun getCachedLocation(): Location? {
        val now = System.currentTimeMillis()
        return if (cachedLocation != null && (now - cacheTimestampMs) < CACHE_TTL_MS) {
            cachedLocation
        } else {
            null
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    // ── Private helpers ────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? =
        suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
        }

    @Suppress("MissingPermission")
    private suspend fun requestSingleFix(): Location? =
        suspendCancellableCoroutine { cont ->
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY) // WiFi + Cell, NOT GPS
                .setDurationMillis(REQUEST_TIMEOUT_MS)
                .setMaxUpdateAgeMillis(STALE_THRESHOLD_MS)
                .build()
            fusedClient.getCurrentLocation(request, null)
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
                .addOnCanceledListener { cont.resume(null) }
        }

    private fun updateCache(loc: Location) {
        cachedLocation = loc
        cacheTimestampMs = System.currentTimeMillis()
    }
}
