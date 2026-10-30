package com.cadence.fit.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** What the voice-search control is doing right now. */
enum class VoiceSearchPhase {
    IDLE,
    LISTENING,
    PERMISSION_DENIED,
    UNAVAILABLE,
    ERROR,
}

/**
 * The voice-search UI state.
 *
 * [query] is a normal, editable search string. Speech is never used as a hidden
 * filter: it fills the same field the user can type into, and every failure keeps
 * text search available.
 */
data class VoiceSearchState(
    val phase: VoiceSearchPhase = VoiceSearchPhase.IDLE,
    val query: String = "",
    val message: String? = null,
) {
    val isListening: Boolean get() = phase == VoiceSearchPhase.LISTENING

    val hasNotice: Boolean get() = phase != VoiceSearchPhase.IDLE
}

/**
 * Pure state rules for the voice control, kept out of the composable so the
 * denied and unavailable paths can be tested without a device.
 */
object VoiceSearchPolicy {

    const val FALLBACK_MESSAGE = "You can type the search instead."

    fun onMicTapped(available: Boolean, permissionGranted: Boolean): VoiceSearchState = when {
        !available -> VoiceSearchState(
            phase = VoiceSearchPhase.UNAVAILABLE,
            message = "Voice search is not available on this device. $FALLBACK_MESSAGE",
        )

        !permissionGranted -> VoiceSearchState(
            phase = VoiceSearchPhase.PERMISSION_DENIED,
            message = "Microphone access is off, so Cadence cannot listen. $FALLBACK_MESSAGE",
        )

        else -> VoiceSearchState(phase = VoiceSearchPhase.LISTENING)
    }

    fun onPermissionResult(granted: Boolean, available: Boolean): VoiceSearchState = when {
        !available -> VoiceSearchState(
            phase = VoiceSearchPhase.UNAVAILABLE,
            message = "Voice search is not available on this device. $FALLBACK_MESSAGE",
        )

        !granted -> VoiceSearchState(
            phase = VoiceSearchPhase.PERMISSION_DENIED,
            message = "No microphone permission. $FALLBACK_MESSAGE",
        )

        else -> VoiceSearchState(phase = VoiceSearchPhase.LISTENING)
    }

    /** A finished phrase becomes an ordinary editable query. */
    fun onResult(text: String, previousQuery: String = ""): VoiceSearchState {
        val query = normalizeQuery(text)
        return if (query.isEmpty()) {
            VoiceSearchState(
                phase = VoiceSearchPhase.ERROR,
                query = previousQuery,
                message = "Nothing was recognised. $FALLBACK_MESSAGE",
            )
        } else {
            VoiceSearchState(phase = VoiceSearchPhase.IDLE, query = query)
        }
    }

    fun onError(permissionMissing: Boolean = false): VoiceSearchState = if (permissionMissing) {
        VoiceSearchState(
            phase = VoiceSearchPhase.PERMISSION_DENIED,
            message = "Microphone access was lost. $FALLBACK_MESSAGE",
        )
    } else {
        VoiceSearchState(
            phase = VoiceSearchPhase.ERROR,
            message = "Voice search did not finish. $FALLBACK_MESSAGE",
        )
    }

    fun onCancelled(): VoiceSearchState = VoiceSearchState()

    /**
     * Trims, collapses whitespace and drops a trailing full stop. The result is a
     * normal search string, not a command.
     */
    fun normalizeQuery(text: String): String = text
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.', ',', '!', '?', ';', ':')
        .trim()
}

/**
 * Thin wrapper around Android's on-device speech recogniser.
 *
 * It listens only while the screen is in the foreground: the screen cancels it on
 * stop and destroys it when it leaves composition. No audio is stored or sent by
 * Cadence; recognition is handled by the system service the user already chose.
 */
class VoiceInputController(
    context: Context,
    private val onReady: () -> Unit,
    private val onFinished: (String) -> Unit,
    private val onError: (permissionMissing: Boolean) -> Unit,
) : RecognitionListener {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    /** Cancelling produces an ERROR_CLIENT callback; it is not a user-facing failure. */
    private var ignoringCancelledError = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start() {
        ignoringCancelledError = false
        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(this)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { engine.startListening(intent) }
            .onFailure { onError(false) }
    }

    /** Stops waiting but keeps any result the recogniser already has. */
    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    /** Discards the current utterance without producing a user-facing error. */
    fun cancel() {
        ignoringCancelledError = true
        runCatching { recognizer?.cancel() }
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) = onReady()

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        onFinished(text)
    }

    override fun onError(error: Int) {
        if (ignoringCancelledError) {
            ignoringCancelledError = false
            return
        }
        onError(error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
