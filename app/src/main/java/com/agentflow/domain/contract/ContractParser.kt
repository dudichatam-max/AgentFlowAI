package com.agentflow.domain.contract

import kotlinx.serialization.json.Json

class ContractException(message: String) : IllegalArgumentException(message)

object ContractJson {
    val strict = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = false
        encodeDefaults = true
    }
}

object JsonExtractor {
    fun extract(raw: String): String {
        val trimmed = raw.trim()
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(trimmed)
        if (fenced != null) return fenced.groupValues[1].trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        throw ContractException("No JSON object found")
    }
}

object ContractParser {
    fun parse(raw: String): AgentDecisionEnvelope {
        val json = try {
            JsonExtractor.extract(raw)
        } catch (e: ContractException) {
            throw e
        }
        val envelope = try {
            ContractJson.strict.decodeFromString(AgentDecisionEnvelope.serializer(), json)
        } catch (e: Exception) {
            throw ContractException("Malformed contract JSON: ${e.message}")
        }
        if (envelope.version != CURRENT_CONTRACT_VERSION) {
            throw ContractException("Unsupported contract version ${envelope.version}")
        }
        if (envelope.message.isBlank()) throw ContractException("message is required")
        return envelope
    }
}
