package com.agentflow.domain.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatMessageStateMachineTest {
    @Test
    fun streamingLifecycle() {
        assertThat(ChatMessageStateMachine.canTransition(ChatMessageStatus.PENDING, ChatMessageStatus.STREAMING)).isTrue()
        assertThat(ChatMessageStateMachine.canTransition(ChatMessageStatus.STREAMING, ChatMessageStatus.COMPLETED)).isTrue()
        assertThat(ChatMessageStateMachine.canTransition(ChatMessageStatus.COMPLETED, ChatMessageStatus.STREAMING)).isFalse()
        assertThat(ChatMessageStateMachine.canTransition(ChatMessageStatus.STREAMING, ChatMessageStatus.CANCELLED)).isTrue()
        assertThat(ChatMessageStateMachine.canTransition(ChatMessageStatus.PENDING, ChatMessageStatus.CANCELLED)).isTrue()
    }
}
