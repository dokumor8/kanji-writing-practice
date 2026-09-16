package com.example.kanjipractice.domain.stroke

import com.example.kanjipractice.domain.AppScript

/**
 * Where a character's stroke-order SVG lives in the assets.
 *
 * This is deliberately the only place that knows: the directory differs per
 * flavour, and having the loader hard-code one of them is exactly the bug that
 * made every Chinese character fall back to being drawn in a system font.
 */
object StrokeAssets {

    fun pathFor(character: String): String? {
        if (character.isEmpty()) return null
        val codePoint = character.codePointAt(0)
        return AppScript.strokeAssetDir + "/" + codePoint.toString(16).padStart(5, '0') + ".svg"
    }
}
