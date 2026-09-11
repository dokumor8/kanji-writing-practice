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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjipractice.domain.recognition.ModelState

/**
 * The deck list (plan, section 9.1), plus the two bits of setup the app needs:
 * the offline recognition model, and how many new cards to feed in per day.
 */
@Composable
fun DeckListScreen(
    onStartReview: () -> Unit,
    onBrowse: () -> Unit,
    viewModel: DeckListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Counts depend on the wall clock and on the study day, so recompute them
    // whenever the screen is resumed rather than trusting the values computed
    // when the ViewModel was created.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
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

        Spacer(Modifier.height(20.dp))

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
                    text = state.dueReviews.toString() + " due  -  " +
                        state.newAvailable.toString() + " new  -  " +
                        state.totalCount.toString() + " cards",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (state.newAllowanceUsedUp) {
                    Text(
                        text = "Daily new-card limit reached. This is what keeps the " +
                            "deck from burying you.",
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
                TextButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                    Text("Browse all cards")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        DailyNewLimitRow(
            limit = state.dailyNewLimit,
            introducedToday = state.introducedToday,
            onNudge = viewModel::nudgeDailyNewLimit,
        )

        Spacer(Modifier.height(16.dp))

        ModelStatusRow(
            state = state.modelState,
            onPrepare = viewModel::prepareModel,
            onReinstall = viewModel::reinstallModel,
        )

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
 * The daily new-card allowance. It is a plain stepper rather than a slider so the
 * value is readable and reproducible.
 */
@Composable
private fun DailyNewLimitRow(
    limit: Int,
    introducedToday: Int,
    onNudge: (Int) -> Unit,
) {
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
                Text(text = "New cards per day", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = introducedToday.toString() + " introduced today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                textAlign = TextAlign.Center,
                modifier = Modifier.width(48.dp),
            )
            OutlinedButton(
                onClick = { onNudge(+1) },
                modifier = Modifier.size(width = 56.dp, height = 40.dp),
            ) { Text("+") }
        }
    }
}

/**
 * The one place where the app needs the network: ML Kit's Japanese model is a
 * one-off download and recognition is offline afterwards.
 */
@Composable
private fun ModelStatusRow(
    state: ModelState,
    onPrepare: () -> Unit,
    onReinstall: () -> Unit,
) {
    var confirmReinstall by remember { mutableStateOf(false) }
    if (confirmReinstall) {
        AlertDialog(
            onDismissRequest = { confirmReinstall = false },
            title = { Text("Reinstall the handwriting model?") },
            text = {
                Text(
                    "The downloaded Japanese model is deleted and fetched again. " +
                        "Recognition is unavailable until that finishes. Your review " +
                        "history is not affected."
                )
            },
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
                        ModelState.Unknown -> "Checking whether it is already installed..."
                        ModelState.Checking -> "Checking..."
                        ModelState.NotDownloaded -> "Not installed yet"
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
                // Never offer a download until a check has actually said the
                // model is missing: that is what used to make the button flash
                // on every launch and then vanish.
                ModelState.Unknown, ModelState.Checking, ModelState.Downloading ->
                    CircularProgressIndicator(Modifier.size(20.dp))

                // A model that is present can still be broken, and it lives in
                // app-private storage the user cannot reach; this is the only
                // recovery that does not wipe their review history too.
                ModelState.Ready -> TextButton(onClick = { confirmReinstall = true }) {
                    Text("Reinstall")
                }

                ModelState.NotDownloaded -> OutlinedButton(onClick = onPrepare) {
                    Text("Download")
                }

                is ModelState.Failed -> OutlinedButton(onClick = onPrepare) {
                    Text("Retry")
                }
            }
        }
    }
}
