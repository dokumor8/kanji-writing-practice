package com.example.kanjipractice.domain.recognition

import com.example.kanjipractice.domain.model.Stroke
import kotlinx.coroutines.flow.StateFlow

/** Where the offline Japanese recognition model is. */
sealed interface ModelState {
    /** Nothing has been checked yet. */
    data object Unknown : ModelState
    data object Checking : ModelState
    /** Downloading the model; the first run needs network. */
    data object Downloading : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * Whole-character handwriting recognition (plan, section 5.2).
 *
 * Kept behind an interface so the review state machine can be driven by a fake
 * in tests without ML Kit or a device.
 */
interface RecognitionService {

    val modelState: StateFlow<ModelState>

    /**
     * Makes sure the recognizer is usable, downloading the Japanese model if
     * necessary. Safe to call repeatedly.
     *
     * @throws Exception when the model cannot be obtained; [modelState] then
     *   reports [ModelState.Failed].
     */
    suspend fun prepare()

    /**
     * Recognises the drawn strokes and returns candidate characters, best first.
     * An empty list means nothing legible was drawn.
     */
    suspend fun recognize(strokes: List<Stroke>): List<String>
}
