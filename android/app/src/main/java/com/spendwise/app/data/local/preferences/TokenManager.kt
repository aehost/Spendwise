package com.spendwise.app.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.spendwise.app.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        try {
            EncryptedSharedPreferences.create(
                ctx, "sw_secure_prefs", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to plain prefs if encryption fails (e.g. emulator)
            ctx.getSharedPreferences("sw_plain_prefs", Context.MODE_PRIVATE)
        }
    }

    var accessToken: String?
        get() = prefs.getString(Constants.TOKEN_KEY, null)
        set(v) = prefs.edit().putString(Constants.TOKEN_KEY, v).apply()

    var refreshToken: String?
        get() = prefs.getString(Constants.REFRESH_KEY, null)
        set(v) = prefs.edit().putString(Constants.REFRESH_KEY, v).apply()

    var userId: String?
        get() = prefs.getString(Constants.USER_ID_KEY, null)
        set(v) = prefs.edit().putString(Constants.USER_ID_KEY, v).apply()

    var userEmail: String?
        get() = prefs.getString(Constants.USER_EMAIL_KEY, null)
        set(v) = prefs.edit().putString(Constants.USER_EMAIL_KEY, v).apply()

    var userName: String?
        get() = prefs.getString(Constants.USER_NAME_KEY, null)
        set(v) = prefs.edit().putString(Constants.USER_NAME_KEY, v).apply()

    var setupDone: Boolean
        get() = prefs.getBoolean(Constants.SETUP_DONE_KEY, false)
        set(v) = prefs.edit().putBoolean(Constants.SETUP_DONE_KEY, v).apply()

    var smsScanFromMs: Long
        get() = prefs.getLong(Constants.SMS_SCAN_FROM_MS, 0L)
        set(v) = prefs.edit().putLong(Constants.SMS_SCAN_FROM_MS, v).apply()

    var roundUpSavings: Double
        // BUG FIX: Double.fromBits can yield NaN/Infinity if the stored bits are
        // corrupted (e.g. manual prefs edit, partial write). Coerce to a sane 0.0
        // so the UI never renders "NaN" or a runaway value.
        get() {
            val v = Double.fromBits(prefs.getLong("round_up_savings", 0L))
            return if (v.isNaN() || v.isInfinite() || v < 0.0) 0.0 else v
        }
        set(v) {
            val safe = if (v.isNaN() || v.isInfinite() || v < 0.0) 0.0 else v
            prefs.edit().putLong("round_up_savings", safe.toBits()).apply()
        }

    var lastSpendStreakCheckDate: String
        get() = prefs.getString("streak_check_date", "") ?: ""
        set(v) = prefs.edit().putString("streak_check_date", v).apply()

    var spendingStreak: Int
        get() = prefs.getInt("spending_streak", 0)
        set(v) = prefs.edit().putInt("spending_streak", v).apply()

    // Gmail IMAP accounts — stored as JSON array string
    var gmailImapAccountsJson: String
        get() = prefs.getString("gmail_imap_accounts", "[]") ?: "[]"
        set(v) = prefs.edit().putString("gmail_imap_accounts", v).apply()

    // XP & Gamification
    var userXp: Int
        get() = prefs.getInt("user_xp", 0)
        set(v) = prefs.edit().putInt("user_xp", v).apply()

    var userLevel: Int
        get() = prefs.getInt("user_level", 1)
        set(v) = prefs.edit().putInt("user_level", v).apply()

    // Daily Challenge
    var dailyChallengeDate: String
        get() = prefs.getString("daily_challenge_date", "") ?: ""
        set(v) = prefs.edit().putString("daily_challenge_date", v).apply()

    var dailyChallengeType: String
        get() = prefs.getString("daily_challenge_type", "") ?: ""
        set(v) = prefs.edit().putString("daily_challenge_type", v).apply()

    var dailyChallengeAccepted: Boolean
        get() = prefs.getBoolean("daily_challenge_accepted", false)
        set(v) = prefs.edit().putBoolean("daily_challenge_accepted", v).apply()

    var dailyChallengeCompleted: Boolean
        get() = prefs.getBoolean("daily_challenge_completed", false)
        set(v) = prefs.edit().putBoolean("daily_challenge_completed", v).apply()

    // Goal milestones — JSON map of goalId → lastPctInt
    var goalMilestoneMapJson: String
        get() = prefs.getString("goal_milestone_map", "{}") ?: "{}"
        set(v) = prefs.edit().putString("goal_milestone_map", v).apply()

    // Alias used by GoalMilestoneWorker
    var goalMilestonesJson: String
        get() = goalMilestoneMapJson
        set(v) { goalMilestoneMapJson = v }

    // Gmail IMAP last sync per account — JSON map of email → lastSyncMs
    var gmailImapLastSyncJson: String
        get() = prefs.getString("gmail_imap_last_sync", "{}") ?: "{}"
        set(v) = prefs.edit().putString("gmail_imap_last_sync", v).apply()

    // Daily pulse last sent date (prevents duplicate daily notifications)
    var lastDailyPulseDate: String
        get() = prefs.getString("last_pulse_date", "") ?: ""
        set(v) = prefs.edit().putString("last_pulse_date", v).apply()

    // Daily challenge index (used for rotating challenge by day-of-year)
    var dailyChallengeIndex: Int
        get() = prefs.getInt("daily_challenge_index", 0)
        set(v) = prefs.edit().putInt("daily_challenge_index", v).apply()

    // Weekly review
    var lastWeeklyReviewDate: String
        get() = prefs.getString("last_weekly_review", "") ?: ""
        set(v) = prefs.edit().putString("last_weekly_review", v).apply()

    fun isLoggedIn(): Boolean = accessToken != null && userId != null

    fun clearAuth() {
        prefs.edit()
            .remove(Constants.TOKEN_KEY)
            .remove(Constants.REFRESH_KEY)
            .remove(Constants.USER_ID_KEY)
            .remove(Constants.USER_EMAIL_KEY)
            .remove(Constants.USER_NAME_KEY)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
