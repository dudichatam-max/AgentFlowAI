package com.agentflow.data.repository

import androidx.room.withTransaction
import com.agentflow.data.dao.AgentDao
import com.agentflow.data.dao.AgentRuleDao
import com.agentflow.data.dao.ProjectDao
import com.agentflow.data.database.AgentFlowDatabase
import com.agentflow.data.mapper.toDomain
import com.agentflow.data.mapper.toEntity
import com.agentflow.domain.agent.AgentBrain
import com.agentflow.domain.agent.AgentBrainConfig
import com.agentflow.domain.agent.AgentValidator
import com.agentflow.domain.agent.DefaultAgentFactory
import com.agentflow.domain.agent.sortedForBrain
import com.agentflow.domain.agent.toBrain
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.AgentStatus
import com.agentflow.domain.model.Ids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AgentRepository(
    private val db: AgentFlowDatabase,
    private val agentDao: AgentDao,
    private val ruleDao: AgentRuleDao,
    private val projectDao: ProjectDao,
) {
    constructor(db: AgentFlowDatabase) : this(db, db.agentDao(), db.agentRuleDao(), db.projectDao())

    fun observeAgentsForProject(projectId: String): Flow<List<Agent>> =
        agentDao.observeByProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeAgent(id: String): Flow<Agent?> =
        agentDao.observeById(id).map { it?.toDomain() }

    suspend fun getAgent(id: String): Agent? = agentDao.getById(id)?.toDomain()

    suspend fun getAgentsForProject(projectId: String): List<Agent> =
        agentDao.listByProject(projectId).map { it.toDomain() }

    suspend fun createAgent(agent: Agent, rules: List<AgentRule> = emptyList()): Agent {
        val projectOk = projectDao.getById(agent.projectId) != null
        AgentValidator.requireValid(agent, projectExists = { it == agent.projectId && projectOk }, rules = rules)
        db.withTransaction {
            agentDao.insert(agent.toEntity())
            rules.forEach { ruleDao.insert(it.copy(agentId = agent.id).toEntity()) }
        }
        return agent
    }

    suspend fun updateAgent(agent: Agent) {
        val rules = getAgentRules(agent.id)
        val projectOk = projectDao.getById(agent.projectId) != null
        AgentValidator.requireValid(agent, projectExists = { it == agent.projectId && projectOk }, rules = rules)
        agentDao.update(agent.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteAgent(id: String) {
        db.withTransaction {
            ruleDao.deleteByAgent(id)
            agentDao.deleteById(id)
        }
    }

    suspend fun updateAgentStatus(id: String, status: AgentStatus, now: Long = System.currentTimeMillis()) {
        val current = agentDao.getById(id) ?: return
        agentDao.update(current.copy(status = status.name, updatedAt = now))
    }

    suspend fun enableAgent(id: String) = updateAgentStatus(id, AgentStatus.READY)

    suspend fun disableAgent(id: String) = updateAgentStatus(id, AgentStatus.DISABLED)

    suspend fun getAgentRules(agentId: String): List<AgentRule> =
        ruleDao.listByAgent(agentId).map { it.toDomain() }.sortedForBrain()

    suspend fun getEnabledRulesForAgent(agentId: String): List<AgentRule> =
        getAgentRules(agentId).filter { it.enabled }

    fun observeRules(agentId: String): Flow<List<AgentRule>> =
        ruleDao.observeByAgent(agentId).map { list -> list.map { it.toDomain() }.sortedForBrain() }

    suspend fun getRule(id: String): AgentRule? = ruleDao.getById(id)?.toDomain()

    suspend fun createRule(rule: AgentRule): AgentRule {
        val errors = AgentValidator.validateRule(rule)
        require(errors.isEmpty()) { errors.joinToString() }
        ruleDao.insert(rule.toEntity())
        return rule
    }

    suspend fun updateRule(rule: AgentRule) {
        val errors = AgentValidator.validateRule(rule)
        require(errors.isEmpty()) { errors.joinToString() }
        ruleDao.update(rule.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteRule(id: String) = ruleDao.deleteById(id)

    suspend fun enableRule(id: String) = setRuleEnabled(id, true)

    suspend fun disableRule(id: String) = setRuleEnabled(id, false)

    private suspend fun setRuleEnabled(id: String, enabled: Boolean) {
        val current = ruleDao.getById(id) ?: return
        ruleDao.update(current.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
    }

    suspend fun getAgentBrain(agentId: String): AgentBrain? {
        val agent = getAgent(agentId) ?: return null
        return agent.toBrain(getAgentRules(agentId))
    }

    suspend fun saveAgentBrainConfiguration(agentId: String, config: AgentBrainConfig) {
        val current = getAgent(agentId) ?: return
        updateAgent(
            current.copy(
                role = config.role,
                outputStyle = config.outputStyle,
                verbosity = config.verbosity,
                exposeUncertainty = config.exposeUncertainty,
                includeAssumptions = config.includeAssumptions,
                includeAlternatives = config.includeAlternatives,
                reasoningLevelEnum = config.reasoningLevel,
            ),
        )
    }

    suspend fun cloneAgent(sourceId: String, newName: String, now: Long = System.currentTimeMillis()): Agent {
        val source = getAgent(sourceId) ?: error("Agent $sourceId not found")
        val rules = getAgentRules(sourceId)
        val clone = source.copy(
            id = Ids.new(),
            name = newName,
            status = AgentStatus.READY,
            createdAt = now,
            updatedAt = now,
        )
        val clonedRules = rules.map {
            it.copy(id = Ids.new(), agentId = clone.id, createdAt = now, updatedAt = now)
        }
        return createAgent(clone, clonedRules)
    }

    /**
     * Explicit reset of brain/rules. History is untouched.
     * [resetModel] also restores default free Groq/Gemini pair.
     */
    suspend fun resetAgent(agentId: String, resetModel: Boolean = false, now: Long = System.currentTimeMillis()) {
        val current = getAgent(agentId) ?: return
        val reset = current.copy(
            outputStyle = com.agentflow.domain.agent.OutputStyle.BALANCED,
            reasoningLevelEnum = com.agentflow.domain.agent.ReasoningLevel.MEDIUM,
            verbosity = com.agentflow.domain.agent.Verbosity.NORMAL,
            exposeUncertainty = true,
            includeAssumptions = true,
            includeAlternatives = true,
            providerId = if (resetModel) com.agentflow.domain.model.ProviderId.GROQ else current.providerId,
            modelId = if (resetModel) "openai/gpt-oss-20b" else current.modelId,
            fallbackProviderId = if (resetModel) com.agentflow.domain.model.ProviderId.GEMINI else current.fallbackProviderId,
            fallbackModelId = if (resetModel) "gemini-2.5-flash" else current.fallbackModelId,
            freeOnly = true,
            status = if (current.status == AgentStatus.DISABLED) AgentStatus.DISABLED else AgentStatus.READY,
            updatedAt = now,
        )
        db.withTransaction {
            agentDao.update(reset.toEntity())
            ruleDao.deleteByAgent(agentId)
        }
    }

    suspend fun seedDefaults(projectId: String): List<Agent> {
        val seeded = DefaultAgentFactory.createAll(projectId)
        return seeded.map { createAgent(it.agent, it.rules) }
    }
}
