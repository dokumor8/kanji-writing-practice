package com.example.kanjipractice.data

import android.content.Context
import androidx.room.withTransaction
import com.example.kanjipractice.data.db.CardDao
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.data.db.KanjiDatabase
import com.example.kanjipractice.data.deck.DeckCard
import com.example.kanjipractice.data.deck.DeckJsonParser
import com.example.kanjipractice.domain.AppScript
import com.example.kanjipractice.domain.deck.DeckCatalog
import com.example.kanjipractice.domain.settings.StudySettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled card sets into the database.
 *
 * Two separate jobs, and keeping them separate is what makes upgrading safe:
 *
 *  - **inserting** cards that are not there yet. This uses a plain INSERT that
 *    skips existing rows, so a re-seed can never overwrite the FSRS state of a
 *    card somebody has already studied.
 *  - **assigning set membership**, which is data rather than progress and is safe
 *    to rewrite. Assigning it separately is also what migrates cards created by a
 *    build from before sets existed.
 */
@Singleton
class DeckSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cardDao: CardDao,
    private val database: KanjiDatabase,
    private val settingsRepository: StudySettingsRepository,
    private val clock: Clock,
) : DeckInitializer {

    override suspend fun syncDecks(): Int {
        val alreadySynced = settingsRepository.observeDeckDataVersion().first() ==
            DeckCatalog.DATA_VERSION && cardDao.countUnassigned() == 0
        if (alreadySynced) return 0

        // Parsing a few hundred KB of JSON for a few thousand cards, then a write
        // per card. Both belong off the main thread: on an upgrade this runs
        // before the first frame the user sees.
        val cards = withContext(Dispatchers.IO) { loadCards() }
        val inserted = cardDao.insertMissing(
            cards.map { it.toEntity(LocalDateTime.now(clock)) }
        )
        withContext(Dispatchers.IO) {
            database.withTransaction {
                for (card in cards) {
                    cardDao.assignDeck(card.id, card.deckId, card.sortKey)
                }
            }
        }
        settingsRepository.setDeckDataVersion(DeckCatalog.DATA_VERSION)
        // Room reports -1 for each row the INSERT skipped, so this is the number
        // actually added rather than the number attempted.
        return inserted.count { it != IGNORED_ROW }
    }

    /**
     * Reads and parses every bundled set. Public so a test can check the real
     * assets without going through the database.
     */
    fun loadCards(): List<LoadedCard> {
        val cards = ArrayList<LoadedCard>()
        for (asset in AppScript.cardAssets) {
            val json = try {
                context.assets.open(asset).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                throw IllegalStateException("bundled card set $asset is missing", e)
            }
            DeckJsonParser.parse(json).forEach { cards += LoadedCard(it) }
        }
        return cards
    }

    /** A deck card plus the primary key it will be stored under. */
    data class LoadedCard(val card: DeckCard) {
        val id: Long = card.character.codePointAt(0).toLong()
        val deckId: String get() = card.deckId
        val sortKey: Int get() = card.sortKey

        fun toEntity(now: LocalDateTime) = CardEntity(
            id = id,
            character = card.character,
            meaning = card.meaning,
            reading1 = card.reading1,
            reading2 = card.reading2,
            exampleWord = card.exampleWord,
            exampleReading = card.exampleReading,
            exampleMeaning = card.exampleMeaning,
            level = card.level,
            deckId = card.deckId,
            deckSortKey = card.sortKey,
            // New cards are due immediately.
            due = now,
        )
    }

    private companion object {
        /** What Room returns for a row an INSERT ... OR IGNORE skipped. */
        const val IGNORED_ROW = -1L
    }
}
