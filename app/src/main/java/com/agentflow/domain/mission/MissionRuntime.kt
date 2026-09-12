package com.agentflow.domain.mission

import com.agentflow.domain.inspector.ArtifactService
import com.agentflow.domain.inspector.InMemoryArtifactCatalog
import com.agentflow.domain.policy.DecisionGateway
import com.agentflow.domain.policy.LoopGuard
import com.agentflow.domain.policy.PolicyEngine
import com.agentflow.domain.task.TaskWorker
import com.agentflow.domain.validation.DomainException

/**
 * Single production wiring for Mission planning/execution.
 * AppContainer and tests must use this so planner + gateway cannot drift.
 */
data class MissionRuntime(
    val engine: MissionEngine,
    val gateway: DecisionGateway,
) {
    companion object {
        fun wire(
            store: MissionStore,
            worker: TaskWorker,
            planner: MissionPlanner,
            artifacts: ArtifactService = ArtifactService(InMemoryArtifactCatalog()),
            contextFactory: MissionContextFactory? = null,
            planningRequired: Boolean = true,
            policy: PolicyEngine = PolicyEngine(store),
            loopGuard: LoopGuard = LoopGuard(),
        ): MissionRuntime {
            val engine = MissionEngine(
                store = store,
                worker = worker,
                artifacts = artifacts,
                contextFactory = contextFactory,
                planner = planner,
                planningRequired = planningRequired,
            )
            val gateway = DecisionGateway(engine, store, policy, loopGuard)
            engine.bindPlanIngest { missionId, raw ->
                val outcome = gateway.ingest(missionId, raw)
                if (outcome.parseError != null) {
                    throw DomainException.InvalidAction("planner contract: ${outcome.parseError}")
                }
            }
            if (!engine.planningWired()) {
                throw DomainException.InvalidAction("planner/ingest not wired")
            }
            return MissionRuntime(engine, gateway)
        }
    }
}
