package com.agentflow.domain.reference

import com.agentflow.domain.model.InclusionMode
import com.agentflow.domain.model.Reference
import com.agentflow.domain.model.ReferenceType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InMemoryCatalog : ReferenceCatalog {
    val items = linkedMapOf<String, Reference>()
    override suspend fun insert(reference: Reference) {
        items[reference.id] = reference
    }
    override suspend fun update(reference: Reference) {
        items[reference.id] = reference
    }
    override suspend fun delete(id: String) {
        items.remove(id)
    }
    override suspend fun get(id: String) = items[id]
    override suspend fun listByProject(projectId: String) = items.values.filter { it.projectId == projectId }
    override suspend fun findByHash(hash: String) = items.values.filter { it.contentHash == hash }
}

class ReferenceManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun addTextPersistsHashAndFile() = runTest {
        val storage = FileReferenceStorage(tmp.newFolder("r"))
        val catalog = InMemoryCatalog()
        val manager = ReferenceManager(catalog, storage)
        val ref = manager.addText("p", "architecture.md", "hello architecture")
        assertThat(ref.contentHash).isEqualTo(ContentHasher.sha256("hello architecture"))
        assertThat(storage.readText(ref.id)).contains("hello architecture")
        manager.delete(ref.id)
        assertThat(storage.exists(ref.id)).isFalse()
        assertThat(catalog.items).isEmpty()
    }

    @Test
    fun rejectsSecretAndHuge() = runTest {
        val manager = ReferenceManager(InMemoryCatalog(), FileReferenceStorage(tmp.newFolder("r2")))
        try {
            manager.addFile("p", ".env", "x=1".toByteArray())
            throw AssertionError("expected secret")
        } catch (e: ReferenceException) {
            assertThat(e.type).isEqualTo(ReferenceErrorType.SECRET_FILE)
        }
        try {
            manager.addFile("p", "big.kt", ByteArray(ReferenceLimits.MAX_SINGLE_FILE_BYTES + 1))
            throw AssertionError("expected size")
        } catch (e: ReferenceException) {
            assertThat(e.type).isEqualTo(ReferenceErrorType.FILE_TOO_LARGE)
        }
    }


    @Test
    fun addGitHubFileFetchesAndPersistsContent() = runTest {
        val storage = FileReferenceStorage(tmp.newFolder("github"))
        val catalog = InMemoryCatalog()
        val fetcher = object : GitHubFetcher {
            override suspend fun listTree(locator: GitHubLocator) = emptyList<GitHubTreeEntry>()

            override suspend fun fetchFile(locator: GitHubLocator, path: String) = GitHubFileContent(
                path = path,
                sha = "abc123",
                content = "class MainActivity",
                size = 18,
            )
        }
        val manager = ReferenceManager(catalog, storage, github = fetcher)

        val ref = manager.addGitHub(
            projectId = "p",
            url = "https://github.com/example/example/blob/main/app/src/MainActivity.kt",
        )

        assertThat(ref.type).isEqualTo(ReferenceType.GITHUB)
        assertThat(ref.name).isEqualTo("MainActivity.kt")
        assertThat(ref.contentHash).isNotNull()
        assertThat(storage.readText(ref.id)).isEqualTo("class MainActivity")
        assertThat(ref.path).isEqualTo("app/src/MainActivity.kt")
    }


    @Test
    fun storageStatsExcludeCacheBytes() = runTest {
        val storage = FileReferenceStorage(tmp.newFolder("stats"))
        val catalog = InMemoryCatalog()
        val manager = ReferenceManager(catalog, storage)
        val ref = manager.addText("p", "a.txt", "12345")
        storage.cacheFile("cache", ByteArray(100))

        val stats = manager.storageStats(setOf(ref.id))

        assertThat(stats.referenceBytes).isEqualTo(ref.sizeBytes)
    }

    @Test
    fun inclusionUpdate() = runTest {
        val catalog = InMemoryCatalog()
        val mgr = ReferenceManager(catalog, FileReferenceStorage(tmp.newFolder("r4")))
        val created = mgr.addText("p", "n.md", "x")
        mgr.setInclusion(created.id, InclusionMode.ALWAYS)
        assertThat(catalog.get(created.id)!!.inclusionMode).isEqualTo(InclusionMode.ALWAYS)
    }
}
