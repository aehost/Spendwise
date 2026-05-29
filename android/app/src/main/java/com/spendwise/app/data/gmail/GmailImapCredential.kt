package com.spendwise.app.data.gmail

/**
 * Alias for storing Gmail IMAP credentials.
 * The actual storage uses [com.spendwise.app.data.local.preferences.GmailImapAccount].
 */
data class GmailImapCredential(
    val email: String,
    val appPassword: String   // Google App Password (16 chars, no spaces)
)
