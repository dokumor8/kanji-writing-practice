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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjipractice.R
import com.example.kanjipractice.domain.recognition.ModelState

/**
 * The deck list: what is due, and the way into a session. Limits and the
 * handwriting model live in settings.
 */
@Composable
fun DeckListScreen(
    onStartReview: () -> Unit,
    onBrowse: () -> Unit,
    onSettings: () -> Unit,
    viewModel: DeckListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            // The app's own name, so the Chinese build does not announce itself
            // as a kanji trainer.
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = state.dueReviews.toString() + " due  -  " +
                        state.newAvailable.toString() + " new",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.totalCount.toString() + " cards",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.newAllowanceUsedUp) {
                    Text(
                        text = "Daily new-card limit reached",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onStartReview,
                    enabled = !state.loading && state.studyCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.studyCount > 0) "Start review" else "Nothing to study")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBrowse, modifier = Modifier.weight(1f)) {
                Text("Browse")
            }
            TextButton(onClick = onSettings, modifier = Modifier.weight(1f)) {
                Text("Settings")
            }
        }

        if (state.nothingSelected) {
            Spacer(Modifier.height(8.dp))
            Notice(
                text = "No card sets selected",
                action = "Choose sets",
                onAction = onSettings,
            )
        }

        // The model is the one thing that can stop a review from working at all,
        // so it is the one setup problem worth surfacing here.
        if (state.modelState !is ModelState.Ready) {
            Spacer(Modifier.height(8.dp))
            Notice(
                text = modelLabel(state.modelState),
                action = "Settings",
                onAction = onSettings,
            )
        }

        if (state.loading) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Preparing deck", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun modelLabel(state: ModelState): String = when (state) {
    ModelState.Unknown, ModelState.Checking -> "Checking the handwriting model"
    ModelState.NotDownloaded -> "Handwriting model not downloaded"
    ModelState.Downloading -> "Downloading the handwriting model"
    is ModelState.Failed -> "Handwriting model unavailable: " + state.message
    ModelState.Ready -> "Handwriting model ready"
}

@Composable
private fun Notice(text: String, action: String, onAction: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
