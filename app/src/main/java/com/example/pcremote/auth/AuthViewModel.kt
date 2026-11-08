package com.example.pcremote.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthState(
    val user: FirebaseUser? = null,
    val loading: Boolean = true,
    val error: String? = null
)

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        auth.addAuthStateListener { a ->
            _state.update { it.copy(user = a.currentUser, loading = false) }
        }
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message ?: "Ошибка входа") }
        }
    }

    fun register(email: String, password: String) = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        try {
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message ?: "Ошибка регистрации") }
        }
    }

    fun loginWithGoogle(idToken: String) = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val cred = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(cred).await()
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message ?: "Ошибка Google-входа") }
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
