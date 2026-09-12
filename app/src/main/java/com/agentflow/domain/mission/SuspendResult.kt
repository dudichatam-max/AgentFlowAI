package com.agentflow.domain.mission

import kotlinx.coroutines.CancellationException

/** Like runCatching, but preserves coroutine cancellation as control flow. */
suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }
