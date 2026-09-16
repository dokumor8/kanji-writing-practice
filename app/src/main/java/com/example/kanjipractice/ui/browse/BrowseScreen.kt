package com.example.kanjipractice.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.domain.deck.DeckCatalog
import com.example.kanjipractice.domain.model.levelLabel
import com.example.kanjipractice.domain.repository.CardRepository
import com.example.kanjipractice.domain.settings.StudySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Read-only deck browser. Shows the selected sets only, so it reflects what the
 * user is actually studying.
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    cardRepository: CardRepository,
    settingsRepository: StudySettingsRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val cards: StateFlow<List<CardEntity>> = settingsRepository.observeSelectedDeckIds()
        .flatMapLatest { cardRepository.observeAll(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onBack: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(cards.size.toString() + " cards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(cards, key = { it.id }) { card ->
                CardRow(card)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CardRow(card: CardEntity) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = card.character, fontSize = 32.sp, fontWeight = FontWeight.Medium)
        Column(
            Modifier.weight(1f).padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = card.meaning, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detailLine(card),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun detailLine(card: CardEntity): String {
    val parts = mutableListOf(DeckCatalog.nameOf(card.deckId.orEmpty()))
    card.levelLabel()?.let { parts += it }
    parts += card.state.name.lowercase()
    parts += "reps " + card.reps
    parts += "S " + format(card.stability)
    parts += "D " + format(card.difficulty)
    return parts.joinToString(" - ")
}

private fun format(value: Double): String =
    if (value == 0.0) "-" else ((value * 100).toInt() / 100.0).toString()
