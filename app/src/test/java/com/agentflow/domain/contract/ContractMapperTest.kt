package com.agentflow.domain.contract

import com.agentflow.domain.mission.MissionAction
import com.agentflow.domain.model.Priority
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContractMapperTest {
    @Test
    fun mapsCreateTask() {
        val action = ContractMapper.mapAction(
            ProposedActionDto(
                type = "CREATE_TASK",
                title = "Analyze offline persistence",
                description = "Inspect Room.",
                assignedAgentId = "developer-agent-id",
                priority = "HIGH",
            ),
        ) as MissionAction.CreateTask
        assertThat(action.title).contains("offline")
        assertThat(action.priority).isEqualTo(Priority.HIGH)
    }

    @Test(expected = ContractException::class)
    fun unknownTypeRejected() {
        ContractMapper.mapAction(ProposedActionDto(type = "DELETE_EVERYTHING"))
    }

    @Test(expected = ContractException::class)
    fun missingTitleRejected() {
        ContractMapper.mapAction(ProposedActionDto(type = "CREATE_TASK", description = "x", assignedAgentId = "a"))
    }
}
