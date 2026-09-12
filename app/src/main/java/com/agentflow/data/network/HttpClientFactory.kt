package com.agentflow.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {
    const val CONNECT_TIMEOUT_MS = 15_000L
    const val REQUEST_TIMEOUT_MS = 60_000L
    const val SOCKET_TIMEOUT_MS = 60_000L

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        isLenient = true
    }

    fun create(logEnabled: Boolean = false): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
        if (logEnabled) {
            install(Logging) {
                level = LogLevel.INFO
                logger = object : Logger {
                    override fun log(message: String) {
                        val redacted = redact(message)
                        android.util.Log.d("AgentFlowHttp", redacted)
                    }
                }
            }
        }
    }

    fun redact(message: String): String {
        var out = message
        out = Regex("(?i)(Authorization:\\s*Bearer\\s+)\\S+").replace(out, "$1[redacted]")
        out = Regex("(?i)(x-goog-api-key:\\s*)\\S+").replace(out, "$1[redacted]")
        out = Regex("(?i)(x-api-key:\\s*)\\S+").replace(out, "$1[redacted]")
        out = Regex("(?i)AIza[0-9A-Za-z\\-_]{10,}").replace(out, "[redacted]")
        out = Regex("(?i)gsk_[0-9A-Za-z]{8,}").replace(out, "[redacted]")
        out = Regex("(?i)sk-or-[0-9A-Za-z\\-_]{8,}").replace(out, "[redacted]")
        return out
    }
}
