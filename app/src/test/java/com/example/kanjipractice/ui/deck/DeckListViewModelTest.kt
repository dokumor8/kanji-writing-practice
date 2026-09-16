package com.example.kanjipractice.ui.deck

import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.data.db.ReviewLogEntity
import com.example.kanjipractice.domain.deck.DeckCatalog
import com.example.kanjipractice.domain.model.CardState
import com.example.kanjipractice.domain.recognition.ModelState
import com.example.kanjipractice.domain.settings.StudySettings
import com.example.kanjipractice.testing.FakeCardRepository
import com.example.kanjipractice.testing.FakeDeckInitializer
import com.example.kanjipractice.testing.FakeRecognitionService
import com.example.kanjipractice.testing.FakeReviewLogRepository
import com.example.kanjipractice.testing.FakeStudySettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import kotlin.test.assertTrue

/**
 * Covers the deck screen's counts, which is where the reported "there is nothing
 * to review" bug lived.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelTest {

    private val now: LocalDateTime = LocalDateTime.of(2024, 5, 1, 9, 0)
    private val clock: Clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val zone = ZoneOffset.UTC

    private lateinit var cards: FakeCardRepository
    private lateinit var logs: FakeReviewLogRepository
    private lateinit var settings: FakeStudySettingsRepository
    private lateinit var recognition: FakeRecognitionService
    private val collectors = mutableListOf<Job>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        collectors.forEach { it.cancel() }
        collectors.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(
        dailyNewLimit: Int = StudySettings.DEFAULT_DAILY_NEW_LIMIT,
        onSeed: suspend () -> Unit = {},
    ): DeckListViewModel {
        cards = FakeCardRepository(emptyList())
        logs = FakeReviewLogRepository()
        // Every set selected, so a test about counts is not also a test about
        // which sets a flavour happens to enable by default.
        settings = FakeStudySettingsRepository(
            initialLimit = dailyNewLimit,
            initialDecks = DeckCatalog.ALL.map { it.id }.toSet(),
        )
        recognition = FakeRecognitionService()
        val vm = DeckListViewModel(
            cardRepository = cards,
            reviewLogRepository = logs,
            settingsRepository = settings,
            deckSeeder = FakeDeckInitializer(onSeed),
            recognitionService = recognition,
            clock = clock,
            zone = zone,
        )
        // uiState is a WhileSubscribed flow, so it must have a collector before
        // it reports anything.
        collectors += CoroutineScope(UnconfinedTestDispatcher()).launch {
            vm.uiState.collect { }
        }
        return vm
    }

    private fun newCard(id: Long, deck: String = DeckCatalog.ALL.first().id) = CardEntity(
        id = id,
        character = "\u65E5",
        meaning = "m",
        reading1 = null,
        reading2 = null,
        exampleWord = null,
        level = 5,
        deckId = deck,
        deckSortKey = id.toInt(),
        due = now,
    )

    private fun reviewedCard(id: Long, deck: String = DeckCatalog.ALL.first().id) = newCard(id, deck).copy(
        state = CardState.REVIEW,
        reps = 1,
        stability = 5.0,
        difficulty = 5.0,
        due = now.minusDays(1),
    )

    private fun introducedToday(vararg ids: Long) {
        logs.logs.value = ids.mapIndexed { index, id ->
            ReviewLogEntity(
                id = index + 1L,
                cardId = id,
                rating = 3,
                usedIDontKnow = false,
                retryCount = 0,
                reviewedAt = now.minusMinutes(index.toLong()),
            )
        }
    }

    @Test
    fun aDeckSeededAfterTheScreenLoadsIsVisibleWithoutRestartingTheApp() {
        // Regression: new cards were seeded with due == now and then queried with
        // a "now" captured when the ViewModel was built, so every card was born
        // slightly in the future and the deck read as empty until a restart.
        val deck = (1..5).map { newCard(it.toLong()) }
        val vm = viewModel(onSeed = { cards.cards.value = deck })

        assertEquals(5, vm.uiState.value.newAvailable)
        assertEquals(0, vm.uiState.value.dueReviews)
        assertEquals(5, vm.uiState.value.studyCount)
    }

    @Test
    fun theNewCardAllowanceIsCappedByTheDailyLimit() {
        val deck = (1..40).map { newCard(it.toLong()) }
        val vm = viewModel(dailyNewLimit = 20, onSeed = { cards.cards.value = deck })

        assertEquals(20, vm.uiState.value.newAvailable)
        assertEquals(40, vm.uiState.value.newRemainingInDeck)
        assertFalse(vm.uiState.value.newAllowanceUsedUp)
    }

    @Test
    fun aSpentAllowanceShowsNoNewCardsButSaysWhy() {
        val deck = (1..40).map { newCard(it.toLong()) }
        val vm = viewModel(dailyNewLimit = 20, onSeed = { cards.cards.value = deck })
        introducedToday(*(1L..20L).toList().toLongArray())

        assertEquals(0, vm.uiState.value.newAvailable)
        assertTrue(vm.uiState.value.newAllowanceUsedUp)
        assertEquals(20, vm.uiState.value.introducedToday)
    }

    @Test
    fun cardsAlreadyIntroducedTodayReduceTheAllowance() {
        val deck = (1..40).map { newCard(it.toLong()) }
        val vm = viewModel(dailyNewLimit = 20, onSeed = { cards.cards.value = deck })
        assertEquals(20, vm.uiState.value.newAvailable)

        introducedToday(1L, 2L)
        assertEquals(18, vm.uiState.value.newAvailable)
    }

    @Test
    fun dueReviewsAreCountedSeparatelyAndAreNotCapped() {
        val deck = (1..30).map { reviewedCard(it.toLong()) }
        val vm = viewModel(onSeed = { cards.cards.value = deck })

        assertEquals(30, vm.uiState.value.dueReviews)
        assertEquals(30, vm.uiState.value.studyCount)
    }

    @Test
    fun reviewsAndNewCardsAddUp() {
        val deck = (1..10).map { reviewedCard(it.toLong()) } + (11..40).map { newCard(it.toLong()) }
        val vm = viewModel(dailyNewLimit = 20, onSeed = { cards.cards.value = deck })

        assertEquals(10, vm.uiState.value.dueReviews)
        assertEquals(20, vm.uiState.value.newAvailable)
        assertEquals(30, vm.uiState.value.studyCount)
    }

    @Test
    fun nothingToStudyWhenTheDeckIsEmpty() {
        val vm = viewModel()
        assertEquals(0, vm.uiState.value.studyCount)
    }

    @Test
    fun theModelStateIsReadRatherThanGuessed() {
        val vm = viewModel()
        assertEquals(ModelState.Ready, vm.uiState.value.modelState)
    }

    @Test
    fun onlyCardsFromSelectedSetsAreCounted() {
        val first = DeckCatalog.ALL[0].id
        val second = DeckCatalog.ALL[1].id
        val deck = (1..5).map { newCard(it.toLong(), deck = first) } +
            (6..9).map { newCard(it.toLong(), deck = second) }
        val vm = viewModel(onSeed = { cards.cards.value = deck })

        settings.deckIds.value = setOf(first)
        assertEquals(5, vm.uiState.value.newRemainingInDeck)
        assertEquals(5, vm.uiState.value.totalCount)

        settings.deckIds.value = setOf(first, second)
        assertEquals(9, vm.uiState.value.newRemainingInDeck)
        assertEquals(9, vm.uiState.value.totalCount)
    }

    @Test
    fun withNothingSelectedThereIsNothingToStudy() {
        val deck = (1..5).map { newCard(it.toLong()) }
        val vm = viewModel(onSeed = { cards.cards.value = deck })

        settings.deckIds.value = emptySet()

        assertEquals(0, vm.uiState.value.studyCount)
        assertTrue(vm.uiState.value.nothingSelected)
    }

    @Test
    fun resumingChecksTheModelAgain() {
        val vm = viewModel()
        val before = recognition.refreshCount
        vm.onResumed()
        assertTrue(recognition.refreshCount > before)
    }

    @Test
    fun resumingRecomputesTheCounts() {
        val vm = viewModel()
        assertEquals(0, vm.uiState.value.totalCount)

        cards.cards.value = (1..3).map { newCard(it.toLong()) }
        vm.onResumed()

        assertEquals(3, vm.uiState.value.totalCount)
    }
}
