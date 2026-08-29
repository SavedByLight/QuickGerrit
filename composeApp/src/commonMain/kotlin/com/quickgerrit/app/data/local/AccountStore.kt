package com.quickgerrit.app.data.local

import com.quickgerrit.app.data.model.GerritAccount
import com.quickgerrit.app.platform.readPreferencesJson
import com.quickgerrit.app.platform.writePreferencesJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
private data class StoredPrefs(
    val accounts: List<GerritAccount> = emptyList(),
    val activeAccountId: String? = null
)

/**
 * Multiplatform account store backed by a JSON preferences file
 * (see [readPreferencesJson] / [writePreferencesJson]).
 */
class AccountStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val state = MutableStateFlow(load())

    val accounts: Flow<List<GerritAccount>> = state.map { it.accounts }
    val activeAccountId: Flow<String?> = state.map { it.activeAccountId }

    private fun load(): StoredPrefs {
        val raw = readPreferencesJson()
        if (raw.isBlank()) return StoredPrefs()
        return runCatching { json.decodeFromString<StoredPrefs>(raw) }.getOrElse { StoredPrefs() }
    }

    private suspend fun persist(prefs: StoredPrefs) {
        writePreferencesJson(json.encodeToString(prefs))
        state.value = prefs
    }

    suspend fun addAccount(account: GerritAccount): GerritAccount = mutex.withLock {
        val withId = if (account.id.isBlank()) account.copy(id = UUID.randomUUID().toString()) else account
        val current = state.value
        val updated = current.copy(
            accounts = current.accounts + withId,
            activeAccountId = if (current.accounts.isEmpty() || withId.isDefault) withId.id else current.activeAccountId
        )
        persist(updated)
        withId
    }

    suspend fun updateAccount(account: GerritAccount) = mutex.withLock {
        val current = state.value
        persist(
            current.copy(accounts = current.accounts.map { if (it.id == account.id) account else it })
        )
    }

    suspend fun removeAccount(id: String) = mutex.withLock {
        val current = state.value
        val updatedList = current.accounts.filter { it.id != id }
        val newActive = when {
            current.activeAccountId != id -> current.activeAccountId
            else -> updatedList.firstOrNull()?.id
        }
        persist(StoredPrefs(accounts = updatedList, activeAccountId = newActive))
    }

    suspend fun setActiveAccount(id: String) = mutex.withLock {
        persist(state.value.copy(activeAccountId = id))
    }
}
