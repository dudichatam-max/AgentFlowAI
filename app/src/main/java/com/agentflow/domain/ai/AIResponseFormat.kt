package com.agentflow.domain.ai

import kotlinx.serialization.json.JsonObject

enum class AIResponseFormatType {
    TEXT,
    JSON_OBJECT,
    JSON_SCHEMA,
}

data class AIResponseFormat(
    val type: AIResponseFormatType = AIResponseFormatType.TEXT,
    val schemaName: String? = null,
    val schema: JsonObject? = null,
) {
    companion object {
        val Text = AIResponseFormat(AIResponseFormatType.TEXT)
        val JsonObjectFormat = AIResponseFormat(AIResponseFormatType.JSON_OBJECT)
        fun jsonSchema(name: String, schema: JsonObject) = AIResponseFormat(
            type = AIResponseFormatType.JSON_SCHEMA,
            schemaName = name,
            schema = schema,
        )
    }
}
