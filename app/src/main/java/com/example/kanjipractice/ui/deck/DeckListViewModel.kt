package com.example.kanjipractice.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kanjipractice.data.DeckSeeder
import com.example.kanjipractice.domain.recognition.ModelState
import com.example.kanjipractice.domain.recognition.RecognitionService
import com.example.kanjipractice.domain.repository.CardRepository
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
import javax.inject.Inject

data class DeckListUiState(
    val dueCount: Int = 0,
    val totalCount: Int = 0,
    val modelState: ModelState = ModelState.Unknown,
    val loading: Boolean = true,
)

@HiltViewModel
class DeckListViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val deckSeeder: DeckSeeder,
    private val recognitionService: RecognitionService,
    private val clock: Clock,
) : ViewModel() {

    /**
     * "Due" is a function of the current time, so the query is re-run whenever
     * [refresh] moves this forward; otherwise a card that becomes due while the
     * app sits open would never appear.
     */
    private val now = MutableStateFlow(LocalDateTime.now(clock))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dueCount = now.flatMapLatest { cardRepository.observeDueCount(it) }

    val uiState: StateFlow<DeckListUiState> =
        combine(
            dueCount,
            cardRepository.observeTotalCount(),
            recognitionService.modelState,
        ) { due, total, model ->
            DeckListUiState(dueCount = due, totalCount = total, modelState = model, loading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckListUiState(),
        )

    init {
        // First launch: fill the cards table from the bundled deck.
        viewModelScope.launch { deckSeeder.seedIfEmpty() }
    }

    fun refresh() {
        now.value = LocalDateTime.now(clock)
    }

    /** Starts (or retries) the one-off Japanese model download. */
    fun prepareModel() {
        viewModelScope.launch { runCatching { recognitionService.prepare() } }
    }
}
