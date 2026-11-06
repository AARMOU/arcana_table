package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Never reuse an obsolete snapshot's actions while a decision or update is pending. */
internal fun handDrawerActions(actions: List<JSONObject>, busy: Boolean): List<JSONObject> =
    if (busy) emptyList() else ArgentumActions.visibleChoices(actions).filter {
        it.optJSONObject("action") != null && it.optBoolean("isAffordable", true) &&
            ArgentumActions.needsHandlingReason(it) == null
    }

/** CAST without a selection offers only actual engine-provided hand plays. */
internal fun handDrawerCastActions(actions: List<JSONObject>, handServerIds: Set<String>): List<JSONObject> =
    actions.filter { info ->
        val action = info.optJSONObject("action")
        action?.optString("type") in setOf("CastSpell", "PlayLand") &&
            action?.optString("cardId") in handServerIds
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HandDrawerControls(
    primaryLabel: String?, undoAvailable: Boolean, actionCount: Int, castAvailable: Boolean,
    stackCount: Int, libraryCount: Int, graveyardCount: Int, exileCount: Int,
    onPass: () -> Unit, onUndo: () -> Unit, onActions: () -> Unit, onCast: () -> Unit,
    onStack: () -> Unit, onLibrary: () -> Unit, onGraveyard: () -> Unit, onExile: () -> Unit,
    onPassHold: (() -> Unit)? = null,
) {
    // Preserve reference proportions on the reference phone; wrap into two pairs on
    // Fold cover / large text so CAST and every zone remain visible without sideways scrolling.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 360.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale
        val controls = listOf(
            DrawerControl(primaryLabel ?: "WAITING", "hand-pass", primaryLabel != null, onPass, 145f, true),
            DrawerControl("UNDO", "hand-undo", undoAvailable, onUndo, 63f),
            DrawerControl("ACTIONS · $actionCount", "hand-actions", actionCount > 0, onActions, 78f),
            DrawerControl("CAST", "hand-cast", castAvailable, onCast, 71f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            controls.chunked(if (narrow) 2 else 4).forEach { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.forEach { c -> HandDrawerButton(c.label, c.tag, c.enabled, c.click,
                        Modifier.weight(c.weight), c.strong, if (c.tag == "hand-pass") onPassHold else null) }
                }
            }
            val metrics = listOf(
                DrawerControl("STACK\n$stackCount", "hand-stack", true, onStack),
                DrawerControl("LIBRARY\n$libraryCount", "hand-library", true, onLibrary),
                DrawerControl("GRAVEYARD\n$graveyardCount", "hand-graveyard", true, onGraveyard),
                DrawerControl("EXILE\n$exileCount", "hand-exile", true, onExile))
            metrics.chunked(if (narrow) 2 else 4).forEach { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.forEach { c -> HandDrawerButton(c.label, c.tag, true, c.click, Modifier.weight(1f)) }
                }
            }
        }
    }
}

private data class DrawerControl(val label: String, val tag: String, val enabled: Boolean, val click: () -> Unit,
                                 val weight: Float = 1f, val strong: Boolean = false)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HandDrawerButton(label: String, tag: String, enabled: Boolean, onClick: () -> Unit,
                             modifier: Modifier, strong: Boolean = false, onLongClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.heightIn(min = 48.dp).clip(shape)
        .background(if (strong && enabled) Mint else PanelLight)
        .border(1.dp, if (enabled) Mint.copy(alpha = .35f) else Muted.copy(alpha = .2f), shape)
        .testTag(tag).combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick).padding(horizontal = 4.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = if (strong && enabled) Panel else if (enabled) SoftWhite else Muted,
            fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
