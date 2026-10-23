package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Tap-and-hold card preview. Holding any card shows it large. Letting go without moving keeps it
 * up until the next tap; sliding a finger off the card before letting go closes it. Hidden cards
 * ("Hidden card", face-down) are shown exactly as hidden as they are on the table.
 */
@Stable
class CardZoomController {
    /** True in Argentum games, where hold-to-preview replaces the older long-press dialogs. */
    var enabled by mutableStateOf(false)
    var card by mutableStateOf<GameCard?>(null)
        private set
    private var holding = false

    fun hold(card: GameCard) { this.card = card; holding = true }

    /** End of the press that showed the card: a slide closes it, a still release keeps it. */
    fun release(moved: Boolean) {
        if (holding && moved) card = null
        holding = false
    }

    fun dismiss() { card = null; holding = false }
}

val LocalCardZoom = staticCompositionLocalOf { CardZoomController() }

/** Watches the press without consuming it, so normal taps and long-presses still work. */
fun Modifier.cardZoomRelease(zoom: CardZoomController): Modifier = pointerInput(zoom) {
    val slop = viewConfiguration.touchSlop * 2
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var moved = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if ((change.position - down.position).getDistance() > slop) moved = true
            if (!change.pressed) break
        }
        zoom.release(moved)
    }
}

@Composable
fun CardZoomOverlay(zoom: CardZoomController) {
    val card = zoom.card ?: return
    val screen = LocalConfiguration.current
    val width = minOf(screen.screenWidthDp * 0.82f, screen.screenHeightDp * 0.6f, 380f).dp
    Popup(alignment = Alignment.Center, onDismissRequest = { zoom.dismiss() }, properties = PopupProperties(focusable = true)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { zoom.dismiss() }.testTag("card-zoom"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(width).aspectRatio(0.716f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF263B35))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))) {
                    val url = card.imageUrl
                    when {
                        card.isFaceDown -> Text("FACE DOWN", color = Mint, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
                        !url.isNullOrBlank() -> CardArt(url, Modifier.fillMaxSize(), fallbackName = card.name, fallbackType = card.type)
                        else -> Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(card.name, color = SoftWhite, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            card.manaCost?.let { Text(it, color = CounterGold, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                            Text(card.type, color = Muted, fontSize = 13.sp)
                            card.oracleText?.let { Text(it, color = SoftWhite, fontSize = 14.sp) }
                            if (card.power != null && card.toughness != null) Text("${card.power}/${card.toughness}", color = SoftWhite, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Text("Tap anywhere to close", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
