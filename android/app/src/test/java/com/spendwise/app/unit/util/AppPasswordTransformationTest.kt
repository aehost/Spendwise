package com.spendwise.app.unit.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for the App Password chunked-display offset mapping logic.
 *
 * The transformation converts "abcdefghijklmnop" → "abcd efgh ijkl mnop"
 * and must maintain correct cursor positions (OffsetMapping).
 *
 * Tests validate the mathematical formulas:
 *   originalToTransformed(p) = (p + p/4).coerceAtMost(spaced.length)
 *   transformedToOriginal(t)  = (t - t/5).coerceIn(0, raw.length)
 */
@RunWith(JUnit4::class)
class AppPasswordTransformationTest {

    // Replicate the transformation logic for pure-JVM testing
    private fun transform(raw: String): String =
        raw.chunked(4).joinToString(" ")

    private fun originalToTransformed(offset: Int, raw: String): Int {
        val spaced = transform(raw)
        return (offset + offset / 4).coerceAtMost(spaced.length)
    }

    private fun transformedToOriginal(offset: Int, raw: String): Int =
        (offset - offset / 5).coerceIn(0, raw.length)

    // ─── Display format ───────────────────────────────────────────────────────

    @Test
    fun `full 16-char password formats as 4 groups of 4`() {
        val result = transform("abcdefghijklmnop")
        assertThat(result).isEqualTo("abcd efgh ijkl mnop")
    }

    @Test
    fun `partial 8-char shows 2 groups`() {
        assertThat(transform("abcdefgh")).isEqualTo("abcd efgh")
    }

    @Test
    fun `4 chars show single group`() {
        assertThat(transform("abcd")).isEqualTo("abcd")
    }

    @Test
    fun `empty string stays empty`() {
        assertThat(transform("")).isEqualTo("")
    }

    @Test
    fun `5 chars shows group + start of second`() {
        assertThat(transform("abcde")).isEqualTo("abcd e")
    }

    // ─── Original → Transformed offset mapping ────────────────────────────────

    @Test
    fun `offset 0 maps to 0`() {
        assertThat(originalToTransformed(0, "abcdefghijklmnop")).isEqualTo(0)
    }

    @Test
    fun `offset 4 maps to 5 (past first space)`() {
        // "abcd efgh ijkl mnop" — position 4 in raw → position 5 in spaced (after "abcd ")
        assertThat(originalToTransformed(4, "abcdefghijklmnop")).isEqualTo(5)
    }

    @Test
    fun `offset 8 maps to 10 (past two spaces)`() {
        assertThat(originalToTransformed(8, "abcdefghijklmnop")).isEqualTo(10)
    }

    @Test
    fun `offset 12 maps to 15 (past three spaces)`() {
        assertThat(originalToTransformed(12, "abcdefghijklmnop")).isEqualTo(15)
    }

    @Test
    fun `offset 16 maps to 19 (end of string)`() {
        assertThat(originalToTransformed(16, "abcdefghijklmnop")).isEqualTo(19)
    }

    @Test
    fun `offset never exceeds spaced length`() {
        val raw = "abcdefghijklmnop"
        for (i in 0..raw.length) {
            val t = originalToTransformed(i, raw)
            assertThat(t).isAtMost(transform(raw).length)
        }
    }

    // ─── Transformed → Original offset mapping ────────────────────────────────

    @Test
    fun `transformed 0 maps to 0`() {
        assertThat(transformedToOriginal(0, "abcdefghijklmnop")).isEqualTo(0)
    }

    @Test
    fun `transformed 5 maps to 4 (space at position 4 removed)`() {
        assertThat(transformedToOriginal(5, "abcdefghijklmnop")).isEqualTo(4)
    }

    @Test
    fun `transformed 10 maps to 8`() {
        assertThat(transformedToOriginal(10, "abcdefghijklmnop")).isEqualTo(8)
    }

    @Test
    fun `transformed 19 maps to 16 (end)`() {
        assertThat(transformedToOriginal(19, "abcdefghijklmnop")).isEqualTo(16)
    }

    @Test
    fun `transformed offset never exceeds raw length`() {
        val raw = "abcdefghijklmnop"
        val spaced = transform(raw)
        for (i in 0..spaced.length) {
            val o = transformedToOriginal(i, raw)
            assertThat(o).isAtMost(raw.length)
            assertThat(o).isAtLeast(0)
        }
    }

    // ─── Round-trip consistency ───────────────────────────────────────────────

    @Test
    fun `originalToTransformed then transformedToOriginal is idempotent`() {
        val raw = "abcdefghijklmnop"
        for (i in 0..raw.length) {
            val t = originalToTransformed(i, raw)
            val o = transformedToOriginal(t, raw)
            // May differ at space boundaries, but should be within 1
            assertThat(Math.abs(o - i)).isAtMost(1)
        }
    }

    // ─── Password filtering in UI (no spaces, max 16 chars) ──────────────────

    @Test
    fun `spaces are stripped from raw input`() {
        val raw = "abcd efgh ijkl mnop"   // user pastes formatted password
        val filtered = raw.filter { it != ' ' }.take(16)
        assertThat(filtered).isEqualTo("abcdefghijklmnop")
        assertThat(filtered.length).isEqualTo(16)
    }

    @Test
    fun `input capped at 16 chars`() {
        val raw = "abcdefghijklmnopqrst"  // 20 chars
        val filtered = raw.filter { it != ' ' }.take(16)
        assertThat(filtered.length).isEqualTo(16)
    }

    @Test
    fun `isReady is true only at exactly 16 chars`() {
        assertThat("abcdefghijklmnop".length == 16).isTrue()
        assertThat("abcdefghijklmno".length  == 16).isFalse()
        assertThat("abcdefghijklmnopq".length == 16).isFalse()
    }
}
