package com.example.kanjipractice.data

/**
 * Loads the bundled card sets into the database.
 *
 * An interface only so the deck screen can be tested without an Android
 * `Context` behind the asset loader.
 */
interface DeckInitializer {
    /**
     * Inserts any card that is not in the database yet, and (re)assigns every
     * card to its set. Safe to call on every launch.
     *
     * @return the number of cards inserted.
     */
    suspend fun syncDecks(): Int
}
