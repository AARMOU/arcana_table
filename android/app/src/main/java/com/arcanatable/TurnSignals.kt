package com.arcanatable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Whose turn it is, at a glance (Build 96): each player has one colour that the phase bar, their
 * seat tab and their board's outline use on their turn. You are always gold; opponents are blue.
 */
object TurnColors {
    val You = Color(0xFFFFC857)
    val Opponent = Color(0xFF6FB6FF)
    /** Readable text on top of either colour. */
    val OnColor = Color(0xFF14110A)

    fun forSeat(isYou: Boolean): Color = if (isYou) You else Opponent

    /** "YOUR TURN", "AI'S TURN" or "<NAME>'S TURN". */
    fun label(isYou: Boolean, name: String, isComputer: Boolean): String = when {
        isYou -> "YOUR TURN"
        isComputer || name.equals("Offline AI", ignoreCase = true) || name.contains("AI", ignoreCase = false) -> "AI'S TURN"
        else -> "${name.uppercase()}'S TURN"
    }
}

/** What the turn banner currently says, or null when it's hidden. */
data class TurnBannerState(val text: String, val isYou: Boolean, val key: Int)

/**
 * The banner that crosses the table when a turn starts: big and gold for your turn, a quieter blue
 * note for an opponent's. The caller hides it again after a moment.
 */
@Composable
fun BoxScope.TurnBanner(state: TurnBannerState?) {
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
        exit = fadeOut(tween(350)),
        modifier = Modifier.align(Alignment.Center).testTag("turn-banner"),
    ) {
        val shown = state ?: return@AnimatedVisibility
        val color = TurnColors.forSeat(shown.isYou)
        Text(
            shown.text,
            color = if (shown.isYou) TurnColors.OnColor else color,
            fontSize = if (shown.isYou) 30.sp else 17.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = if (shown.isYou) 3.sp else 1.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(14.dp))
                .background(if (shown.isYou) color.copy(alpha = 0.96f) else Color(0xE6101615))
                .border(2.dp, color, RoundedCornerShape(14.dp))
                .padding(horizontal = if (shown.isYou) 34.dp else 22.dp, vertical = if (shown.isYou) 16.dp else 10.dp)
        )
    }
}

/**
 * The pass button. [canRespond] lights it up (gold, gently pulsing) when you have priority on
 * someone else's turn, so "you can respond now" never looks like "it's your turn".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PassButton(label: String, onClick: () -> Unit, enabled: Boolean, canRespond: Boolean, modifier: Modifier = Modifier) {
    val pulse = if (canRespond && enabled) {
        val transition = rememberInfiniteTransition(label = "respond-pulse")
        val alpha by transition.animateFloat(0.72f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "respond-alpha")
        alpha
    } else 1f
    val lit = canRespond && enabled
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.heightIn(min = 50.dp).clip(shape)
            .background(if (lit) TurnColors.You.copy(alpha = pulse) else if (enabled) PanelLight else Panel)
            .border(if (lit) 2.dp else 0.dp, if (lit) TurnColors.You else Color.Transparent, shape)
            .combinedClickable(enabled = enabled, onClick = onClick)
            .testTag(if (lit) "pass-button-respond" else "pass-button"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = if (lit) TurnColors.OnColor else if (enabled) Mint else Muted,
            fontSize = 10.sp, fontWeight = if (lit) FontWeight.Black else FontWeight.Bold,
            maxLines = 2, lineHeight = 12.sp, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

/** A board outline in the active player's colour; other boards are dimmed a little. */
@Composable
fun Modifier.turnHighlight(enabled: Boolean, isActive: Boolean, isYou: Boolean): Modifier = when {
    !enabled -> this
    isActive -> this.border(2.dp, TurnColors.forSeat(isYou).copy(alpha = 0.9f), RoundedCornerShape(12.dp)).testTag("active-board")
    else -> this.graphicsLayer(alpha = 0.84f)
}
