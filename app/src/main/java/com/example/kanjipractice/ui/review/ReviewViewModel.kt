package com.example.kanjipractice.ui.review

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.recognition.RecognitionMatcher
import com.example.kanjipractice.domain.recognition.RecognitionService
import com.example.kanjipractice.domain.repository.CardRepository
import com.example.kanjipractice.domain.repository.ReviewLogRepository
import com.example.kanjipractice.domain.scheduler.ReviewScheduler
import com.example.kanjipractice.domain.session.StudyQueueBuilder
import com.example.kanjipractice.domain.settings.StudySettings
import com.example.kanjipractice.domain.settings.StudySettingsRepository
import com.example.kanjipractice.domain.stroke.StrokeDiagramProvider
import com.example.kanjipractice.domain.stroke.StrokeSimilarity
import com.example.kanjipractice.domain.util.DayBoundary
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
import java.time.ZoneId
import javax.inject.Inject

/**
 * Owns the review state machine.
 *
 * The session's queue is built once when the session starts, which is what an
 * SRS session should do: a card rescheduled during the session does not jump
 * back into this session's queue.
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val settingsRepository: StudySettingsRepository,
    private val scheduler: ReviewScheduler,
    private val recognitionService: RecognitionService,
    private val strokeDataService: StrokeDiagramProvider,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    /**
     * Mutable because a lapsed card is re-appended to the session rather than
     * deferred by the clock: "Again" should come back later in this sitting.
     */
    private var queue: List<CardEntity> = emptyList()
    private var position = 0

    // Per-card state, reset whenever a new card is shown.
    private var strokes: List<Stroke> = emptyList()
    private var retryCount = 0
    private var hintCount = 0

    private var messageJob: Job? = null
    private var undoRecord: UndoRecord? = null

    /** How a drawing is judged, read when the session starts. */
    /** Set once the user chooses to draw again over the guide. */
    private var usedGuide = false

    private var acceptedCandidates = StudySettings.DEFAULT_ACCEPTED_CANDIDATES
    private var similarityThreshold = StudySettings.DEFAULT_SIMILARITY_PERCENT / 100.0

    /**
     * Everything needed to take a review back. Only the most recent review can be
     * undone (the same one-level undo Anki offers), which is enough to fix a
     * misclick without turning the review log into a general edit surface.
     */
    private data class UndoRecord(
        val cardIndex: Int,
        /** The card as it was before the review, from the session snapshot. */
        val cardBefore: CardEntity,
        val logId: Long,
        /** The screen to put the user back on, so they can just re-rate. */
        val stateBefore: ReviewUiState,
        val hintCount: Int,
        val retryCount: Int,
        /** True when this review appended a copy of the card to the queue. */
        val requeued: Boolean,
    )

    init {
        startSession()
    }

    private val remaining: Int get() = (queue.size - position).coerceAtLeast(0)

    fun startSession() {
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            queue = buildQueue()
            position = 0
            undoRecord = null
            if (queue.isEmpty()) {
                _uiState.value = ReviewUiState.SessionComplete(canUndo = false)
                return@launch
            }
            showCurrentCard()
            // Warm the recogniser up in the background so the first Submit is not
            // the thing that waits for the model download.
            launch { runCatching { recognitionService.prepare() } }
        }
    }

    private suspend fun buildQueue(): List<CardEntity> {
        val settings = currentStudySettings()
        acceptedCandidates = settings.acceptedCandidates
        similarityThreshold = settings.similarityThreshold
        val deckIds = settingsRepository.observeSelectedDeckIds().first()
        // Everything due today, not everything due this minute.
        val dueReviews = cardRepository
            .observeDueReviews(DayBoundary.endOfStudyDay(clock, zone), deckIds)
            .first()
        val newCards = cardRepository.nextNewCards(settings.remainingNewAllowance, deckIds)
        return StudyQueueBuilder.build(dueReviews, newCards, settings.remainingNewAllowance)
    }

    private suspend fun currentStudySettings(): StudySettings {
        val limit = settingsRepository.observeDailyNewLimit().first()
        val introduced = reviewLogRepository
            .observeIntroducedSince(DayBoundary.startOfStudyDay(clock, zone))
            .first()
        return StudySettings(
            dailyNewLimit = limit,
            introducedToday = introduced,
            acceptedCandidates = settingsRepository.observeAcceptedCandidates().first(),
            similarityThresholdPercent =
                settingsRepository.observeSimilarityThresholdPercent().first(),
        )
    }

    private suspend fun showCurrentCard() {
        val card = queue.getOrNull(position)
        if (card == null) {
            _uiState.value = ReviewUiState.SessionComplete(canUndo = undoRecord != null)
            return
        }
        strokes = emptyList()
        retryCount = 0
        hintCount = 0
        usedGuide = false
        _uiState.value = ReviewUiState.Prompt(
            card = card,
            diagram = strokeDataService.diagramFor(card.character),
            strokes = emptyList(),
            retryCount = 0,
            hintCount = 0,
            hintVisible = false,
            message = null,
            recognitionFailed = false,
            busy = false,
            remaining = remaining,
            canUndo = undoRecord != null,
        )
    }

    // ------------------------------------------------------------- drawing

    fun onStrokeFinished(stroke: Stroke) = mutatePrompt { state ->
        strokes = strokes + stroke
        state.copy(strokes = strokes)
    }

    /** Removes the last stroke. The drawing survives failed attempts. */
    fun undoStroke() = mutatePrompt { state ->
        strokes = strokes.dropLast(1)
        state.copy(strokes = strokes)
    }

    fun clear() = mutatePrompt { state ->
        strokes = emptyList()
        state.copy(strokes = strokes)
    }

    // ---------------------------------------------------------------- hint

    /**
     * "I don't know" opens the stroke hint. It no longer fails the card by
     * itself: the user closes the popup, draws from memory, and the result screen
     * opens with Again selected, which they may change if they genuinely recalled
     * it after the glance.
     */
    fun showHint() = mutatePrompt { state ->
        hintCount++
        state.copy(hintVisible = true, hintCount = hintCount)
    }

    fun dismissHint() = mutatePrompt { state -> state.copy(hintVisible = false) }

    // ------------------------------------------------------------- actions

    /** Submit from PROMPT: recognise, then succeed or stay put. */
    fun submit() {
        val state = _uiState.value as? ReviewUiState.Prompt ?: return
        if (state.busy) return
        if (strokes.isEmpty()) {
            setPromptMessage(state, MESSAGE_EMPTY_DRAWING, clearAfterMillis = null)
            return
        }
        _uiState.value = state.copy(busy = true, message = null, recognitionFailed = false)

        viewModelScope.launch {
            val candidates = try {
                recognitionService.prepare()
                recognitionService.recognize(strokes)
            } catch (e: Exception) {
                // An infrastructure failure is not a failed attempt: it must not
                // count towards retryCount, and it must not fail the card. The
                // real cause goes on screen and into logcat, because "recognition
                // failed" on its own is not something anyone can act on.
                Log.w(TAG, "Recognition failed for " + state.card.character, e)
                val current = _uiState.value as? ReviewUiState.Prompt ?: return@launch
                _uiState.value = current.copy(
                    busy = false,
                    strokes = strokes,
                    message = recognitionErrorMessage(e),
                    recognitionFailed = true,
                )
                return@launch
            }

            // Two questions, not one. The recogniser answers "which character is
            // this?", which a drawing can win while being visibly wrong; the shape
            // comparison answers "did you draw *this* character?", which a correct
            // drawing can only fail if it really is off.
            val similarity = StrokeSimilarity.compare(strokes, state.diagram)
            val ranked = RecognitionMatcher.isCorrect(
                target = state.card.character,
                candidates = candidates,
                acceptedCandidates = acceptedCandidates,
            )
            val shapeOk = similarity == null || similarity.score >= similarityThreshold

            if (ranked && shapeOk) {
                _uiState.value = ReviewUiState.Success(
                    card = state.card,
                    diagram = state.diagram,
                    strokes = strokes,
                    recognized = RecognitionMatcher.bestCandidate(candidates),
                    rating = defaultRating(),
                    hintCount = hintCount,
                    retryCount = retryCount,
                    remaining = remaining,
                    similarityPercent = similarity?.percent,
                    canUndo = undoRecord != null,
                )
            } else {
                retryCount++
                val current = _uiState.value as? ReviewUiState.Prompt ?: return@launch
                val read = RecognitionMatcher.bestCandidate(candidates)
                // Naming what the recogniser saw turns "wrong" into something the
                // user can act on, and is the only way to diagnose a character
                // the model will not accept from a distance.
                Log.i(
                    TAG,
                    "Rejected " + state.card.character + ": ranked=" + ranked +
                        " shape=" + (similarity?.percent ?: -1) + " best=" + read,
                )
                // Saying which of the two failed, and naming the character the
                // recogniser saw, turns "wrong" into something to act on.
                val message = when {
                    ranked && similarity != null ->
                        "Not quite - shape match " + similarity.percent + "%"
                    !read.isNullOrBlank() -> "Not quite - I read that as " + read
                    else -> MESSAGE_NOT_QUITE
                }
                setPromptMessage(
                    current.copy(retryCount = retryCount, strokes = strokes),
                    message,
                    clearAfterMillis = TRANSIENT_MESSAGE_MILLIS,
                )
            }
        }
    }

    /**
     * Skips machine checking for this card and lets the user grade themselves.
     *
     * Only reachable after the recogniser has actually failed, so it cannot be
     * used to dodge a drawing that was merely wrong. Without it, a broken ML Kit
     * install leaves the user unable to finish any card at all.
     */
    fun gradeManually() {
        val state = _uiState.value as? ReviewUiState.Prompt ?: return
        // Enforced here, not just by which button happens to be on screen: this
        // must never become a way to skip drawing a character.
        if (!state.recognitionFailed) return
        _uiState.value = ReviewUiState.Success(
            card = state.card,
            diagram = state.diagram,
            strokes = strokes,
            recognized = null,
            // No default here: nothing verified the drawing, so the user has to
            // make a deliberate choice rather than accept a suggestion.
            rating = null,
            hintCount = hintCount,
            retryCount = retryCount,
            remaining = remaining,
            gaveUp = false,
            canUndo = undoRecord != null,
        )
    }

    /**
     * Which rating the result screen opens with.
     *
     * The suggestion is the app's honest guess at how the recall went, and the
     * user is always free to disagree:
     *  - a peek at the hint means it was not recalled, so Again;
     *  - needing more than one attempt is a shaky recall, so Hard;
     *  - first time right is the ordinary good outcome, so Good.
     * Easy is never suggested: only the user can say a card was effortless.
     */
    private fun defaultRating(): Rating = when {
        // Seeing the answer at all -- through the hint, or by choosing to draw it
        // again over the guide -- means it was not recalled.
        hintCount > 0 || usedGuide -> Rating.AGAIN
        retryCount > 0 -> Rating.HARD
        else -> Rating.GOOD
    }

    /**
     * Back to the canvas with the reference diagram showing, for deliberate
     * practice after a review that went through.
     *
     * Until now the only way to practise a character was to fail it, which is
     * backwards: the ones worth practising are the ones you nearly know. What is
     * drawn here is practice, not recall, so the next rating is suggested Again.
     */
    fun drawAgain() {
        val state = _uiState.value as? ReviewUiState.Success ?: return
        usedGuide = true
        strokes = emptyList()
        retryCount = 0
        _uiState.value = ReviewUiState.Prompt(
            card = state.card,
            diagram = state.diagram,
            strokes = emptyList(),
            retryCount = 0,
            hintCount = hintCount,
            hintVisible = false,
            message = null,
            recognitionFailed = false,
            guideVisible = true,
            busy = false,
            remaining = remaining,
            canUndo = undoRecord != null,
        )
    }

    /**
     * Abandons the card.
     *
     * The recogniser rejecting a drawing the user cannot improve is a dead end
     * otherwise: every other route to the next card runs through getting it
     * accepted. Giving up rates the card Again and moves on, which is also the
     * honest record of what happened.
     */
    fun giveUp() {
        val state = _uiState.value as? ReviewUiState.Prompt ?: return
        if (state.busy) return
        _uiState.value = ReviewUiState.Success(
            card = state.card,
            diagram = state.diagram,
            strokes = strokes,
            recognized = null,
            rating = Rating.AGAIN,
            hintCount = hintCount,
            retryCount = retryCount,
            remaining = remaining,
            gaveUp = true,
            canUndo = undoRecord != null,
        )
    }

    /** Stores the rating on the success screen; Next is what commits it. */
    fun rate(rating: Rating) {
        val state = _uiState.value as? ReviewUiState.Success ?: return
        _uiState.value = state.copy(rating = rating)
    }

    /**
     * Commits the review that is currently displayed.
     *
     * The rating is read from the state rather than from a separate field: the
     * suggested rating shown on screen *is* the answer, so Next works immediately
     * whether the user picked it or the app did.
     */
    fun next() {
        val state = _uiState.value as? ReviewUiState.Success ?: return
        val rating = state.rating ?: return
        viewModelScope.launch { persistAndAdvance(state, rating) }
    }

    /**
     * Takes the last committed review back: the log row is deleted, the card is
     * restored to the state it had before, and the user is put back on the rating
     * screen with the rating cleared.
     *
     * Deleting the log row also, and deliberately, un-counts the card against the
     * daily new-card allowance.
     */
    fun undoLastReview() {
        val record = undoRecord ?: return
        undoRecord = null
        viewModelScope.launch {
            // If the review being taken back re-queued the card, the copy it
            // appended has to go too, or re-rating it as Good would leave a card
            // that is no longer due sitting in the queue.
            if (record.requeued && queue.lastOrNull()?.id == record.cardBefore.id) {
                queue = queue.dropLast(1)
            }
            reviewLogRepository.delete(record.logId)
            cardRepository.update(record.cardBefore)
            position = record.cardIndex
            hintCount = record.hintCount
            retryCount = record.retryCount
            _uiState.value = when (val restored = record.stateBefore) {
                is ReviewUiState.Success -> restored.copy(rating = null, canUndo = false)
                // Giving the drawing back, with the hint closed so it does not
                // immediately reopen.
                is ReviewUiState.Prompt -> restored.copy(hintVisible = false, canUndo = false)
                else -> restored
            }
        }
    }

    // --------------------------------------------------------------- plumbing

    private suspend fun persistAndAdvance(state: ReviewUiState, rating: Rating) {
        val card = queue.getOrNull(position)
        if (card == null) {
            _uiState.value = ReviewUiState.SessionComplete(canUndo = undoRecord != null)
            return
        }
        val now = LocalDateTime.now(clock)
        val updated = scheduler.schedule(card, rating, now)
        cardRepository.update(updated)
        val logId = reviewLogRepository.log(
            cardId = card.id,
            rating = rating,
            usedIDontKnow = hintCount > 0,
            retryCount = retryCount,
            reviewedAt = now,
        )
        // A failed card goes back into this session's queue. The *updated* card is
        // re-appended, not the snapshot, so the next review of it starts from the
        // memory state this lapse produced.
        val requeued = rating == Rating.AGAIN
        if (requeued) queue = queue + updated

        undoRecord = UndoRecord(
            cardIndex = position,
            cardBefore = card,
            logId = logId,
            stateBefore = state,
            hintCount = hintCount,
            retryCount = retryCount,
            requeued = requeued,
        )
        position++
        if (position >= queue.size) {
            _uiState.value = ReviewUiState.SessionComplete(canUndo = true)
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

    private fun setPromptMessage(
        state: ReviewUiState.Prompt,
        message: String,
        clearAfterMillis: Long?,
    ) {
        _uiState.value = state.copy(busy = false, message = message, strokes = strokes)
        if (clearAfterMillis != null) scheduleMessageClear(clearAfterMillis)
    }

    /**
     * The failure message is transient: it must not sit there nagging the user,
     * who is free to retry for as long as they like.
     */
    private fun scheduleMessageClear(afterMillis: Long) {
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(afterMillis)
            val state = _uiState.value
            if (state is ReviewUiState.Prompt) {
                _uiState.value = state.copy(message = null)
            }
        }
    }

    companion object {
        const val TAG = "ReviewViewModel"
        const val TRANSIENT_MESSAGE_MILLIS = 2_500L
        const val MESSAGE_NOT_QUITE = "Not quite - try again."
        const val MESSAGE_EMPTY_DRAWING = "Draw the character first."

        /** Builds an error line that says what actually went wrong. */
        internal fun recognitionErrorMessage(error: Throwable): String {
            val detail = error.message?.replace('\n', ' ')?.trim()?.take(MAX_ERROR_LENGTH)
            return if (detail.isNullOrEmpty()) {
                "Recognition failed for an unknown reason."
            } else {
                "Recognition failed: " + detail
            }
        }

        private const val MAX_ERROR_LENGTH = 140
    }
}
