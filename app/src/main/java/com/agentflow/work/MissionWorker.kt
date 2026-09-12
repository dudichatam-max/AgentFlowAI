package com.agentflow.work

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.agentflow.AgentFlowApp
import com.agentflow.domain.retry.WorkFailureClassifier
import com.agentflow.domain.retry.WorkFailureKind
import kotlinx.coroutines.CancellationException
import com.agentflow.notify.AttentionNotifier

/**
 * Durable mission runner. Long-running execution is promoted to a foreground worker
 * so Android can keep the work alive while the mission is actively executing.
 */
class MissionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val missionId = inputData.getString(KEY_MISSION_ID) ?: return Result.failure()
        val app = applicationContext as? AgentFlowApp ?: return Result.failure()
        val store = app.container.missionStore
        val engine = app.container.missionEngine
        val mission = store.getMission(missionId)

        when (MissionWorkPolicy.decide(mission)) {
            WorkDecision.SKIP -> return Result.success()
            WorkDecision.RETRY -> return Result.retry()
            WorkDecision.RUN -> Unit
        }

        setForeground(createForegroundInfo(missionId))

        return try {
            val outcome = engine.continueMission(missionId)
            if (outcome.isFailure) {
                val error = outcome.exceptionOrNull()!!
                when (WorkFailureClassifier.classify(error)) {
                    WorkFailureKind.CANCELLED -> throw error
                    WorkFailureKind.TRANSIENT -> Result.retry()
                    WorkFailureKind.PERMANENT -> Result.failure()
                }
            } else {
                val value = outcome.getOrThrow()
                if (value.exhaustedHops && value.workRemaining) Result.retry() else Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    private fun createForegroundInfo(missionId: String): ForegroundInfo {
        AttentionNotifier.ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(
            applicationContext,
            AttentionNotifier.CHANNEL_ID,
        )
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("AgentFlow mission running")
            .setContentText("Mission $missionId is executing")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_MISSION_ID = "missionId"
        private const val FOREGROUND_NOTIFICATION_ID = 0xAF10
    }
}
