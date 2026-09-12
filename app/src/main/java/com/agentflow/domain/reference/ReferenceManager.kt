package com.agentflow.domain.reference

import kotlinx.coroutines.CancellationException

import com.agentflow.domain.model.InclusionMode
import com.agentflow.domain.model.Ids
import com.agentflow.domain.model.Reference
import com.agentflow.domain.model.ReferenceType

interface ReferenceCatalog {
    suspend fun insert(reference: Reference)
    suspend fun update(reference: Reference)
    suspend fun delete(id: String)
    suspend fun get(id: String): Reference?
    suspend fun listByProject(projectId: String): List<Reference>
    suspend fun findByHash(hash: String): List<Reference>
}

class ReferenceManager(
    private val catalog: ReferenceCatalog,
    private val storage: ReferenceStorage,
    private val github: GitHubFetcher? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun addText(
        projectId: String,
        name: String,
        text: String,
        agentId: String? = null,
        inclusion: InclusionMode = InclusionMode.RELEVANT,
    ): Reference {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > ReferenceLimits.MAX_TEXT_REFERENCE_BYTES) {
            throw ReferenceException(ReferenceErrorType.FILE_TOO_LARGE, "Text exceeds limit")
        }
        return persist(projectId, agentId, name, ReferenceType.TEXT, bytes, "text/plain", inclusion, name)
    }

    suspend fun addFile(
        projectId: String,
        name: String,
        bytes: ByteArray,
        mime: String? = null,
        agentId: String? = null,
        inclusion: InclusionMode = InclusionMode.RELEVANT,
        relativePath: String? = null,
    ): Reference {
        if (bytes.size > ReferenceLimits.MAX_SINGLE_FILE_BYTES) {
            throw ReferenceException(ReferenceErrorType.FILE_TOO_LARGE, "File exceeds ${ReferenceLimits.MAX_SINGLE_FILE_BYTES}")
        }
        if (SecretDenylist.isDenied(name)) {
            throw ReferenceException(ReferenceErrorType.SECRET_FILE, "Secret-like file is blocked from automatic import")
        }
        if (FileClassifier.isTextLike(name, mime) && SecretDenylist.looksLikeSecretContent(bytes.toString(Charsets.UTF_8))) {
            throw ReferenceException(ReferenceErrorType.SECRET_FILE, "File looks like it contains a secret")
        }
        return persist(projectId, agentId, name, ReferenceType.FILE, bytes, mime, inclusion, relativePath ?: name)
    }

    suspend fun addFolderFiles(
        projectId: String,
        files: List<Pair<String, ByteArray>>,
        agentId: String? = null,
    ): List<Reference> {
        if (files.size > ReferenceLimits.MAX_FOLDER_FILES) {
            throw ReferenceException(ReferenceErrorType.FILE_TOO_LARGE, "Folder exceeds file count")
        }
        var total = 0L
        val imported = mutableListOf<Reference>()
        for ((name, bytes) in files) {
            if (SecretDenylist.isDenied(name)) continue
            if (!FileClassifier.isTextLike(name)) continue
            total += bytes.size
            if (total > ReferenceLimits.MAX_TOTAL_IMPORT_BYTES) {
                throw ReferenceException(ReferenceErrorType.FILE_TOO_LARGE, "Folder exceeds total bytes")
            }
            imported += addFile(projectId, name.substringAfterLast('/'), bytes, agentId = agentId, relativePath = name)
        }
        return imported
    }

    suspend fun addGitHub(
        projectId: String,
        url: String,
        ref: String = "main",
        path: String = "",
        agentId: String? = null,
        inclusion: InclusionMode = InclusionMode.RELEVANT,
    ): Reference {
        val fetcher = github ?: throw ReferenceException(ReferenceErrorType.NETWORK_ERROR, "GitHub fetcher missing")
        val locator = GitHubUrlParser.parse(url, ref, path.ifBlank { null })
        val githubPath = locator.path.trim('/')
        if (githubPath.isBlank()) {
            throw ReferenceException(
                ReferenceErrorType.INVALID_FILE,
                "A GitHub reference must point to a file; use previewGitHub to select a file",
            )
        }
        if (SecretDenylist.isDenied(githubPath)) {
            throw ReferenceException(ReferenceErrorType.SECRET_FILE, "Secret-like GitHub file is blocked from automatic import")
        }

        val remote = fetcher.fetchFile(locator, githubPath)
        if (remote.content.toByteArray(Charsets.UTF_8).size > ReferenceLimits.MAX_SINGLE_FILE_BYTES) {
            throw ReferenceException(ReferenceErrorType.FILE_TOO_LARGE, "GitHub file exceeds ${ReferenceLimits.MAX_SINGLE_FILE_BYTES}")
        }
        if (SecretDenylist.looksLikeSecretContent(remote.content)) {
            throw ReferenceException(ReferenceErrorType.SECRET_FILE, "GitHub file looks like it contains a secret")
        }

        val id = Ids.new()
        val name = githubPath.substringAfterLast('/').ifBlank { "github-file" }
        val blob = try {
            storage.save(id, name, remote.content.toByteArray(Charsets.UTF_8))
        } catch (t: CancellationException) {
            throw t
        } catch (t: Exception) {
            throw ReferenceException(ReferenceErrorType.STORAGE_ERROR, t.message ?: "storage")
        }
        val stamp = now()
        val reference = Reference(
            id = id,
            projectId = projectId,
            agentId = agentId,
            name = name,
            type = ReferenceType.GITHUB,
            path = githubPath,
            uri = "${locator.url}/blob/${locator.ref}/$githubPath",
            sizeBytes = blob.sizeBytes,
            mimeType = mimeTypeFor(name),
            contentHash = blob.contentHash,
            inclusionMode = inclusion,
            createdAt = stamp,
            updatedAt = stamp,
        )
        try {
            catalog.insert(reference)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Exception) {
            storage.delete(id)
            throw t
        }
        return reference
    }

    private fun mimeTypeFor(name: String): String? = when (FileClassifier.extension(name)) {
        "kt", "kts" -> "text/x-kotlin"
        "java" -> "text/x-java-source"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "md" -> "text/markdown"
        "txt", "properties", "yml", "yaml", "toml", "gradle", "sh", "bash", "css", "html", "js", "ts" -> "text/plain"
        else -> null
    }

    suspend fun previewGitHub(url: String, ref: String = "main", path: String = ""): List<GitHubTreeEntry> {
        val fetcher = github ?: throw ReferenceException(ReferenceErrorType.NETWORK_ERROR, "GitHub fetcher missing")
        val locator = GitHubUrlParser.parse(url, ref, path.ifBlank { null })
        return fetcher.listTree(locator).take(ReferenceLimits.MAX_GITHUB_TREE)
    }

    suspend fun delete(id: String) {
        catalog.delete(id)
        storage.delete(id)
    }

    suspend fun setInclusion(id: String, mode: InclusionMode) {
        val current = catalog.get(id) ?: return
        catalog.update(current.copy(inclusionMode = mode, updatedAt = now()))
    }

    suspend fun setAgentScope(id: String, agentId: String?) {
        val current = catalog.get(id) ?: return
        catalog.update(current.copy(agentId = agentId, updatedAt = now()))
    }

    suspend fun storageStats(knownIds: Set<String>): StorageStats = StorageStats(
        referenceBytes = (storage as? FileReferenceStorage)?.let { fileStorage ->
            // totalBytes() includes cache data; referenceBytes must describe only persisted references.
            fileStorage.totalReferenceBytes()
        } ?: storage.totalBytes(),
        referenceCount = knownIds.size,
        orphansRemoved = 0,
    )

    suspend fun cleanupOrphans(knownIds: Set<String>): Int = storage.cleanupOrphans(knownIds)

    private suspend fun persist(
        projectId: String,
        agentId: String?,
        name: String,
        type: ReferenceType,
        bytes: ByteArray,
        mime: String?,
        inclusion: InclusionMode,
        path: String,
    ): Reference {
        val id = Ids.new()
        val blob = try {
            storage.save(id, name, bytes)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Exception) {
            throw ReferenceException(ReferenceErrorType.STORAGE_ERROR, t.message ?: "storage")
        }
        val stamp = now()
        val reference = Reference(
            id = id,
            projectId = projectId,
            agentId = agentId,
            name = name,
            type = type,
            path = path,
            uri = blob.relativePath,
            sizeBytes = blob.sizeBytes,
            mimeType = mime,
            contentHash = blob.contentHash,
            inclusionMode = inclusion,
            createdAt = stamp,
            updatedAt = stamp,
        )
        try {
            catalog.insert(reference)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Exception) {
            storage.delete(id)
            throw t
        }
        return reference
    }
}

data class StorageStats(
    val referenceBytes: Long,
    val referenceCount: Int,
    val orphansRemoved: Int,
)
