package com.spendwise.app.runner

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom test runner that uses HiltTestApplication.
 * Required for Hilt dependency injection in instrumented tests.
 *
 * Register in build.gradle:
 *   defaultConfig { testInstrumentationRunner "com.spendwise.app.runner.HiltTestRunner" }
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
