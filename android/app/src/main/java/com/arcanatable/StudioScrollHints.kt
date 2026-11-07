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
