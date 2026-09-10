package com.example.kanjipractice.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.kanjipractice.domain.model.CardState
import java.time.LocalDateTime

/**
 * One kanji in the deck, together with its FSRS memory state.
 *
 * The id is the character's Unicode code point, which makes seeding idempotent:
 * re-inserting the same deck cannot create duplicates.
 */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: Long,
    val character: String,
    val meaning: String,
    val onyomi: String?,
    val kunyomi: String?,
    /** A common compound with the target character already replaced by a blank. */
    val exampleWord: String?,
    val jlpt: Int,

    // ---- FSRS memory state ----
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val due: LocalDateTime,
    val lastReview: LocalDateTime? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    val state: CardState = CardState.NEW,
)

/** True once the card has been answered at least once. */
val CardEntity.isNew: Boolean get() = reps == 0
