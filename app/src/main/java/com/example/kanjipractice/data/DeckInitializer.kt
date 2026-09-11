package com.example.kanjipractice.data

/**
 * Fills the cards table from the bundled deck on first launch.
 *
 * An interface only so the deck screen can be tested without an Android
 * `Context` behind the asset loader.
 */
interface DeckInitializer {
    /** @return the number of cards inserted; 0 when the deck was already seeded. */
    suspend fun seedIfEmpty(): Int
}
