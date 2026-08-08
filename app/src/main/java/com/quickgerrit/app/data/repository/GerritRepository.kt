package com.quickgerrit.app.data.repository

import com.quickgerrit.app.data.api.GerritApi
import com.quickgerrit.app.data.api.GerritClientFactory
import com.quickgerrit.app.data.local.AccountStore
import com.quickgerrit.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class GerritRepository(
    private val accountStore: AccountStore
) {
    val accounts: Flow<List<GerritAccount>> = accountStore.accounts
    val activeAccountId: Flow<String?> = accountStore.activeAccountId

    val activeAccount: Flow<GerritAccount?> = combine(accounts, activeAccountId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }

    private suspend fun api(): GerritApi {
        val account = activeAccount.first()
            ?: throw IllegalStateException("No account configured")
        return GerritClientFactory.create(account)
    }

    suspend fun testLogin(account: GerritAccount): AccountInfo {
        val client = GerritClientFactory.create(account)
        return client.getSelf()
    }

    suspend fun addAccount(account: GerritAccount) = accountStore.addAccount(account)
    suspend fun updateAccount(account: GerritAccount) = accountStore.updateAccount(account)
    suspend fun removeAccount(id: String) = accountStore.removeAccount(id)
    suspend fun setActive(id: String) = accountStore.setActiveAccount(id)

    suspend fun queryChanges(status: String, queryExtra: String = "", limit: Int = 50, start: Int = 0): List<ChangeInfo> {
        val q = buildString {
            append("status:$status")
            if (queryExtra.isNotBlank()) append(" $queryExtra")
        }
        return api().queryChanges(query = q, limit = limit, start = start)
    }

    suspend fun getChangeDetail(changeId: String): ChangeInfo {
        return api().getChangeDetail(changeId)
    }

    suspend fun listFiles(changeId: String, revisionId: String): Map<String, FileInfo> {
        return api().listFiles(changeId, revisionId)
    }

    suspend fun getDiff(changeId: String, revisionId: String, filePath: String): DiffInfo {
        return api().getDiff(changeId, revisionId, filePath)
    }

    suspend fun listComments(changeId: String): Map<String, List<CommentInfo>> {
        return api().listComments(changeId)
    }

    suspend fun listDrafts(changeId: String): Map<String, List<CommentInfo>> {
        return api().listDrafts(changeId)
    }

    suspend fun setReview(changeId: String, revisionId: String, input: ReviewInput): Any {
        return api().setReview(changeId, revisionId, input)
    }

    suspend fun abandon(changeId: String, message: String = ""): ChangeInfo {
        return api().abandon(changeId, if (message.isBlank()) emptyMap() else mapOf("message" to message))
    }

    suspend fun restore(changeId: String, message: String = ""): ChangeInfo {
        return api().restore(changeId, if (message.isBlank()) emptyMap() else mapOf("message" to message))
    }

    suspend fun submit(changeId: String): ChangeInfo {
        return api().submit(changeId)
    }

    suspend fun listProjects(): Map<String, ProjectInfo> {
        return api().listProjects()
    }
}
