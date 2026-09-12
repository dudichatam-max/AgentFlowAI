package com.agentflow.data.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.agentflow.data.entity.MissionEntity
import com.agentflow.data.entity.ProjectEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class AgentFlowDatabaseTransactionTest {
    private lateinit var db: AgentFlowDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AgentFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun missionInsertAndEventRollbackTogether() = runBlocking {
        kotlin.runCatching {
            db.withTransaction {
                db.projectDao().insert(ProjectEntity("p1", "Project", "", 1, 1, false))
                db.missionDao().insert(MissionEntity("m1", "p1", "Mission", "", "EXECUTING", 1, 1, null, null, "user"))
                db.missionEventDao().insert(com.agentflow.data.entity.MissionEventEntity("e1", "m1", "MISSION_CREATED", "created", null, null, 1))
                throw IllegalStateException("simulated crash")
            }
        }

        assertThat(db.projectDao().getById("p1")).isNull()
        assertThat(db.missionDao().getById("m1")).isNull()
        assertThat(db.missionEventDao().pageChronological("m1", 20, 0)).isEmpty()
    }
}
