package com.example.kanjipractice.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjipractice.domain.recognition.ModelState

/**
 * The deck list (plan, section 9.1). The prototype ships a single deck, so this
 * screen is really "start a session", plus the one piece of setup the app needs:
 * the offline recognition model.
 */
@Composable
fun DeckListScreen(
    onStartReview: () -> Unit,
    onBrowse: () -> Unit,
    viewModel: DeckListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Kanji Practice",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Draw each character from memory. Meaning and readings only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = "JLPT N5-N3 Kanji",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.dueCount.toString() + " due - " +
                        state.totalCount.toString() + " cards",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onStartReview,
                    enabled = !state.loading && state.dueCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.dueCount > 0) "Start review" else "Nothing due")
                }
                TextButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                    Text("Browse all cards")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        ModelStatusRow(state.modelState, onPrepare = viewModel::prepareModel)

        if (state.loading) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Preparing deck...", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * The one place where the app needs the network (plan, section 12): ML Kit's
 * Japanese model is a one-off download and recognition is offline afterwards.
 */
@Composable
private fun ModelStatusRow(state: ModelState, onPrepare: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "Handwriting model", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = when (state) {
                        ModelState.Unknown -> "Not checked yet"
                        ModelState.Checking -> "Checking..."
                        ModelState.Downloading -> "Downloading the Japanese model..."
                        ModelState.Ready -> "Ready - recognition works offline"
                        is ModelState.Failed -> "Unavailable: " + state.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state is ModelState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            when (state) {
                ModelState.Downloading, ModelState.Checking ->
                    CircularProgressIndicator(Modifier.size(20.dp))
                ModelState.Ready -> Unit
                else -> OutlinedButton(onClick = onPrepare) {
                    Text(if (state is ModelState.Failed) "Retry" else "Download")
                }
            }
        }
    }
}
