package com.agentflow.data.network.github

import com.agentflow.data.network.HttpClientFactory
import com.agentflow.domain.reference.GitHubFileContent
import com.agentflow.domain.reference.GitHubFetcher
import com.agentflow.domain.reference.GitHubLocator
import com.agentflow.domain.reference.GitHubTreeEntry
import com.agentflow.domain.reference.ReferenceErrorType
import com.agentflow.domain.reference.ReferenceException
import com.agentflow.domain.reference.ReferenceLimits
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

class KtorGitHubFetcher(
    private val http: HttpClient,
    private val json: Json = HttpClientFactory.json,
    private val apiBase: String = "https://api.github.com",
) : GitHubFetcher {

    override suspend fun listTree(locator: GitHubLocator): List<GitHubTreeEntry> {
        val url = "$apiBase/repos/${locator.owner}/${locator.repo}/git/trees/${locator.ref}?recursive=1"
        val text = get(url)
        val parsed = json.decodeFromString(GitHubTreeResponse.serializer(), text)
        val prefix = locator.path.trim('/')
        return parsed.tree.orEmpty()
            .filter { it.type == "blob" }
            .filter { prefix.isBlank() || it.path.orEmpty().startsWith(prefix) }
            .map {
                GitHubTreeEntry(
                    path = it.path.orEmpty(),
                    size = it.size ?: 0,
                    sha = it.sha,
                    type = it.type.orEmpty(),
                )
            }
    }

    override suspend fun fetchFile(locator: GitHubLocator, path: String): GitHubFileContent {
        val url = "$apiBase/repos/${locator.owner}/${locator.repo}/contents/$path?ref=${locator.ref}"
        val text = get(url)
        val parsed = json.decodeFromString(GitHubContentResponse.serializer(), text)
        if ((parsed.size ?: 0) > ReferenceLimits.MAX_SINGLE_FILE_BYTES) {
            throw ReferenceException(ReferenceErrorType.CONTENT_TOO_LARGE, "GitHub file too large")
        }
        val decoded = when (parsed.encoding) {
            "base64" -> String(
                Base64.getMimeDecoder().decode(parsed.content.orEmpty().replace("\n", "")),
                Charsets.UTF_8,
            )
            else -> parsed.content.orEmpty()
        }
        return GitHubFileContent(path, parsed.sha, decoded, parsed.size ?: decoded.length.toLong())
    }

    private suspend fun get(url: String): String {
        val response = http.get(url) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "AgentFlowAI")
        }
        val body = response.bodyAsText()
        when (response.status.value) {
            200 -> return body
            401 -> throw ReferenceException(ReferenceErrorType.UNAUTHORIZED, "GitHub unauthorized")
            403 -> throw ReferenceException(
                if (body.contains("rate limit", true)) ReferenceErrorType.RATE_LIMITED else ReferenceErrorType.FORBIDDEN,
                "GitHub forbidden",
            )
            404 -> throw ReferenceException(ReferenceErrorType.NOT_FOUND, "GitHub path not found")
            429 -> throw ReferenceException(ReferenceErrorType.RATE_LIMITED, "GitHub rate limited")
            in 500..599 -> throw ReferenceException(ReferenceErrorType.SERVER_ERROR, "GitHub server error")
            else -> throw ReferenceException(ReferenceErrorType.NETWORK_ERROR, "GitHub HTTP ${response.status.value}")
        }
    }
}

@Serializable
private data class GitHubTreeResponse(val tree: List<GitHubTreeNode>? = null)

@Serializable
private data class GitHubTreeNode(
    val path: String? = null,
    val type: String? = null,
    val sha: String? = null,
    val size: Long? = null,
)

@Serializable
private data class GitHubContentResponse(
    val content: String? = null,
    val encoding: String? = null,
    val sha: String? = null,
    val size: Long? = null,
)
