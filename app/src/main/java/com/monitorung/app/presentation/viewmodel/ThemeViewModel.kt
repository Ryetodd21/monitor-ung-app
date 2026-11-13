package com.monitorung.app.presentation.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monitorung.app.presentation.ui.theme.ThemeVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

@HiltViewModel
class ThemeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _themeVariant = MutableStateFlow<ThemeVariant>(ThemeVariant.LIGHT)
    val themeVariant: StateFlow<ThemeVariant> = _themeVariant.asStateFlow()

    init {
        viewModelScope.launch {
            context.themeDataStore.data.collect { prefs ->
                _themeVariant.value = when (prefs[KEY_THEME]) {
                    "dark" -> ThemeVariant.DARK
                    "amoled" -> ThemeVariant.AMOLED
                    else -> ThemeVariant.LIGHT
                }
            }
        }
    }

    fun setTheme(variant: ThemeVariant) {
        viewModelScope.launch {
            context.themeDataStore.edit { prefs ->
                prefs[KEY_THEME] = when (variant) {
                    ThemeVariant.LIGHT -> "light"
                    ThemeVariant.DARK -> "dark"
                    ThemeVariant.AMOLED -> "amoled"
                }
            }
        }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_variant")
    }
}
