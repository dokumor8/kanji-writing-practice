package com.example.kanjipractice.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjipractice.domain.recognition.ModelState
import com.example.kanjipractice.domain.settings.StudySettings

/** Which card sets are studied, how many new cards a day, and the model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SectionHeader("Kana")
            state.kanaDecks.forEach { row ->
                DeckToggleRow(row, viewModel::toggleDeck)
            }

            SectionHeader("Kanji")
            state.kanjiDecks.forEach { row ->
                DeckToggleRow(row, viewModel::toggleDeck)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.setAllKanji(true) }) { Text("All") }
                TextButton(onClick = { viewModel.setAllKanji(false) }) { Text("None") }
            }

            SectionHeader("New cards per day")
            LimitRow(
                limit = state.dailyNewLimit,
                introducedToday = state.introducedToday,
                onNudge = viewModel::nudgeDailyNewLimit,
            )

            SectionHeader("Handwriting model")
            ModelSection(
                state = state.modelState,
                onPrepare = viewModel::prepareModel,
                onReinstall = viewModel::reinstallModel,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = state.selectedCardCount.toString() + " cards in " +
                    state.selectedCount.toString() + " sets",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    HorizontalDivider(Modifier.padding(top = 6.dp, bottom = 4.dp))
}

@Composable
private fun DeckToggleRow(row: DeckRow, onToggle: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = row.selected, role = Role.Checkbox) { onToggle(row.info.id) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.selected, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(text = row.info.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = row.cardCount.toString() + " cards",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LimitRow(limit: Int, introducedToday: Int, onNudge: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = introducedToday.toString() + " introduced today",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(
            onClick = { onNudge(-1) },
            enabled = limit > 0,
            modifier = Modifier.size(width = 56.dp, height = 40.dp),
        ) { Text("-") }
        Text(
            text = limit.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        OutlinedButton(
            onClick = { onNudge(+1) },
            enabled = limit < StudySettings.MAX_DAILY_NEW_LIMIT,
            modifier = Modifier.size(width = 56.dp, height = 40.dp),
        ) { Text("+") }
    }
}

@Composable
private fun ModelSection(
    state: ModelState,
    onPrepare: () -> Unit,
    onReinstall: () -> Unit,
) {
    var confirmReinstall by remember { mutableStateOf(false) }
    if (confirmReinstall) {
        AlertDialog(
            onDismissRequest = { confirmReinstall = false },
            title = { Text("Reinstall the handwriting model?") },
            text = { Text("The model is downloaded again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReinstall = false
                    onReinstall()
                }) { Text("Reinstall") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReinstall = false }) { Text("Cancel") }
            },
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = modelLabel(state), style = MaterialTheme.typography.bodyMedium)
        }
        when (state) {
            // Never offer a download until a check has actually said the model is
            // missing: that is what used to make the button flash on every launch
            // and then vanish.
            ModelState.Unknown, ModelState.Checking, ModelState.Downloading ->
                CircularProgressIndicator(Modifier.size(20.dp))

            // A model that is present can still be broken, and it lives in
            // app-private storage the user cannot reach.
            ModelState.Ready -> OutlinedButton(onClick = { confirmReinstall = true }) {
                Text("Reinstall")
            }

            ModelState.NotDownloaded, is ModelState.Failed -> OutlinedButton(onClick = onPrepare) {
                Text(if (state is ModelState.Failed) "Retry" else "Download")
            }
        }
    }
}

private fun modelLabel(state: ModelState): String = when (state) {
    ModelState.Unknown, ModelState.Checking -> "Checking..."
    ModelState.NotDownloaded -> "Not installed"
    ModelState.Downloading -> "Downloading..."
    ModelState.Ready -> "Ready"
    is ModelState.Failed -> "Unavailable: " + state.message
}
