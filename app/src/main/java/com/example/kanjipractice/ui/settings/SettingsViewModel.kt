package com.example.kanjipractice.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kanjipractice.domain.deck.DeckCatalog
import com.example.kanjipractice.domain.deck.DeckInfo
import com.example.kanjipractice.domain.deck.DeckKind
import com.example.kanjipractice.domain.recognition.ModelState
import com.example.kanjipractice.domain.recognition.RecognitionService
import com.example.kanjipractice.domain.repository.CardRepository
import com.example.kanjipractice.domain.repository.ReviewLogRepository
import com.example.kanjipractice.domain.settings.StudySettings
import com.example.kanjipractice.domain.settings.StudySettingsRepository
import com.example.kanjipractice.domain.util.DayBoundary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

data class DeckRow(
    val info: DeckInfo,
    val cardCount: Int,
    val selected: Boolean,
)

data class SettingsUiState(
    val kanaDecks: List<DeckRow> = emptyList(),
    val kanjiDecks: List<DeckRow> = emptyList(),
    val dailyNewLimit: Int = StudySettings.DEFAULT_DAILY_NEW_LIMIT,
    val introducedToday: Int = 0,
    val modelState: ModelState = ModelState.Unknown,
) {
    val selectedCount: Int
        get() = (kanaDecks + kanjiDecks).count { it.selected }

    val selectedCardCount: Int
        get() = (kanaDecks + kanjiDecks).filter { it.selected }.sumOf { it.cardCount }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val settingsRepository: StudySettingsRepository,
    private val recognitionService: RecognitionService,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {

    private data class Base(
        val counts: Map<String, Int>,
        val selected: Set<String>,
        val limit: Int,
        val model: ModelState,
    )

    private val base = combine(
        cardRepository.observeDeckCounts(),
        settingsRepository.observeSelectedDeckIds(),
        settingsRepository.observeDailyNewLimit(),
        recognitionService.modelState,
    ) { counts, selected, limit, model -> Base(counts, selected, limit, model) }

    val uiState: StateFlow<SettingsUiState> = combine(
        base,
        reviewLogRepository.observeIntroducedSince(DayBoundary.startOfStudyDay(clock, zone)),
    ) { state, introduced ->
        val rows = DeckCatalog.ALL.map { deck ->
            DeckRow(
                info = deck,
                cardCount = state.counts[deck.id] ?: 0,
                selected = deck.id in state.selected,
            )
        }
        SettingsUiState(
            kanaDecks = rows.filter { it.info.kind == DeckKind.KANA },
            kanjiDecks = rows.filter { it.info.kind == DeckKind.KANJI },
            dailyNewLimit = state.limit,
            introducedToday = introduced,
            modelState = state.model,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        viewModelScope.launch { runCatching { recognitionService.refreshModelState() } }
    }

    fun toggleDeck(deckId: String) {
        viewModelScope.launch {
            val current = settingsRepository.observeSelectedDeckIds().first()
            val next = if (deckId in current) current - deckId else current + deckId
            settingsRepository.setSelectedDeckIds(next)
        }
    }

    fun setAllKanji(selected: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.observeSelectedDeckIds().first()
            val next = if (selected) {
                current + DeckCatalog.KANJI_IDS
            } else {
                current - DeckCatalog.KANJI_IDS
            }
            settingsRepository.setSelectedDeckIds(next)
        }
    }

    fun setDailyNewLimit(limit: Int) {
        val clamped = StudySettings.coerceDailyNewLimit(limit)
        viewModelScope.launch { settingsRepository.setDailyNewLimit(clamped) }
    }

    fun nudgeDailyNewLimit(steps: Int) {
        val next = uiState.value.dailyNewLimit + steps * StudySettings.DAILY_NEW_LIMIT_STEP
        setDailyNewLimit(next)
    }

    /** Starts (or retries) the one-off Japanese model download. */
    fun prepareModel() {
        viewModelScope.launch { runCatching { recognitionService.prepare() } }
    }

    /** Deletes the downloaded model and fetches it again. */
    fun reinstallModel() {
        viewModelScope.launch { runCatching { recognitionService.reinstallModel() } }
    }
}
