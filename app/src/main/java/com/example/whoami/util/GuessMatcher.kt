package com.example.whoami.util

/**
 * Forgiving name matching so a guess like "spiderman", "Spider Man" or
 * "Son Goku" vs "Goku" isn't marked wrong over punctuation, spacing or one typo.
 * Tune the thresholds here if it feels too strict or too generous.
 */
object GuessMatcher {

    fun isMatch(guess: String, answer: String): Boolean {
        val g = normalize(guess)
        val a = normalize(answer)
        if (g.isEmpty() || a.isEmpty()) return false
        if (g == a) return true

        // One contains the other ("goku" vs "songoku"), only for reasonably long strings
        if (minOf(g.length, a.length) >= 4 && (a.contains(g) || g.contains(a))) return true

        // A single typo is fine on longer names ("beyonce" vs "beyoncé")
        if (g.length >= 5 && a.length >= 5 && editDistance(g, a) <= 1) return true

        return false
    }

    /** Lowercase, keep only letters/digits (works for non-Latin scripts too). */
    private fun normalize(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private fun editDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = temp
            }
        }
        return dp[b.length]
    }
}
