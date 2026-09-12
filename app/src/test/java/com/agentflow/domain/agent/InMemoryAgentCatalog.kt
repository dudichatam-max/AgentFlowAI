package com.agentflow.domain.agent

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.AgentStatus
import com.agentflow.domain.model.Ids

/**
 * JVM stand-in for AgentRepository transactions. Room-backed repository
 * is exercised on device; clone/reset semantics are identical.
 */
class InMemoryAgentCatalog(
    private val knownProjects: MutableSet<String> = mutableSetOf(),
) {
    private val agents = linkedMapOf<String, Agent>()
    private val rules = linkedMapOf<String, AgentRule>()

    fun addProject(id: String) {
        knownProjects += id
    }

    fun createAgent(agent: Agent, agentRules: List<AgentRule> = emptyList()): Agent {
        AgentValidator.requireValid(agent, projectExists = { it in knownProjects }, rules = agentRules)
        agents[agent.id] = agent
        agentRules.forEach { rules[it.id] = it.copy(agentId = agent.id) }
        return agent
    }

    fun getAgent(id: String): Agent? = agents[id]

    fun agentsForProject(projectId: String): List<Agent> = agents.values.filter { it.projectId == projectId }

    fun getRules(agentId: String): List<AgentRule> = rules.values.filter { it.agentId == agentId }.sortedForBrain()

    fun createRule(rule: AgentRule): AgentRule {
        val errors = AgentValidator.validateRule(rule)
        require(errors.isEmpty()) { errors.joinToString() }
        rules[rule.id] = rule
        return rule
    }

    fun setRuleEnabled(id: String, enabled: Boolean) {
        val current = rules[id] ?: return
        rules[id] = current.copy(enabled = enabled)
    }

    fun deleteAgent(id: String) {
        agents.remove(id)
        rules.values.filter { it.agentId == id }.forEach { rules.remove(it.id) }
    }

    fun clone(sourceId: String, newName: String, now: Long = 1L): Agent {
        val source = agents[sourceId] ?: error("missing")
        val clone = source.copy(id = Ids.new(), name = newName, status = AgentStatus.READY, createdAt = now, updatedAt = now)
        val copied = getRules(sourceId).map { it.copy(id = Ids.new(), agentId = clone.id, createdAt = now, updatedAt = now) }
        return createAgent(clone, copied)
    }

    fun reset(agentId: String, resetModel: Boolean = false, now: Long = 2L) {
        val current = agents[agentId] ?: return
        agents[agentId] = current.copy(
            outputStyle = OutputStyle.BALANCED,
            reasoningLevelEnum = ReasoningLevel.MEDIUM,
            verbosity = Verbosity.NORMAL,
            exposeUncertainty = true,
            includeAssumptions = true,
            includeAlternatives = true,
            providerId = if (resetModel) com.agentflow.domain.model.ProviderId.GROQ else current.providerId,
            modelId = if (resetModel) "openai/gpt-oss-20b" else current.modelId,
            freeOnly = true,
            status = if (current.status == AgentStatus.DISABLED) AgentStatus.DISABLED else AgentStatus.READY,
            updatedAt = now,
        )
        rules.values.filter { it.agentId == agentId }.forEach { rules.remove(it.id) }
    }

    fun brain(agentId: String): AgentBrain? {
        val agent = agents[agentId] ?: return null
        return agent.toBrain(getRules(agentId))
    }
}
