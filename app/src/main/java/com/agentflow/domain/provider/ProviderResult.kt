package com.agentflow.domain.provider

sealed class ProviderResult<out T> {
    data class Success<T>(val value: T) : ProviderResult<T>()
    data class Failure(val error: ProviderError) : ProviderResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    fun getOrNull(): T? = (this as? Success)?.value
}

inline fun <T, R> ProviderResult<T>.map(transform: (T) -> R): ProviderResult<R> = when (this) {
    is ProviderResult.Success -> ProviderResult.Success(transform(value))
    is ProviderResult.Failure -> this
}
