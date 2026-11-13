package com.monitorung.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monitorung.app.domain.model.User
import com.monitorung.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val passwordChangeSuccess: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { user ->
                _uiState.update { it.copy(user = user) }
            }
        }
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.fetchProfile()
                .onSuccess { user ->
                    _uiState.update { it.copy(user = user, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun updateProfile(
        fullName: String?,
        email: String?,
        phone: String?,
        bio: String?,
        avatarUrl: String?,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, saveSuccess = false) }
            authRepository.updateProfile(fullName, email, phone, avatarUrl, bio)
                .onSuccess { user ->
                    _uiState.update { it.copy(user = user, isSaving = false, saveSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    fun changePassword(current: String, new: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, passwordChangeSuccess = false) }
            authRepository.changePassword(current, new)
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, passwordChangeSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSaveSuccess() = _uiState.update { it.copy(saveSuccess = false) }
    fun clearPasswordSuccess() = _uiState.update { it.copy(passwordChangeSuccess = false) }
}
