package com.example.kanjipractice.domain.stroke

/**
 * Supplies stroke-order diagrams for characters.
 *
 * This exists so the review state machine can be driven by a fake in unit tests
 * without an Android Context behind it.
 */
interface StrokeDiagramProvider {
    suspend fun diagramFor(character: String): StrokeDiagram
}
