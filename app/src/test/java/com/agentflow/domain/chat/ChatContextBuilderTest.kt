package com.agentflow.domain.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatContextBuilderTest {
    @Test
    fun keepsSystemAndLatestUserWithinBudget() {
        val builder = ChatContextBuilder(charBudget = 80, maxMessages = 4)
        val history = (1..10).map { i ->
            ChatMessage(
                id = "$i",
                sessionId = "s",
                role = if (i % 2 == 0) ChatRole.ASSISTANT else ChatRole.USER,
                content = "msg-$i-xxxxxxxxx",
                createdAt = i.toLong(),
                updatedAt = i.toLong(),
            )
        }
        val prepared = builder.prepare("SYS", history, "latest-user")
        assertThat(prepared.systemInstruction).isEqualTo("SYS")
        assertThat(prepared.messages.last().content).isEqualTo("latest-user")
        assertThat(prepared.messages.size).isAtMost(5)
        val total = prepared.systemInstruction.length + prepared.messages.sumOf { it.content.length }
        assertThat(total).isAtMost(80 + "latest-user".length)
    }
}
