package dev.harlow.core.common

sealed interface HarlowResult<out T> {
    data class Success<T>(val data: T) : HarlowResult<T>
    data class Error(val exception: Throwable) : HarlowResult<Nothing>
    data object Loading : HarlowResult<Nothing>
}
