package com.agentflow.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.agentflow.domain.model.Mission
import java.util.concurrent.TimeUnit

class MissionWorkScheduler(private val context: Context) {
    fun enqueue(missionId: String, requireNetwork: Boolean = true) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requireNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .build()
        val request = OneTimeWorkRequestBuilder<MissionWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(MissionWorker.KEY_MISSION_ID to missionId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MissionWorkPolicy.uniqueName(missionId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueReplace(missionId: String, requireNetwork: Boolean = true) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requireNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .build()
        val request = OneTimeWorkRequestBuilder<MissionWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(MissionWorker.KEY_MISSION_ID to missionId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MissionWorkPolicy.uniqueName(missionId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun recover(active: List<Mission>) {
        active.filter { MissionWorkPolicy.shouldRecover(it.status) }.forEach { enqueueReplace(it.id) }
    }
}
