package com.agentflow.domain.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatSessionOpsTest {
    @Test
    fun createRenameArchive() {
        val session = ChatSession("s", "p", "a", ChatLimits.TITLE_DEFAULT, createdAt = 1, updatedAt = 1, lastMessageAt = 1)
        assertThat(session.status).isEqualTo(ChatSessionStatus.ACTIVE)
        val renamed = session.copy(title = "Offline plan")
        assertThat(renamed.title).isEqualTo("Offline plan")
        val archived = renamed.copy(status = ChatSessionStatus.ARCHIVED)
        assertThat(archived.status).isEqualTo(ChatSessionStatus.ARCHIVED)
        assertThat(archived.id).isEqualTo(session.id)
    }
}
