package com.quickgerrit.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quickgerrit.app.data.model.GerritAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quickgerrit_accounts")

class AccountStore(private val context: Context) {

    private val accountsKey = stringPreferencesKey("accounts_json")
    private val activeIdKey = stringPreferencesKey("active_account_id")
    private val json = Json { ignoreUnknownKeys = true }

    val accounts: Flow<List<GerritAccount>> = context.dataStore.data.map { prefs ->
        val raw = prefs[accountsKey] ?: "[]"
        runCatching { json.decodeFromString<List<GerritAccount>>(raw) }.getOrElse { emptyList() }
    }

    val activeAccountId: Flow<String?> = context.dataStore.data.map { it[activeIdKey] }

    suspend fun addAccount(account: GerritAccount): GerritAccount {
        val withId = if (account.id.isBlank()) account.copy(id = UUID.randomUUID().toString()) else account
        context.dataStore.edit { prefs ->
            val current = runCatching {
                json.decodeFromString<List<GerritAccount>>(prefs[accountsKey] ?: "[]")
            }.getOrElse { emptyList() }
            val updated = current + withId
            prefs[accountsKey] = json.encodeToString(updated)
            if (current.isEmpty() || withId.isDefault) {
                prefs[activeIdKey] = withId.id
            }
        }
        return withId
    }

    suspend fun updateAccount(account: GerritAccount) {
        context.dataStore.edit { prefs ->
            val current = runCatching {
                json.decodeFromString<List<GerritAccount>>(prefs[accountsKey] ?: "[]")
            }.getOrElse { emptyList() }
            val updated = current.map { if (it.id == account.id) account else it }
            prefs[accountsKey] = json.encodeToString(updated)
        }
    }

    suspend fun removeAccount(id: String) {
        context.dataStore.edit { prefs ->
            val current = runCatching {
                json.decodeFromString<List<GerritAccount>>(prefs[accountsKey] ?: "[]")
            }.getOrElse { emptyList() }
            val updated = current.filter { it.id != id }
            prefs[accountsKey] = json.encodeToString(updated)
            if (prefs[activeIdKey] == id) {
                prefs[activeIdKey] = updated.firstOrNull()?.id
            }
        }
    }

    suspend fun setActiveAccount(id: String) {
        context.dataStore.edit { it[activeIdKey] = id }
    }
}
