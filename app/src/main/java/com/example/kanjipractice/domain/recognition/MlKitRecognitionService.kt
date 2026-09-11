package com.example.kanjipractice.domain.recognition

import android.content.Context
import android.util.Log
import com.example.kanjipractice.domain.model.Stroke
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit Digital Ink Recognition, Japanese model (plan, section 5.2).
 *
 * The model is downloaded on first use and is fully offline afterwards, so only
 * the download needs the network.
 */
@Singleton
class MlKitRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecognitionService {

    private val _modelState = MutableStateFlow<ModelState>(ModelState.Unknown)
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val mutex = Mutex()
    private var recognizer: DigitalInkRecognizer? = null

    override suspend fun refreshModelState() {
        if (recognizer != null) {
            _modelState.value = ModelState.Ready
            return
        }
        mutex.withLock {
            if (recognizer != null) {
                _modelState.value = ModelState.Ready
                return
            }
            try {
                _modelState.value = ModelState.Checking
                val model = buildModel()
                val downloaded = RemoteModelManager.getInstance()
                    .isModelDownloaded(model)
                    .awaitResult()
                if (downloaded) {
                    recognizer = createRecognizer(model)
                    _modelState.value = ModelState.Ready
                } else {
                    _modelState.value = ModelState.NotDownloaded
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not check the Japanese digital-ink model", e)
                _modelState.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    override suspend fun prepare() {
        if (recognizer != null) return
        mutex.withLock {
            if (recognizer != null) return
            try {
                _modelState.value = ModelState.Checking
                val model = buildModel()
                val manager = RemoteModelManager.getInstance()
                if (!manager.isModelDownloaded(model).awaitResult()) {
                    _modelState.value = ModelState.Downloading
                    // Default conditions are "any network"; the model is a few
                    // megabytes, so it is not restricted to Wi-Fi.
                    manager.download(model, DownloadConditions.Builder().build()).awaitResult()
                }
                recognizer = createRecognizer(model)
                _modelState.value = ModelState.Ready
            } catch (e: Exception) {
                Log.w(TAG, "Japanese digital-ink model unavailable", e)
                _modelState.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
                throw e
            }
        }
    }

    override suspend fun recognize(strokes: List<Stroke>): List<String> {
        if (strokes.all { it.isEmpty }) return emptyList()
        if (recognizer == null) prepare()
        val client = recognizer ?: return emptyList()

        val ink = buildInk(StrokeNormalizer.normalize(strokes))
        if (ink.strokes.isEmpty()) return emptyList()

        // Telling the model the size of the writing area it is looking at lets it
        // calibrate stroke width and speed, which is the documented way to get
        // better results from normalised ink.
        val context = RecognitionContext.builder()
            .setWritingArea(
                WritingArea(
                    StrokeNormalizer.DEFAULT_TARGET_SIZE,
                    StrokeNormalizer.DEFAULT_TARGET_SIZE,
                )
            )
            .build()

        // ML Kit 19.x returns a RecognitionResult wrapper; older releases (and
        // the plan's sample code) handed back the candidate list directly.
        return client.recognize(ink, context).awaitResult().candidates.map { it.text }
    }

    private fun buildModel(): DigitalInkRecognitionModel {
        val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANGUAGE_TAG)
            ?: error("ML Kit has no digital-ink model for language tag '$LANGUAGE_TAG'")
        return DigitalInkRecognitionModel.builder(identifier).build()
    }

    private fun createRecognizer(model: DigitalInkRecognitionModel): DigitalInkRecognizer =
        DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())

    /**
     * Builds ML Kit ink from normalised strokes.
     *
     * Real sample times are used where the canvas provided them, because the
     * recogniser models stroke speed. Points with no timestamp (tests, synthetic
     * ink) fall back to a plausible fixed sampling interval, and timestamps are
     * forced to increase because that is what the API expects.
     */
    private fun buildInk(strokes: List<Stroke>): Ink {
        val builder = Ink.builder()
        var lastTimestamp = 0L
        for (stroke in strokes) {
            if (stroke.isEmpty) continue
            val strokeBuilder = Ink.Stroke.builder()
            for (point in stroke.points) {
                val timestamp = when {
                    point.timestampMillis > lastTimestamp -> point.timestampMillis
                    else -> lastTimestamp + FALLBACK_STEP_MILLIS
                }
                strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, timestamp))
                lastTimestamp = timestamp
            }
            builder.addStroke(strokeBuilder.build())
        }
        return builder.build()
    }

    private companion object {
        const val TAG = "MlKitRecognition"

        /** Japanese. The app is a kanji trainer, so this is the only model. */
        const val LANGUAGE_TAG = "ja"

        /** Used only for points that arrived without a timestamp. */
        const val FALLBACK_STEP_MILLIS = 10L
    }
}
