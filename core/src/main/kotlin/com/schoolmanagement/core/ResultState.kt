package com.schoolmanagement.core

sealed interface ResultState<out T> {
    data class Success<T>(val value: T) : ResultState<T>
    data class Error(val message: String, val throwable: Throwable? = null) : ResultState<Nothing>
    data object Loading : ResultState<Nothing>
}
