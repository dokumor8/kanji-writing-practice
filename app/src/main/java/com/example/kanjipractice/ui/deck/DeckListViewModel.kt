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
import kotlinx.coroutines.flow.Flow
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
    /** Scheduled cards that are due today. */
    val dueReviews: Int = 0,
    /** Never-seen cards that may still be introduced today. */
    val newAvailable: Int = 0,
    /** Never-seen cards left in the selected sets overall. */
    val newRemainingInDeck: Int = 0,
    val totalCount: Int = 0,
    /** True when the user has switched every set off. */
    val nothingSelected: Boolean = false,
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
     * The counts are a function of the study day and of which sets are selected,
     * so they are recomputed whenever either moves. Without that the deck screen
     * kept whatever it computed at construction.
     */
    private val now = MutableStateFlow(LocalDateTime.now(clock))

    /**
     * True until the bundled card data has been reconciled. On an upgrade the
     * first launch inserts a thousand-odd new cards, and starting a session
     * against a half-populated table would show an arbitrary slice of the deck.
     */
    private val syncing = MutableStateFlow(true)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val counts: Flow<Counts> =
        combine(now, settingsRepository.observeSelectedDeckIds()) { _, deckIds -> deckIds }
            .flatMapLatest { deckIds ->
                combine(
                    // "Due" means due before the end of today's study day, so the
                    // number here is what a session will actually contain.
                    cardRepository.observeDueReviewCount(
                        DayBoundary.endOfStudyDay(clock, zone),
                        deckIds,
                    ),
                    reviewLogRepository.observeIntroducedSince(
                        DayBoundary.startOfStudyDay(clock, zone)
                    ),
                    cardRepository.observeNewCount(deckIds),
                    cardRepository.observeTotalCount(deckIds),
                ) { due, introduced, newCount, total ->
                    Counts(
                        due = due,
                        introducedToday = introduced,
                        newInDeck = newCount,
                        total = total,
                        nothingSelected = deckIds.isEmpty(),
                    )
                }
            }

    val uiState: StateFlow<DeckListUiState> =
        combine(
            counts,
            settingsRepository.observeDailyNewLimit(),
            recognitionService.modelState,
            syncing,
        ) { counts, limit, model, isSyncing ->
            val remainingAllowance = (limit - counts.introducedToday).coerceAtLeast(0)
            DeckListUiState(
                dueReviews = counts.due,
                newAvailable = minOf(remainingAllowance, counts.newInDeck),
                newRemainingInDeck = counts.newInDeck,
                totalCount = counts.total,
                nothingSelected = counts.nothingSelected,
                dailyNewLimit = limit,
                introducedToday = counts.introducedToday,
                modelState = model,
                loading = isSyncing,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckListUiState(),
        )

    init {
        viewModelScope.launch {
            runCatching { deckSeeder.syncDecks() }
            syncing.value = false
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

    private data class Counts(
        val due: Int,
        val introducedToday: Int,
        val newInDeck: Int,
        val total: Int,
        val nothingSelected: Boolean,
    )
}
