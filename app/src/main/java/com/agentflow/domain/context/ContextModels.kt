package com.agentflow.domain.context

import com.agentflow.domain.model.ReferenceType

data class ContextBudget(
    val maxInputTokens: Int = 6_000,
    val reservedSystemTokens: Int = 800,
    val reservedOutputTokens: Int = 1_000,
    val maxReferenceTokens: Int = 3_500,
) {
    val availableReferenceTokens: Int
        get() = maxOf(0, minOf(maxReferenceTokens, maxInputTokens - reservedSystemTokens - reservedOutputTokens))
}

data class ContextRequest(
    val projectId: String,
    val agentId: String? = null,
    val userQuery: String,
    val missionId: String? = null,
    val taskId: String? = null,
    val chatSessionId: String? = null,
    val budget: ContextBudget = ContextBudget(),
    val includeSharedReferences: Boolean = true,
    val includeAgentReferences: Boolean = true,
    val explicitReferenceIds: Set<String> = emptySet(),
)

data class ContextDocument(
    val referenceId: String,
    val name: String,
    val sourceType: ReferenceType,
    val path: String?,
    val content: String,
    val relevanceScore: Double,
    val tokenEstimate: Int,
    val reason: String,
    val sourceLocator: String = referenceId,
    val excerptStart: Int? = null,
    val excerptEnd: Int? = null,
)

enum class ContextWarning {
    CONTEXT_LIMIT_REACHED,
    REFERENCE_TOO_LARGE,
    UNSUPPORTED_FILE_TYPE,
    REFERENCE_UNAVAILABLE,
    DUPLICATE_CONTENT,
    NO_RELEVANT_REFERENCES,
    SECRET_FILE_EXCLUDED,
}

data class OmittedReference(
    val referenceId: String,
    val name: String,
    val reason: ContextWarning,
)

data class ContextBuildResult(
    val selectedDocuments: List<ContextDocument>,
    val omittedReferences: List<OmittedReference>,
    val estimatedInputTokens: Int,
    val budget: ContextBudget,
    val warnings: Set<ContextWarning>,
) {
    fun renderBlock(): String = buildString {
        selectedDocuments.forEach { doc ->
            val label = doc.path ?: doc.name
            appendLine("--- SOURCE: $label [ref=${doc.sourceLocator}] ---")
            appendLine(doc.content)
            appendLine("--- END SOURCE ---")
            appendLine()
        }
    }.trimEnd()
}

object TokenEstimator {
    /** Conservative: ~3 characters per token. */
    fun estimate(text: String): Int = maxOf(1, (text.length + 2) / 3)
}
