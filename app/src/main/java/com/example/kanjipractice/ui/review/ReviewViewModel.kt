package com.example.kanjipractice.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.recognition.RecognitionMatcher
import com.example.kanjipractice.domain.recognition.RecognitionService
import com.example.kanjipractice.domain.repository.CardRepository
import com.example.kanjipractice.domain.repository.ReviewLogRepository
import com.example.kanjipractice.domain.scheduler.ReviewScheduler
import com.example.kanjipractice.domain.stroke.StrokeDiagramProvider
import com.example.fsrs.Rating
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Owns the review state machine (plan, section 6).
 *
 * The session's due queue is snapshotted when the session starts, which is what
 * an SRS session should do: a card rescheduled during the session does not jump
 * back into this session's queue.
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val scheduler: ReviewScheduler,
    private val recognitionService: RecognitionService,
    private val strokeDataService: StrokeDiagramProvider,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var queue: List<com.example.kanjipractice.data.db.CardEntity> = emptyList()
    private var position = 0

    // Per-card state, reset whenever a new card is shown.
    private var strokes: List<Stroke> = emptyList()
    private var retryCount = 0
    private var usedIDontKnow = false
    private var userRating: Rating? = null

    private var messageJob: Job? = null

    init {
        startSession()
    }

    private val remaining: Int get() = (queue.size - position).coerceAtLeast(0)

    fun startSession() {
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            queue = cardRepository.observeDue(LocalDateTime.now(clock)).first()
            position = 0
            if (queue.isEmpty()) {
                _uiState.value = ReviewUiState.SessionComplete
                return@launch
            }
            showCurrentCard()
            // Warm the recogniser up in the background so the first Submit is not
            // the thing that waits for the model download. Failure is reported by
            // Submit instead, where the user can actually do something about it.
            launch { runCatching { recognitionService.prepare() } }
        }
    }

    private fun showCurrentCard() {
        val card = queue.getOrNull(position) ?: run {
            _uiState.value = ReviewUiState.SessionComplete
            return
        }
        strokes = emptyList()
        retryCount = 0
        usedIDontKnow = false
        userRating = null
        _uiState.value = ReviewUiState.Prompt(
            card = card,
            strokes = emptyList(),
            retryCount = 0,
            message = null,
            busy = false,
            remaining = remaining,
        )
    }

    // ------------------------------------------------------------- drawing

    fun onStrokeFinished(stroke: Stroke) = mutatePrompt { state ->
        strokes = strokes + stroke
        state.copy(strokes = strokes)
    }

    /** Removes the last stroke. The drawing survives failed attempts. */
    fun undo() = mutatePrompt { state ->
        strokes = strokes.dropLast(1)
        state.copy(strokes = strokes)
    }

    fun clear() = mutatePrompt { state ->
        strokes = emptyList()
        state.copy(strokes = strokes)
    }

    // ------------------------------------------------------------- actions

    /** Submit from PROMPT: recognise, then succeed or stay put (plan 6.2). */
    fun submit() {
        val state = _uiState.value as? ReviewUiState.Prompt ?: return
        if (state.busy) return
        if (strokes.isEmpty()) {
            setPromptMessage(state, MESSAGE_EMPTY_DRAWING, clearAfterMillis = null)
            return
        }
        _uiState.value = state.copy(busy = true, message = null)

        viewModelScope.launch {
            val candidates = try {
                recognitionService.prepare()
                recognitionService.recognize(strokes)
            } catch (e: Exception) {
                // An infrastructure failure is not a failed attempt: it must not
                // count towards retryCount, and it must not fail the card.
                setPromptMessage(
                    _uiState.value as? ReviewUiState.Prompt ?: return@launch,
                    MESSAGE_RECOGNITION_UNAVAILABLE,
                    clearAfterMillis = null,
                )
                return@launch
            }

            if (RecognitionMatcher.isCorrect(state.card.character, candidates)) {
                _uiState.value = ReviewUiState.Success(
                    card = state.card,
                    recognized = RecognitionMatcher.bestCandidate(candidates),
                    rating = null,
                    // Shown for self-check: the user confirms their stroke order.
                    diagram = strokeDataService.diagramFor(state.card.character),
                    remaining = remaining,
                    retryCount = retryCount,
                )
            } else {
                retryCount++
                val current = _uiState.value as? ReviewUiState.Prompt ?: return@launch
                setPromptMessage(
                    current.copy(retryCount = retryCount, strokes = strokes),
                    MESSAGE_NOT_QUITE,
                    clearAfterMillis = TRANSIENT_MESSAGE_MILLIS,
                )
            }
        }
    }

    /**
     * "I don't know" (plan 6.4): fail the card, show the diagram, and force a
     * tracing step. The card's rating is locked to Again from here on.
     */
    fun iDontKnow() {
        val state = _uiState.value as? ReviewUiState.Prompt ?: return
        usedIDontKnow = true
        // RELEARN gets a fresh, empty canvas over the diagram.
        strokes = emptyList()
        viewModelScope.launch {
            val diagram = strokeDataService.diagramFor(state.card.character)
            _uiState.value = ReviewUiState.Relearn(
                card = state.card,
                strokes = emptyList(),
                diagram = diagram,
                message = null,
                busy = false,
                remaining = remaining,
            )
        }
    }

    fun onRelearnStrokeFinished(stroke: Stroke) = mutateRelearn { state ->
        strokes = strokes + stroke
        state.copy(strokes = strokes)
    }

    fun undoRelearn() = mutateRelearn { state ->
        strokes = strokes.dropLast(1)
        state.copy(strokes = strokes)
    }

    fun clearRelearn() = mutateRelearn { state ->
        strokes = emptyList()
        state.copy(strokes = strokes)
    }

    /**
     * Tracing is motor practice, not a test, so a non-empty drawing always
     * passes (plan 6.4). The card is scheduled as Again and the session advances.
     */
    fun submitRelearn() {
        val state = _uiState.value as? ReviewUiState.Relearn ?: return
        if (state.busy) return
        if (strokes.isEmpty()) {
            setRelearnMessage(state, MESSAGE_EMPTY_TRACING)
            return
        }
        _uiState.value = state.copy(busy = true, message = null)
        viewModelScope.launch { persistAndAdvance(Rating.AGAIN) }
    }

    /** Stores the rating on the SUCCESS screen; Next is what commits it. */
    fun rate(rating: Rating) {
        val state = _uiState.value as? ReviewUiState.Success ?: return
        userRating = rating
        _uiState.value = state.copy(rating = rating)
    }

    fun next() {
        val state = _uiState.value as? ReviewUiState.Success ?: return
        val rating = userRating ?: return
        viewModelScope.launch { persistAndAdvance(rating) }
    }

    // --------------------------------------------------------------- plumbing

    private suspend fun persistAndAdvance(rating: Rating) {
        val card = queue.getOrNull(position)
        if (card != null) {
            val now = LocalDateTime.now(clock)
            // Again can only come from the "I don't know" path, never from the
            // success screen (plan 4.3), so the log records which it was.
            cardRepository.update(scheduler.schedule(card, rating, now))
            reviewLogRepository.log(
                cardId = card.id,
                rating = rating,
                usedIDontKnow = usedIDontKnow,
                retryCount = retryCount,
                reviewedAt = now,
            )
        }
        position++
        if (position >= queue.size) {
            _uiState.value = ReviewUiState.SessionComplete
        } else {
            showCurrentCard()
        }
    }

    private inline fun mutatePrompt(
        crossinline block: (ReviewUiState.Prompt) -> ReviewUiState.Prompt,
    ) {
        val state = _uiState.value as? ReviewUiState.Prompt ?: return
        if (state.busy) return
        _uiState.value = block(state)
    }

    private inline fun mutateRelearn(
        crossinline block: (ReviewUiState.Relearn) -> ReviewUiState.Relearn,
    ) {
        val state = _uiState.value as? ReviewUiState.Relearn ?: return
        if (state.busy) return
        _uiState.value = block(state)
    }

    private fun setPromptMessage(
        state: ReviewUiState.Prompt,
        message: String,
        clearAfterMillis: Long?,
    ) {
        _uiState.value = state.copy(busy = false, message = message, strokes = strokes)
        if (clearAfterMillis != null) scheduleMessageClear(clearAfterMillis)
    }

    private fun setRelearnMessage(state: ReviewUiState.Relearn, message: String) {
        _uiState.value = state.copy(busy = false, message = message)
    }

    /**
     * The failure message is transient: it must not sit there nagging the user,
     * who is free to retry for as long as they like (plan 6.2).
     */
    private fun scheduleMessageClear(afterMillis: Long) {
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(afterMillis)
            when (val state = _uiState.value) {
                is ReviewUiState.Prompt -> _uiState.value = state.copy(message = null)
                is ReviewUiState.Relearn -> _uiState.value = state.copy(message = null)
                else -> Unit
            }
        }
    }

    companion object {
        const val TRANSIENT_MESSAGE_MILLIS = 2_500L
        const val MESSAGE_NOT_QUITE = "Not quite - try again."
        const val MESSAGE_EMPTY_DRAWING = "Draw the character first."
        const val MESSAGE_EMPTY_TRACING = "Trace the character to continue."
        const val MESSAGE_RECOGNITION_UNAVAILABLE =
            "Recognition is unavailable. Check the model download and try again."
    }
}
