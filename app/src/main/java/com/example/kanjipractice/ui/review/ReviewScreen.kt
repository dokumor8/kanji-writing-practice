package com.example.kanjipractice.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.data.deck.DeckJsonParser
import com.example.kanjipractice.domain.model.readings
import com.example.fsrs.Rating
import com.example.kanjipractice.ui.components.DrawingCanvas
import com.example.kanjipractice.ui.components.DrawingPreview
import com.example.kanjipractice.ui.components.StrokeHintDialog
import com.example.kanjipractice.ui.components.StrokeOrderView

/**
 * The review screen.
 *
 * Every visual decision here protects one rule: nothing on the prompt may hint at
 * the shape of the character. The hint exists, but it is a popup that has to be
 * dismissed before drawing, so it cannot be traced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onExit: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(state)) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Leave review",
                        )
                    }
                },
                actions = {
                    // Recover from a misclick on the rating screen.
                    TextButton(
                        onClick = viewModel::undoLastReview,
                        enabled = state.canUndo,
                    ) { Text("Undo review") }
                },
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                ReviewUiState.Loading -> CenteredMessage("Loading...")
                is ReviewUiState.SessionComplete -> SessionComplete(current, onExit)
                is ReviewUiState.Prompt -> PromptContent(current, viewModel)
                is ReviewUiState.Success -> SuccessContent(current, viewModel)
            }
        }
    }
}

private fun titleFor(state: ReviewUiState): String = when (state) {
    is ReviewUiState.Prompt -> state.remaining.toString() + " to go"
    is ReviewUiState.Success -> "Correct"
    is ReviewUiState.SessionComplete -> "Session complete"
    ReviewUiState.Loading -> "Review"
}

// --------------------------------------------------------------------- PROMPT

@Composable
private fun PromptContent(state: ReviewUiState.Prompt, viewModel: ReviewViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        PromptPanel(state.card)

        Spacer(Modifier.height(12.dp))

        // A square writing area: it is the shape a kanji is designed for, it
        // keeps the geometry the recogniser sees consistent across devices, and
        // it matches the square the stroke diagram is drawn in.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val side = minOf(maxWidth, maxHeight)
            DrawingCanvas(
                strokes = state.strokes,
                onStrokeFinished = viewModel::onStrokeFinished,
                modifier = Modifier.size(side).align(Alignment.Center),
                enabled = !state.busy,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            if (state.busy) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        MessageLine(state.message)

        // A recogniser that is simply broken must not make the deck unusable.
        if (state.recognitionFailed) {
            TextButton(
                onClick = viewModel::gradeManually,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Rate this card myself") }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::undoStroke,
                enabled = !state.busy && state.strokes.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Undo") }
            OutlinedButton(
                onClick = viewModel::clear,
                enabled = !state.busy && state.strokes.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Clear") }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = viewModel::showHint,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.hintCount == 0) "I don't know" else "Hint again")
            }
            Button(
                onClick = viewModel::submit,
                enabled = !state.busy,
                modifier = Modifier.weight(1.4f),
            ) { Text("Submit") }
        }
    }

    if (state.hintVisible) {
        StrokeHintDialog(
            card = state.card,
            diagram = state.diagram,
            onDismiss = viewModel::dismissHint,
        )
    }
}

/**
 * The prompt carries meaning and readings only. There is deliberately no button
 * here that reveals the shape of the character in place: the hint is a popup
 * that covers the canvas, so it can be glimpsed but not traced.
 */
@Composable
private fun PromptPanel(card: CardEntity) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = card.meaning,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            card.readings().forEach { (label, value) -> LabeledValue(label, value) }
            card.exampleWord?.let { word ->
                LabeledValue("Example", blanked(word, card.character))
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.padding(top = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The bundled deck already stores the example word with the target blanked;
 * blanking again is harmless and keeps the prompt correct if a deck ships the
 * full word.
 */
private fun blanked(exampleWord: String, character: String): String =
    exampleWord.replace(character, DeckJsonParser.BLANK)

// -------------------------------------------------------------------- SUCCESS

@Composable
private fun SuccessContent(state: ReviewUiState.Success, viewModel: ReviewViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The target and what the user actually drew, side by side: seeing only
        // the correct character is not enough to judge your own attempt, and for
        // the "I drew a wrong character that was accepted" case it is the whole
        // point.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ComparisonPane(label = "Target", modifier = Modifier.weight(1f)) {
                Text(
                    text = state.card.character,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            ComparisonPane(label = "You drew", modifier = Modifier.weight(1f)) {
                DrawingPreview(
                    strokes = state.strokes,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(text = state.card.meaning, style = MaterialTheme.typography.titleMedium)
        if (state.retryCount > 0) {
            Text(
                text = "after " + state.retryCount + " attempts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.recognized != null && state.recognized != state.card.character) {
            Text(
                text = "Recognised as " + state.recognized,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (state.recognized == null) {
            Text(
                text = "Not checked - the recogniser was unavailable.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Stroke order",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StrokeOrderView(
            diagram = state.diagram,
            character = state.card.character,
            modifier = Modifier.size(200.dp).padding(top = 4.dp),
            animate = true,
            showNumbers = true,
        )

        Spacer(Modifier.height(12.dp))

        if (state.hintCount > 0) {
            Text(
                text = "Hint used",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Again is here so a wrong drawing the recogniser accepted can still be
        // failed. Two rows of two rather than one row of four: four labels do not
        // fit across a phone, and "Again" was being clipped to "Agai".
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingButton("Again", state.rating == Rating.AGAIN, Modifier.weight(1f)) {
                    viewModel.rate(Rating.AGAIN)
                }
                RatingButton("Hard", state.rating == Rating.HARD, Modifier.weight(1f)) {
                    viewModel.rate(Rating.HARD)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingButton("Good", state.rating == Rating.GOOD, Modifier.weight(1f)) {
                    viewModel.rate(Rating.GOOD)
                }
                RatingButton("Easy", state.rating == Rating.EASY, Modifier.weight(1f)) {
                    viewModel.rate(Rating.EASY)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = viewModel::next,
            enabled = state.rating != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Next") }
    }
}

/** One square of the target-versus-drawing comparison. */
@Composable
private fun ComparisonPane(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(104.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun RatingButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---------------------------------------------------------------- shared bits

/**
 * Reserves at least a line's height even when empty so the canvas does not resize
 * and the drawing does not appear to jump when a message appears -- but grows for
 * a long one, because a recognition error has to be readable to be actionable.
 */
@Composable
private fun MessageLine(message: String?) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SessionComplete(state: ReviewUiState.SessionComplete, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Nothing left to study", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Come back tomorrow.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onExit) { Text("Back to deck") }
    }
}
