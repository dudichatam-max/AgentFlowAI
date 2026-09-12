package com.agentflow.domain.context

import com.agentflow.domain.model.InclusionMode
import com.agentflow.domain.model.Reference
import com.agentflow.domain.model.ReferenceType
import com.agentflow.domain.reference.FileReferenceStorage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContextBuilderScenarioTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun audioArchitectureScenario() {
        val storage = FileReferenceStorage(tmp.newFolder("refs"))
        fun ref(id: String, name: String, mode: InclusionMode, text: String, agentId: String? = null): Reference {
            storage.save(id, name, text.toByteArray())
            return Reference(
                id = id,
                projectId = "p",
                agentId = agentId,
                name = name,
                type = ReferenceType.TEXT,
                path = name,
                contentHash = com.agentflow.domain.reference.ContentHasher.sha256(text),
                inclusionMode = mode,
                createdAt = 1,
                updatedAt = 1,
            )
        }
        val refs = listOf(
            ref("1", "architecture.md", InclusionMode.ALWAYS, "Overall Android architecture and offline cache."),
            ref("2", "AudioProcessor.kt", InclusionMode.RELEVANT, "audio processing architecture class AudioProcessor { fun process() }"),
            ref("3", "MainActivity.kt", InclusionMode.RELEVANT, "class MainActivity : ComponentActivity"),
            ref("4", ".env", InclusionMode.RELEVANT, "SECRET=1"),
            ref("5", "notes.txt", InclusionMode.NEVER, "personal notes"),
            ref("6", "product-research.md", InclusionMode.RELEVANT, "market research", agentId = "rd"),
        )
        val result = ContextBuilder(storage).build(
            ContextRequest(
                projectId = "p",
                agentId = "dev",
                userQuery = "How should I modify the audio processing architecture?",
            ),
            refs,
        )
        val names = result.selectedDocuments.map { it.name }
        assertThat(names).contains("architecture.md")
        assertThat(names).contains("AudioProcessor.kt")
        assertThat(names).doesNotContain(".env")
        assertThat(names).doesNotContain("notes.txt")
        assertThat(names).doesNotContain("product-research.md")
        assertThat(result.estimatedInputTokens).isAtMost(result.budget.maxInputTokens)
        val again = ContextBuilder(storage).build(
            ContextRequest(projectId = "p", agentId = "dev", userQuery = "How should I modify the audio processing architecture?"),
            refs,
        )
        assertThat(again.selectedDocuments.map { it.referenceId }).isEqualTo(result.selectedDocuments.map { it.referenceId })
    }

    @Test
    fun unavailableDuplicateDoesNotHideUsableReference() {
        val storage = FileReferenceStorage(tmp.newFolder("refs3"))
        val text = "offline architecture"
        storage.save("usable", "usable.md", text.toByteArray())
        val hash = com.agentflow.domain.reference.ContentHasher.sha256(text)
        val refs = listOf(
            Reference("missing", "p", null, "missing.md", ReferenceType.TEXT, "missing.md", null, text.length.toLong(), null, hash, InclusionMode.ALWAYS, 2, 2),
            Reference("usable", "p", null, "usable.md", ReferenceType.TEXT, "usable.md", null, text.length.toLong(), null, hash, InclusionMode.ALWAYS, 1, 1),
        )

        val result = ContextBuilder(storage).build(ContextRequest("p", "dev", "offline architecture"), refs)

        assertThat(result.selectedDocuments.map { it.referenceId }).containsExactly("usable")
        assertThat(result.omittedReferences).contains(
            OmittedReference("missing", "missing.md", ContextWarning.REFERENCE_UNAVAILABLE),
        )
    }

    @Test
    fun binaryDocumentsAreNotDecodedAsText() {
        val storage = FileReferenceStorage(tmp.newFolder("refs4"))
        storage.save("pdf", "spec.pdf", byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x37))
        val ref = Reference(
            id = "pdf",
            projectId = "p",
            agentId = null,
            name = "spec.pdf",
            type = ReferenceType.DOCUMENT,
            path = "spec.pdf",
            contentHash = null,
            sizeBytes = 8,
            mimeType = "application/pdf",
            inclusionMode = InclusionMode.ALWAYS,
            createdAt = 1,
            updatedAt = 1,
        )

        val result = ContextBuilder(storage).build(ContextRequest("p", "dev", "spec"), listOf(ref))

        assertThat(result.selectedDocuments).isEmpty()
        assertThat(result.omittedReferences).contains(
            OmittedReference("pdf", "spec.pdf", ContextWarning.UNSUPPORTED_FILE_TYPE),
        )
    }


    @Test
    fun explicitReferenceStillBlocksSecretContent() {
        val storage = FileReferenceStorage(tmp.newFolder("refs5"))
        val content = "password=abcdefgh123456"
        storage.save("secret", "config.txt", content.toByteArray())
        val ref = Reference(
            id = "secret", projectId = "p", agentId = null, name = "config.txt",
            type = ReferenceType.TEXT, path = "config.txt", contentHash = null,
            sizeBytes = content.length.toLong(), mimeType = "text/plain",
            inclusionMode = InclusionMode.RELEVANT, createdAt = 1, updatedAt = 1,
        )

        val result = ContextBuilder(storage).build(
            ContextRequest(projectId = "p", agentId = "dev", userQuery = "show config", explicitReferenceIds = setOf("secret")),
            listOf(ref),
        )

        assertThat(result.selectedDocuments).isEmpty()
        assertThat(result.omittedReferences).contains(
            OmittedReference("secret", "config.txt", ContextWarning.SECRET_FILE_EXCLUDED),
        )
    }

    @Test
    fun neverExceedsBudgetAndDropsDuplicates() {
        val storage = FileReferenceStorage(tmp.newFolder("refs2"))
        val text = "cache offline room"
        storage.save("a", "a.md", text.toByteArray())
        storage.save("b", "b.md", text.toByteArray())
        val hash = com.agentflow.domain.reference.ContentHasher.sha256(text)
        val refs = listOf(
            Reference("a", "p", null, "a.md", ReferenceType.TEXT, "a.md", null, text.length.toLong(), null, hash, InclusionMode.ALWAYS, 1, 1),
            Reference("b", "p", null, "b.md", ReferenceType.TEXT, "b.md", null, text.length.toLong(), null, hash, InclusionMode.ALWAYS, 1, 1),
        )
        val result = ContextBuilder(storage).build(ContextRequest("p", "dev", "offline cache"), refs)
        assertThat(result.selectedDocuments).hasSize(1)
        assertThat(result.warnings).contains(ContextWarning.DUPLICATE_CONTENT)
    }
}
