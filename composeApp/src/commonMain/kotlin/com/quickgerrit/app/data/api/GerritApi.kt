package com.quickgerrit.app.data.api

import com.quickgerrit.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface GerritApi {

    // Accounts
    @GET("a/accounts/self")
    suspend fun getSelf(): AccountInfo

    // Changes
    @GET("a/changes/")
    suspend fun queryChanges(
        @Query("q") query: String,
        @Query("n") limit: Int = 50,
        @Query("S") start: Int = 0,
        @Query("o") options: List<String> = listOf(
            "LABELS",
            "CURRENT_REVISION",
            "DETAILED_ACCOUNTS",
            "SUBMITTABLE",
            "CHANGE_ACTIONS"
        )
    ): List<ChangeInfo>

    @GET("a/changes/{changeId}/detail")
    suspend fun getChangeDetail(
        @Path("changeId", encoded = true) changeId: String,
        @Query("o") options: List<String> = listOf(
            "ALL_REVISIONS",
            "ALL_COMMITS",
            "ALL_FILES",
            "DETAILED_LABELS",
            "DETAILED_ACCOUNTS",
            "MESSAGES",
            // REVIEWERS is not supported on older Gerrit (400: not a valid -o value).
            // Reviewer votes still appear via DETAILED_LABELS → labels.*.all.
            "REVIEWER_UPDATES",
            "SUBMITTABLE",
            "CHANGE_ACTIONS",
            "CURRENT_ACTIONS",
            "COMMIT_FOOTERS"
        )
    ): ChangeInfo

    /** Create an empty change (subject + project + branch). */
    @POST("a/changes/")
    suspend fun createChange(@Body input: ChangeInput): ChangeInfo

    @GET("a/changes/{changeId}/revisions/{revisionId}/files/")
    suspend fun listFiles(
        @Path("changeId", encoded = true) changeId: String,
        @Path("revisionId") revisionId: String
    ): Map<String, FileInfo>

    /**
     * Search all files in the revision tree (modified or not) by path substring.
     * Gerrit returns a JSON array of paths (typically capped ~20 matches).
     */
    @GET("a/changes/{changeId}/revisions/{revisionId}/files/")
    suspend fun searchRevisionFiles(
        @Path("changeId", encoded = true) changeId: String,
        @Path("revisionId") revisionId: String,
        @Query("q") query: String
    ): List<String>

    @GET("a/changes/{changeId}/revisions/{revisionId}/files/{fileId}/diff")
    suspend fun getDiff(
        @Path("changeId", encoded = true) changeId: String,
        @Path("revisionId") revisionId: String,
        @Path("fileId", encoded = true) fileId: String,
        @Query("context") context: String = "ALL",
        @Query("intraline") intraline: Boolean = true
    ): DiffInfo

    @GET("a/changes/{changeId}/comments")
    suspend fun listComments(
        @Path("changeId", encoded = true) changeId: String
    ): Map<String, List<CommentInfo>>

    @GET("a/changes/{changeId}/drafts")
    suspend fun listDrafts(
        @Path("changeId", encoded = true) changeId: String
    ): Map<String, List<CommentInfo>>

    @POST("a/changes/{changeId}/revisions/{revisionId}/review")
    suspend fun setReview(
        @Path("changeId", encoded = true) changeId: String,
        @Path("revisionId") revisionId: String,
        @Body input: ReviewInput
    ): Response<Unit>

    @POST("a/changes/{changeId}/abandon")
    suspend fun abandon(
        @Path("changeId", encoded = true) changeId: String,
        @Body body: Map<String, String> = emptyMap()
    ): ChangeInfo

    @POST("a/changes/{changeId}/restore")
    suspend fun restore(
        @Path("changeId", encoded = true) changeId: String,
        @Body body: Map<String, String> = emptyMap()
    ): ChangeInfo

    @POST("a/changes/{changeId}/submit")
    suspend fun submit(
        @Path("changeId", encoded = true) changeId: String,
        @Body body: SubmitInput = SubmitInput()
    ): ChangeInfo

    /** Mark change as Work-In-Progress. */
    @POST("a/changes/{changeId}/wip")
    suspend fun setWorkInProgress(
        @Path("changeId", encoded = true) changeId: String,
        @Body body: Map<String, String> = emptyMap()
    ): Response<Unit>

    /** Mark change as Ready for Review (active). */
    @POST("a/changes/{changeId}/ready")
    suspend fun setReadyForReview(
        @Path("changeId", encoded = true) changeId: String,
        @Body body: Map<String, String> = emptyMap()
    ): Response<Unit>

    // Projects
    @GET("a/projects/")
    suspend fun listProjects(
        @Query("d") description: Boolean = true,
        @Query("t") tree: Boolean = false,
        @Query("type") type: String = "ALL"
    ): Map<String, ProjectInfo>

    /**
     * List branches for a project.
     * Gerrit returns a JSON array of [BranchInfo] (not a map).
     */
    @GET("a/projects/{project}/branches/")
    suspend fun listBranches(
        @Path("project", encoded = true) project: String
    ): List<BranchInfo>

    /** Create a branch. [branch] is the short name (e.g. "feature/foo"). */
    @PUT("a/projects/{project}/branches/{branch}")
    suspend fun createBranch(
        @Path("project", encoded = true) project: String,
        @Path("branch", encoded = true) branch: String,
        @Body input: BranchInput
    ): BranchInfo

    // —— File content & change edit (in-app editor) ——

    /** File content is base64-encoded plain text (or binary). */
    @GET("a/changes/{changeId}/revisions/{revisionId}/files/{fileId}/content")
    suspend fun getFileContent(
        @Path("changeId", encoded = true) changeId: String,
        @Path("revisionId") revisionId: String,
        @Path("fileId", encoded = true) fileId: String
    ): Response<okhttp3.ResponseBody>

    /**
     * Content of any file at the tip of a branch (not limited to files already in a change).
     * Base64 body.
     */
    @GET("a/projects/{project}/branches/{branch}/files/{fileId}/content")
    suspend fun getBranchFileContent(
        @Path("project", encoded = true) project: String,
        @Path("branch", encoded = true) branch: String,
        @Path("fileId", encoded = true) fileId: String
    ): Response<okhttp3.ResponseBody>

    /**
     * Put file content into the change edit (creates the edit if needed).
     * Body is raw file bytes / text; Content-Type text/plain.
     */
    @PUT("a/changes/{changeId}/edit/{fileId}")
    suspend fun putEditFile(
        @Path("changeId", encoded = true) changeId: String,
        @Path("fileId", encoded = true) fileId: String,
        @Body body: okhttp3.RequestBody
    ): Response<Unit>

    /**
     * Current change edit, if any.
     * 204 / empty when no edit exists; 200 + EditInfo when it does.
     */
    @GET("a/changes/{changeId}/edit")
    suspend fun getEdit(
        @Path("changeId", encoded = true) changeId: String
    ): Response<EditInfo>

    /** Commit message of the current change edit (plain text). */
    @GET("a/changes/{changeId}/edit:message")
    suspend fun getEditMessage(
        @Path("changeId", encoded = true) changeId: String
    ): Response<okhttp3.ResponseBody>

    /** Publish change edit as a new patch set. */
    @POST("a/changes/{changeId}/edit:publish")
    suspend fun publishEdit(
        @Path("changeId", encoded = true) changeId: String,
        @Body body: Map<String, String> = emptyMap()
    ): Response<Unit>

    /** Delete the current change edit. */
    @DELETE("a/changes/{changeId}/edit")
    suspend fun deleteEdit(
        @Path("changeId", encoded = true) changeId: String
    ): Response<Unit>

    /** Set change topic. */
    @PUT("a/changes/{changeId}/topic")
    suspend fun setTopic(
        @Path("changeId", encoded = true) changeId: String,
        @Body body: Map<String, String>
    ): String

    /** Modify commit message inside the change edit. */
    @PUT("a/changes/{changeId}/edit:message")
    suspend fun putEditMessage(
        @Path("changeId", encoded = true) changeId: String,
        @Body body: Map<String, String>
    ): Response<Unit>
}
