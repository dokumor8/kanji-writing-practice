package com.example.kanjipractice.ui.review

import com.example.fsrs.Fsrs
import com.example.fsrs.Rating
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.data.db.ReviewLogEntity
import com.example.kanjipractice.domain.model.CardState
import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.model.StrokePoint
import com.example.kanjipractice.domain.recognition.ModelState
import com.example.kanjipractice.domain.recognition.RecognitionService
import com.example.kanjipractice.domain.repository.CardRepository
import com.example.kanjipractice.domain.repository.ReviewLogRepository
import com.example.kanjipractice.domain.scheduler.ReviewScheduler
import com.example.kanjipractice.domain.stroke.StrokeDiagram
import com.example.kanjipractice.domain.stroke.StrokeDiagramProvider
import com.example.kanjipractice.domain.stroke.SvgPathCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
 * Drives the whole review state machine from plan section 6 with fakes, so the
 * behaviours the design calls out explicitly -- drawing persistence across
 * retries, Again being locked, lenient tracing -- are pinned down by tests
 * rather than by inspection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val now: LocalDateTime = LocalDateTime.of(2024, 5, 1, 9, 0)
    private val clock: Clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private lateinit var cards: FakeCardRepository
    private lateinit var logs: FakeReviewLogRepository
    private lateinit var recognition: FakeRecognitionService

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(vararg deck: CardEntity): ReviewViewModel {
        cards = FakeCardRepository(deck.toList())
        logs = FakeReviewLogRepository()
        recognition = FakeRecognitionService()
        return ReviewViewModel(
            cardRepository = cards,
            reviewLogRepository = logs,
            scheduler = ReviewScheduler(Fsrs(), clock),
            recognitionService = recognition,
            strokeDataService = FakeStrokeDiagramProvider(),
            clock = clock,
        )
    }

    private fun card(id: Long, character: String) = CardEntity(
        id = id,
        character = character,
        meaning = "meaning $id",
        onyomi = null,
        kunyomi = null,
        exampleWord = null,
        jlpt = 5,
        due = now.minusDays(1),
    )

    private fun stroke(x: Float = 1f) = Stroke(listOf(StrokePoint(x, x), StrokePoint(x + 5f, x + 5f)))

    // ------------------------------------------------------------ the session

    @Test
    fun anEmptyQueueEndsTheSessionImmediately() {
        val vm = viewModel()
        assertIs<ReviewUiState.SessionComplete>(vm.uiState.value)
    }

    @Test
    fun theSessionStartsOnTheMostOverdueCard() {
        val vm = viewModel(card(1, "\u65E5"), card(2, "\u6708"))
        val state = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertEquals("\u65E5", state.card.character)
        assertEquals(2, state.remaining)
    }

    // ----------------------------------------------------------- drawing rules

    @Test
    fun aFailedAttemptKeepsTheDrawingAndCountsARetry() {
        // Plan 6.2 and 12: the drawing must survive so one bad stroke can be undone.
        val vm = viewModel(card(1, "\u65E5"))
        recognition.candidates = listOf("\u6708")

        vm.onStrokeFinished(stroke())
        vm.submit()

        val state = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertFalse(state.busy)
        assertEquals(1, state.retryCount)
        assertEquals(1, state.strokes.size)
        assertTrue(state.message != null)
    }

    @Test
    fun undoRemovesOnlyTheLastStroke() {
        val vm = viewModel(card(1, "\u65E5"))
        vm.onStrokeFinished(stroke(1f))
        vm.onStrokeFinished(stroke(2f))
        vm.undo()
        assertEquals(1, assertIs<ReviewUiState.Prompt>(vm.uiState.value).strokes.size)
    }

    @Test
    fun clearEmptiesTheCanvasWithoutLeavingThePrompt() {
        val vm = viewModel(card(1, "\u65E5"))
        vm.onStrokeFinished(stroke())
        vm.clear()
        val state = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertTrue(state.strokes.isEmpty())
        assertEquals(1, state.remaining)
    }

    @Test
    fun submittingAnEmptyCanvasIsNotAFailedAttempt() {
        val vm = viewModel(card(1, "\u65E5"))
        vm.submit()
        val state = assertIs<ReviewUiState.Prompt>(vm.uiState.value)
        assertEquals(0, state.retryCount)
        assertTrue(state.message != null)
    }

    // ---------------------------------------------------------------- success

    @Test
    fun aRecognisedDrawingReachesSuccessWithTheDiagram() {
        val vm = viewModel(card(1, "\u65E5"))
        recognition.candidates = listOf("\u6708", "\u65E5", "\u76EE")

        vm.onStrokeFinished(stroke())
        vm.submit()

        val state = assertIs<ReviewUiState.Success>(vm.uiState.value)
        assertEquals("\u65E5", state.card.character)
        assertEquals("\u6708", state.recognized)
        assertNull(state.rating)
        assertTrue(state.diagram.strokeCount > 0)
    }

    @Test
    fun nextDoesNothingUntilARatingIsChosen() {
        val vm = viewModel(card(1, "\u65E5"))
        recognition.candidates = listOf("\u65E5")
        vm.onStrokeFinished(stroke())
        vm.submit()

        vm.next()
        assertIs<ReviewUiState.Success>(vm.uiState.value)
        assertTrue(cards.updates.isEmpty())
        assertTrue(logs.entries.isEmpty())
    }

    @Test
    fun nextPersistsTheRatingAndAdvances() {
        val vm = viewModel(card(1, "\u65E5"), card(2, "\u6708"))
        recognition.candidates = listOf("\u65E5")

        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.GOOD)
        vm.next()

        assertEquals(1, cards.updates.size)
        val updated = cards.updates.single()
        assertEquals(Rating.GOOD.value, logs.entries.single().rating)
        assertFalse(logs.entries.single().usedIDontKnow)
        assertEquals(0, logs.entries.single().retryCount)
        assertEquals(now, updated.lastReview)
        assertEquals(CardState.REVIEW, updated.state)

        assertEquals("\u6708", assertIs<ReviewUiState.Prompt>(vm.uiState.value).card.character)
    }

    @Test
    fun theLastCardEndsTheSession() {
        val vm = viewModel(card(1, "\u65E5"))
        recognition.candidates = listOf("\u65E5")
        vm.onStrokeFinished(stroke())
        vm.submit()
        vm.rate(Rating.EASY)
        vm.next()

        assertIs<ReviewUiState.SessionComplete>(vm.uiState.value)
        assertEquals(Rating.EASY.value, logs.entries.single().rating)
    }

    @Test
    fun retriesAreRecordedInTheReviewLog() {
        val vm = viewModel(card(1, "\u65E5"))
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

    // -------------------------------------------------------------- "I don't know"

    @Test
    fun iDontKnowGoesToRelearnWithAFreshCanvas() {
        val vm = viewModel(card(1, "\u65E5"))
        vm.onStrokeFinished(stroke())
        vm.iDontKnow()

        val state = assertIs<ReviewUiState.Relearn>(vm.uiState.value)
        assertTrue(state.strokes.isEmpty(), "RELEARN must start from a blank canvas")
        assertTrue(state.diagram.strokeCount > 0)
    }

    @Test
    fun relearnWillNotAdvanceOnAnEmptyCanvas() {
        val vm = viewModel(card(1, "\u65E5"))
        vm.iDontKnow()
        vm.submitRelearn()

        assertIs<ReviewUiState.Relearn>(vm.uiState.value)
        assertTrue(cards.updates.isEmpty())
    }

    @Test
    fun tracingAnythingPassesAndLocksTheRatingToAgain() {
        // Plan 6.4 and 12: the tracing step is practice, not a test, and Again
        // cannot be upgraded.
        val vm = viewModel(card(1, "\u65E5"))
        vm.iDontKnow()
        vm.onRelearnStrokeFinished(stroke())
        vm.submitRelearn()

        assertEquals(1, cards.updates.size)
        val entry = logs.entries.single()
        assertEquals(Rating.AGAIN.value, entry.rating)
        assertTrue(entry.usedIDontKnow)
        assertEquals(CardState.LEARNING, cards.updates.single().state)
    }

    @Test
    fun aFailedRecognitionIsNeverUpgradedToASuccessRating() {
        // Pressing "I don't know" commits the card to Again even if the user
        // could draw it perfectly afterwards.
        val vm = viewModel(card(1, "\u65E5"))
        recognition.candidates = listOf("\u65E5")

        vm.iDontKnow()
        vm.onRelearnStrokeFinished(stroke())
        vm.submitRelearn()

        assertEquals(Rating.AGAIN.value, logs.entries.single().rating)
        assertTrue(logs.entries.single().usedIDontKnow)
    }

    // ------------------------------------------------------------ error paths

    @Test
    fun aRecognitionFailureIsNotCountedAsARetryAndDoesNotFailTheCard() {
        val vm = viewModel(card(1, "\u65E5"))
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
    fun aModelDownloadIsStartedWhenTheSessionBegins() {
        viewModel(card(1, "\u65E5"))
        assertTrue(recognition.prepareCount > 0)
    }
}

// --------------------------------------------------------------------- fakes

private class FakeCardRepository(private val cards: List<CardEntity>) : CardRepository {
    val updates = mutableListOf<CardEntity>()

    override fun observeDue(now: LocalDateTime): Flow<List<CardEntity>> =
        flowOf(cards.filter { !it.due.isAfter(now) })

    override fun observeDueCount(now: LocalDateTime): Flow<Int> =
        flowOf(cards.count { !it.due.isAfter(now) })

    override fun observeTotalCount(): Flow<Int> = flowOf(cards.size)

    override fun observeAll(): Flow<List<CardEntity>> = flowOf(cards)

    override suspend fun getById(id: Long): CardEntity? = cards.firstOrNull { it.id == id }

    override suspend fun update(card: CardEntity) {
        updates += card
    }

    override suspend fun count(): Int = cards.size
}

private class FakeReviewLogRepository : ReviewLogRepository {
    val entries = mutableListOf<ReviewLogEntity>()

    override suspend fun log(
        cardId: Long,
        rating: Rating,
        usedIDontKnow: Boolean,
        retryCount: Int,
        reviewedAt: LocalDateTime,
    ) {
        entries += ReviewLogEntity(
            cardId = cardId,
            rating = rating.value,
            usedIDontKnow = usedIDontKnow,
            retryCount = retryCount,
            reviewedAt = reviewedAt,
        )
    }

    override fun observeRecent(limit: Int): Flow<List<ReviewLogEntity>> = flowOf(entries.toList())
}

private class FakeRecognitionService : RecognitionService {
    private val state = MutableStateFlow<ModelState>(ModelState.Ready)
    override val modelState: StateFlow<ModelState> = state

    var candidates: List<String> = emptyList()
    var failure: Exception? = null
    var prepareCount = 0

    override suspend fun prepare() {
        prepareCount++
        failure?.let { throw it }
    }

    override suspend fun recognize(strokes: List<Stroke>): List<String> {
        failure?.let { throw it }
        return candidates
    }
}

private class FakeStrokeDiagramProvider : StrokeDiagramProvider {
    override suspend fun diagramFor(character: String) = StrokeDiagram(
        viewBoxWidth = 109f,
        viewBoxHeight = 109f,
        strokes = listOf(
            listOf(SvgPathCommand.MoveTo(0f, 0f), SvgPathCommand.LineTo(10f, 10f)),
        ),
        numbers = emptyList(),
    )
}
