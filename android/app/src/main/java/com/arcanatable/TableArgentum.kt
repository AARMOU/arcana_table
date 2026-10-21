@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.arcanatable

import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.app.Activity
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope

/** Argentum games: talking to the Argentum engine/server, decisions, combat and offline practice games. */

/** Persisted so a relaunch can offer (never force) a resume. */
internal fun TableController.setArgentumGameActive(active: Boolean) {
    // Offline games live only in this app session; there is nothing on a server to resume.
    val resumable = active && !argentumOffline
    argentumResumeAvailable = resumable
    optionPrefs.edit().putBoolean("argentum_game_active", resumable).apply()
}

/** A card or player for the combat board, from Argentum's masked state. */
internal fun TableController.argentumPiece(id: String): CombatPiece {
    val snap = argentumSnapshot
    val displayId = snap?.serverCardIds?.entries?.firstOrNull { it.value == id }?.key
    val card = displayId?.let { d -> snap.players.flatMap { it.battlefield }.firstOrNull { it.id == d } }
    val seat = players.indexOfFirst { it.id == id }
    val name = card?.name ?: if (seat >= 0 && seat == localSeat) "You" else players.getOrNull(seat)?.name ?: snap?.targets?.get(id)?.name ?: "Object ${id.takeLast(6)}"
    return CombatPiece(id, name, card, players.getOrNull(seat)?.life)
}

internal fun TableController.argentumName(id: String): String =
    argentumSnapshot?.targets?.get(id)?.name ?: players.firstOrNull { it.id == id }?.name ?: argentumPiece(id).name

/** Opens Argentum's attack/block declaration when the server offers one. */
internal fun TableController.openArgentumCombat(): Boolean {
    val actions = argentumSnapshot?.legalActions.orEmpty()
    actions.firstNotNullOfOrNull { ArgentumAttackPlan.from(it) }?.let { argentumAttackPlan = it; recordDiagnostic("Opened attack declaration (${it.options.size} possible attackers)", DiagnosticCategory.ACTION); return true }
    actions.firstNotNullOfOrNull { ArgentumBlockPlan.from(it, argentumLastStateJson) }?.let { argentumBlockPlan = it; recordDiagnostic("Opened block declaration (${it.blockers.size} possible blockers)", DiagnosticCategory.ACTION); return true }
    return false
}

internal fun TableController.reportArgentumUnsupported(key: String, detail: String) {
    if (argentumReportedUnsupported.add(key)) recordDiagnostic("Needs handling: $detail", DiagnosticCategory.UNSUPPORTED)
}

internal fun TableController.submitArgentumAction(action: JSONObject, epoch: String): Boolean {
    if (argentumAwaitingUpdate) return false
    val decision = argentumPendingDecision
    val type = action.optString("type")
    val outbound = if (decision?.optString("type") == "PlayCardDecision" && type in setOf("CastSpell", "PlayLand")) {
        JSONObject().put("type", "SubmitDecision").put("playerId", decision.optString("playerId"))
            .put("response", JSONObject().put("type", "PlayCardResponse").put("decisionId", decision.optString("id")).put("action", JSONObject(action.toString())))
    } else action
    val sent = argentumSocket.submitLegalAction(outbound, epoch)
    if (sent) argentumAwaitingUpdate = true
    argentumStackUse?.let { use -> if (sent && !use.started) argentumStackUse = use.copy(started = true, manaColor = use.manaColor ?: action.optString("manaColorChoice").takeIf { it.isNotBlank() && it != "null" }) }
    return sent
}

internal fun TableController.sendArgentumPriority() {
    if (argentumAwaitingUpdate) { message = "Waiting for Argentum to confirm the previous action."; return }
    // Never pass over an open question (Argentum rejects it): bring the prompt back instead.
    argentumPendingDecision?.takeIf { it.optString("type") != "PlayCardDecision" }?.let { decision ->
        if (argentumDeferredDecisionId == decision.optString("id")) argentumDeferredDecisionId = null
        message = "Answer the open prompt first."
        return
    }
    val snapshot = argentumSnapshot ?: return
    val pass = snapshot.legalActions.firstOrNull { it.optJSONObject("action")?.optString("type") == "PassPriority" }
    val action = pass?.optJSONObject("action")
    val epoch = pass?.optString("interactionEpoch")?.takeUnless { it.isNullOrBlank() || it == "null" }
        ?: snapshot.interactionEpoch
    if (action == null || epoch == null) {
        if (openArgentumCombat()) return
        message = "Argentum has not offered a pass-priority action right now."
        return
    }
    if (!submitArgentumAction(action, epoch)) message = "Could not send the Argentum action. Check your connection."
    else recordDiagnostic("Sent PassPriority", DiagnosticCategory.ACTION)
}

internal fun TableController.submitArgentumDecision(response: JSONObject) {
    if (argentumAwaitingUpdate) { message = "Waiting for Argentum to confirm the previous choice."; return }
    val pending = argentumPendingDecision ?: return
    val epoch = argentumSnapshot?.interactionEpoch ?: return
    val action = JSONObject().put("type", "SubmitDecision").put("playerId", pending.optString("playerId"))
        .put("response", JSONObject(response.toString()).put("decisionId", pending.optString("id")))
    if (argentumSocket.submitLegalAction(action, epoch)) {
        recordDiagnostic("Answered ${pending.optString("type")} with ${response.optString("type")}", DiagnosticCategory.ACTION)
        argentumAwaitingUpdate = true
        argentumDeferredDecisionId = null
        argentumPendingDecision = null
        argentumDecisionSelection = emptySet()
        argentumDecisionColors = emptySet()
        argentumDecisionTargets = emptyMap()
    } else message = "Argentum did not receive that choice. Check the connection and try again."
}

internal fun TableController.openArgentumActions(card: GameCard) {
    val snapshot = argentumSnapshot ?: return
    val serverId = snapshot.serverCardIds[card.id]
    if (serverId == null) { message = "This card is not part of Argentum's current state."; return }
    val choices = snapshot.legalActions.filter { info ->
        val action = info.optJSONObject("action") ?: return@filter false
        listOf("cardId", "sourceId", "permanentId", "entityId").any { key -> action.optString(key) == serverId }
    }
    argentumActionCard = card
    // Always open the sheet: it shows the card's text even when there is nothing to do with it now.
    argentumActionChoices = choices.ifEmpty { listOf(JSONObject().put("__none", true)) }
}

/**
 * Single entry point for submitting a server-offered legal action from any Arcana control.
 * Routes to combat, X or target pickers; refuses (with a needs-handling note) anything
 * Arcana cannot collect input for. Never invents an action.
 */
internal fun TableController.chooseArgentumLegalAction(info: JSONObject) {
    val action = info.optJSONObject("action") ?: return
    val type = action.optString("type")
    if (type == "DeclareAttackers" || type == "DeclareBlockers") { if (!openArgentumCombat()) message = "Argentum's combat declaration is no longer available."; return }
    ArgentumActions.needsHandlingReason(info)?.let { reason ->
        reportArgentumUnsupported("action:${info.optString("description")}:$type", "${info.optString("description", type)} — $reason")
        message = "Needs handling: $reason Nothing was sent to Argentum."
        return
    }
    val plan = ArgentumActionPlan.from(info)
    if (plan.needsInput) {
        // A separate ChooseTargetsDecision from the server takes precedence over pre-chosen targets.
        if (argentumPendingDecision?.optString("type") == "ChooseTargetsDecision") return
        argentumComposer = plan
        // Offline, the engine works out the X auto-pay can really pay only now, not on every update.
        val cardId = action.optString("cardId")
        argentumXChecking = if (argentumOffline && plan.xRange != null && type == "CastSpell" && cardId.isNotBlank() && !info.optBoolean("requiresTargets") &&
            argentumSocket.send("refineMaxX", JSONObject().put("cardId", cardId))) cardId else null
        return
    }
    val epoch = ArgentumActions.epochFor(info, argentumSnapshot?.interactionEpoch)
    if (epoch == null || !submitArgentumAction(action, epoch)) message = "This Argentum action is stale or the connection is unavailable. Refresh and try again."
    else recordDiagnostic("Sent ${type} (${info.optString("description")})", DiagnosticCategory.ACTION)
}

internal fun TableController.argentumPrimary() {
    if ("DeclareAttackers" in argentumLegalTypes || "DeclareBlockers" in argentumLegalTypes) { if (!openArgentumCombat()) message = "Argentum's combat declaration is no longer available." }
    else sendArgentumPriority()
}

internal fun TableController.requestArgentumUndo() {
    if (argentumAwaitingUpdate) { message = "Waiting for Argentum to confirm the previous action."; return }
    if (!argentumUndoAvailable) { message = "Argentum has nothing to undo right now."; return }
    passToMyTurnFrom = null
    if (argentumSocket.requestUndo()) { argentumAwaitingUpdate = true; recordDiagnostic("Requested undo", DiagnosticCategory.ACTION) }
    else message = "Could not reach Argentum to undo. Check your connection."
}

internal fun TableController.togglePassToMyTurn() {
    if (passToMyTurnFrom != null) { passToMyTurnFrom = null; recordDiagnostic("Pass to my turn: stopped by player", DiagnosticCategory.ACTION); return }
    val snapshot = argentumSnapshot ?: return
    passToMyTurnFrom = snapshot.turnNumber
    recordDiagnostic("Pass to my turn: started on turn ${snapshot.turnNumber}", DiagnosticCategory.ACTION)
}

// Every server message is handled once, in order. (A single state variable conflated bursts:
// e.g. gameStarted + mulliganDecision arriving in one frame lost the first message.)
internal fun TableController.handleArgentumMessage(incoming: JSONObject) {
    argentumInbound = incoming
    if (argentumEndPreviousRequested && incoming.optString("type") in setOf("connected", "reconnected")) {
        argentumEndPreviousRequested = false
        if (incoming.optString("context") == "game") argentumSocket.concede()
        setArgentumGameActive(false)
        // Outside this effect: a new inbound message would cancel it before the close ran.
        scope.launch { delay(600); argentumSocket.close() }
        message = if (incoming.optString("context") == "game") "The previous Argentum game was conceded." else "The previous Argentum game had already ended."
        return
    }
    // Outside an Argentum session, never let pushed game messages take over the table.
    if (!argentumEnabled && incoming.optString("type") !in setOf("connected", "reconnected", "error", "sessionReplaced")) return
    when (incoming.optString("type")) {
        "connected", "reconnected" -> {
            argentumPlayerId = incoming.optString("playerId").takeUnless { it.isBlank() || it == "null" }
            val inGame = incoming.optString("context") == "game"
            recordDiagnostic("Argentum session ${incoming.optString("type")}${if (inGame) " to an active game" else ""}", DiagnosticCategory.CONNECTION)
            if (argentumResumeRequested) {
                argentumResumeRequested = false
                if (!inGame) {
                    // The player asked to resume, but Argentum no longer has that game.
                    setArgentumGameActive(false)
                    argentumSocket.close()
                    argentumEnabled = false
                    homeOpen = true
                    argentumLastError = "That Argentum game has ended or expired, so there is nothing to resume. Start a new game when you're ready."
                }
            }
            // Argentum pushes state itself after a reconnect; this only covers a missed push.
            if (inGame) argentumSocket.requestResync()
        }
        "quickGameLobbyState" -> {
            val playersInLobby = incoming.optJSONArray("players") ?: JSONArray()
            localSeat = (0 until playersInLobby.length()).firstOrNull {
                playersInLobby.optJSONObject(it)?.optString("playerId") == incoming.optString("youPlayerId")
            } ?: 0
            val cards = argentumDeckForLobby
            val commander = argentumCommanderForLobby
            if (cards != null && commander != null && !argentumLobbySubmitted && argentumStatus == ArgentumConnectionStatus.CONNECTED) {
                argentumLobbySubmitted = true
                if (!argentumSocket.submitCommanderDeck(cards, commander) || !argentumSocket.markReady()) {
                    argentumLobbySubmitted = false
                    argentumLastError = "Could not submit your deck to the Argentum lobby."
                } else {
                    lobbyNotice = "Deck sent to Argentum. Waiting for the authoritative game to start."
                    recordDiagnostic("Argentum lobby received; compatible deck submitted and marked ready")
                }
            }
        }
        "gameStarted" -> {
            val roster = incoming.optJSONArray("players") ?: JSONArray()
            localSeat = (0 until roster.length()).firstOrNull { roster.optJSONObject(it)?.optBoolean("isYou") == true } ?: 0
            gameStartedOnline = true
            homeOpen = false
            lobbyOpen = false
            gameTimerRunning = true
            turnTimerRunning = true
            setArgentumGameActive(true)
            argentumGameOverDismissed = false; argentumGameOverText = null; argentumWon = null
            recordDiagnostic("Argentum game started", DiagnosticCategory.ENGINE)
        }
        "stateUpdate" -> {
            val state = incoming.optJSONObject("state")
            if (state != null) {
                argentumAwaitingUpdate = false
                if (argentumLocalPlayersBackup == null) argentumLocalPlayersBackup = loadSavedPlayers(context) ?: players.toList()
                val snapshot = ArgentumTableSnapshot.parse(state, incoming.optJSONArray("legalActions") ?: JSONArray(), incoming.optString("interactionEpoch").takeUnless { it.isBlank() || it == "null" })
                val nextDecision = incoming.optJSONObject("pendingDecision")
                argentumLastStateJson = state
                if (nextDecision?.optString("id") != argentumPendingDecision?.optString("id")) {
                    argentumDecisionSelection = emptySet()
                    argentumDecisionColors = emptySet()
                    argentumDecisionTargets = emptyMap()
                    argentumModeSelection = emptySet()
                    argentumOrderSelection = nextDecision?.let(ArgentumDecisions::orderingItems).orEmpty()
                    argentumAmounts = when (nextDecision?.optString("type")) {
                        "AssignDamageDecision" -> ArgentumDecisions.defaultDamageAssignments(nextDecision)
                        "CombatResolutionDecision" -> ArgentumCombatResolution.from(nextDecision)?.defaults(nextDecision.optString("playerId")).orEmpty()
                        "SplitPilesDecision" -> ArgentumPileSplit(nextDecision).initial()
                        "DistributeDecision" -> nextDecision.optJSONArray("targets")?.let { t -> (0 until t.length()).associate { t.optString(it) to nextDecision.optInt("minPerTarget", 0) } }.orEmpty()
                        else -> emptyMap()
                    }
                    argentumDeferredDecisionId = null
                    argentumBudgetCounts = emptyMap()
                    argentumReplacementPick = nextDecision?.takeIf { it.optString("type") == "ChooseReplacementDecision" }
                        ?.let { ArgentumReplacementChoice(it) }?.let { c -> c.defaultFrom() to (c.allowedTo(c.defaultFrom()).firstOrNull() ?: 0) } ?: (0 to 0)
                    argentumAttackPlan = null
                    argentumBlockPlan = null
                    nextDecision?.let { decision ->
                        recordDiagnostic("Argentum decision ${decision.optString("type")}: ${decision.optString("prompt")}", DiagnosticCategory.ENGINE)
                        ArgentumDecisions.needsHandlingReason(decision)?.let { reportArgentumUnsupported("decision:${decision.optString("id")}", "${decision.optString("type")} — $it") }
                    }
                }
                argentumPendingDecision = nextDecision
                argentumNextStop = incoming.optString("nextStopPoint").takeUnless { it.isBlank() || it == "null" }
                argentumUndoAvailable = incoming.optBoolean("undoAvailable", false)
                val firstSnapshot = argentumSnapshot == null
                argentumSnapshot = snapshot
                // Keep an open action sheet in step with the new state so it never offers stale actions.
                if (argentumActionChoices.isNotEmpty()) {
                    val sheetCard = argentumActionCard?.let { old -> snapshot.players.flatMap { it.hand + it.battlefield + it.graveyard + it.exile + listOfNotNull(it.commanderCard) }.firstOrNull { it.id == old.id } }
                    if (argentumActionCard != null && sheetCard == null) { argentumActionChoices = emptyList(); argentumActionCard = null }
                    else if (sheetCard != null) openArgentumActions(sheetCard)
                    else argentumActionChoices = snapshot.legalActions.ifEmpty { listOf(JSONObject().put("__none", true)) }
                }
                players = snapshot.players
                localSeat = snapshot.viewingPlayer
                activePlayer = snapshot.activePlayer
                if (firstSnapshot) selectedPlayer = snapshot.viewingPlayer
                turnNumber = snapshot.turnNumber
                phaseIndex = phaseSteps.indexOfFirst { it.equals(snapshot.stepName, true) }.takeIf { it >= 0 }
                    ?: phaseSteps.indexOfFirst { it.equals(snapshot.phaseName, true) }.takeIf { it >= 0 } ?: 3
                gameEndedOnline = snapshot.gameOver
                if (snapshot.gameOver) setArgentumGameActive(false) else if (!argentumResumeAvailable) setArgentumGameActive(true)
                gameStartedOnline = true
                homeOpen = false
                lobbyOpen = false
                gameTimerRunning = true
                turnTimerRunning = true
                val entries = incoming.optJSONArray("events")
                if (entries != null && entries.length() > 0) {
                    val cardsJson = state.optJSONObject("cards")
                    val played = PlayFeedParser.parse(entries, cardsJson)
                    played.filter { it.kind != PlayedCard.Kind.ABILITY }.takeIf { it.isNotEmpty() }?.let { playFeed = (playFeed + it).takeLast(8) }
                    played.lastOrNull { it.kind != PlayedCard.Kind.ABILITY && !it.byYou }?.let { playPopup = it }
                    played.lastOrNull { it.kind == PlayedCard.Kind.ABILITY }?.let { playBanner = it }
                    if (snapshot.activePlayer != localSeat) {
                        val base = aiRecaps.firstOrNull { it.turn == snapshot.turnNumber && it.seat == snapshot.activePlayer }?.recap ?: TurnRecap()
                        val recap = PlayFeedParser.recap(entries, cardsJson, argentumPlayerId, base)
                        aiRecaps = mergeTurnRecap(aiRecaps, snapshot.turnNumber, snapshot.activePlayer, recap)
                    }
                    val eventLines = (0 until entries.length()).mapNotNull { entries.optJSONObject(it)?.let(PlayFeedParser::logLine) }
                    if (eventLines.isNotEmpty()) gameLog = (gameLog + eventLines).takeLast(250)
                }
                recordDiagnostic("Argentum state v${incoming.optLong("stateVersion")} applied: ${snapshot.players.size} players, turn ${snapshot.turnNumber} ${snapshot.stepName}, ${snapshot.legalActions.size} legal actions${if (incoming.has("engineMs")) ", engine ${incoming.optLong("engineMs")} ms" else ""}${incoming.optJSONObject("engineTimingsMs")?.let { ", stages $it" } ?: ""}", DiagnosticCategory.ENGINE)
            }
        }
        "stateDeltaUpdate" -> {
            // Arcana never guesses at a delta. Ask Argentum for a complete masked snapshot.
            argentumAwaitingUpdate = false
            if (!argentumSocket.requestResync()) argentumLastError = "Waiting to reconnect before syncing the game state."
        }
        "mulliganDecision" -> {
            argentumAwaitingUpdate = false
            argentumMulliganPrompt = true
            argentumMulliganInfo = Triple(incoming.optInt("mulliganCount"), incoming.optBoolean("isOnThePlay"), incoming.optInt("cardsToPutOnBottom"))
            val handIds = incoming.optJSONArray("hand") ?: JSONArray()
            val cardInfo = incoming.optJSONObject("cards") ?: JSONObject()
            val hand = (0 until handIds.length()).mapNotNull { index ->
                val serverId = handIds.optString(index)
                val detail = cardInfo.optJSONObject(serverId) ?: return@mapNotNull null
                GameCard(
                    id = (serverId.hashCode() and 0x7fffffff).coerceAtLeast(1),
                    name = detail.optString("name", "Card"), type = detail.optString("typeLine").takeUnless { it.isBlank() || it == "null" } ?: "Card", color = Color(0xFF3C5A52),
                    imageUrl = detail.optString("imageUri").takeUnless { it.isBlank() || it == "null" },
                    manaCost = detail.optString("manaCost").takeUnless { it.isBlank() || it == "null" },
                    oracleText = detail.optString("oracleText").takeUnless { it.isBlank() || it == "null" },
                    power = detail.opt("power")?.toString()?.takeUnless { it == "null" },
                    toughness = detail.opt("toughness")?.toString()?.takeUnless { it == "null" }
                )
            }
            if (hand.isNotEmpty()) players = players.mapIndexed { index, player ->
                if (index == localSeat) player.copy(hand = hand) else player
            }
        }
        "chooseBottomCards" -> {
            argentumAwaitingUpdate = false
            val ids = incoming.optJSONArray("hand") ?: JSONArray()
            argentumBottomCardIds = (0 until ids.length()).map { ids.optString(it) }
            argentumBottomCardCount = incoming.optInt("cardsToPutOnBottom", 0)
            argentumBottomSelection = emptySet()
        }
        "arcanaNudge" -> { buzz(500); flashMessage = FlashText("Your go", 0xFFE5484D) }
        "maxXRefined" -> {
            val cardId = incoming.optString("cardId")
            val plan = argentumComposer
            if (argentumXChecking == cardId) argentumXChecking = null
            val range = plan?.xRange
            if (plan != null && range != null && plan.info.optJSONObject("action")?.optString("cardId") == cardId && !incoming.isNull("maxX")) {
                val max = incoming.optInt("maxX").coerceIn(range.first, range.last)
                if (max != range.last) argentumComposer = plan.copy(xRange = range.first..max)
                recordDiagnostic("X for ${plan.description}: up to $max (engine ${incoming.optLong("engineMs", -1)} ms)", DiagnosticCategory.ENGINE)
            }
        }
        "mulliganComplete" -> { argentumMulliganPrompt = false; argentumAwaitingUpdate = false }
        "gameOver" -> {
            setArgentumGameActive(false)
            gameEndedOnline = true
            argentumAwaitingUpdate = false
            val winner = incoming.optString("winnerId").takeIf { it.isNotBlank() && it != "null" }
            val won = winner != null && winner == argentumPlayerId
            argentumWon = if (winner == null) null else won
            val reason = incoming.optString("reason").lowercase().replace('_', ' ')
            argentumGameOverText = incoming.optString("message").takeIf { it.isNotBlank() && it != "null" }
                ?: listOfNotNull(if (winner == null) "No winner." else if (won) "You won." else "${players.firstOrNull { it.id == winner }?.name ?: "Your opponent"} won.", reason.takeIf { it.isNotBlank() }?.let { "Reason: $it." }).joinToString(" ")
            recordDiagnostic("Argentum game over (${incoming.optString("reason")})", DiagnosticCategory.ENGINE)
        }
        "sessionReplaced" -> recordDiagnostic("Argentum session replaced by another connection", DiagnosticCategory.CONNECTION)
        "error" -> if (incoming.optString("code") == ArgentumCloudGuest.LINK_NOTE) {
            // Online connection news (dropped, reconnecting, back): shown, not treated as an error.
            message = incoming.optString("message")
            recordDiagnostic("Online: ${incoming.optString("message")}", DiagnosticCategory.CONNECTION)
        } else if (incoming.optString("code") == "WIFI_NOTE") {
            // An information note from the host (e.g. a card left out of a deck), not a problem.
            message = "Note: " + incoming.optString("message")
            recordDiagnostic("Note from host: ${incoming.optString("message")}", DiagnosticCategory.ENGINE)
        } else {
            argentumLastError = incoming.optString("message", "Argentum returned an error."); message = argentumLastError; argentumAwaitingUpdate = false; argentumXChecking = null
            recordDiagnostic("Argentum error ${incoming.optString("code")}: ${argentumLastError}", DiagnosticCategory.ERROR)
        }
    }
}

internal fun TableController.startArgentumPractice(vsAi: Boolean = true, joinLobbyCode: String? = null, acceptSubstitutions: Boolean = false) {
    if (argentumDeckValidationRunning) return
    if (joinLobbyCode != null && joinLobbyCode.isBlank()) {
        argentumLastError = "Enter the Argentum room code to join."
        return
    }
    val seat = players.getOrNull(localSeat) ?: run { message = "Import a deck before starting an Argentum game."; return }
    if (!seat.deckLoaded) {
        selectedPlayer = localSeat
        importError = null
        importOpen = true
        message = "Import your deck, then choose Play with Argentum."
        return
    }
    val commander = seat.commanderName?.takeIf(String::isNotBlank)
    if (commander == null) {
        message = "Argentum Commander games need a commander. Set one in the deck import or editor."
        return
    }
    scope.launch {
        argentumDeckValidationRunning = true
        argentumLastError = null
        val submitted = argentumCommanderLibraryDeck(
            (seat.library + seat.hand)
                .filterNot { it.isToken || it.name == "Hidden card" }
                .map { it.name },
            commander
        )
        if (submitted.isEmpty()) { argentumLastError = "Your imported deck has no cards to submit."; argentumDeckValidationRunning = false; return@launch }
        try {
            val api = ArgentumDeckApi(argentumOrigin)
            var commanderForGame: String = requireNotNull(commander)
            // Premade decks skip the server deck check (it only applies to imported decks).
            val skipDeckCheck = activeDeckId?.startsWith("builtin:") == true
            if (skipDeckCheck) recordDiagnostic("Deck check skipped: premade deck")
            var replacementCommander = false
            var validation = if (skipDeckCheck) ArgentumDeckValidation.skipped(submitted.values.sum())
                else withContext(Dispatchers.IO) { api.validate(submitted, commanderForGame, "COMMANDER") }
            if (validation.unsupportedCards.isNotEmpty() && !acceptSubstitutions) {
                val rejected = validation.unsupportedCards.sortedBy { it.lowercase() }
                rejected.forEach { reportArgentumUnsupported("card:$it", "Argentum cannot run card $it") }
                recordDiagnostic("Deck check: ${rejected.size} card(s) need handling; waiting for the player's choice", DiagnosticCategory.UNSUPPORTED)
                argentumDeckReview = ArgentumDeckReview(
                    unsupported = rejected,
                    commanderUnsupported = rejected.any { it.equals(commander, ignoreCase = true) },
                    commander = commander,
                    vsAi = vsAi,
                    joinCode = joinLobbyCode,
                )
                return@launch
            }
            val unsupported = linkedSetOf<String>()
            val replacementBasics = mutableListOf<String>()
            var checks = 0
            while (validation.unsupportedCards.isNotEmpty() && checks++ < 100) {
                val newlyUnsupported = validation.unsupportedCards
                unsupported += newlyUnsupported
                val commanderUnsupported = newlyUnsupported.any { it.equals(commander, ignoreCase = true) }
                val priorSize = submitted.size
                submitted.keys.filter { name -> newlyUnsupported.any { it.equals(name, ignoreCase = true) } }.forEach(submitted::remove)
                if (commanderUnsupported) {
                    val candidates = withContext(Dispatchers.IO) {
                        api.compatibleCommanderOptions((seat.library + seat.hand)
                            .filterNot { it.isToken || it.name == "Hidden card" || newlyUnsupported.any { name -> name.equals(it.name, true) } }
                            .map { it.name })
                    }
                    var replacement: Pair<String, Pair<Map<String, Int>, List<String>>>? = null
                    for ((candidate, basics) in candidates) {
                        val candidateDeck = submitted.toMutableMap()
                        // The replacement commander moves from the 99-card library into
                        // the command zone, so remove one copy before filling the library.
                        val candidateCount = candidateDeck.entries.firstOrNull {
                            it.key.equals(candidate, ignoreCase = true)
                        }
                        if (candidateCount != null) {
                            if (candidateCount.value <= 1) candidateDeck.remove(candidateCount.key)
                            else candidateDeck[candidateCount.key] = candidateCount.value - 1
                        }
                        if (basics.isEmpty()) continue
                        val candidateBasics = mutableListOf<String>()
                        val missing = (99 - candidateDeck.values.sum()).coerceAtLeast(0)
                        repeat(missing) { offset ->
                            val basic = basics[offset % basics.size]
                            candidateDeck[basic] = (candidateDeck[basic] ?: 0) + 1
                            candidateBasics += basic
                        }
                        val candidateValidation = withContext(Dispatchers.IO) {
                            api.validate(candidateDeck, candidate, "COMMANDER")
                        }
                        if (candidateValidation.valid) {
                            replacement = candidate to (candidateDeck to candidateBasics)
                            validation = candidateValidation
                            break
                        }
                    }
                    if (replacement == null) {
                        argentumLastError = "Argentum does not support commander $commander, and this deck has no compatible commander. Choose a supported commander and try again."
                        return@launch
                    }
                    val selectedReplacement = replacement
                    commanderForGame = selectedReplacement.first
                    submitted.clear()
                    submitted.putAll(selectedReplacement.second.first)
                    replacementBasics += selectedReplacement.second.second
                    replacementCommander = true
                    break
                }
                if (submitted.size == priorSize) break
                validation = withContext(Dispatchers.IO) { api.validate(submitted, commanderForGame, "COMMANDER") }
            }
            if (unsupported.isNotEmpty() && !validation.valid) {
                val missing = (99 - submitted.values.sum()).coerceAtLeast(0)
                if (missing > 0) {
                    val basics = withContext(Dispatchers.IO) { api.supportedBasicLands(commanderForGame) }
                    if (basics.isEmpty()) {
                        argentumLastError = "Argentum rejected ${unsupported.joinToString()}; no supported basic lands were found to complete the deck."
                        return@launch
                    }
                    repeat(missing) { offset ->
                        val basic = basics[offset % basics.size]
                        submitted[basic] = (submitted[basic] ?: 0) + 1
                        replacementBasics += basic
                    }
                    validation = withContext(Dispatchers.IO) { api.validate(submitted, commanderForGame, "COMMANDER") }
                }
            }
            if (unsupported.isNotEmpty()) {
                recordDiagnostic("Removed ${unsupported.size} Argentum-unsupported card name(s) and added ${replacementBasics.size} supported basic land(s)")
            }
            if (!validation.valid) {
                val reasons = validation.errors.joinToString("\n") { it.message }.ifBlank { "Argentum rejected this Commander deck (${validation.totalCards} cards)." }
                argentumLastError = (if (unsupported.isEmpty()) "" else "Unsupported cards: ${unsupported.joinToString()}. They were left out and replaced with ${replacementBasics.size} supported basic lands.\n") + reasons
                return@launch
            }
            if (unsupported.isNotEmpty()) {
                // Substitutions apply to this Argentum game only; the saved Arcana deck is untouched.
                message = "For this Argentum game, ${unsupported.size} unsupported card(s) were left out${if (replacementCommander) " and $commanderForGame is commander" else ""}; ${replacementBasics.size} supported basic land(s) fill the gap. Your saved deck is unchanged."
                recordDiagnostic("Player chose to play without ${unsupported.size} unsupported card(s); ${replacementBasics.size} basic land(s) added for this game only", DiagnosticCategory.UNSUPPORTED)
            }
            argentumDeckForLobby = submitted
            argentumCommanderForLobby = commanderForGame
            argentumVsAiLobby = vsAi
            argentumLobbySubmitted = false
            argentumLocalPlayersBackup = players.toList()
            argentumEnabled = true
            homeOpen = false
            lobbyOpen = false
            argentumSocket.connect(lobbyPlayerName)
            var waitCount = 0
            while (argentumStatus != ArgentumConnectionStatus.CONNECTED && waitCount < 30) {
                delay(400)
                waitCount++
            }
            if (argentumStatus != ArgentumConnectionStatus.CONNECTED) {
                argentumLastError = "Could not connect to Argentum. The app will keep retrying in the background."
                return@launch
            }
            val lobbySent = if (joinLobbyCode != null) {
                argentumSocket.joinQuickGameLobby(joinLobbyCode.trim())
            } else {
                argentumSocket.createCommanderPracticeLobby(vsAi = vsAi)
            }
            if (!lobbySent) {
                argentumLastError = if (joinLobbyCode != null) "Connected to Argentum, but could not join that room. Check the code and try again."
                else "Connected to Argentum, but could not create the room."
            } else {
                recordDiagnostic("Argentum Commander ${if (joinLobbyCode != null) "room join" else if (vsAi) "practice lobby" else "room"} requested with ${validation.totalCards} supported cards")
            }
        } catch (error: Exception) {
            argentumLastError = when (error) {
                is java.net.SocketTimeoutException -> "Argentum took too long to answer. Check your connection and try again."
                is java.net.UnknownHostException, is java.net.ConnectException, is javax.net.ssl.SSLException, is java.io.IOException ->
                    "Couldn't reach the Argentum server. Check your internet connection and try again. Your deck hasn't been changed."
                else -> error.message ?: "Could not validate this deck with Argentum."
            }
            recordDiagnostic("Argentum deck validation failed (${error.javaClass.simpleName}): ${error.message.orEmpty()}", DiagnosticCategory.ERROR)
        } finally {
            argentumDeckValidationRunning = false
        }
    }
}

/** Ends this Argentum session on the client and restores the local table. */
internal fun TableController.closeArgentumSession(concede: Boolean) {
    if (concede) { if (argentumSnapshot == null) argentumSocket.leaveQuickGameLobby() else if (!gameEndedOnline) argentumSocket.concede() }
    argentumSocket.close()
    stopWifi()
    argentumEnabled = false
    argentumOffline = false
    setArgentumGameActive(false)
    argentumSnapshot = null
    argentumPendingDecision = null
    argentumActionChoices = emptyList(); argentumActionCard = null; argentumComposer = null
    argentumAttackPlan = null; argentumBlockPlan = null
    playPopup = null; playFeed = emptyList(); playBanner = null; recapText = null; aiRecaps = emptyList(); argentumStackUse = null; argentumAutoYes = emptyMap()
    argentumLocalPlayersBackup?.let { players = it; argentumLocalPlayersBackup = null }
    argentumDeckForLobby = null
    argentumCommanderForLobby = null
    argentumLobbySubmitted = false
    gameStartedOnline = false
    gameEndedOnline = false
    gameTimerRunning = false
    turnTimerRunning = false
    homeOpen = true
    showOverview = true
    showHand = false
}

/**
 * What a card-grid pick shows for one Argentum object id: the card's picture, and who controls it
 * (you first, then each opponent by seat, then players themselves, then the stack).
 */
internal fun TableController.argentumPickCard(id: String): PickCard {
    val snap = argentumSnapshot
    val you = snap?.viewingPlayer ?: localSeat
    val seatOfPlayer = snap?.players?.indexOfFirst { it.id == id } ?: -1
    if (seatOfPlayer >= 0) {
        val p = snap!!.players[seatOfPlayer]
        return PickCard(id, if (seatOfPlayer == you) "You" else p.name, null, group = "Players", groupOrder = 100, isPlayer = true, detail = "${p.life} life")
    }
    val displayId = snap?.serverCardIds?.entries?.firstOrNull { it.value == id }?.key
    if (snap != null && displayId != null) {
        snap.players.forEachIndexed { seat, p ->
            val zones = listOf("" to p.battlefield, "graveyard" to p.graveyard, "exile" to p.exile, "hand" to p.hand, "revealed" to p.revealed)
            for ((zone, cards) in zones) {
                val card = cards.firstOrNull { it.id == displayId } ?: continue
                val owner = if (seat == you) "You" else p.name
                val group = if (zone.isEmpty()) owner else "$owner · $zone"
                val order = (if (seat == you) 0 else 1 + seat) * 10 + if (zone.isEmpty()) 0 else 1
                return PickCard(id, card.name, card.imageUrl?.takeIf { !card.isFaceDown }, card.type, group, order, isYou = seat == you, isToken = card.isToken)
            }
        }
    }
    snap?.stack?.firstOrNull { it.id == id }?.let { item ->
        return PickCard(id, item.name, item.imageUrl, group = "On the stack", groupOrder = 200, isYou = item.isYours, detail = item.controllerName)
    }
    val ref = snap?.targets?.get(id)
    val ownerSeat = ref?.ownerId?.let { o -> snap.players.indexOfFirst { it.id == o } } ?: -1
    val owner = when { ownerSeat < 0 -> "Other"; ownerSeat == you -> "You"; else -> snap!!.players[ownerSeat].name }
    val refName = ref?.name ?: argentumName(id)
    return PickCard(id, refName, artByName(refName, token = ref?.type?.contains("Token", ignoreCase = true) == true), ref?.type.orEmpty(), owner, if (ownerSeat == you) 5 else 1 + maxOf(ownerSeat, 0) * 10 + 5, isYou = ownerSeat == you)
}
