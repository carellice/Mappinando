package it.mappinando.app.data

import android.content.Context

class AppPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("mappinando_experience", Context.MODE_PRIVATE)
    val onboardingComplete: Boolean get() = preferences.getBoolean("onboarding.complete.v1", false)
    fun completeOnboarding() { preferences.edit().putBoolean("onboarding.complete.v1", true).apply() }
}
