package com.agentflow.domain.context

import com.agentflow.domain.model.InclusionMode
import com.agentflow.domain.model.Reference
import com.agentflow.domain.reference.FileClassifier
import com.agentflow.domain.reference.ReferenceStorage
import com.agentflow.domain.reference.SecretDenylist

/**
 * Shared by Direct Chat and future Missions. Deterministic, no embeddings.
 */
class ContextBuilder(
    private val storage: ReferenceStorage,
    private val relevanceFloor: Double = 0.2,
    private val retriever: ReferenceRetriever = ReferenceRetriever(),
) {
    fun build(request: ContextRequest, references: List<Reference>): ContextBuildResult {
        val omitted = mutableListOf<OmittedReference>()
        val warnings = mutableSetOf<ContextWarning>()
        val scoped = references.filter { ref ->
            when {
                ref.projectId != request.projectId -> false
                ref.agentId == null -> request.includeSharedReferences
                ref.agentId == request.agentId -> request.includeAgentReferences
                else -> false
            }
        }

        val candidates = scoped.mapNotNull { ref ->
            when (ref.inclusionMode) {
                InclusionMode.NEVER -> {
                    omitted += OmittedReference(ref.id, ref.name, ContextWarning.NO_RELEVANT_REFERENCES)
                    null
                }
                InclusionMode.ALWAYS, InclusionMode.RELEVANT -> ref
            }
        }

        val selected = mutableListOf<ContextDocument>()
        val seenHashes = mutableSetOf<String>()
        var usedTokens = 0
        val budget = request.budget.availableReferenceTokens

        val ranked = candidates.sortedWith(
            compareByDescending<Reference> { it.id in request.explicitReferenceIds }
                .thenByDescending { it.inclusionMode == InclusionMode.ALWAYS }
                .thenByDescending { RelevanceScorer.score(request.userQuery, it) }
                .thenByDescending { it.updatedAt }
                .thenBy { it.id },
        )

        for (ref in ranked) {
            if (SecretDenylist.isDenied(ref.name) || SecretDenylist.isDenied(ref.path ?: "")) {
                omitted += OmittedReference(ref.id, ref.name, ContextWarning.SECRET_FILE_EXCLUDED)
                warnings += ContextWarning.SECRET_FILE_EXCLUDED
                continue
            }
            if (!FileClassifier.isTextLike(ref.name, ref.mimeType)) {
                omitted += OmittedReference(ref.id, ref.name, ContextWarning.UNSUPPORTED_FILE_TYPE)
                warnings += ContextWarning.UNSUPPORTED_FILE_TYPE
                continue
            }
            val hash = ref.contentHash
            if (hash != null && hash in seenHashes) {
                omitted += OmittedReference(ref.id, ref.name, ContextWarning.DUPLICATE_CONTENT)
                warnings += ContextWarning.DUPLICATE_CONTENT
                continue
            }
            val raw = storage.readText(ref.id)
            if (raw == null) {
                omitted += OmittedReference(ref.id, ref.name, ContextWarning.REFERENCE_UNAVAILABLE)
                warnings += ContextWarning.REFERENCE_UNAVAILABLE
                continue
            }
            if (hash != null) seenHashes += hash
            val documentScore = RelevanceScorer.score(request.userQuery, ref, raw)
            val explicit = ref.id in request.explicitReferenceIds
            val always = ref.inclusionMode == InclusionMode.ALWAYS
            if (!explicit && !always && documentScore < relevanceFloor) {
                omitted += OmittedReference(ref.id, ref.name, ContextWarning.NO_RELEVANT_REFERENCES)
                continue
            }
            if (SecretDenylist.looksLikeSecretContent(raw)) {
                omitted += OmittedReference(ref.id, ref.name, ContextWarning.SECRET_FILE_EXCLUDED)
                warnings += ContextWarning.SECRET_FILE_EXCLUDED
                continue
            }

            val chunks = if (explicit || always) {
                listOf(RetrievedChunk(ref.id, 0, raw.length, raw, documentScore))
            } else {
                retriever.retrieve(request.userQuery, ref, raw)
                    .filter { it.score >= relevanceFloor }
            }
            if (chunks.isEmpty()) {
                omitted += OmittedReference(ref.id, ref.name, ContextWarning.NO_RELEVANT_REFERENCES)
                continue
            }

            for (chunk in chunks) {
                val tokens = TokenEstimator.estimate(chunk.content)
                if (tokens > budget && selected.isEmpty() && tokens > request.budget.maxReferenceTokens) {
                    omitted += OmittedReference(ref.id, ref.name, ContextWarning.REFERENCE_TOO_LARGE)
                    warnings += ContextWarning.REFERENCE_TOO_LARGE
                    break
                }
                if (usedTokens + tokens > budget) {
                    warnings += ContextWarning.CONTEXT_LIMIT_REACHED
                    omitted += OmittedReference(ref.id, ref.name, ContextWarning.CONTEXT_LIMIT_REACHED)
                    break
                }
                usedTokens += tokens
                selected += ContextDocument(
                    referenceId = ref.id,
                    name = ref.name,
                    sourceType = ref.type,
                    path = ref.path ?: ref.name,
                    content = chunk.content,
                    relevanceScore = chunk.score,
                    tokenEstimate = tokens,
                    reason = when {
                        explicit -> "explicit"
                        always -> "always"
                        else -> "retrieved"
                    },
                    sourceLocator = ref.id,
                    excerptStart = chunk.start,
                    excerptEnd = chunk.end,
                )
            }
        }
        if (selected.isEmpty()) warnings += ContextWarning.NO_RELEVANT_REFERENCES
        return ContextBuildResult(
            selectedDocuments = selected,
            omittedReferences = omitted,
            estimatedInputTokens = usedTokens + request.budget.reservedSystemTokens,
            budget = request.budget,
            warnings = warnings,
        )
    }
}
