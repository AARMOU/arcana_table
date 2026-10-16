package com.arcanatable

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/** How the hand tray arranges cards. Persisted by name in arcana_options. */
enum class HandArrangement(val label: String, val detail: String) {
    AUTO_FIT("Fit to tray", "Cards wrap into rows and resize so the whole hand fits the tray when possible."),
    SINGLE_ROW("Single row", "One horizontally scrolling row; cards grow with the tray height.");

    companion object {
        fun parse(value: String?): HandArrangement = entries.firstOrNull { it.name == value } ?: AUTO_FIT
    }
}

/** Result of laying out a hand inside the tray's card area. All sizes are in dp. */
data class HandGrid(
    val columns: Int,
    val rows: Int,
    val cardWidth: Float,
    val cardHeight: Float,
    /** True when the cards do not all fit and the tray must scroll. */
    val scrolls: Boolean,
)

/**
 * Pure layout math for the resizable hand drawer, kept free of Compose so it is unit-testable.
 *
 * AUTO_FIT tries every row count and keeps the one giving the largest card that still lets the
 * whole hand fit inside [areaWidth] x [areaHeight]. The result is clamped between the minimum
 * readable size and the user's maximum ([sizeScale]); if even the minimum size cannot fit all
 * cards, the grid keeps the minimum size and scrolls vertically.
 *
 * SINGLE_ROW keeps Arcana's original one-row strip: card height follows the tray height.
 */
object HandLayout {
    const val CARD_ASPECT = 0.716f // width / height of a Magic card
    const val GAP = 8f
    const val MIN_CARD_WIDTH = 64f
    const val BASE_MAX_CARD_WIDTH = 230f

    fun compute(
        cardCount: Int,
        areaWidth: Float,
        areaHeight: Float,
        arrangement: HandArrangement,
        sizeScale: Float = 1f,
    ): HandGrid {
        val scale = sizeScale.coerceIn(0.6f, 1.6f)
        val maxWidth = BASE_MAX_CARD_WIDTH * scale
        val minWidth = min(MIN_CARD_WIDTH * scale.coerceAtMost(1f).coerceAtLeast(0.85f), maxWidth)
        val width = areaWidth.coerceAtLeast(1f)
        val height = areaHeight.coerceAtLeast(1f)
        if (cardCount <= 0) return HandGrid(0, 0, minWidth, minWidth / CARD_ASPECT, false)

        if (arrangement == HandArrangement.SINGLE_ROW) {
            val cardHeight = (height - 6f).coerceAtLeast(minWidth / CARD_ASPECT)
            val cardWidth = (cardHeight * CARD_ASPECT).coerceIn(minWidth, maxWidth)
            val total = cardCount * cardWidth + (cardCount - 1) * GAP
            return HandGrid(cardCount, 1, cardWidth, cardWidth / CARD_ASPECT, total > width + 0.5f)
        }

        var best: HandGrid? = null
        for (rows in 1..cardCount) {
            val columns = ceil(cardCount / rows.toFloat()).toInt()
            val widthLimit = (width - (columns - 1) * GAP) / columns
            val heightLimit = ((height - (rows - 1) * GAP) / rows) * CARD_ASPECT
            val cardWidth = floor(min(widthLimit, heightLimit).coerceAtMost(maxWidth))
            if (cardWidth < minWidth) continue
            if (best == null || cardWidth > best.cardWidth) best = HandGrid(columns, rows, cardWidth, cardWidth / CARD_ASPECT, false)
        }
        best?.let { return it }

        // Nothing fits at a readable size: keep the minimum size, fill the width, scroll vertically.
        val columns = floor((width + GAP) / (minWidth + GAP)).toInt().coerceIn(1, cardCount)
        val rows = ceil(cardCount / columns.toFloat()).toInt()
        return HandGrid(columns, rows, minWidth, minWidth / CARD_ASPECT, true)
    }
}
