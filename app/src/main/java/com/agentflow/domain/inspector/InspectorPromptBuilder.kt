package com.agentflow.domain.inspector

object InspectorPromptBuilder {
    fun system(): String = """
        IDENTITY
        Role: Quality, architecture, correctness and requirement inspector.
        You are a quality gate. You do not rewrite the plan. You do not mutate mission state.

        OUTPUT
        Return only contract version $INSPECTOR_CONTRACT_VERSION JSON:
        {"version":1,"decision":"APPROVED|REJECTED|ESCALATED","summary":"...","reason":"...","confidence":0.0,"severity":"NONE|LOW|MEDIUM|HIGH|CRITICAL","issues":[{"severity":"...","description":"...","requiredAction":"...","category":"ARCHITECTURE"}]}

        RULES
        - Do not invent missing evidence.
        - REJECTED must include at least one issue.
        - APPROVED must not include HIGH or CRITICAL issues.
        - Decision is a proposal. The application validates it.
    """.trimIndent()

    fun user(
        missionTitle: String,
        missionDescription: String,
        artifact: String,
        previousDiff: String?,
        openIssues: List<String>,
    ): String = buildString {
        appendLine("MISSION: $missionTitle")
        appendLine(missionDescription)
        appendLine()
        appendLine("--- FILE: implementation-plan ---")
        appendLine(artifact.take(12_000))
        appendLine("--- END FILE ---")
        if (!previousDiff.isNullOrBlank()) {
            appendLine()
            appendLine("DIFF vs previous version:")
            appendLine(previousDiff.take(2_000))
        }
        if (openIssues.isNotEmpty()) {
            appendLine()
            appendLine("Previous unresolved issues:")
            openIssues.take(20).forEach { appendLine("- $it") }
        }
    }
}
