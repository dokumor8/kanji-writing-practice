package com.example.kanjipractice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.domain.stroke.StrokeDiagram

/**
 * The stroke hint, shown when the user presses "I don't know".
 *
 * It is a popup rather than a screen on purpose. Dismissing it puts the user back
 * on the same drawing surface, so the character has to survive a few seconds in
 * working memory before it can be drawn -- which is the whole point of the hint
 * being a peek rather than a reference to copy from. The user may reopen it as
 * often as they like.
 *
 * The result screen opens with **Again** selected after a hint, so peeking is
 * honest about what it cost, without the app forcing the rating.
 */
@Composable
fun StrokeHintDialog(
    card: CardEntity,
    diagram: StrokeDiagram,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it - let me draw") }
        },
        title = {
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
        },
        text = {
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
                    if (card.onyomi != null) {
                        Text(
                            text = "On: " + card.onyomi,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (card.kunyomi != null) {
                        Text(
                            text = "Kun: " + card.kunyomi,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Close this and draw it from memory. You can reopen the hint " +
                        "as many times as you need.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

internal fun strokeCountLabel(diagram: StrokeDiagram): String =
    if (diagram.strokeCount == 0) "" else diagram.strokeCount.toString() + " strokes"
