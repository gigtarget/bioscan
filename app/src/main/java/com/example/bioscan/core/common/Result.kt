package com.example.bioscan.core.common

sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val exception: Throwable, val message: String? = exception.localizedMessage) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}

enum class KioskModeState {
    INACTIVE,
    ACTIVE,
    LOCKED,
    PROVISIONING_REQUIRED
}

enum class TerminalDirectionMode {
    SMART_AUTO,
    CHECK_IN_ONLY,
    CHECK_OUT_ONLY,
    MANUAL_PROMPT
}

enum class LivenessMode {
    OFF,
    STANDARD,
    STRICT
}
