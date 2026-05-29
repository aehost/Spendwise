package com.spendwise.app.data.challenge

import com.spendwise.app.data.local.preferences.TokenManager
import java.time.LocalDate

data class DailyChallenge(
    val type: String,
    val title: String,
    val description: String,
    val emoji: String,
    val xpReward: Int,
    val savingsEstimate: String
)

object DailyChallengeManager {

    private val ALL_CHALLENGES = listOf(
        DailyChallenge("no_delivery",    "No Food Delivery",       "Cook at home or eat out instead of ordering in", "🍳", 25, "Save ₹200-400"),
        DailyChallenge("no_shopping",    "No Online Shopping",     "Resist all online purchase urges today", "🛍️", 25, "Save ₹500+"),
        DailyChallenge("walk_commute",   "Walk One Trip",          "Replace one Uber/Ola ride with walking or public transport", "🚶", 20, "Save ₹80-200"),
        DailyChallenge("no_cafe",        "Skip the Cafe",          "Make your own coffee or tea at home", "☕", 15, "Save ₹120-250"),
        DailyChallenge("log_all",        "Log Every Expense",      "Record every single spend today — cash, UPI, card", "📝", 30, "Awareness"),
        DailyChallenge("review_subs",    "Audit One Subscription", "Check if one subscription is still worth it", "📺", 20, "Potential ₹200/mo"),
        DailyChallenge("no_impulse",     "No Impulse Buys",        "Wait 24 hours before buying anything not planned", "🧘", 25, "Save ₹300+"),
        DailyChallenge("check_budget",   "Budget Check-In",        "Review your monthly budget and see where you stand", "📊", 20, "Awareness"),
        DailyChallenge("goal_contrib",   "Goal Contribution",      "Add any amount to one of your savings goals", "🎯", 35, "Progress"),
        DailyChallenge("bill_review",    "Review Upcoming Bills",  "Check which bills are due this week and plan for them", "📅", 15, "Avoid late fees"),
        DailyChallenge("spend_free",     "Spend-Free Day",         "Try to have zero non-essential spending today", "💎", 50, "Save 100% of budget"),
        DailyChallenge("pack_lunch",     "Pack Your Lunch",        "Bring food from home instead of buying lunch", "🥗", 20, "Save ₹150-300")
    )

    val XP_LEVELS = listOf(
        0    to "Saver",
        100  to "Planner",
        300  to "Investor",
        600  to "Wealth Builder",
        1000 to "Financial Master"
    )

    fun getTodayChallenge(tokenManager: TokenManager): DailyChallenge {
        val today = LocalDate.now().toString()
        val storedDate = tokenManager.dailyChallengeDate
        if (storedDate == today && tokenManager.dailyChallengeType.isNotBlank()) {
            val stored = ALL_CHALLENGES.find { it.type == tokenManager.dailyChallengeType }
            if (stored != null) return stored
        }
        // Pick based on day of year for variety
        val idx = (LocalDate.now().dayOfYear - 1) % ALL_CHALLENGES.size
        val challenge = ALL_CHALLENGES[idx]
        tokenManager.dailyChallengeDate = today
        tokenManager.dailyChallengeType = challenge.type
        tokenManager.dailyChallengeAccepted = false
        tokenManager.dailyChallengeCompleted = false
        return challenge
    }

    fun awardXp(tokenManager: TokenManager, xp: Int) {
        val newXp = tokenManager.userXp + xp
        tokenManager.userXp = newXp
        // Update level index
        val newLevelIdx = XP_LEVELS.filter { newXp >= it.first }.indices.last
        tokenManager.userLevel = newLevelIdx + 1
    }

    fun getLevelName(tokenManager: TokenManager): String {
        val xp = tokenManager.userXp
        return XP_LEVELS.filter { xp >= it.first }.maxByOrNull { it.first }?.second ?: "Saver"
    }

    fun getXpToNextLevel(tokenManager: TokenManager): Pair<Int, Int> {
        val xp = tokenManager.userXp
        val currentThreshold = XP_LEVELS.filter { xp >= it.first }.maxByOrNull { it.first }?.first ?: 0
        val nextThreshold = XP_LEVELS.firstOrNull { it.first > xp }?.first ?: (xp + 500)
        return Pair(xp - currentThreshold, nextThreshold - currentThreshold)
    }
}
