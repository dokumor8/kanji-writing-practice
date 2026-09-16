package com.example.kanjipractice.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.kanjipractice.domain.model.CardState
import java.time.LocalDateTime

/**
 * One character in the deck, together with its FSRS memory state.
 *
 * The id is the character's Unicode code point, which makes seeding idempotent:
 * re-inserting the same deck cannot create duplicates.
 *
 * The two reading columns and the level column are named for Japanese in the
 * database, because that is what they were when this was a Japanese-only app.
 * The Kotlin names are script-neutral and the SQL names are pinned with
 * [ColumnInfo], so the Chinese flavour stores pinyin in the same physical columns
 * without a migration and without every caller having to read "onyomi" and
 * understand "pinyin".
 */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: Long,
    val character: String,
    val meaning: String,

    /** Japanese on-yomi, or Chinese pinyin. */
    @ColumnInfo(name = "onyomi") val reading1: String?,

    /** Japanese kun-yomi, or unused. */
    @ColumnInfo(name = "kunyomi") val reading2: String?,

    /** A common compound with the target character already replaced by a blank. */
    val exampleWord: String?,

    /** JLPT level in the Japanese flavour, HSK level in the Chinese one. */
    @ColumnInfo(name = "jlpt") val level: Int,

    /**
     * Which card set this belongs to, and where it sits in the overall order.
     *
     * Nullable because cards created before sets existed have no membership until
     * the seeder assigns one; nothing schedules a card with no set.
     */
    val deckId: String? = null,
    val deckSortKey: Int? = null,

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
