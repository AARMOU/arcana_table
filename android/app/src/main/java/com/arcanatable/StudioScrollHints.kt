package com.arcanatable

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.layout.layout

/**
 * Owner, Nov 7: "If a bar can scroll, I want some kind of indicator, like a small arrow." A sideways-scrolling row
 * shows a small › (or ‹) with a soft fade at any edge where more is hidden, and nothing once you reach that end.
 * Studio builds only; release builds scroll exactly as before.
 */
@Composable
internal fun Modifier.hintedHorizontalScroll(state: ScrollState = rememberScrollState()): Modifier {
    if (!BuildConfig.STUDIO_ENABLED) return this.horizontalScroll(state)
    val fade = LocalTableColors.current.background
    val arrow = LocalTableColors.current.text
    return this.drawWithContent {
        drawContent()
        val w = 28.dp.toPx(); val a = 6.dp.toPx(); val cy = size.height / 2
        if (state.canScrollForward) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, fade.copy(alpha = 0.9f)), startX = size.width - w, endX = size.width),
                Offset(size.width - w, 0f), Size(w, size.height))
            val x = size.width - 10.dp.toPx()
            drawPath(Path().apply { moveTo(x - a, cy - a); lineTo(x, cy); lineTo(x - a, cy + a); close() }, arrow.copy(alpha = 0.85f))
        }
        if (state.canScrollBackward) {
            drawRect(Brush.horizontalGradient(listOf(fade.copy(alpha = 0.9f), Color.Transparent), startX = 0f, endX = w),
                Offset.Zero, Size(w, size.height))
            val x = 10.dp.toPx()
            drawPath(Path().apply { moveTo(x + a, cy - a); lineTo(x, cy); lineTo(x + a, cy + a); close() }, arrow.copy(alpha = 0.85f))
        }
    }.horizontalScroll(state)
}

/**
 * Owner, Nov 7: "quadruple check that the player chips move correctly". A chip moved down out of its sideways-scrolling
 * row disappeared (and couldn't be tapped): a scrolling row clips everything outside its own height. This row scrolls
 * the same way but never clips up or down, and when everything fits it doesn't clip at all, so a moved or enlarged
 * item stays fully visible and tappable. Same ›/‹ hints as [hintedHorizontalScroll]. Release builds: unchanged.
 */
@Composable
internal fun Modifier.studioRowScroll(): Modifier {
    if (!BuildConfig.STUDIO_ENABLED) return this.horizontalScroll(rememberScrollState())
    val offset = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val max = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val state = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
        val old = offset.floatValue
        offset.floatValue = (old - delta).coerceIn(0f, max.floatValue)
        old - offset.floatValue
    }
    val fade = LocalTableColors.current.background
    val arrow = LocalTableColors.current.text
    return this.drawWithContent {
        if (max.floatValue > 0f) clipRect(0f, -100_000f, size.width, 100_000f) { this@drawWithContent.drawContent() }
        else drawContent()
        val w = 28.dp.toPx(); val a = 6.dp.toPx(); val cy = size.height / 2
        if (offset.floatValue < max.floatValue - 0.5f) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, fade.copy(alpha = 0.9f)), startX = size.width - w, endX = size.width),
                Offset(size.width - w, 0f), Size(w, size.height))
            val x = size.width - 10.dp.toPx()
            drawPath(Path().apply { moveTo(x - a, cy - a); lineTo(x, cy); lineTo(x - a, cy + a); close() }, arrow.copy(alpha = 0.85f))
        }
        if (offset.floatValue > 0.5f) {
            drawRect(Brush.horizontalGradient(listOf(fade.copy(alpha = 0.9f), Color.Transparent), startX = 0f, endX = w), Offset.Zero, Size(w, size.height))
            val x = 10.dp.toPx()
            drawPath(Path().apply { moveTo(x + a, cy - a); lineTo(x, cy); lineTo(x + a, cy + a); close() }, arrow.copy(alpha = 0.85f))
        }
    }.scrollable(state, androidx.compose.foundation.gestures.Orientation.Horizontal)
        .layout { measurable, constraints ->
            val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
            val w = p.width.coerceIn(constraints.minWidth, constraints.maxWidth)
            max.floatValue = (p.width - w).coerceAtLeast(0).toFloat()
            val o = offset.floatValue.coerceIn(0f, max.floatValue)
            layout(w, p.height) { p.placeRelative(-kotlin.math.round(o).toInt(), 0) }
        }
}
