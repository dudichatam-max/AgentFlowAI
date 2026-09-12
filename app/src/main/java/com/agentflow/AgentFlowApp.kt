package com.agentflow

import android.app.Application
import com.agentflow.data.database.AgentFlowDatabase
import com.agentflow.di.AppContainer
import com.agentflow.di.ProviderModule
import com.agentflow.notify.AttentionNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AgentFlowApp : Application() {
    lateinit var database: AgentFlowDatabase
        private set
    lateinit var providers: ProviderModule
        private set
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        database = AgentFlowDatabase.build(this)
        providers = ProviderModule(this, database)
        container = AppContainer(this, database, providers)
        AttentionNotifier.ensureChannel(this)
        appScope.launch {
            container.artifactCatalog.cleanupOrphans()
            container.missionEngine.recoverActive()
            val active = container.missionStore.listActiveMissions()
            container.workScheduler.recover(active)
        }
    }
}
