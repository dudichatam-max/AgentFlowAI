package com.agentflow.domain.policy

import com.agentflow.domain.contract.ContractException
import com.agentflow.domain.contract.ContractMapper
import com.agentflow.domain.contract.ContractParser
import com.agentflow.domain.mission.MissionActionResult
import com.agentflow.domain.mission.MissionEngine
import com.agentflow.domain.mission.MissionStore
import com.agentflow.domain.model.MissionEventType
import com.agentflow.domain.mission.event
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DecisionOutcome(
    val accepted: List<MissionActionResult>,
    val rejected: List<RejectedAction>,
    val parseError: String? = null,
)

data class RejectedAction(
    val summary: String,
    val reason: String,
)

/**
 * Trust boundary. Parses untrusted AI text, maps to typed actions,
 * applies PolicyEngine + LoopGuard, then MissionEngine.
 */
class DecisionGateway(
    private val engine: MissionEngine,
    private val store: MissionStore,
    private val policy: PolicyEngine,
    private val loopGuard: LoopGuard = LoopGuard(),
) {
    private val ingestMutex = Mutex()

    suspend fun ingest(missionId: String, rawAgentText: String): DecisionOutcome = ingestMutex.withLock {
        val envelope = try {
            ContractParser.parse(rawAgentText)
        } catch (e: ContractException) {
            engine.failMission(missionId, "contract rejected: ${e.message}")
            return DecisionOutcome(emptyList(), emptyList(), e.message)
        }
        val actions = try {
            ContractMapper.toActions(envelope)
        } catch (e: ContractException) {
            engine.failMission(missionId, "action map rejected: ${e.message}")
            return DecisionOutcome(emptyList(), emptyList(), e.message)
        }
        val loop = loopGuard.inspect(missionId, actions, store)
        if (loop is PolicyDecision.Deny) {
            engine.failMission(missionId, "loop-guard: ${loop.reason}")
            return DecisionOutcome(emptyList(), listOf(RejectedAction("batch", loop.reason)))
        }
        val mission = store.getMission(missionId)
            ?: return DecisionOutcome(emptyList(), listOf(RejectedAction("mission", "not found")))

        val batch = engine.processActionBatch(missionId, actions) { current, action ->
            when (val decision = policy.evaluate(current, action)) {
                PolicyDecision.Allow -> Unit
                is PolicyDecision.Deny -> throw com.agentflow.domain.validation.DomainException.PolicyViolation(
                    decision.reason,
                )
            }
        }
        if (batch.isFailure) {
            val error = batch.exceptionOrNull()
            val reason = error?.message ?: "engine rejected batch"
            if (error !is com.agentflow.domain.validation.DomainException.PolicyViolation) {
                engine.failMission(missionId, "engine blocked batch: $reason")
            }
            return DecisionOutcome(
                emptyList(),
                listOf(RejectedAction("batch", reason.removePrefix("Policy violation: "))),
            )
        }
        val accepted = batch.getOrThrow()
        actions.forEach { action ->
            loopGuard.record(missionId, action, store)
        }
        return DecisionOutcome(accepted, emptyList())
    }
}
