package com.example.kanjipractice.domain.stroke

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads KanjiVG stroke-order diagrams from assets/kanjivg/{codepoint}.svg
 * (plan, section 5.4).
 *
 * Parsed diagrams are cached for the process lifetime: they are small, immutable
 * and read on every card.
 */
@Singleton
class StrokeDataService @Inject constructor(
    @ApplicationContext private val context: Context,
) : StrokeDiagramProvider {
    private val cache = HashMap<String, StrokeDiagram>()
    private val lock = Mutex()

    /**
     * Returns the diagram for [character], or [StrokeDiagram.EMPTY] when none is
     * bundled. Callers fall back to rendering the character itself, which keeps a
     * missing diagram from breaking the review flow (plan, section 11, M6).
     */
    override suspend fun diagramFor(character: String): StrokeDiagram = lock.withLock {
        cache.getOrPut(character) { load(character) }
    }

    private suspend fun load(character: String): StrokeDiagram = withContext(Dispatchers.IO) {
        val path = assetPath(character) ?: return@withContext StrokeDiagram.EMPTY
        val svg = try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            return@withContext StrokeDiagram.EMPTY
        }
        try {
            KanjiVgParser.parse(svg)
        } catch (e: IllegalArgumentException) {
            // A malformed diagram should degrade to "show the character", not crash.
            StrokeDiagram.EMPTY
        }
    }

    /** KanjiVG names each file after the zero-padded Unicode code point. */
    fun assetPath(character: String): String? {
        if (character.isEmpty()) return null
        val codePoint = character.codePointAt(0)
        return "kanjivg/" + codePoint.toString(16).padStart(5, '0') + ".svg"
    }
}
