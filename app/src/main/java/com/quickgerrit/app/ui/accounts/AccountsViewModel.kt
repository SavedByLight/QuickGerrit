package com.quickgerrit.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quickgerrit.app.data.model.GerritAccount
import com.quickgerrit.app.data.repository.GerritRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountsUiState(
    val accounts: List<GerritAccount> = emptyList(),
    val activeId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val testing: Boolean = false
)

class AccountsViewModel(private val repo: GerritRepository) : ViewModel() {

    private val _ui = MutableStateFlow(AccountsUiState())
    val ui: StateFlow<AccountsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.accounts.collect { list ->
                _ui.update { it.copy(accounts = list) }
            }
        }
        viewModelScope.launch {
            repo.activeAccountId.collect { id ->
                _ui.update { it.copy(activeId = id) }
            }
        }
    }

    fun addOrUpdate(account: GerritAccount, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null, testing = true) }
            try {
                val self = repo.testLogin(account)
                val name = account.name.ifBlank { self.displayName ?: self.name ?: account.username }
                val saved = if (account.id.isBlank()) {
                    repo.addAccount(account.copy(name = name))
                } else {
                    repo.updateAccount(account.copy(name = name))
                    account.copy(name = name)
                }
                if (account.isDefault || _ui.value.accounts.isEmpty()) {
                    repo.setActive(saved.id)
                }
                _ui.update { it.copy(isLoading = false, testing = false) }
                onSuccess()
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        isLoading = false,
                        testing = false,
                        error = e.message ?: "Login failed. Check URL, username and HTTP password."
                    )
                }
            }
        }
    }

    fun setActive(id: String) {
        viewModelScope.launch { repo.setActive(id) }
    }

    fun remove(id: String) {
        viewModelScope.launch { repo.removeAccount(id) }
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }

    class Factory(private val repo: GerritRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountsViewModel(repo) as T
    }
}
