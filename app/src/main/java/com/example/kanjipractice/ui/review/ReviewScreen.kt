package com.example.kanjipractice.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.data.deck.DeckJsonParser
import com.example.kanjipractice.domain.stroke.StrokeDiagram
import com.example.fsrs.Rating
import com.example.kanjipractice.ui.components.DrawingCanvas
import com.example.kanjipractice.ui.components.StrokeOrderView

/**
 * The review screen (plan, sections 6 and 9.2). Every visual decision here is
 * there to protect one rule: nothing on the PROMPT screen may hint at the shape
 * of the character.
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
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                ReviewUiState.Loading -> CenteredMessage("Loading your reviews...")
                ReviewUiState.SessionComplete -> SessionComplete(onExit)
                is ReviewUiState.Prompt -> PromptContent(current, viewModel)
                is ReviewUiState.Success -> SuccessContent(current, viewModel)
                is ReviewUiState.Relearn -> RelearnContent(current, viewModel)
            }
        }
    }
}

private fun titleFor(state: ReviewUiState): String = when (state) {
    is ReviewUiState.Prompt -> state.remaining.toString() + " to go"
    is ReviewUiState.Success -> "Correct"
    is ReviewUiState.Relearn -> "Learn it, then trace it"
    ReviewUiState.SessionComplete -> "Session complete"
    ReviewUiState.Loading -> "Review"
}

// --------------------------------------------------------------------- PROMPT

@Composable
private fun PromptContent(state: ReviewUiState.Prompt, viewModel: ReviewViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        PromptPanel(state.card)

        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            DrawingCanvas(
                strokes = state.strokes,
                onStrokeFinished = viewModel::onStrokeFinished,
                modifier = Modifier.fillMaxSize(),
                enabled = !state.busy,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            if (state.busy) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        MessageLine(state.message)

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::undo,
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
                onClick = viewModel::iDontKnow,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { Text("I don't know") }
            Button(
                onClick = viewModel::submit,
                enabled = !state.busy,
                modifier = Modifier.weight(1.4f),
            ) { Text("Submit") }
        }
    }
}

/**
 * The prompt carries meaning and readings only. There is deliberately no button
 * here that reveals the shape of the character: the only way to see the diagram
 * before succeeding is to press "I don't know", which fails the card (plan,
 * section 12).
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
            if (card.onyomi != null) {
                LabeledValue("On", card.onyomi)
            }
            if (card.kunyomi != null) {
                LabeledValue("Kun", card.kunyomi)
            }
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
        Text(
            text = state.card.character,
            fontSize = 44.sp,
            fontWeight = FontWeight.SemiBold,
        )
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
                text = "You wrote " + state.recognized,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            modifier = Modifier.size(220.dp).padding(top = 4.dp),
            animate = true,
            showNumbers = true,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "How well did you recall it?",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RatingButton("Hard", state.rating == Rating.HARD, Modifier.weight(1f)) {
                viewModel.rate(Rating.HARD)
            }
            RatingButton("Good", state.rating == Rating.GOOD, Modifier.weight(1f)) {
                viewModel.rate(Rating.GOOD)
            }
            RatingButton("Easy", state.rating == Rating.EASY, Modifier.weight(1f)) {
                viewModel.rate(Rating.EASY)
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

@Composable
private fun RatingButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

// -------------------------------------------------------------------- RELEARN

@Composable
private fun RelearnContent(state: ReviewUiState.Relearn, viewModel: ReviewViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.card.character,
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(text = state.card.meaning, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = strokeCountLabel(state.diagram),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "This card is marked Again. Trace the character to finish it.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(8.dp))

        // The diagram sits underneath, dimmed, and the canvas goes on top with a
        // transparent background: tracing is the point of this step (plan 6.4).
        Box(Modifier.weight(1f).fillMaxWidth()) {
            StrokeOrderView(
                diagram = state.diagram,
                character = state.card.character,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                animate = true,
                showNumbers = true,
                strokeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
                showGuideBox = false,
            )
            DrawingCanvas(
                strokes = state.strokes,
                onStrokeFinished = viewModel::onRelearnStrokeFinished,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                enabled = !state.busy,
                backgroundColor = Color.Transparent,
            )
            if (state.busy) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        MessageLine(state.message)

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::undoRelearn,
                enabled = !state.busy && state.strokes.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Undo") }
            OutlinedButton(
                onClick = viewModel::clearRelearn,
                enabled = !state.busy && state.strokes.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Clear") }
            Button(
                onClick = viewModel::submitRelearn,
                enabled = !state.busy,
                modifier = Modifier.weight(1.4f),
            ) { Text("Done tracing") }
        }
    }
}

private fun strokeCountLabel(diagram: StrokeDiagram): String =
    if (diagram.strokeCount == 0) "" else diagram.strokeCount.toString() + " strokes"

// ---------------------------------------------------------------- shared bits

/**
 * Reserves its line's height even when empty so the canvas does not resize and
 * the drawing does not appear to jump when a message appears.
 */
@Composable
private fun MessageLine(message: String?) {
    Box(
        Modifier.fillMaxWidth().height(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
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
private fun SessionComplete(onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Nothing left to review", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Come back when the next cards are due.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onExit) { Text("Back to deck") }
    }
}
