package com.monitorung.app.presentation.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding_prefs")

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private object PrefsKeys {
        val HAS_ACCEPTED_TERMS = booleanPreferencesKey("has_accepted_terms")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val hasAcceptedTerms: StateFlow<Boolean?> = context.onboardingDataStore.data
        .map { prefs ->
            try {
                prefs[PrefsKeys.HAS_ACCEPTED_TERMS]
            } catch (e: Exception) {
                null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Drives the outer AuthNavigation gate. Distinguishes "finished the full
     * onboarding flow" from "saw the terms once but never reached the
     * permissions step" — so a user who clicked Setuju but crashed before
     * completing permissions lands back on PermissionScreen, not LoginScreen.
     */
    val isOnboardingCompleted: StateFlow<Boolean?> = context.onboardingDataStore.data
        .map { prefs ->
            try {
                prefs[PrefsKeys.ONBOARDING_COMPLETED]
            } catch (e: Exception) {
                null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun acceptTerms() {
        viewModelScope.launch {
            context.onboardingDataStore.edit { prefs ->
                prefs[PrefsKeys.HAS_ACCEPTED_TERMS] = true
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            context.onboardingDataStore.edit { prefs ->
                prefs[PrefsKeys.HAS_ACCEPTED_TERMS] = true
                prefs[PrefsKeys.ONBOARDING_COMPLETED] = true
            }
        }
    }
}
