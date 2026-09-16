package com.example.kanjipractice.domain.model

import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.domain.AppScript

/**
 * The readings to show for a card, labelled the way the current script labels
 * them: On and Kun for Japanese, Pinyin for Chinese.
 */
fun CardEntity.readings(): List<Pair<String, String>> {
    val values = listOf(reading1, reading2)
    return AppScript.readingLabels.mapIndexedNotNull { index, label ->
        val value = values.getOrNull(index)
        if (label != null && !value.isNullOrBlank()) label to value else null
    }
}

/** "N5" for Japanese, "HSK 3" for Chinese, or null where no level applies. */
fun CardEntity.levelLabel(): String? {
    val prefix = AppScript.levelPrefix ?: return null
    return if (level in 1..9) prefix + level else null
}
