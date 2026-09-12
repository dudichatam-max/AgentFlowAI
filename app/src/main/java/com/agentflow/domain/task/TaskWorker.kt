package com.agentflow.domain.task

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskResult

enum class TaskFailureKind { TRANSIENT, PERMANENT }

data class TaskWorkResult(
    val content: String,
    val confidence: Double = 0.7,
    val success: Boolean = true,
    val failureKind: TaskFailureKind = TaskFailureKind.PERMANENT,
)

fun interface TaskWorker {
    suspend fun execute(
        task: Task,
        agent: Agent,
        dependencyResults: List<TaskResult>,
        contextBlock: String,
    ): TaskWorkResult
}
