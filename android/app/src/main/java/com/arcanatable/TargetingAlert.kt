package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** An alert is keyed to a real engine stack object, not the transient play-feed. */
internal data class IncomingTargetAlert(
    val stackId: String,
    val sourceName: String,
    val controllerName: String,
    val targets: List<String>,
)

/** Do not infer targeting from matching text/card names: only engine entity IDs count.
 * Targets owned/controlled by the local seat remain visible until the stack object
 * leaves the engine stack. Deliberately ignores your own spells and abilities.
 */
internal fun incomingTargetAlerts(
    stack: List<ArgentumStackItem>,
    ownPlayerId: String,
    ownCardIds: Set<String>,
    ownCardNames: Map<String, String>,
): List<IncomingTargetAlert> {
    if (ownPlayerId.isBlank()) return emptyList()
    return stack.filterNot { it.isYours }.mapNotNull { item ->
        val targets = item.targetIds.distinct().mapNotNull { id ->
            when {
                id == ownPlayerId -> "YOU"
                id in ownCardIds -> ownCardNames[id]?.let { "YOUR $it" } ?: "YOUR CARD"
                else -> null
            }
        }
        if (targets.isEmpty()) null
        else IncomingTargetAlert(item.id, item.name, item.controllerName, targets)
    }
}

internal fun ArgentumTableSnapshot.incomingAlerts(): List<IncomingTargetAlert> {
    val me = players.getOrNull(viewingPlayer) ?: return emptyList()
    val own = me.battlefield + me.graveyard + me.exile + me.hand +
        me.revealed.filterNot { it.isFaceDown } + listOfNotNull(me.commanderCard)
    val ids = own.mapNotNull { card ->
        serverCardIds[card.id]?.let { it to card.name }
    }.toMap()
    return incomingTargetAlerts(stack, me.id, ids.keys, ids)
}

@Composable
internal fun TargetingAlertPanel(alert: IncomingTargetAlert, onInspect: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(0.94f)
            .background(Panel, RoundedCornerShape(12.dp))
            .border(2.dp, CounterGold, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("YOU / YOUR CARD IS BEING TARGETED", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text("${alert.controllerName}: ${alert.sourceName}", color = SoftWhite, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(alert.targets.joinToString(" · "), color = SoftWhite, fontSize = 12.sp, maxLines = 3,
            overflow = TextOverflow.Ellipsis)
        Row {
            TextButton(onClick = onInspect) { Text("INSPECT STACK", color = Mint, fontWeight = FontWeight.Bold) }
        }
    }
}
