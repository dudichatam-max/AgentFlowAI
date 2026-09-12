package com.agentflow.domain.chat

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.model.RulePriority
import com.agentflow.domain.provider.ProviderManager
import com.agentflow.domain.provider.ProviderType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChatServiceStreamTest {

    private val agent = Agent(
        id = "rd",
        projectId = "p",
        name = "R&D",
        role = "Research and development orchestrator.",
        description = "Plans work",
        providerId = ProviderId.GEMINI,
        modelId = "gemini-2.5-flash",
        createdAt = 1,
        updatedAt = 1,
    )
    private val rules = listOf(
        AgentRule("1", "rd", "Facts", "Never invent technical facts.", RulePriority.CRITICAL, true, 1, 1),
    )

    @Test
    fun completedAccumulatesChunks() = runTest {
        val store = InMemoryChatPersistence().apply {
            put(ChatSession("s", "p", "rd", "My Android App - R&D", createdAt = 1, updatedAt = 1, lastMessageAt = 1))
        }
        val provider = FakeStreamingProvider()
        val service = ChatService(store, ProviderManager(mapOf(ProviderType.GEMINI to provider), sleeper = {}), { agent }, { rules })
        val events = service.send("s", "I want to add offline support to my application. What architecture should I use?").toList()
        assertThat(events.first()).isInstanceOf(ChatStreamEvent.Started::class.java)
        assertThat(events.last()).isInstanceOf(ChatStreamEvent.Completed::class.java)
        val assistant = store.messages.last { it.role == ChatRole.ASSISTANT }
        assertThat(assistant.content).isEqualTo("Hello world!")
        assertThat(assistant.status).isEqualTo(ChatMessageStatus.COMPLETED)
        assertThat(store.messages.any { it.role == ChatRole.USER }).isTrue()
    }

    @Test
    fun failureKeepsPartial() = runTest {
        val store = InMemoryChatPersistence().apply {
            put(ChatSession("s", "p", "rd", "t", createdAt = 1, updatedAt = 1, lastMessageAt = 1))
        }
        val provider = FakeStreamingProvider(chunks = listOf("Hello"), terminal = FakeStreamingProvider.Terminal.Fail)
        val service = ChatService(store, ProviderManager(mapOf(ProviderType.GEMINI to provider), sleeper = {}), { agent }, { rules })
        service.send("s", "hi").toList()
        val assistant = store.messages.last { it.role == ChatRole.ASSISTANT }
        assertThat(assistant.content).isEqualTo("Hello")
        assertThat(assistant.status).isEqualTo(ChatMessageStatus.FAILED)
    }
}
