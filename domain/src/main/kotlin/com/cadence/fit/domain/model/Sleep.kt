package com.cadence.fit.domain.model

enum class SleepStage(val displayName: String) {
    AWAKE("Awake"),
    LIGHT("Light"),
    DEEP("Deep"),
    REM("REM"),
    UNKNOWN("Unspecified"),
}

/**
 * The *minimum* sleep information Cadence uses. Deliberately small: duration and
 * session timing. Journal notes, subjective ratings and any free text are never
 * read, and nothing here is sent to a third party.
 */
data class SleepSummary(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val durationMinutes: Int,
    val stages: Map<SleepStage, Int> = emptyMap(),
    /** Package that wrote the session, e.g. the sleep app the user trusts. */
    val sourcePackage: String? = null,
    val sourceLabel: String? = null,
) {
    val midpointMs: Long get() = startMs + (endMs - startMs) / 2
}

/** Whether the device can deliver sleep data at all. */
enum class SleepAvailability(val displayName: String, val explanation: String) {
    AVAILABLE(
        "Available",
        "Health Connect is installed and ready to share sleep sessions.",
    ),
    PROVIDER_UPDATE_REQUIRED(
        "Update required",
        "Health Connect is installed but needs an update before it can share sleep data.",
    ),
    NOT_SUPPORTED(
        "Not supported on this device",
        "This device or Android version cannot run Health Connect, so sleep sync is unavailable.",
    ),
}

enum class SleepPermissionState { UNKNOWN, GRANTED, DENIED, REVOKED }

/**
 * The single source of truth the sleep UI renders. Every state is explicit so the
 * screen can explain exactly what is going on instead of failing silently.
 */
data class SleepConnectionStatus(
    val availability: SleepAvailability = SleepAvailability.NOT_SUPPORTED,
    val permission: SleepPermissionState = SleepPermissionState.UNKNOWN,
    val enabled: Boolean = false,
    val lastSyncMs: Long? = null,
    val sessionCount: Int = 0,
    val errorMessage: String? = null,
) {
    /** True only when data could actually be read right now. */
    val isConnected: Boolean
        get() = availability == SleepAvailability.AVAILABLE &&
            permission == SleepPermissionState.GRANTED &&
            enabled

    val isActionable: Boolean
        get() = availability == SleepAvailability.AVAILABLE
}

/** What a refresh attempt produced, so the UI can react precisely. */
sealed interface SleepRefreshResult {
    data class Success(val sessions: List<SleepSummary>) : SleepRefreshResult

    /** Health Connect is missing or too old on this device. */
    data class Unavailable(val availability: SleepAvailability) : SleepRefreshResult

    /** The user denied or revoked the sleep permission. */
    data object PermissionMissing : SleepRefreshResult

    /** The feature is switched off in privacy settings. */
    data object Disabled : SleepRefreshResult

    /** Something unexpected happened; the message is safe to display. */
    data class Failed(val message: String) : SleepRefreshResult
}
