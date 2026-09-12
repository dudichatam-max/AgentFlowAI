package com.agentflow.data.network.ai

import com.agentflow.domain.provider.ProviderError
import com.agentflow.domain.provider.ProviderErrorType
import com.agentflow.domain.provider.ProviderType
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object HttpErrorMapper {

    fun fromStatus(
        provider: ProviderType,
        status: Int,
        body: String,
        retryAfterMs: Long? = null,
    ): ProviderError {
        val type = when (status) {
            401 -> ProviderErrorType.AUTHENTICATION_ERROR
            403 -> ProviderErrorType.AUTHORIZATION_ERROR
            404 -> ProviderErrorType.MODEL_NOT_FOUND
            408 -> ProviderErrorType.TIMEOUT
            429 -> ProviderErrorType.RATE_LIMITED
            400, 422 -> ProviderErrorType.INVALID_REQUEST
            in 500..599 -> ProviderErrorType.SERVER_ERROR
            else -> ProviderErrorType.UNKNOWN
        }
        val snippet = ProviderError.sanitize(body).ifBlank { "HTTP $status" }
        return ProviderError.of(
            provider = provider,
            type = type,
            message = snippet,
            httpStatus = status,
            retryAfterMs = retryAfterMs,
        )
    }

    fun fromThrowable(provider: ProviderType, error: Throwable): ProviderError {
        val type = when (error) {
            is HttpRequestTimeoutException,
            is ConnectTimeoutException,
            is SocketTimeoutException,
            -> ProviderErrorType.TIMEOUT
            is UnknownHostException,
            is IOException,
            -> ProviderErrorType.NETWORK_ERROR
            else -> ProviderErrorType.UNKNOWN
        }
        return ProviderError.of(
            provider = provider,
            type = type,
            message = ProviderError.sanitize(error.message ?: error::class.java.simpleName),
        )
    }

    fun parseRetryAfterMs(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        header.toLongOrNull()?.let { return it * 1000 }
        return null
    }
}
