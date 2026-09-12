package com.agentflow.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentflow.data.repository.AgentRepository
import com.agentflow.data.repository.ChatRepository
import com.agentflow.domain.chat.ChatService
import com.agentflow.domain.chat.ChatStreamEvent
import com.agentflow.domain.model.Ids
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val sessionId: String,
    private val chatRepository: ChatRepository,
    private val agentRepository: AgentRepository,
    private val chatService: ChatService,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            val session = chatRepository.getSession(sessionId)
            val agent = session?.let { agentRepository.getAgent(it.agentId) }
            _state.update { it.copy(session = session, agent = agent) }
        }
        viewModelScope.launch {
            chatRepository.observeMessages(sessionId).collect { messages ->
                _state.update { current ->
                    current.copy(messages = messages, canSend = current.inputText.isNotBlank() && !current.isStreaming)
                }
            }
        }
        viewModelScope.launch {
            chatRepository.observeSession(sessionId).collect { session ->
                _state.update { it.copy(session = session) }
            }
        }
    }

    fun onInput(text: String) {
        _state.update { it.copy(inputText = text, canSend = text.isNotBlank() && !it.isStreaming) }
    }

    fun send() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isStreaming) return
        _state.update { it.copy(inputText = "", canSend = false, isStreaming = true, error = null) }
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            try {
                chatService.send(sessionId, text).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Started -> _state.update {
                            it.copy(isStreaming = true, streamingMessageId = event.messageId)
                        }
                        is ChatStreamEvent.Chunk -> _state.update {
                            it.copy(streamingMessageId = event.messageId, isStreaming = true)
                        }
                        is ChatStreamEvent.Completed -> _state.update {
                            it.copy(isStreaming = false, streamingMessageId = null)
                        }
                        is ChatStreamEvent.Failed -> _state.update {
                            it.copy(
                                isStreaming = false,
                                streamingMessageId = null,
                                error = chatService.userFacingError(event.error),
                            )
                        }
                        is ChatStreamEvent.Cancelled -> _state.update {
                            it.copy(isStreaming = false, streamingMessageId = null)
                        }
                    }
                }
            } finally {
                _state.update { it.copy(isStreaming = false, canSend = it.inputText.isNotBlank()) }
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        _state.update { it.copy(isStreaming = false, streamingMessageId = null) }
    }

    fun regenerate(assistantMessageId: String) {
        val messages = _state.value.messages
        val assistant = messages.firstOrNull { it.id == assistantMessageId } ?: return
        val user = messages.lastOrNull { it.role.name == "USER" && it.createdAt <= assistant.createdAt } ?: return
        if (_state.value.isStreaming) return
        _state.update { it.copy(isStreaming = true, error = null) }
        streamJob = viewModelScope.launch {
            chatService.send(sessionId, user.content, generationGroupId = assistant.generationGroupId ?: Ids.new())
                .collect { }
            _state.update { it.copy(isStreaming = false) }
        }
    }

    fun toggleBookmark(messageId: String) {
        viewModelScope.launch {
            val current = chatRepository.getMessage(messageId) ?: return@launch
            chatRepository.setBookmarked(messageId, !current.bookmarked)
        }
    }

    fun retryLastFailed() {
        val failed = _state.value.messages.lastOrNull { it.status.name == "FAILED" } ?: return
        regenerate(failed.id)
    }

    class Factory(
        private val sessionId: String,
        private val chatRepository: ChatRepository,
        private val agentRepository: AgentRepository,
        private val chatService: ChatService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(sessionId, chatRepository, agentRepository, chatService) as T
    }
}
