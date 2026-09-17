package com.example.kanjipractice.domain.recognition

import android.content.Context
import android.util.Log
import com.example.kanjipractice.domain.deck.ScriptProfile
import com.example.kanjipractice.domain.model.Stroke
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit Digital Ink Recognition (plan, section 5.2).
 *
 * Which language model to use comes from [ScriptProfile] and never from a literal
 * in this file. It was a literal once, which meant the Chinese app silently
 * downloaded and used the Japanese model: every character it could recognise was
 * one that exists in Japanese, and 你 could never be recognised at all.
 *
 * The model is downloaded on first use and is fully offline afterwards, so only
 * the download needs the network.
 */
@Singleton
class MlKitRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val script: ScriptProfile,
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

    /**
     * Throws the downloaded model away and fetches it again. The recovery path
     * for a model that reports as present but does not work, which is otherwise
     * only fixable by clearing the whole app's data.
     */
    override suspend fun reinstallModel() {
        mutex.withLock {
            try {
                recognizer?.close()
                recognizer = null
                _modelState.value = ModelState.Checking
                val model = buildModel()
                val manager = RemoteModelManager.getInstance()
                if (manager.isModelDownloaded(model).awaitResult()) {
                    manager.deleteDownloadedModel(model).awaitResult()
                }
                _modelState.value = ModelState.Downloading
                manager.download(model, DownloadConditions.Builder().build()).awaitResult()
                recognizer = createRecognizer(model)
                _modelState.value = ModelState.Ready
            } catch (e: Exception) {
                Log.w(TAG, "Could not reinstall the Japanese digital-ink model", e)
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

        // The single-argument overload, deliberately.
        //
        // 1.1 briefly passed a RecognitionContext describing the writing area.
        // That is the documented way to give the model more to work with, but it
        // made recognition fail outright on a real device while this exact call
        // had been working, so it was reverted. If it is ever revisited it must
        // be measured on a device, not assumed.
        //
        // ML Kit 19.x returns a RecognitionResult wrapper; older releases (and
        // the plan's sample code) handed back the candidate list directly.
        return client.recognize(ink).awaitResult().candidates.map { it.text }
    }

    private fun buildModel(): DigitalInkRecognitionModel {
        val tag = script.recognitionLanguageTag
        val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)
            ?: error("ML Kit has no digital-ink model for language tag '$tag'")
        return DigitalInkRecognitionModel.builder(identifier).build()
    }

    private fun createRecognizer(model: DigitalInkRecognitionModel): DigitalInkRecognizer =
        DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())

    /**
     * Builds ML Kit ink from normalised strokes.
     *
     * Points carry evenly spaced timestamps. Passing the device's real uptime was
     * tried and dropped: it is a change to a code path that works, for a benefit
     * that could not be measured without a device.
     */
    private fun buildInk(strokes: List<Stroke>): Ink {
        val builder = Ink.builder()
        var time = 0L
        for (stroke in strokes) {
            if (stroke.isEmpty) continue
            val strokeBuilder = Ink.Stroke.builder()
            for (point in stroke.points) {
                strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, time))
                time += TIME_STEP_MILLIS
            }
            builder.addStroke(strokeBuilder.build())
        }
        return builder.build()
    }

    private companion object {
        const val TAG = "MlKitRecognition"

        /** Simulated sampling interval between recorded points. */
        const val TIME_STEP_MILLIS = 10L
    }
}
