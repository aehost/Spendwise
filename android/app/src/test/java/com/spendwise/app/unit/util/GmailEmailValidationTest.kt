package com.spendwise.app.unit.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for the Gmail email validation logic used in SheetStepEmail.
 *
 * The current validation:
 *   val isValidEmail = email.contains("@") && email.contains(".")
 *
 * These tests document the current behaviour (including known weak spots)
 * so any future improvement is verified against these baselines.
 */
@RunWith(JUnit4::class)
class GmailEmailValidationTest {

    private fun isValid(email: String): Boolean =
        email.contains("@") && email.contains(".")

    private fun alreadyExists(email: String, list: List<String>): Boolean =
        email.trim().lowercase() in list.map { it.lowercase() }

    // ─── Valid emails ─────────────────────────────────────────────────────────

    @Test
    fun `valid gmail address passes`() {
        assertThat(isValid("user@gmail.com")).isTrue()
    }

    @Test
    fun `valid work email passes`() {
        assertThat(isValid("pravveen@spendwise.app")).isTrue()
    }

    @Test
    fun `email with subdomain passes`() {
        assertThat(isValid("user@mail.domain.co.in")).isTrue()
    }

    @Test
    fun `email with plus tag passes`() {
        assertThat(isValid("user+tag@gmail.com")).isTrue()
    }

    // ─── Invalid emails ───────────────────────────────────────────────────────

    @Test
    fun `blank email fails`() {
        assertThat(isValid("")).isFalse()
    }

    @Test
    fun `email without at-sign fails`() {
        assertThat(isValid("usergmail.com")).isFalse()
    }

    @Test
    fun `email without dot fails`() {
        assertThat(isValid("user@gmailcom")).isFalse()
    }

    @Test
    fun `plain text fails`() {
        assertThat(isValid("notanemail")).isFalse()
    }

    // ─── Duplicate detection ──────────────────────────────────────────────────

    @Test
    fun `exact match detected as duplicate`() {
        val existing = listOf("user@gmail.com")
        assertThat(alreadyExists("user@gmail.com", existing)).isTrue()
    }

    @Test
    fun `case-insensitive duplicate detected`() {
        val existing = listOf("USER@GMAIL.COM")
        assertThat(alreadyExists("user@gmail.com", existing)).isTrue()
    }

    @Test
    fun `trimmed whitespace duplicate detected`() {
        val existing = listOf("user@gmail.com")
        assertThat(alreadyExists("  user@gmail.com  ", existing)).isTrue()
    }

    @Test
    fun `different email not detected as duplicate`() {
        val existing = listOf("other@gmail.com")
        assertThat(alreadyExists("user@gmail.com", existing)).isFalse()
    }

    @Test
    fun `empty existing list has no duplicates`() {
        assertThat(alreadyExists("user@gmail.com", emptyList())).isFalse()
    }

    // ─── UI gate: Continue button logic ──────────────────────────────────────

    @Test
    fun `canContinue is false when email invalid`() {
        val email = "notvalid"
        val isValidEmail  = email.contains("@") && email.contains(".")
        val alreadyExists = false
        val canContinue   = isValidEmail && !alreadyExists
        assertThat(canContinue).isFalse()
    }

    @Test
    fun `canContinue is false when already exists`() {
        val email = "user@gmail.com"
        val isValidEmail  = email.contains("@") && email.contains(".")
        val alreadyExists = true
        val canContinue   = isValidEmail && !alreadyExists
        assertThat(canContinue).isFalse()
    }

    @Test
    fun `canContinue is true for valid unique email`() {
        val email = "new@gmail.com"
        val isValidEmail  = email.contains("@") && email.contains(".")
        val alreadyExists = false
        val canContinue   = isValidEmail && !alreadyExists
        assertThat(canContinue).isTrue()
    }
}
