package com.example.kanjipractice.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kanjipractice.data.DeckInitializer
import com.example.kanjipractice.domain.recognition.ModelState
import com.example.kanjipractice.domain.recognition.RecognitionService
import com.example.kanjipractice.domain.repository.CardRepository
import com.example.kanjipractice.domain.repository.ReviewLogRepository
import com.example.kanjipractice.domain.settings.StudySettings
import com.example.kanjipractice.domain.settings.StudySettingsRepository
import com.example.kanjipractice.domain.util.DayBoundary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

data class DeckListUiState(
    /** Scheduled cards that are due now. */
    val dueReviews: Int = 0,
    /** Never-seen cards that may still be introduced today. */
    val newAvailable: Int = 0,
    /** Never-seen cards left in the deck overall. */
    val newRemainingInDeck: Int = 0,
    val totalCount: Int = 0,
    val dailyNewLimit: Int = StudySettings.DEFAULT_DAILY_NEW_LIMIT,
    val introducedToday: Int = 0,
    val modelState: ModelState = ModelState.Unknown,
    val loading: Boolean = true,
) {
    /** What "Start review" will actually put in front of the user. */
    val studyCount: Int get() = dueReviews + newAvailable

    val newAllowanceUsedUp: Boolean
        get() = newRemainingInDeck > 0 && dailyNewLimit - introducedToday <= 0
}

@HiltViewModel
class DeckListViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val settingsRepository: StudySettingsRepository,
    private val deckSeeder: DeckInitializer,
    private val recognitionService: RecognitionService,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {

    /**
     * "Due" is a function of the current time, so the queries are re-run whenever
     * [refresh] moves this forward. Without that the deck screen kept the counts
     * it computed at construction and cards seeded a moment later were invisible
     * until the app was restarted.
     */
    private val now = MutableStateFlow(LocalDateTime.now(clock))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val counts = combine(
        now.flatMapLatest { cardRepository.observeDueReviewCount(it) },
        now.flatMapLatest {
            reviewLogRepository.observeIntroducedSince(DayBoundary.startOfToday(clock, zone))
        },
        cardRepository.observeNewCount(),
        cardRepository.observeTotalCount(),
    ) { due, introduced, newCount, total ->
        Counts(due = due, introducedToday = introduced, newInDeck = newCount, total = total)
    }

    val uiState: StateFlow<DeckListUiState> =
        combine(
            counts,
            settingsRepository.observeDailyNewLimit(),
            recognitionService.modelState,
        ) { counts, limit, model ->
            val remainingAllowance = (limit - counts.introducedToday).coerceAtLeast(0)
            DeckListUiState(
                dueReviews = counts.due,
                newAvailable = minOf(remainingAllowance, counts.newInDeck),
                newRemainingInDeck = counts.newInDeck,
                totalCount = counts.total,
                dailyNewLimit = limit,
                introducedToday = counts.introducedToday,
                modelState = model,
                loading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckListUiState(),
        )

    init {
        viewModelScope.launch {
            // First launch: fill the cards table from the bundled deck.
            deckSeeder.seedIfEmpty()
            refresh()
        }
    }

    /**
     * Called whenever the screen comes back to the foreground: the counts depend
     * on the wall clock, the study-day boundary, and on the model still being on
     * the device.
     */
    fun onResumed() {
        refresh()
        viewModelScope.launch { runCatching { recognitionService.refreshModelState() } }
    }

    fun refresh() {
        now.value = LocalDateTime.now(clock)
    }

    /** Starts (or retries) the one-off Japanese model download. */
    fun prepareModel() {
        viewModelScope.launch { runCatching { recognitionService.prepare() } }
    }

    fun setDailyNewLimit(limit: Int) {
        val clamped = StudySettings.coerceDailyNewLimit(limit)
        viewModelScope.launch { settingsRepository.setDailyNewLimit(clamped) }
    }

    fun nudgeDailyNewLimit(steps: Int) {
        val next = uiState.value.dailyNewLimit + steps * StudySettings.DAILY_NEW_LIMIT_STEP
        setDailyNewLimit(next)
    }

    private data class Counts(
        val due: Int,
        val introducedToday: Int,
        val newInDeck: Int,
        val total: Int,
    )
}
