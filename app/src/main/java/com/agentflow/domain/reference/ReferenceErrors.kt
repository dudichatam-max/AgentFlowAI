package com.agentflow.domain.reference

enum class ReferenceErrorType {
    INVALID_FILE,
    FILE_TOO_LARGE,
    UNSUPPORTED_FILE,
    STORAGE_ERROR,
    HASH_ERROR,
    PERMISSION_ERROR,
    INVALID_URL,
    NOT_FOUND,
    RATE_LIMITED,
    UNAUTHORIZED,
    FORBIDDEN,
    INVALID_REF,
    CONTENT_TOO_LARGE,
    NETWORK_ERROR,
    TIMEOUT,
    SERVER_ERROR,
    INVALID_REPOSITORY,
    SECRET_FILE,
}

class ReferenceException(
    val type: ReferenceErrorType,
    message: String,
) : Exception(message)
