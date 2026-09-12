package com.agentflow.domain.context

import com.agentflow.domain.model.InclusionMode
import com.agentflow.domain.model.Reference
import com.agentflow.domain.model.ReferenceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReferenceRetrieverTest {
    @Test
    fun retrievesRelevantChunkInsteadOfSendingWholeDocument() {
        val content = buildString {
            appendLine("unrelated introduction")
            repeat(80) { appendLine("background material that is not relevant") }
            appendLine("Kotlin coroutine cancellation and structured concurrency")
            repeat(80) { appendLine("additional unrelated material") }
        }
        val reference = Reference(
            id = "ref-1",
            projectId = "project",
            name = "architecture.md",
            type = ReferenceType.TEXT,
            path = "architecture.md",
            createdAt = 1,
            updatedAt = 1,
            inclusionMode = InclusionMode.RELEVANT,
        )

        val result = ReferenceRetriever(chunkSizeChars = 500, overlapChars = 50)
            .retrieve("How does coroutine cancellation work?", reference, content)

        assertThat(result).isNotEmpty()
        assertThat(result.map { it.content }.joinToString()).contains("coroutine cancellation")
        assertThat(result.all { it.content.length < content.length }).isTrue()
    }

    @Test
    fun orderingIsDeterministic() {
        val reference = Reference(
            id = "ref-1",
            projectId = "project",
            name = "notes.md",
            type = ReferenceType.TEXT,
            path = "notes.md",
            createdAt = 1,
            updatedAt = 1,
        )
        val content = (1..20).joinToString("\n") { "section $it architecture" }
        val retriever = ReferenceRetriever(chunkSizeChars = 80, overlapChars = 10)

        val first = retriever.retrieve("architecture section 10", reference, content)
        val second = retriever.retrieve("architecture section 10", reference, content)

        assertThat(first.map { it.start }).isEqualTo(second.map { it.start })
    }
}
