package com.quickgerrit.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountInfo(
    @SerialName("_account_id") val accountId: Int = 0,
    val name: String? = null,
    val email: String? = null,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val avatars: List<AvatarInfo>? = null
)

@Serializable
data class AvatarInfo(
    val url: String? = null,
    val height: Int? = null
)

@Serializable
data class ChangeInfo(
    val id: String = "",
    val project: String = "",
    val branch: String = "",
    val topic: String? = null,
    @SerialName("change_id") val changeId: String = "",
    val subject: String = "",
    val status: String = "", // NEW, MERGED, ABANDONED
    val created: String = "",
    val updated: String = "",
    val insertions: Int = 0,
    val deletions: Int = 0,
    @SerialName("_number") val number: Int = 0,
    val owner: AccountInfo? = null,
    val labels: Map<String, LabelInfo>? = null,
    val reviewers: Map<String, List<AccountInfo>>? = null,
    val messages: List<ChangeMessageInfo>? = null,
    @SerialName("current_revision") val currentRevision: String? = null,
    val revisions: Map<String, RevisionInfo>? = null,
    val actions: Map<String, ActionInfo>? = null,
    val mergeable: Boolean? = null,
    val submittable: Boolean? = null,
    @SerialName("work_in_progress") val workInProgress: Boolean? = null,
    val starred: Boolean? = null,
    val reviewed: Boolean? = null
)

@Serializable
data class LabelInfo(
    val approved: AccountInfo? = null,
    val rejected: AccountInfo? = null,
    val recommended: AccountInfo? = null,
    val disliked: AccountInfo? = null,
    val blocking: Boolean? = null,
    val value: Int? = null,
    val defaultValue: Int? = null,
    val values: Map<String, String>? = null,
    val all: List<ApprovalInfo>? = null,
    val optional: Boolean? = null
)

@Serializable
data class ApprovalInfo(
    @SerialName("_account_id") val accountId: Int = 0,
    val name: String? = null,
    val email: String? = null,
    val value: Int? = null,
    val date: String? = null,
    val tag: String? = null
)

@Serializable
data class RevisionInfo(
    val kind: String? = null,
    @SerialName("_number") val number: Int = 0,
    val created: String = "",
    val uploader: AccountInfo? = null,
    val ref: String? = null,
    val commit: CommitInfo? = null,
    val files: Map<String, FileInfo>? = null,
    val actions: Map<String, ActionInfo>? = null,
    val description: String? = null
)

@Serializable
data class CommitInfo(
    val commit: String = "",
    val parents: List<CommitInfo>? = null,
    val author: GitPersonInfo? = null,
    val committer: GitPersonInfo? = null,
    val subject: String = "",
    val message: String = ""
)

@Serializable
data class GitPersonInfo(
    val name: String = "",
    val email: String = "",
    val date: String = ""
)

@Serializable
data class FileInfo(
    val status: String? = null, // A, D, R, C, M
    @SerialName("lines_inserted") val linesInserted: Int? = null,
    @SerialName("lines_deleted") val linesDeleted: Int? = null,
    @SerialName("size_delta") val sizeDelta: Int? = null,
    val size: Int? = null,
    @SerialName("old_path") val oldPath: String? = null
)

@Serializable
data class ChangeMessageInfo(
    val id: String = "",
    val author: AccountInfo? = null,
    val date: String = "",
    val message: String = "",
    val tag: String? = null,
    @SerialName("_revision_number") val revisionNumber: Int? = null
)

@Serializable
data class ActionInfo(
    val method: String? = null,
    val label: String? = null,
    val title: String? = null,
    val enabled: Boolean? = null
)

@Serializable
data class ProjectInfo(
    val id: String = "",
    val name: String = "",
    val parent: String? = null,
    val description: String? = null,
    val state: String? = null,
    @SerialName("web_links") val webLinks: List<WebLinkInfo>? = null
)

@Serializable
data class WebLinkInfo(
    val name: String? = null,
    val url: String? = null
)

@Serializable
data class CommentInfo(
    val id: String = "",
    val path: String? = null,
    @SerialName("patch_set") val patchSet: Int? = null,
    val side: String? = null,
    val line: Int? = null,
    val range: CommentRange? = null,
    @SerialName("in_reply_to") val inReplyTo: String? = null,
    val message: String = "",
    val updated: String = "",
    val author: AccountInfo? = null,
    val unresolved: Boolean? = null,
    val tag: String? = null
)

@Serializable
data class CommentRange(
    val startLine: Int = 0,
    val startCharacter: Int = 0,
    val endLine: Int = 0,
    val endCharacter: Int = 0
)

@Serializable
data class DiffInfo(
    @SerialName("meta_a") val metaA: DiffFileMetaInfo? = null,
    @SerialName("meta_b") val metaB: DiffFileMetaInfo? = null,
    @SerialName("change_type") val changeType: String? = null,
    val content: List<DiffContent>? = null
)

@Serializable
data class DiffFileMetaInfo(
    val name: String = "",
    @SerialName("content_type") val contentType: String? = null,
    val lines: Int = 0
)

@Serializable
data class DiffContent(
    val a: List<String>? = null,
    val b: List<String>? = null,
    val ab: List<String>? = null,
    @SerialName("skip") val skip: Int? = null
)

@Serializable
data class ReviewInput(
    val message: String? = null,
    val labels: Map<String, Int>? = null,
    val comments: Map<String, List<CommentInput>>? = null,
    val drafts: String? = null, // PUBLISH, PUBLISH_ALL_REVISIONS, KEEP
    val strictLabels: Boolean? = null,
    val reviewers: List<ReviewerInput>? = null,
    @SerialName("ready") val ready: Boolean? = null,
    @SerialName("work_in_progress") val workInProgress: Boolean? = null
)

@Serializable
data class CommentInput(
    val id: String? = null,
    val path: String? = null,
    val side: String? = null,
    val line: Int? = null,
    val range: CommentRange? = null,
    @SerialName("in_reply_to") val inReplyTo: String? = null,
    val message: String = "",
    val unresolved: Boolean? = null
)

@Serializable
data class ReviewerInput(
    val reviewer: String = "",
    val state: String? = null // REVIEWER, CC
)

// Local account model
@Serializable
data class GerritAccount(
    val id: String, // uuid
    val name: String,
    val baseUrl: String, // e.g. https://gerrit.example.com
    val username: String,
    val httpPassword: String,
    val isDefault: Boolean = false
)
