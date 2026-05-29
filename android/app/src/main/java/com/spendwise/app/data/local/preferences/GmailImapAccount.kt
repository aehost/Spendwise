package com.spendwise.app.data.local.preferences

data class GmailImapAccount(
    val email: String,
    val appPassword: String,
    val lastSyncMs: Long = 0L,
    val isActive: Boolean = true
)
