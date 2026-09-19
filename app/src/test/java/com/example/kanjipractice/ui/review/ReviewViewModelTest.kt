package com.example.kanjipractice.ui.review

import com.example.fsrs.Fsrs
import com.example.fsrs.Rating
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.domain.deck.DeckCatalog
import com.example.kanjipractice.domain.model.CardState
import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.model.StrokePoint
import com.example.kanjipractice.domain.scheduler.ReviewScheduler
import com.example.kanjipractice.domain.settings.StudySettings
import com.example.kanjipractice.domain.util.DayBoundary
import com.example.kanjipractice.testing.FakeCardRepository
import com.example.kanjipractice.testing.FakeRecognitionService
import com.example.kanjipractice.testing.FakeReviewLogRepository
import com.example.kanjipractice.testing.FakeStrokeDiagramProvider
import com.example.kanjipractice.testing.FakeStudySettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the review state machine with fakes, so the behaviours the design calls
 * out explicitly -- the capped new-card flow, drawing persistence across retries,
 * the hint being a peek, undo -- are pinned by tests rather than by inspection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val now: LocalDateTime = LocalDateTime.of(2024, 5, 1, 9, 0)
    private val clock: Clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val zone = ZoneOffset.UTC

    private lateinit var cards: FakeCardRepository
    private lateinit var logs: FakeReviewLogRepository
    private lateinit var recognition: FakeRecognitionService
    private lateinit var settings: FakeStudySettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------------ setup

    private fun viewModel(
        deck: List<CardEntity> = emptyList(),
        dailyNewLimit: Int = StudySettings.DEFAULT_DAILY_NEW_LIMIT,
    ): ReviewViewModel {
        cards = FakeCardRepository(deck)
        logs = FakeReviewLogRepository()
        recognition = FakeRecognitionService()
        settings = FakeStudySettingsRepository(
            initialLimit = dailyNewLimit,
            // Every set on, so these tests are about the state machine rather
            // than about a flavour's default selection.
            initialDecks = DeckCatalog.ALL.map { it.id }.toSet(),
        )
        return ReviewViewModel(
            cardRepository = cards,
            reviewLogRepository = logs,
            settingsRepository = settings,
            scheduler = ReviewScheduler(Fsrs(), clock),
            recognitionService = recognition,
            strokeDataService = FakeStrokeDiagramProvider(),
            clock = clock,
            zone = zone,
        )
    }

    /** A card that has been introduced before and is now due. */
    private fun dueCard(id: Long, character: String) = CardEntity(
        id = id,
        character = character,
        meaning = "meaning $id",
        reading1 = null,
        reading2 = null,
        exampleWord = null,
        level = 5,
        deckId = DeckCatalog.ALL.first().id,
        deckSortKey = id.toInt(),
        stability = 5.0,
        difficulty = 5.0,
        due = now.minusDays(1),
        lastReview = now.minusDays(6),
        reps = 1,
        state = CardState.REVIEW,
    )

    /** A card the user has never seen. */
    private fun newCard(id: Long, character: String) = CardEntity(
        id = id,
        character = character,
        meaning = "meaning $id",
        reading1 = null,
        reading2 = null,
        exampleWord = null,
        level = 5,
        deckId = DeckCatalog.ALL.first().id,
        deckSortKey = id.toInt(),
        due = now,
    )

    private fun stroke(x: Float = 1f) =
        Stroke(listOf(StrokePoint(x, x), StrokePoint(x + 5f, x + 5f)))

    // ------------------------------------------------- the daily new-card flow

    @Test
    fun anEmptyDeckEndsTheSessionImmediately() {
        val vm = viewModel()
        assertIs<ReviewUiState.SessionComplete>(vm.uiState.value)
    }

    @Test
    fun newCardsAreCappedByTheDailyLimit() {
        // The whole point: a 600-card deck must not arrive on day one.
        val deck = (1..10).map { newCard(it.toLong(), "\u65E5") }
        val vm = viewModel(deck, dailyNewLimit = 3)

        assertEquals(3, assertIs<ReviewUiState.Prompt>(vm.uiState.value).remaining)
    }

    @Test
    fun theNewCardAllowanceCanBeZero() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")), dailyNewLimit = 0)
        assertIs<ReviewUiState.SessionComplete>(vm.uiState.value)
    }

    @Test
    fun cardsIntroducedEarlierTodayDoNotComeRoundAgain() {
        val deck = (1..5).map { newCard(it.toLong(), "\u65E5") }
        cards = FakeCardRepository(deck)
        logs = FakeReviewLogRepository()
        settings = FakeStudySettingsRepository(
            initialLimit = 5,
            initialDecks = DeckCatalog.ALL.map { it.id }.toSet(),
        )
        recognition = FakeRecognitionService()
        // Pretend two cards were already introduced today.
        runTest {
            val today = DayBoundary.startOfStudyDay(clock, zone)
            logs.log(1L, Rating.GOOD, false, 0, today.plusHours(1))
            logs.log(2L, Rating.GOOD, false, 0, today.plusHours(2))
        }

        val vm = ReviewViewModel(
            cardRepository = cards,
            reviewLogRepository = logs,
            settingsRepository = settings,
            scheduler = ReviewScheduler(Fsrs(), clock),
            recognitionService = recognition,
            strokeDataService = FakeStrokeDiagramProvider(),
            clock = clock,
            zone = zone,
        )

        assertEquals(3, assertIs<ReviewUiState.Prompt>(vm.uiState.value).remaining)
    }

    @Test
    fun dueReviewsAreQueuedBeforeNewCards() {
        val vm = viewModel(
            listOf(newCard(1, "\u65E5"), dueCard(2, "\u6708")),
            dailyNewLimit = 5,
        )
        val state = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertEquals("\u6708", state.card.character)
        assertEquals(2, state.remaining)
    }

    // ----------------------------------------------------------- drawing rules

    @Test
    fun aFailedAttemptKeepsTheDrawingAndCountsARetry() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        recognition.candidates = listOf("\u6708")

        vm.onStrokeFinished(stroke())
        vm.submit()

        val state = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertFalse(state.busy)
        assertEquals(1, state.retryCount)
        assertEquals(1, state.strokes.size)
        assertTrue(state.message != null)
    }

    // How wide the accepted window is belongs to the recogniser rather than to the
    // state machine, so it is tested in RecognitionMatcherTest with explicit ranks
    // and pinned per flavour. Hard-coding an outcome here would make this suite
    // disagree with one of the two apps.

    @Test
    fun aTopRankedMatchReachesTheSuccessScreen() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        recognition.candidates = listOf("\u65E5", "\u66F0")

        vm.onStrokeFinished(stroke())
        vm.submit()

        val state = assertIs<ReviewUiState.Success>(vm.uiState.value)
        assertEquals("\u65E5", state.recognized)
        assertEquals(
            Rating.GOOD,
            state.rating,
            "a first-try correct drawing suggests Good",
        )
        assertEquals(1, state.strokes.size, "the drawing is carried over for self-check")
    }

    @Test
    fun undoStrokeRemovesOnlyTheLastStroke() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        vm.onStrokeFinished(stroke(1f))
        vm.onStrokeFinished(stroke(2f))
        vm.undoStroke()
        assertEquals(1, assertIs<ReviewUiState.Prompt>(vm.uiState.value).strokes.size)
    }

    @Test
    fun clearEmptiesTheCanvas() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        vm.onStrokeFinished(stroke())
        vm.clear()
        assertTrue(assertIs<ReviewUiState.Prompt>(vm.uiState.value).strokes.isEmpty())
    }

    @Test
    fun submittingAnEmptyCanvasIsNotAFailedAttempt() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        vm.submit()
        val state = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertEquals(0, state.retryCount)
        assertTrue(state.message != null)
    }

    // -------------------------------------------------------------- the hint

    @Test
    fun theHintOpensAPopupAndDoesNotFailTheCard() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        vm.onStrokeFinished(stroke())
        vm.showHint()

        val state = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertTrue(state.hintVisible)
        assertEquals(1, state.hintCount)
        assertEquals(1, state.strokes.size, "the drawing survives opening the hint")
        assertTrue(cards.updates.isEmpty(), "the hint must not commit a review")
        assertTrue(logs.entries.isEmpty())
    }

    @Test
    fun theHintCanBeReopenedAsOftenAsTheUserLikes() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        repeat(3) {
            vm.showHint()
            vm.dismissHint()
        }
        val state = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertEquals(3, state.hintCount)
        assertFalse(state.hintVisible)
    }

    @Test
    fun theUserCanStillDrawAfterClosingTheHint() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        vm.showHint()
        vm.dismissHint()
        vm.onStrokeFinished(stroke())
        assertEquals(1, assertIs<ReviewUiState.Prompt>(vm.uiState.value).strokes.size)
    }

    @Test
    fun successAfterAHintPreselectsAgain() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        recognition.candidates = listOf("\u65E5")

        vm.showHint()
        vm.dismissHint()
        vm.onStrokeFinished(stroke())
        vm.submit()

        val state = assertIs<ReviewUiState.Success>(vm.uiState.value)
        assertEquals(Rating.AGAIN, state.rating)
        assertEquals(1, state.hintCount)
    }

    @Test
    fun thePreselectedAgainCanBeChangedAfterAHint() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        recognition.candidates = listOf("\u65E5")

        vm.showHint()
        vm.dismissHint()
        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.GOOD)
        vm.next()

        assertEquals(Rating.GOOD.value, logs.entries.single().rating)
        assertTrue(logs.entries.single().usedIDontKnow, "the log still records the hint")
    }

    // ---------------------------------------------------------------- ratings

    @Test
    fun againIsAvailableOnTheSuccessScreen() {
        // The escape hatch for a wrong drawing the recogniser accepted.
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        recognition.candidates = listOf("\u65E5")
        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.AGAIN)
        vm.next()

        assertEquals(Rating.AGAIN.value, logs.entries.single().rating)
        assertFalse(logs.entries.single().usedIDontKnow)
        assertTrue(cards.updates.single().lapses == 1)
    }

    @Test
    fun nextCommitsTheSuggestedRatingWithoutTheUserClickingIt() {
        // Regression: the suggested rating used to be painted on screen from one
        // field while next() read another, so Next silently did nothing until the
        // user clicked the button that was already highlighted.
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        recognition.candidates = listOf("\u65E5")
        vm.onStrokeFinished(stroke())
        vm.submit()

        vm.next()

        assertEquals(1, logs.entries.size, "Next must commit without a second click")
        assertEquals(Rating.GOOD.value, logs.entries.single().rating)
    }

    @Test
    fun nextAlsoWorksStraightAfterAHint() {
        // The exact sequence reported: hint, draw, see Again highlighted, press Next.
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        recognition.candidates = listOf("\u65E5")

        vm.showHint()
        vm.dismissHint()
        vm.onStrokeFinished(stroke())
        vm.submit()
        assertEquals(Rating.AGAIN, assertIs<ReviewUiState.Success>(vm.uiState.value).rating)

        vm.next()

        assertEquals(1, logs.entries.size)
        assertEquals(Rating.AGAIN.value, logs.entries.single().rating)
        assertTrue(logs.entries.single().usedIDontKnow)
    }

    @Test
    fun aDrawingThatNeededRetriesSuggestsHard() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        recognition.candidates = listOf("\u6708")
        vm.onStrokeFinished(stroke())
        vm.submit()

        recognition.candidates = listOf("\u65E5")
        vm.submit()

        val state = assertIs<ReviewUiState.Success>(vm.uiState.value)
        assertEquals(Rating.HARD, state.rating)
        assertEquals(1, state.retryCount)
    }

    @Test
    fun easyIsNeverSuggested() {
        // Only the user can say a card was effortless.
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        recognition.candidates = listOf("\u65E5")
        vm.onStrokeFinished(stroke())
        vm.submit()
        assertEquals(Rating.GOOD, assertIs<ReviewUiState.Success>(vm.uiState.value).rating)

        vm.rate(Rating.EASY)
        assertEquals(Rating.EASY, assertIs<ReviewUiState.Success>(vm.uiState.value).rating)
    }

    @Test
    fun manualGradingLeavesTheChoiceToTheUser() {
        val vm = viewModel(listOf(newCard(1, "\u65E5")))
        recognition.failure = IllegalStateException("boom")
        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.gradeManually()

        val state = assertIs<ReviewUiState.Success>(vm.uiState.value)
        assertNull(state.rating, "nothing verified the drawing, so nothing is suggested")
        vm.next()
        assertTrue(logs.entries.isEmpty(), "Next does nothing until a rating is chosen")
    }

    @Test
    fun nextPersistsTheReviewAndAdvances() {
        val vm = viewModel(listOf(dueCard(1, "\u65E5"), dueCard(2, "\u6708")))
        recognition.candidates = listOf("\u65E5")

        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.GOOD)
        vm.next()

        assertEquals(1, cards.updates.size)
        assertEquals(Rating.GOOD.value, logs.entries.single().rating)
        assertEquals(now, cards.updates.single().lastReview)
        assertEquals("\u6708", assertIs<ReviewUiState.Prompt>(vm.uiState.value).card.character)
    }

    @Test
    fun retriesAreRecordedInTheReviewLog() {
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.candidates = listOf("\u6708")
        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.submit()
        recognition.candidates = listOf("\u65E5")
        vm.submit()
        vm.rate(Rating.HARD)
        vm.next()

        assertEquals(2, logs.entries.single().retryCount)
    }

    // ------------------------------------------------------------------ undo

    @Test
    fun undoPutsTheUserBackOnTheRatingScreenForThatCard() {
        val vm = viewModel(listOf(dueCard(1, "\u65E5"), dueCard(2, "\u6708")))
        recognition.candidates = listOf("\u65E5")

        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.EASY)
        vm.next()
        assertEquals("\u6708", assertIs<ReviewUiState.Prompt>(vm.uiState.value).card.character)
        assertTrue(vm.uiState.value.canUndo)

        vm.undoLastReview()

        val restored = assertIs<ReviewUiState.Success>(vm.uiState.value)
        assertEquals("\u65E5", restored.card.character)
        assertNull(restored.rating, "the rating is cleared so it can be redone")
        assertTrue(logs.entries.isEmpty(), "the review log entry is removed")
        assertFalse(vm.uiState.value.canUndo, "undo is a single level")
    }

    @Test
    fun undoRestoresTheCardsStoredState() {
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.candidates = listOf("\u65E5")
        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.EASY)
        vm.next()

        vm.undoLastReview()

        val restored = cards.updates.last()
        assertEquals(5.0, restored.stability)
        assertEquals(5.0, restored.difficulty)
        assertEquals(now.minusDays(1), restored.due)
        assertEquals(1, restored.reps)
        assertEquals(CardState.REVIEW, restored.state)
    }

    @Test
    fun undoingANewCardReturnsItToTheDailyAllowance() = runTest {
        val vm = viewModel(listOf(newCard(1, "\u65E5"), newCard(2, "\u6708")), dailyNewLimit = 1)
        recognition.candidates = listOf("\u65E5")

        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.GOOD)
        vm.next()

        val today = DayBoundary.startOfStudyDay(clock, zone)
        assertEquals(1, logs.observeIntroducedSince(today).first())

        vm.undoLastReview()
        assertEquals(0, logs.observeIntroducedSince(today).first())
    }

    @Test
    fun undoTwiceIsANoOp() {
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.candidates = listOf("\u65E5")
        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.GOOD)
        vm.next()

        vm.undoLastReview()
        val afterFirst = vm.uiState.value
        vm.undoLastReview()
        assertEquals(afterFirst, vm.uiState.value)
    }

    @Test
    fun theLastReviewOfASessionCanStillBeUndone() {
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.candidates = listOf("\u65E5")
        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.GOOD)
        vm.next()

        val complete = assertIs<ReviewUiState.SessionComplete>(vm.uiState.value)
        assertTrue(complete.canUndo)

        vm.undoLastReview()
        assertIs<ReviewUiState.Success>(vm.uiState.value)
    }

    // ---------------------------------------------------------------- giving up

    @Test
    fun givingUpGoesToTheRatingScreenRatherThanScoringItself() {
        // The only route onwards used to run through getting the drawing
        // accepted, so a character the recogniser disliked blocked the session.
        // Committing Again immediately would be its own trap: being stuck should
        // not force a lapse on a card the user actually knows.
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.candidates = listOf("\u7389")

        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.showHint()
        vm.giveUp()

        val state = assertIs<ReviewUiState.Success>(vm.uiState.value)
        assertTrue(state.gaveUp)
        assertEquals(Rating.AGAIN, state.rating)
        assertEquals(1, state.strokes.size, "the drawing is kept for comparison")
        assertTrue(logs.entries.isEmpty(), "nothing is committed until Next")
    }

    @Test
    fun givingUpLetsTheUserUpgradeTheRating() {
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.candidates = listOf("\u7389")

        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.showHint()
        vm.giveUp()
        vm.rate(Rating.GOOD)
        vm.next()

        assertEquals(Rating.GOOD.value, logs.entries.single().rating)
    }

    @Test
    fun givingUpAndKeepingAgainCommitsTheLapse() {
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.candidates = listOf("\u7389")

        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.giveUp()
        vm.next()

        assertEquals(Rating.AGAIN.value, logs.entries.single().rating)
    }

    // -------------------------------------------------------- day-wide queueing

    @Test
    fun cardsDueLaterTodayAreAlreadyInTheQueue() {
        // A session covers the whole study day rather than the current instant, so
        // a card that becomes due this evening is available in the morning.
        val laterToday = dueCard(1, "\u65E5").copy(due = now.plusHours(8))
        val vm = viewModel(listOf(laterToday))
        assertEquals(1, assertIs<ReviewUiState.Prompt>(vm.uiState.value).remaining)
    }

    @Test
    fun cardsDueAfterTheEndOfTheStudyDayAreNot() {
        val tomorrowEvening = dueCard(1, "\u65E5").copy(due = now.plusHours(30))
        val vm = viewModel(listOf(tomorrowEvening))
        assertIs<ReviewUiState.SessionComplete>(vm.uiState.value)
    }

    @Test
    fun aLapsedCardComesBackInTheSameSessionInsteadOfTenMinutesLater() {
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.candidates = listOf("\u65E5")
        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.AGAIN)
        vm.next()

        // The session does not end and the card is waiting, rather than being
        // parked ten minutes into the future.
        val state = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertEquals("\u65E5", state.card.character)
        assertEquals(1, state.remaining)
        assertEquals(0, state.retryCount, "the second run at the card starts clean")
    }

    @Test
    fun theSecondRunAtALapsedCardSuggestsGoodAgain() {
        // Matches the reported sequence: fail it, then get it right straight away.
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.candidates = listOf("\u65E5")

        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.AGAIN)
        vm.next()

        vm.onStrokeFinished(stroke())
        vm.submit()

        val state = assertIs<ReviewUiState.Success>(vm.uiState.value)
        assertEquals(Rating.GOOD, state.rating)
        assertEquals("\u65E5", state.recognized)
    }

    @Test
    fun undoingALapseRemovesTheCopyItPutBackInTheQueue() {
        val vm = viewModel(listOf(dueCard(1, "\u65E5"), dueCard(2, "\u6708")))
        recognition.candidates = listOf("\u65E5")
        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.AGAIN)
        vm.next()
        assertEquals("\u6708", assertIs<ReviewUiState.Prompt>(vm.uiState.value).card.character)

        vm.undoLastReview()
        assertIs<ReviewUiState.Success>(vm.uiState.value)

        // Re-rated as Good, the card is scheduled days out, so the copy the lapse
        // appended must not come round again.
        vm.rate(Rating.GOOD)
        vm.next()

        val prompt = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertEquals("\u6708", prompt.card.character)
        assertEquals(1, prompt.remaining, "the re-queued copy must not linger")
    }

    // ------------------------------------------------------------ error paths

    @Test
    fun aRecognitionFailureIsNotCountedAsARetryAndDoesNotFailTheCard() {
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.failure = IllegalStateException("model missing")

        vm.onStrokeFinished(stroke())
        vm.submit()

        val state = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertFalse(state.busy)
        assertEquals(0, state.retryCount)
        assertEquals(1, state.strokes.size)
        assertTrue(cards.updates.isEmpty())
    }

    @Test
    fun aRecognitionFailureSaysWhatWentWrong() {
        // "Recognition is unavailable" on its own is not something a user can act
        // on; the underlying cause has to reach the screen.
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.failure = IllegalStateException("Failed to run recognition")

        vm.onStrokeFinished(stroke())
        vm.submit()

        val state = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertTrue(state.recognitionFailed)
        assertTrue(
            state.message?.contains("Failed to run recognition") == true,
            "message did not name the cause: ${state.message}",
        )
    }

    @Test
    fun theErrorMessageFallsBackWhenThereIsNoDetail() {
        assertEquals(
            "Recognition failed for an unknown reason.",
            ReviewViewModel.recognitionErrorMessage(IllegalStateException()),
        )
    }

    @Test
    fun theErrorMessageIsTruncatedToOneLine() {
        val message = ReviewViewModel.recognitionErrorMessage(
            IllegalStateException("x".repeat(500) + "\nsecond line"),
        )
        assertTrue(message.length < 200)
        assertFalse(message.contains('\n'))
    }

    @Test
    fun theUserCanGradeThemselvesWhenRecognitionIsBroken() {
        // Otherwise a broken ML Kit install makes the whole deck unusable.
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.failure = IllegalStateException("boom")

        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.gradeManually()

        val state = assertIs<ReviewUiState.Success>(vm.uiState.value)
        assertEquals("\u65E5", state.card.character)
        assertNull(state.recognized, "nothing was recognised")
        assertNull(state.rating, "the user still has to choose a rating")

        vm.rate(Rating.GOOD)
        vm.next()
        assertEquals(Rating.GOOD.value, logs.entries.single().rating)
    }

    @Test
    fun manualGradingIsNotReachableWithoutARecognitionFailure() {
        // It must not become a way to skip drawing: it is only offered on the
        // prompt, and only after the recogniser has actually thrown.
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        val before = vm.uiState.value
        vm.gradeManually()
        assertEquals(before, vm.uiState.value)
    }

    @Test
    fun aFollowingSuccessfulSubmitClearsTheFailureFlag() {
        val vm = viewModel(listOf(dueCard(1, "\u65E5")))
        recognition.failure = IllegalStateException("boom")
        vm.onStrokeFinished(stroke())
        vm.submit()
        assertTrue(assertIs<ReviewUiState.Prompt>(vm.uiState.value).recognitionFailed)

        recognition.failure = null
        recognition.candidates = listOf("\u65E5")
        vm.submit()

        assertIs<ReviewUiState.Success>(vm.uiState.value)
    }

    @Test
    fun aModelDownloadIsStartedWhenTheSessionBegins() {
        viewModel(listOf(dueCard(1, "\u65E5")))
        assertTrue(recognition.prepareCount > 0)
    }
}
