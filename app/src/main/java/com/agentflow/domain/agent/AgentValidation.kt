package com.agentflow.domain.agent

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule

sealed class AgentValidationError(val message: String) {
    class BlankName : AgentValidationError("Agent name is blank")
    class BlankRole : AgentValidationError("Agent role is blank")
    class BlankProject : AgentValidationError("projectId is blank")
    class UnknownProject(id: String) : AgentValidationError("Unknown project $id")
    class NameTooLong : AgentValidationError("Agent name exceeds ${AgentLimits.MAX_NAME_LENGTH}")
    class RoleTooLong : AgentValidationError("Agent role exceeds ${AgentLimits.MAX_ROLE_LENGTH}")
    class DescriptionTooLong : AgentValidationError("Description exceeds ${AgentLimits.MAX_DESCRIPTION_LENGTH}")
    class BlankModel : AgentValidationError("modelId is blank")
    class InvalidTemperature(value: Double) : AgentValidationError("temperature $value is out of range")
    class InvalidMaxTokens(value: Int) : AgentValidationError("maxOutputTokens $value is invalid")
    class FallbackEqualsPrimary : AgentValidationError("fallback provider/model equals primary")
    class IncompleteFallback : AgentValidationError("fallback provider and model must both be set")
    class RuleInvalid(detail: String) : AgentValidationError(detail)
}

object AgentValidator {

    fun validate(
        agent: Agent,
        projectExists: (String) -> Boolean = { it.isNotBlank() },
        rules: List<AgentRule> = emptyList(),
    ): List<AgentValidationError> {
        val errors = mutableListOf<AgentValidationError>()
        if (agent.name.isBlank()) errors += AgentValidationError.BlankName()
        if (agent.role.isBlank()) errors += AgentValidationError.BlankRole()
        if (agent.projectId.isBlank()) errors += AgentValidationError.BlankProject()
        else if (!projectExists(agent.projectId)) errors += AgentValidationError.UnknownProject(agent.projectId)
        if (agent.name.length > AgentLimits.MAX_NAME_LENGTH) errors += AgentValidationError.NameTooLong()
        if (agent.role.length > AgentLimits.MAX_ROLE_LENGTH) errors += AgentValidationError.RoleTooLong()
        if (agent.description.length > AgentLimits.MAX_DESCRIPTION_LENGTH) errors += AgentValidationError.DescriptionTooLong()
        if (agent.modelId.isBlank()) errors += AgentValidationError.BlankModel()
        if (agent.temperature !in AgentLimits.MIN_TEMPERATURE..AgentLimits.MAX_TEMPERATURE) {
            errors += AgentValidationError.InvalidTemperature(agent.temperature)
        }
        if (agent.maxOutputTokens !in AgentLimits.MIN_OUTPUT_TOKENS..AgentLimits.MAX_OUTPUT_TOKENS) {
            errors += AgentValidationError.InvalidMaxTokens(agent.maxOutputTokens)
        }
        val hasFbProvider = agent.fallbackProviderId != null
        val hasFbModel = !agent.fallbackModelId.isNullOrBlank()
        if (hasFbProvider != hasFbModel) errors += AgentValidationError.IncompleteFallback()
        if (hasFbProvider && hasFbModel &&
            agent.fallbackProviderId == agent.providerId &&
            agent.fallbackModelId == agent.modelId
        ) {
            errors += AgentValidationError.FallbackEqualsPrimary()
        }
        rules.forEach { rule ->
            validateRule(rule).forEach { errors += AgentValidationError.RuleInvalid(it) }
        }
        return errors
    }

    fun requireValid(agent: Agent, projectExists: (String) -> Boolean = { it.isNotBlank() }, rules: List<AgentRule> = emptyList()) {
        val errors = validate(agent, projectExists, rules)
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException(errors.joinToString { it.message })
        }
    }

    fun validateRule(rule: AgentRule): List<String> {
        val errors = mutableListOf<String>()
        if (rule.name.isBlank()) errors += "Rule name is blank"
        if (rule.instruction.isBlank()) errors += "Rule instruction is blank"
        if (rule.name.length > AgentLimits.MAX_RULE_NAME_LENGTH) {
            errors += "Rule name exceeds ${AgentLimits.MAX_RULE_NAME_LENGTH}"
        }
        if (rule.instruction.length > AgentLimits.MAX_RULE_INSTRUCTION_LENGTH) {
            errors += "Rule instruction exceeds ${AgentLimits.MAX_RULE_INSTRUCTION_LENGTH}"
        }
        if (rule.agentId.isBlank()) errors += "Rule agentId is blank"
        return errors
    }
}
