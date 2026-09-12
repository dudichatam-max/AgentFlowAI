package com.agentflow.domain.mission

data class MissionExecutionPolicy(
    val maxConcurrentTasks: Int = 3,
    val maxTasksPerMission: Int = 50,
    val maxGraphDepth: Int = 10,
    val maxTaskRetries: Int = 2,
    val maxCreatesPerPlanningCycle: Int = 10,
    val maxMissionRuntimeMs: Long = 30 * 60_000L,
    val maxSchedulerHops: Int = 24,
)
