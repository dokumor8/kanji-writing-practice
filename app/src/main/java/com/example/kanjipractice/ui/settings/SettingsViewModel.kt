package com.example.kanjipractice.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kanjipractice.domain.deck.DeckCatalog
import com.example.kanjipractice.domain.deck.DeckInfo
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

/** The sets under one heading, e.g. "Kana" or "HSK". */
data class DeckGroup(val name: String, val decks: List<DeckRow>)

data class SettingsUiState(
    val groups: List<DeckGroup> = emptyList(),
    val dailyNewLimit: Int = StudySettings.DEFAULT_DAILY_NEW_LIMIT,
    val introducedToday: Int = 0,
    val acceptedCandidates: Int = StudySettings.DEFAULT_ACCEPTED_CANDIDATES,
    val similarityThresholdPercent: Int = StudySettings.DEFAULT_SIMILARITY_PERCENT,
    val modelState: ModelState = ModelState.Unknown,
) {
    private val allRows: List<DeckRow> get() = groups.flatMap { it.decks }

    val selectedCount: Int get() = allRows.count { it.selected }

    val selectedCardCount: Int get() = allRows.filter { it.selected }.sumOf { it.cardCount }
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
        val candidates: Int,
        val similarity: Int,
    )

    private val base = combine(
        cardRepository.observeDeckCounts(),
        settingsRepository.observeSelectedDeckIds(),
        settingsRepository.observeDailyNewLimit(),
        recognitionService.modelState,
        settingsRepository.observeAcceptedCandidates(),
    ) { counts, selected, limit, model, candidates ->
        Base(counts, selected, limit, model, candidates, StudySettings.DEFAULT_SIMILARITY_PERCENT)
    }.combine(settingsRepository.observeSimilarityThresholdPercent()) { state, similarity ->
        state.copy(similarity = similarity)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        base,
        reviewLogRepository.observeIntroducedSince(DayBoundary.startOfStudyDay(clock, zone)),
    ) { state, introduced ->
        SettingsUiState(
            groups = DeckCatalog.groups.map { group ->
                DeckGroup(
                    name = group,
                    decks = DeckCatalog.inGroup(group).map { deck ->
                        DeckRow(
                            info = deck,
                            cardCount = state.counts[deck.id] ?: 0,
                            selected = deck.id in state.selected,
                        )
                    },
                )
            },
            dailyNewLimit = state.limit,
            introducedToday = introduced,
            acceptedCandidates = state.candidates,
            similarityThresholdPercent = state.similarity,
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
            settingsRepository.setSelectedDeckIds(
                if (deckId in current) current - deckId else current + deckId
            )
        }
    }

    /** Selects or clears every set under one heading. */
    fun setGroup(group: String, selected: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.observeSelectedDeckIds().first()
            val ids = DeckCatalog.inGroup(group).map { it.id }.toSet()
            settingsRepository.setSelectedDeckIds(
                if (selected) current + ids else current - ids
            )
        }
    }

    fun setDailyNewLimit(limit: Int) {
        val clamped = StudySettings.coerceDailyNewLimit(limit)
        viewModelScope.launch { settingsRepository.setDailyNewLimit(clamped) }
    }

    fun nudgeDailyNewLimit(steps: Int) {
        setDailyNewLimit(uiState.value.dailyNewLimit + steps * StudySettings.DAILY_NEW_LIMIT_STEP)
    }

    fun setAcceptedCandidates(count: Int) {
        val clamped = StudySettings.coerceAcceptedCandidates(count)
        viewModelScope.launch { settingsRepository.setAcceptedCandidates(clamped) }
    }

    fun nudgeAcceptedCandidates(steps: Int) {
        setAcceptedCandidates(uiState.value.acceptedCandidates + steps * 1)
    }

    fun setSimilarityThresholdPercent(percent: Int) {
        val clamped = StudySettings.coerceSimilarityPercent(percent)
        viewModelScope.launch { settingsRepository.setSimilarityThresholdPercent(clamped) }
    }

    fun nudgeSimilarityThreshold(steps: Int) {
        setSimilarityThresholdPercent(
            uiState.value.similarityThresholdPercent + steps * StudySettings.SIMILARITY_STEP
        )
    }

    /** Starts (or retries) the one-off recognition model download. */
    fun prepareModel() {
        viewModelScope.launch { runCatching { recognitionService.prepare() } }
    }

    /** Deletes the downloaded model and fetches it again. */
    fun reinstallModel() {
        viewModelScope.launch { runCatching { recognitionService.reinstallModel() } }
    }
}
