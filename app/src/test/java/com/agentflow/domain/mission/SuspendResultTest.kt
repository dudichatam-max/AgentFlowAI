package com.agentflow.domain.mission

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SuspendResultTest {
    @Test
    fun cancellationEscapesInsteadOfBecomingFailure() = runTest {
        var escaped = false
        try {
            suspendRunCatching<Unit> { throw CancellationException("cancel") }
        } catch (_: CancellationException) {
            escaped = true
        }
        assertThat(escaped).isTrue()
    }
}
