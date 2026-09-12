package com.agentflow.domain.context

import com.agentflow.domain.model.Reference

/**
 * Deterministic lexical retrieval used before provider generation.
 *
 * The retriever keeps the existing free-only/offline-friendly design: no embedding
 * service or vector database is required.
 */
class ReferenceRetriever(
    private val chunkSizeChars: Int = 1_800,
    private val overlapChars: Int = 240,
    private val maxChunksPerReference: Int = 3,
) {
    fun retrieve(
        query: String,
        reference: Reference,
        content: String,
    ): List<RetrievedChunk> {
        if (content.isBlank()) return emptyList()
        val chunks = chunk(content)
        if (chunks.size == 1) {
            return listOf(
                RetrievedChunk(
                    referenceId = reference.id,
                    start = 0,
                    end = content.length,
                    content = content,
                    score = RelevanceScorer.score(query, reference, content),
                ),
            )
        }
        return chunks
            .map { (start, end, text) ->
                RetrievedChunk(
                    referenceId = reference.id,
                    start = start,
                    end = end,
                    content = text,
                    score = RelevanceScorer.score(query, reference, text),
                )
            }
            .sortedWith(compareByDescending<RetrievedChunk> { it.score }.thenBy { it.start })
            .take(maxChunksPerReference)
    }

    private fun chunk(content: String): List<ChunkRange> {
        val ranges = mutableListOf<ChunkRange>()
        var start = 0
        while (start < content.length) {
            val hardEnd = minOf(content.length, start + chunkSizeChars)
            val end = if (hardEnd == content.length) {
                hardEnd
            } else {
                val searchStart = maxOf(start, hardEnd - chunkSizeChars / 2)
                val newline = content.substring(searchStart, hardEnd).lastIndexOf('\n')
                val boundary = if (newline >= 0) searchStart + newline else -1
                if (boundary > start) boundary + 1 else hardEnd
            }
            ranges += ChunkRange(start, end, content.substring(start, end))
            if (end >= content.length) break
            start = maxOf(start + 1, end - overlapChars)
        }
        return ranges
    }

    private data class ChunkRange(
        val start: Int,
        val end: Int,
        val content: String,
    )
}

data class RetrievedChunk(
    val referenceId: String,
    val start: Int,
    val end: Int,
    val content: String,
    val score: Double,
)
