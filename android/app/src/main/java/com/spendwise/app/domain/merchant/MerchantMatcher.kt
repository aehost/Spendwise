package com.spendwise.app.domain.merchant

import com.spendwise.app.domain.merchant.MerchantDatabase.MerchantTag
import com.spendwise.app.domain.merchant.MerchantDatabase.MatchType
import kotlin.math.max
import kotlin.math.min

/**
 * Five-tier pure-text merchant classifier — no location required.
 *
 *  0. User corrections (self-learning)      — highest priority
 *  1. Exact match  → merchantDb.EXACT_MAP
 *  2. UPI domain   → merchantDb.UPI_DOMAIN_MAP  (e.g. "swiggy@upi" → food)
 *  3. Jaro-Winkler fuzzy matching (threshold 0.82)
 *  4. SmartCategoryEngine — MCC code detection, instant patterns, structured SMS
 *     parsing, 600+ weighted keywords, phonetic regex, bigram n-gram, amount heuristics
 *  5. Default → "other"
 */
object MerchantMatcher {

    /**
     * Synchronous classification (Tiers 0-4 + fallback).
     * @param rawMerchant      Merchant name as received from the SMS / transaction
     * @param smsBody          Optional full SMS body for additional context
     * @param amount           Optional transaction amount (used for fuel/food heuristics)
     * @param userCorrections  Optional map of merchant-key → categorySlug from [CategoryCorrectionStore].
     *                         Pass [CategoryCorrectionStore.getSnapshot()] at call site.
     */
    fun classify(
        rawMerchant: String,
        smsBody: String? = null,
        amount: Double? = null,
        userCorrections: Map<String, String>? = null
    ): MerchantTag {
        val normalized = rawMerchant.trim().lowercase()
        if (normalized.isEmpty()) return MerchantDatabase.DEFAULT_TAG

        // ── Tier 0: User corrections (self-learning) ──────────────
        if (!userCorrections.isNullOrEmpty()) {
            userCorrections[normalized]?.let { slug ->
                return MerchantTag(slug, slug, null, 1.0f, MatchType.EXACT)
            }
            for ((key, slug) in userCorrections) {
                if (normalized.contains(key) || key.contains(normalized)) {
                    return MerchantTag(slug, slug, null, 0.95f, MatchType.EXACT)
                }
            }
        }

        // ── Tier 1: Exact match ───────────────────────────────────
        MerchantDatabase.EXACT_MAP[normalized]?.let { return it }

        // Try partial exact matches (e.g. "SWIGGY ORDER" → "swiggy")
        for ((key, tag) in MerchantDatabase.EXACT_MAP) {
            if (normalized.contains(key) || key.contains(normalized)) return tag
        }

        // ── Tier 2: UPI domain ────────────────────────────────────
        // Pattern: merchant@upihandle  or  VPA xxx@yyy  or  UPI/xxx@yyy
        val upiVpa = extractUpiVpa(rawMerchant) ?: extractUpiVpa(smsBody ?: "")
        if (upiVpa != null) {
            val domain = upiVpa.substringBefore('@').lowercase()
            MerchantDatabase.UPI_DOMAIN_MAP[domain]?.let { return it }

            // Also check as key in EXACT_MAP
            MerchantDatabase.EXACT_MAP[domain]?.let { return it }
        }

        // ── Tier 3: Jaro-Winkler fuzzy ───────────────────────────
        var bestScore = 0.0
        var bestTag: MerchantTag? = null
        for ((key, tag) in MerchantDatabase.EXACT_MAP) {
            val score = jaroWinkler(normalized, key)
            if (score > bestScore) { bestScore = score; bestTag = tag }
        }
        if (bestScore >= 0.82 && bestTag != null) {
            return bestTag.copy(
                confidence = bestScore.toFloat() * 0.9f,
                matchType = MatchType.FUZZY
            )
        }

        // ── Tier 4: SmartCategoryEngine ──────────────────────────
        // 600+ keywords, phonetic variants, n-gram similarity, amount heuristics
        SmartCategoryEngine.classify(rawMerchant, smsBody, amount)?.let { return it }

        // ── Tier 5: Default ───────────────────────────────────────
        return MerchantDatabase.DEFAULT_TAG
    }

    // ── UPI VPA extractor ─────────────────────────────────────────
    private val UPI_VPA_REGEX = Regex(
        """(?:VPA[:\s]+|UPI(?:/[A-Z]+)?[:\s]+|paid to\s+|to\s+)?([a-zA-Z0-9._-]+@[a-zA-Z0-9._-]+)""",
        RegexOption.IGNORE_CASE
    )

    private fun extractUpiVpa(text: String): String? =
        UPI_VPA_REGEX.find(text)?.groupValues?.get(1)

    // ── Jaro-Winkler distance ─────────────────────────────────────
    private fun jaroWinkler(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val l1 = s1.length; val l2 = s2.length
        if (l1 == 0 || l2 == 0) return 0.0

        val matchDistance = max(l1, l2) / 2 - 1
        val s1Matches = BooleanArray(l1); val s2Matches = BooleanArray(l2)
        var matches = 0; var transpositions = 0

        for (i in 0 until l1) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, l2)
            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true; s2Matches[j] = true; matches++; break
            }
        }
        if (matches == 0) return 0.0

        var k = 0
        for (i in 0 until l1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val jaro = (matches.toDouble() / l1 + matches.toDouble() / l2 +
            (matches - transpositions / 2.0) / matches) / 3.0

        // Winkler prefix bonus (up to 4 common prefix chars)
        var prefix = 0
        for (i in 0 until min(4, min(l1, l2))) {
            if (s1[i] != s2[i]) break
            prefix++
        }
        return jaro + prefix * 0.1 * (1 - jaro)
    }

    @Suppress("DEPRECATION")
    private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
