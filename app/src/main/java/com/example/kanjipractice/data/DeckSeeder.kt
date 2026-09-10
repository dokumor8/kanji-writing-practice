package com.example.kanjipractice.data

import android.content.Context
import com.example.kanjipractice.data.db.CardDao
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.data.deck.DeckCard
import com.example.kanjipractice.data.deck.DeckJsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills the cards table from the bundled deck the first time the app runs
 * (plan, section 8).
 *
 * Seeding is keyed off the table being empty rather than a "first launch" flag,
 * which also makes it recover from a user clearing their data.
 */
@Singleton
class DeckSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cardDao: CardDao,
    private val clock: Clock,
) {

    /** Returns the number of cards inserted; 0 when the deck was already seeded. */
    suspend fun seedIfEmpty(): Int {
        if (cardDao.count() > 0) return 0
        val cards = loadDeck()
        cardDao.insertAll(cards)
        return cards.size
    }

    /**
     * Reads and parses assets/kanji.json. Public so a test can check the real
     * asset without going through the database.
     */
    fun loadDeck(now: LocalDateTime = LocalDateTime.now(clock)): List<CardEntity> {
        val json = try {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw IllegalStateException("bundled deck $ASSET is missing", e)
        }
        return DeckJsonParser.parse(json).map { it.toEntity(now) }
    }

    private fun DeckCard.toEntity(now: LocalDateTime) =
        CardEntity(
            id = character.codePointAt(0).toLong(),
            character = character,
            meaning = meaning,
            onyomi = onyomi,
            kunyomi = kunyomi,
            exampleWord = exampleWord,
            jlpt = jlpt,
            // New cards are due immediately (plan, section 7).
            due = now,
        )

    companion object {
        const val ASSET = "kanji.json"
    }
}
