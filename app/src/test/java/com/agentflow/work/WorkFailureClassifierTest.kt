package com.agentflow.work

import com.agentflow.domain.retry.WorkFailureClassifier
import com.agentflow.domain.retry.WorkFailureKind
import com.agentflow.domain.validation.DomainException
import com.agentflow.domain.inspector.ArtifactPublicationException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import java.net.SocketTimeoutException
import org.junit.Test

class WorkFailureClassifierTest {
    @Test fun permanentDomainErrorDoesNotRetry() {
        assertThat(WorkFailureClassifier.classify(DomainException.MissionNotFound("m"))).isEqualTo(WorkFailureKind.PERMANENT)
    }

    @Test fun networkTimeoutIsTransient() {
        assertThat(WorkFailureClassifier.classify(SocketTimeoutException("timeout"))).isEqualTo(WorkFailureKind.TRANSIENT)
    }

    @Test fun artifactPublicationFailureIsTransient() {
        assertThat(WorkFailureClassifier.classify(ArtifactPublicationException(IllegalStateException("disk")))).isEqualTo(WorkFailureKind.TRANSIENT)
    }

    @Test fun cancellationIsNotRetryable() {
        assertThat(WorkFailureClassifier.classify(CancellationException("cancelled"))).isEqualTo(WorkFailureKind.CANCELLED)
    }
}
