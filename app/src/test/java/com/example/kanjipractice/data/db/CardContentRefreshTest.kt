package com.example.kanjipractice.data.db

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every column of [CardEntity] has to be classified: refreshed from the bundled
 * data on a re-seed, assigned as set membership, or left alone as scheduling
 * state. This test is the list.
 *
 * The seeder inserts with INSERT OR IGNORE so that re-seeding can never overwrite
 * somebody's review history. The cost of that safety is invisible: a column added
 * in a later version stays null on every card an existing install already had,
 * because those rows are skipped by the insert. That shipped -- 2.3.0 added
 * exampleReading and exampleMeaning, so anybody upgrading got the new label with
 * a blank line under it, while a fresh install looked perfect.
 *
 * So: add a column to [CardEntity], and this fails until you say what happens to
 * it on a row that already exists.
 */
class CardContentRefreshTest {

    @Test
    fun everyColumnIsClassified() {
        val columns = columnsOf(entitySource())
        val refreshed = setClauseOf(daoSource(), "updateContent")
        val assigned = setClauseOf(daoSource(), "assignDeck")

        assertEquals(
            emptySet(),
            columns - refreshed - assigned - SCHEDULING_STATE,
            "columns a re-seed neither refreshes nor deliberately keeps. " +
                "List them in CardDao.updateContent, or in SCHEDULING_STATE if " +
                "they are progress.",
        )

        assertEquals(
            emptySet(),
            (refreshed + assigned) intersect SCHEDULING_STATE,
            "scheduling state a re-seed would overwrite. Re-seeding must never " +
                "touch review history.",
        )

        assertEquals(
            emptySet(),
            refreshed intersect assigned,
            "columns written by both updateContent and assignDeck",
        )

        assertEquals(
            emptySet(),
            (SCHEDULING_STATE + refreshed + assigned) - columns,
            "columns named in CardDao or in SCHEDULING_STATE that CardEntity no " +
                "longer has",
        )
    }

    @Test
    fun theRealEntityIsTheOneBeingRead() {
        // Guards the parsing itself: if the entity ever moves or stops being a
        // data class with a val per column, the classification above would pass
        // by classifying nothing.
        assertTrue(columnsOf(entitySource()).contains("exampleReading"))
    }

    private fun entitySource() = source(
        "src/main/java/com/example/kanjipractice/data/db/CardEntity.kt",
    )

    private fun daoSource() = source(
        "src/main/java/com/example/kanjipractice/data/db/CardDao.kt",
    )

    private fun source(path: String): String {
        val file = File(path)
        assertTrue(file.isFile, "expected ${file.absolutePath}")
        return file.readText()
    }

    /**
     * The *physical* column names of the Room entity: what the SQL in [CardDao]
     * has to say, following @ColumnInfo where the Kotlin name differs.
     */
    private fun columnsOf(source: String): Set<String> =
        source.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trimStart()
                val rest = when {
                    trimmed.startsWith("val ") -> trimmed.removePrefix("val ")
                    trimmed.contains(" val ") -> trimmed.substringAfter(" val ")
                    else -> return@mapNotNull null
                }
                val name = rest.substringBefore(':').trim()
                if (name.isEmpty() || !name.all { c -> c.isLetterOrDigit() || c == '_' }) {
                    return@mapNotNull null
                }
                val pinned = line.substringAfter("@ColumnInfo(name = ", "")
                if (pinned.isEmpty()) name else pinned.removePrefix("\"").substringBefore('"')
            }
            .toSet()

    /** The columns in the SET clause of the UPDATE behind [function]. */
    private fun setClauseOf(dao: String, function: String): Set<String> {
        val functionAt = dao.indexOf("suspend fun $function(")
        assertTrue(functionAt > 0, "CardDao.$function not found")

        val queryAt = dao.lastIndexOf("@Query(", functionAt)
        assertTrue(queryAt > 0, "no @Query above CardDao.$function")

        return dao.substring(queryAt, functionAt)
            .substringAfter(" SET ", "")
            .substringBefore(" WHERE ")
            .split(',')
            .map { part -> part.substringBefore('=').filter { c -> c.isLetterOrDigit() || c == '_' } }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private companion object {
        /**
         * Progress: what the user earned, which a re-seed must never rewrite.
         *
         * Keep in step with CardEntity. The columns a card is found by, scheduled
         * by, or scored by all belong here.
         */
        val SCHEDULING_STATE = setOf(
            "id",
            "stability",
            "difficulty",
            "due",
            "lastReview",
            "reps",
            "lapses",
            "state",
        )
    }
}
