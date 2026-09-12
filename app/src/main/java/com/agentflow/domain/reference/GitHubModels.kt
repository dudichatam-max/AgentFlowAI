package com.agentflow.domain.reference

data class GitHubLocator(
    val owner: String,
    val repo: String,
    val ref: String = "main",
    val path: String = "",
    val url: String,
)

data class GitHubTreeEntry(
    val path: String,
    val size: Long,
    val sha: String?,
    val type: String,
)

data class GitHubFileContent(
    val path: String,
    val sha: String?,
    val content: String,
    val size: Long,
)

object GitHubUrlParser {
    private val pattern = Regex(
        """^https://github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\.git)?(?:/tree/([^/]+)(?:/(.*))?)?(?:/blob/([^/]+)/(.*))?/?$""",
    )

    fun parse(url: String, defaultRef: String = "main", pathOverride: String? = null): GitHubLocator {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://github.com/")) {
            throw ReferenceException(ReferenceErrorType.INVALID_URL, "Only https://github.com URLs are supported")
        }
        val match = pattern.matchEntire(trimmed.removeSuffix(".git"))
            ?: throw ReferenceException(ReferenceErrorType.INVALID_URL, "Unrecognized GitHub URL")
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val treeRef = match.groupValues[3].ifBlank { null }
        val treePath = match.groupValues[4]
        val blobRef = match.groupValues[5].ifBlank { null }
        val blobPath = match.groupValues[6]
        val ref = blobRef ?: treeRef ?: defaultRef
        val path = pathOverride ?: blobPath.ifBlank { treePath }
        return GitHubLocator(owner, repo, ref, path, "https://github.com/$owner/$repo")
    }
}

interface GitHubFetcher {
    suspend fun listTree(locator: GitHubLocator): List<GitHubTreeEntry>
    suspend fun fetchFile(locator: GitHubLocator, path: String): GitHubFileContent
}
