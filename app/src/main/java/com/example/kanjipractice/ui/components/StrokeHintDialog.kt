package com.example.kanjipractice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.domain.model.readings
import com.example.kanjipractice.domain.stroke.StrokeDiagram

/**
 * The stroke hint, shown when the user presses "I don't know".
 *
 * It is a popup rather than a screen on purpose. Dismissing it puts the user back
 * on the same drawing surface, so the character has to survive a few seconds in
 * working memory before it can be drawn -- the whole point of the hint being a
 * peek rather than a reference to copy from. It can be reopened as often as the
 * user likes.
 *
 * "Give up" lives here because this is where a stuck user already is, and it is
 * destructive, so it is red, on the opposite side from Close, and asks first.
 */
@Composable
fun StrokeHintDialog(
    card: CardEntity,
    diagram: StrokeDiagram,
    onDismiss: () -> Unit,
    onGiveUp: () -> Unit,
) {
    var confirmGiveUp by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.character,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(text = card.meaning, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = strokeCountLabel(diagram),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StrokeOrderView(
                        diagram = diagram,
                        character = card.character,
                        modifier = Modifier.size(200.dp),
                        animate = true,
                        showNumbers = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        card.readings().forEach { (label, value) ->
                            Text(
                                text = label + ": " + value,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Laid out here rather than in a dialog's button row, which packs
                // everything together: giving up should not sit under the thumb on
                // the way to Close.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { confirmGiveUp = true }, contentPadding = PaddingValues(8.dp)) {
                        Text(
                            text = "Give up",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Button(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }

    if (confirmGiveUp) {
        AlertDialog(
            onDismissRequest = { confirmGiveUp = false },
            title = { Text("Give up on this card?") },
            text = {
                Text(
                    "It goes to the rating screen with Again selected, so it will " +
                        "come back sooner. You can still change the rating there."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmGiveUp = false
                        onGiveUp()
                    }
                ) {
                    Text(text = "Give up", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmGiveUp = false }) { Text("Keep drawing") }
            },
        )
    }
}

internal fun strokeCountLabel(diagram: StrokeDiagram): String =
    if (diagram.strokeCount == 0) "" else diagram.strokeCount.toString() + " strokes"
