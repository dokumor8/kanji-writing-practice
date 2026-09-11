package com.example.kanjipractice.domain.recognition

import com.example.kanjipractice.domain.model.Stroke
import kotlinx.coroutines.flow.StateFlow

/** Where the offline Japanese recognition model is. */
sealed interface ModelState {
    /** Not checked yet; the UI must not offer a download until it knows. */
    data object Unknown : ModelState

    /** A check or a download is in flight. */
    data object Checking : ModelState

    /** Checked, and the model genuinely is not on the device. */
    data object NotDownloaded : ModelState

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
     * Checks whether the model is already on the device and updates [modelState]
     * accordingly, **without** downloading anything.
     *
     * This is what the deck screen calls on start. Skipping it is why the app
     * used to flash a "Download" button on every launch before noticing the model
     * was already there.
     */
    suspend fun refreshModelState()

    /**
     * Makes sure the recognizer is usable, downloading the Japanese model if
     * necessary. Safe to call repeatedly.
     *
     * @throws Exception when the model cannot be obtained; [modelState] then
     *   reports [ModelState.Failed].
     */
    suspend fun prepare()

    /**
     * Deletes the downloaded model and downloads it again.
     *
     * A model that is present but unusable is otherwise unrecoverable from inside
     * the app: it lives in app-private storage that the user cannot reach without
     * wiping the whole app (and their review history with it).
     */
    suspend fun reinstallModel()

    /**
     * Recognises the drawn strokes and returns candidate characters, best first.
     * An empty list means nothing legible was drawn.
     */
    suspend fun recognize(strokes: List<Stroke>): List<String>
}
