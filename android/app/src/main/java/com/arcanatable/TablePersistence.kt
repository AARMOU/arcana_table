@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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

internal fun savePlayers(context: android.content.Context, players: List<TablePlayer>) {
    fun cardJson(card: GameCard) = JSONObject().apply {
        put("id", card.id); put("name", card.name); put("type", card.type); put("colorArgb", card.color.toArgb())
        put("tapped", card.tapped); put("image", card.imageUrl ?: JSONObject.NULL); put("token", card.isToken)
        put("faceDown", card.isFaceDown); put("oracle", card.oracleText ?: JSONObject.NULL)
        put("manaValue", card.manaValue ?: JSONObject.NULL)
        put("manaCost", card.manaCost ?: JSONObject.NULL)
        put("power", card.power ?: JSONObject.NULL); put("toughness", card.toughness ?: JSONObject.NULL)
        put("keywords", JSONArray().apply { card.keywords.forEach { put(it) } })
        put("chosenCreatureType", card.chosenCreatureType ?: JSONObject.NULL)
        put("artCrop", card.artCropUrl ?: JSONObject.NULL)
        put("importIssue", card.importIssue ?: JSONObject.NULL)
        put("counters", JSONObject().apply { card.counters.forEach { (name, amount) -> put(name, amount) } })
    }
    val data = JSONArray().apply {
        players.forEach { player ->
            put(JSONObject().apply {
                put("name", player.name); put("id", player.id); put("commander", player.commanderName ?: JSONObject.NULL); put("commanderCard", player.commanderCard?.let { cardJson(it) } ?: JSONObject.NULL); put("computer", player.isComputer); put("deckLoaded", player.deckLoaded); put("handRevealedToAll", player.handRevealedToAll); put("life", player.life)
                put("commanderCastCount", player.commanderCastCount); put("isEliminated", player.isEliminated); put("eliminationReason", player.eliminationReason ?: JSONObject.NULL)
                put("playerCounters", JSONObject().apply { player.playerCounters.forEach { (counter, amount) -> put(counter, amount) } })
                put("commanderDamageReceived", JSONObject().apply { player.commanderDamageReceived.forEach { (playerId, amount) -> put(playerId, amount) } })
                put("manaPool", JSONObject().apply { player.manaPool.forEach { (color, amount) -> put(color, amount) } })
                listOf("library" to player.library, "hand" to player.hand, "battlefield" to player.battlefield, "graveyard" to player.graveyard, "exile" to player.exile, "revealed" to player.revealed)
                    .forEach { (key, cards) -> put(key, JSONArray().apply { cards.forEach { put(cardJson(it)) } }) }
            })
        }
    }
    runCatching { context.getSharedPreferences("arcana-table", android.content.Context.MODE_PRIVATE).edit().putString("players", data.toString()).apply() }
}

internal fun saveActiveDeck(context: android.content.Context, id: String?) {
    runCatching { context.getSharedPreferences("arcana-saved-decks", android.content.Context.MODE_PRIVATE).edit().putString("activeDeck", id).apply() }
}

internal fun loadActiveDeck(context: android.content.Context): String? =
    runCatching { context.getSharedPreferences("arcana-saved-decks", android.content.Context.MODE_PRIVATE).getString("activeDeck", null) }.getOrNull()

internal fun saveSavedDecks(context: android.content.Context, decks: List<SavedDeck>) {
    val data = JSONArray().apply { decks.forEach { put(JSONObject().put("name", it.name).put("text", it.text).put("commander", it.commander ?: JSONObject.NULL)
        .put("updatedAt", it.updatedAt).put("tags", JSONArray(it.tags))) } }
    runCatching { context.getSharedPreferences("arcana-saved-decks", android.content.Context.MODE_PRIVATE).edit().putString("savedDecks", data.toString()).apply() }
}

internal fun loadSavedDecks(context: android.content.Context): List<SavedDeck> = runCatching {
    val deckPrefs = context.getSharedPreferences("arcana-saved-decks", android.content.Context.MODE_PRIVATE)
    val raw = deckPrefs.getString("savedDecks", null) ?: run {
        val legacyPrefs = context.getSharedPreferences("arcana-table", android.content.Context.MODE_PRIVATE)
        val legacy = legacyPrefs.getString("savedDecks", null) ?: return emptyList()
        deckPrefs.edit().putString("savedDecks", legacy).apply()
        legacyPrefs.edit().remove("savedDecks").apply()
        legacy
    }
    val data = JSONArray(raw)
    (0 until data.length()).mapNotNull { index ->
        data.optJSONObject(index)?.let { item ->
            val name = item.optString("name").trim()
            val text = item.optString("text").trim()
            if (name.isBlank() || text.isBlank()) null else SavedDeck(name, text, item.optString("commander").takeIf { it.isNotBlank() && it != "null" },
                updatedAt = item.optLong("updatedAt", 0L),
                tags = item.optJSONArray("tags")?.let { t -> (0 until t.length()).mapNotNull { i -> t.optString(i).trim().takeIf { it.isNotEmpty() } } }.orEmpty())
        }
    }
}.getOrDefault(emptyList())

internal fun loadSavedPlayers(context: android.content.Context): List<TablePlayer>? = runCatching {
    val raw = context.getSharedPreferences("arcana-table", android.content.Context.MODE_PRIVATE).getString("players", null) ?: return null
    val data = JSONArray(raw)
    if (data.length() !in 1..4) return null
    fun parseCard(card: JSONObject): GameCard {
        val countersJson = card.optJSONObject("counters") ?: JSONObject()
        val counters = countersJson.keys().asSequence().associateWith { countersJson.optInt(it, 0) }.filterValues { it > 0 }
        return GameCard(
            id = card.optInt("id"), name = card.optString("name"), type = card.optString("type"),
            color = Color(if (card.has("colorArgb")) card.optInt("colorArgb", 0xFF547D9A.toInt()) else card.optLong("color", 0xFF547D9AL).toInt()),
            tapped = card.optBoolean("tapped"),
            imageUrl = card.optString("image").takeUnless { it == "null" || it.isBlank() }, isToken = card.optBoolean("token"),
            counters = counters, isFaceDown = card.optBoolean("faceDown"),
            oracleText = card.optString("oracle").takeUnless { it == "null" || it.isBlank() },
            manaValue = card.optDouble("manaValue").takeUnless { card.isNull("manaValue") },
            importIssue = card.optString("importIssue").takeUnless { it == "null" || it.isBlank() },
            artCropUrl = card.optString("artCrop").takeUnless { it == "null" || it.isBlank() },
            manaCost = if (card.has("manaCost") && !card.isNull("manaCost")) card.optString("manaCost") else null,
            power = card.optString("power").takeUnless { it == "null" || it.isBlank() },
            toughness = card.optString("toughness").takeUnless { it == "null" || it.isBlank() },
            keywords = card.optJSONArray("keywords")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
            chosenCreatureType = card.optString("chosenCreatureType").takeUnless { it == "null" || it.isBlank() }
        )
    }
    fun cards(owner: JSONObject, key: String): List<GameCard> {
        val list = owner.optJSONArray(key) ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val card = list.optJSONObject(index) ?: return@mapNotNull null
            parseCard(card)
        }.filterNot { isDeckSectionHeading(it.name) }
    }
    (0 until data.length()).mapNotNull { index ->
        val player = data.optJSONObject(index) ?: return@mapNotNull null
        val commanderCard = player.optJSONObject("commanderCard")?.let(::parseCard)?.takeUnless { isDeckSectionHeading(it.name) }
        val commanderName = player.optString("commander").takeUnless { it == "null" || it.isBlank() || isDeckSectionHeading(it) }
        TablePlayer(
            name = player.optString("name", "PLAYER ${index + 1}"),
            id = player.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
            commanderName = commanderCard?.name ?: commanderName,
            commanderCard = commanderCard,
            isComputer = player.optBoolean("computer"),
            deckLoaded = player.optBoolean("deckLoaded"), handRevealedToAll = player.optBoolean("handRevealedToAll"), life = player.optInt("life", 40),
            commanderCastCount = player.optInt("commanderCastCount").coerceAtLeast(0),
            isEliminated = player.optBoolean("isEliminated"), eliminationReason = player.optString("eliminationReason").takeUnless { it == "null" || it.isBlank() },
            playerCounters = player.optJSONObject("playerCounters")?.let { counters -> buildMap { counters.keys().forEach { put(it, counters.optInt(it).coerceAtLeast(0)) } } }.orEmpty(),
            commanderDamageReceived = player.optJSONObject("commanderDamageReceived")?.let { damage -> buildMap { damage.keys().forEach { put(it, damage.optInt(it).coerceAtLeast(0)) } } }.orEmpty(),
            manaPool = player.optJSONObject("manaPool")?.let { pool -> buildMap { pool.keys().forEach { color -> put(color, pool.optInt(color).coerceAtLeast(0)) } } }.orEmpty(),
            library = cards(player, "library"), hand = cards(player, "hand"), battlefield = cards(player, "battlefield"),
            graveyard = cards(player, "graveyard"), exile = cards(player, "exile"), revealed = cards(player, "revealed")
        )
    }.takeIf { it.size == data.length() && it.size in 2..4 }
}.getOrNull()
