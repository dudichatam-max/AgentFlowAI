package com.agentflow.domain.retry

import com.agentflow.domain.validation.DomainException
import com.agentflow.domain.inspector.ArtifactPublicationException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException

/** Classification used only at the WorkManager boundary. */
enum class WorkFailureKind { TRANSIENT, PERMANENT, CANCELLED }

object WorkFailureClassifier {
    fun classify(error: Throwable): WorkFailureKind = when (error) {
        is CancellationException -> WorkFailureKind.CANCELLED
        is SocketTimeoutException, is IOException, is ArtifactPublicationException -> WorkFailureKind.TRANSIENT
        is DomainException -> WorkFailureKind.PERMANENT
        else -> WorkFailureKind.PERMANENT
    }
}
