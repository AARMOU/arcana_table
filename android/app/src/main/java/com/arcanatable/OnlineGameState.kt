package com.arcanatable

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

data class OnlineGameState(
    val players: List<TablePlayer>,
    val activePlayer: Int,
    val turnNumber: Int,
    val phaseIndex: Int,
    val elapsedGameSeconds: Long,
    val elapsedTurnSeconds: Long,
    val turnOrder: List<Int>,
    val readyPlayers: Set<Int>,
    val gameLog: List<String>,
    val chat: List<OnlineChatMessage>,
    val gameStarted: Boolean,
    val endProposalBy: Int = -1,
    val endVotes: Map<Int, Boolean> = emptyMap(),
    val gameEnded: Boolean = false,
    val stackActions: List<StackAction> = emptyList(),
    val priorityPlayer: Int = 0,
    val priorityPassed: Set<String> = emptySet(),
    val triggerPrompts: List<TriggerPrompt> = emptyList(),
    val stackPriorityOriginId: String? = null
)

data class OnlineChatMessage(val author: String, val text: String, val timestamp: String)

data class TriggerPrompt(
    val id: String = java.util.UUID.randomUUID().toString(),
    val cardName: String,
    val controllerIndex: Int,
    val eventPlayerIndex: Int,
    val kind: String,
    val triggerText: String = "",
    val effectText: String = "",
    val targetRule: String? = null,
    val eventId: String? = null
)

fun OnlineGameState.toJson(): String = JSONObject().apply {
    put("players", JSONArray().apply { players.forEach { put(it.toJson()) } })
    put("activePlayer", activePlayer)
    put("turnNumber", turnNumber)
    put("phaseIndex", phaseIndex)
    put("elapsedGameSeconds", elapsedGameSeconds)
    put("elapsedTurnSeconds", elapsedTurnSeconds)
    put("turnOrder", JSONArray().apply { turnOrder.forEach { put(it) } })
    put("readyPlayers", JSONArray().apply { readyPlayers.forEach { put(it) } })
    put("gameLog", JSONArray().apply { gameLog.forEach { put(it) } })
    put("chat", JSONArray().apply { chat.forEach { put(JSONObject().put("author", it.author).put("text", it.text).put("timestamp", it.timestamp)) } })
    put("gameStarted", gameStarted)
    put("endProposalBy", endProposalBy)
    put("endVotes", JSONObject().apply { endVotes.forEach { (seat, vote) -> put(seat.toString(), vote) } })
    put("gameEnded", gameEnded)
    put("stackActions", JSONArray().apply { stackActions.forEach { action ->
        put(JSONObject().put("id", action.id).put("title", action.title).put("controllerId", action.controllerId).put("kind", action.kind).put("card", action.card?.toJson() ?: JSONObject.NULL)
            .put("targetText", action.targetText)
            .put("targetCount", action.targetCount?.let { JSONObject().put("minimum", it.minimum).put("maximum", it.maximum) } ?: JSONObject.NULL)
            .put("targets", JSONArray().apply { action.targets.forEach { target -> put(JSONObject().put("key", target.key).put("label", target.label).put("category", target.category).put("ownerId", target.ownerId ?: JSONObject.NULL)) } })
            .put("targetsConfirmed", action.targetsConfirmed))
    } })
    put("priorityPlayer", priorityPlayer)
    put("priorityPassed", JSONArray().apply { priorityPassed.forEach { put(it) } })
    put("stackPriorityOriginId", stackPriorityOriginId ?: JSONObject.NULL)
    put("triggerPrompts", JSONArray().apply { triggerPrompts.forEach { prompt ->
        put(JSONObject()
            .put("id", prompt.id)
            .put("cardName", prompt.cardName)
            .put("controllerIndex", prompt.controllerIndex)
            .put("eventPlayerIndex", prompt.eventPlayerIndex)
            .put("kind", prompt.kind)
            .put("triggerText", prompt.triggerText)
            .put("effectText", prompt.effectText)
            .put("targetRule", prompt.targetRule ?: JSONObject.NULL)
            .put("eventId", prompt.eventId ?: JSONObject.NULL))
    } })
}.toString()

fun parseOnlineGameState(source: String): OnlineGameState? = runCatching {
    val json = JSONObject(source)
    val playersJson = json.optJSONArray("players") ?: return null
    val players = (0 until playersJson.length()).mapNotNull { playersJson.optJSONObject(it)?.toTablePlayer() }
    if (players.isEmpty()) return null
    fun intList(key: String) = json.optJSONArray(key)?.let { array -> (0 until array.length()).mapNotNull { array.optInt(it).takeIf { value -> value >= 0 } } }.orEmpty()
    OnlineGameState(
        players = players,
        activePlayer = json.optInt("activePlayer").coerceIn(players.indices),
        turnNumber = json.optInt("turnNumber", 1).coerceAtLeast(1),
        phaseIndex = json.optInt("phaseIndex").coerceAtLeast(0),
        elapsedGameSeconds = json.optLong("elapsedGameSeconds").coerceAtLeast(0),
        elapsedTurnSeconds = json.optLong("elapsedTurnSeconds").coerceAtLeast(0),
        turnOrder = intList("turnOrder").filter { it in players.indices },
        readyPlayers = intList("readyPlayers").filter { it in players.indices }.toSet(),
        gameLog = json.optJSONArray("gameLog")?.let { array -> (0 until array.length()).map { array.optString(it) }.takeLast(250) }.orEmpty(),
        chat = json.optJSONArray("chat")?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let { item -> OnlineChatMessage(item.optString("author"), item.optString("text"), item.optString("timestamp")) } }.takeLast(200) }.orEmpty(),
        gameStarted = json.optBoolean("gameStarted"),
        endProposalBy = json.optInt("endProposalBy", -1),
        endVotes = json.optJSONObject("endVotes")?.let { votes -> buildMap { votes.keys().forEach { key -> put(key.toIntOrNull() ?: return@forEach, votes.optBoolean(key)) } } }.orEmpty(),
        gameEnded = json.optBoolean("gameEnded"),
        stackActions = json.optJSONArray("stackActions")?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let { item ->
            val card = item.optJSONObject("card")?.toGameCard()
            val targetText = item.optString("targetText", card?.oracleText.orEmpty())
            val targetCountJson = item.optJSONObject("targetCount")
            val targetCount = targetCountJson?.let { TargetCountRange(it.optInt("minimum").coerceAtLeast(0), it.optInt("maximum").coerceAtLeast(0)) } ?: parseTargetCountRange(targetText)
            val targets = item.optJSONArray("targets")?.let { targetArray -> (0 until targetArray.length()).mapNotNull { index ->
                targetArray.optJSONObject(index)?.let { target -> StackTarget(target.optString("key"), target.optString("label"), target.optString("category"), target.optString("ownerId").takeUnless { it.isBlank() || it == "null" }) }
            } }.orEmpty()
            StackAction(item.optString("id"), item.optString("title"), item.optString("controllerId"), item.optString("kind", "SPELL"), card, targetText, targetCount, targets, item.optBoolean("targetsConfirmed", targetCount == null))
        } } }.orEmpty(),
        priorityPlayer = json.optInt("priorityPlayer").coerceIn(players.indices),
        priorityPassed = json.optJSONArray("priorityPassed")?.let { array -> (0 until array.length()).map { array.optString(it) }.toSet() }.orEmpty(),
        stackPriorityOriginId = json.optString("stackPriorityOriginId").takeUnless { it.isBlank() || it == "null" },
        triggerPrompts = json.optJSONArray("triggerPrompts")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val controller = item.optInt("controllerIndex", -1)
                val eventPlayer = item.optInt("eventPlayerIndex", -1)
                if (controller !in players.indices || eventPlayer !in players.indices) return@mapNotNull null
                TriggerPrompt(
                    id = item.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
                    cardName = item.optString("cardName"),
                    controllerIndex = controller,
                    eventPlayerIndex = eventPlayer,
                    kind = item.optString("kind", "MANUAL"),
                    triggerText = item.optString("triggerText"),
                    effectText = item.optString("effectText"),
                    targetRule = item.optString("targetRule").takeUnless { it.isBlank() || it == "null" },
                    eventId = item.optString("eventId").takeUnless { it.isBlank() || it == "null" }
                )
            }
        }.orEmpty()
    )
}.getOrNull()

private fun TablePlayer.toJson() = JSONObject().apply {
    put("name", name)
    put("id", id)
    put("isOnline", isOnline)
    put("commanderName", commanderName ?: JSONObject.NULL)
    put("commanderCard", commanderCard?.toJson() ?: JSONObject.NULL)
    put("isComputer", isComputer)
    put("deckLoaded", deckLoaded)
    put("mulligansTaken", mulligansTaken)
    put("openingHandKept", openingHandKept)
    put("handRevealedToAll", handRevealedToAll)
    put("playerCounters", JSONObject().apply { playerCounters.forEach { (counter, amount) -> put(counter, amount) } })
    put("commanderDamageReceived", JSONObject().apply { commanderDamageReceived.forEach { (playerId, amount) -> put(playerId, amount) } })
    put("commanderCastCount", commanderCastCount)
    put("isEliminated", isEliminated)
    put("eliminationReason", eliminationReason ?: JSONObject.NULL)
    put("life", life)
    put("manaPool", JSONObject().apply { manaPool.forEach { (color, amount) -> put(color, amount) } })
    put("library", library.toJsonArray())
    put("hand", hand.toJsonArray())
    put("battlefield", battlefield.toJsonArray())
    put("graveyard", graveyard.toJsonArray())
    put("exile", exile.toJsonArray())
    put("revealed", revealed.toJsonArray())
}

private fun JSONObject.toTablePlayer(): TablePlayer {
    fun cards(key: String) = optJSONArray(key)?.toGameCards().orEmpty()
    return TablePlayer(
        name = optString("name", "PLAYER"),
        id = optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
        isOnline = optBoolean("isOnline"),
        commanderName = optString("commanderName").takeUnless { it.isBlank() || it == "null" },
        commanderCard = optJSONObject("commanderCard")?.toGameCard(),
        isComputer = optBoolean("isComputer"),
        deckLoaded = optBoolean("deckLoaded"),
        mulligansTaken = optInt("mulligansTaken").coerceIn(0, 8),
        openingHandKept = optBoolean("openingHandKept"),
        handRevealedToAll = optBoolean("handRevealedToAll"),
        playerCounters = optJSONObject("playerCounters")?.let { counters -> buildMap { counters.keys().forEach { key -> put(key, counters.optInt(key).coerceAtLeast(0)) } } }.orEmpty(),
        commanderDamageReceived = optJSONObject("commanderDamageReceived")?.let { damage -> buildMap { damage.keys().forEach { key -> put(key, damage.optInt(key).coerceAtLeast(0)) } } }.orEmpty(),
        commanderCastCount = optInt("commanderCastCount").coerceAtLeast(0),
        isEliminated = optBoolean("isEliminated"),
        eliminationReason = optString("eliminationReason").nullableString(),
        life = optInt("life", 40),
        manaPool = optJSONObject("manaPool")?.let { pool -> buildMap { pool.keys().forEach { color -> put(color, pool.optInt(color).coerceAtLeast(0)) } } }.orEmpty(),
        library = cards("library"), hand = cards("hand"), battlefield = cards("battlefield"),
        graveyard = cards("graveyard"), exile = cards("exile"), revealed = cards("revealed")
    )
}

private fun List<GameCard>.toJsonArray() = JSONArray().apply { forEach { put(it.toJson()) } }
private fun JSONArray.toGameCards() = (0 until length()).mapNotNull { optJSONObject(it)?.toGameCard() }

private fun GameCard.toJson() = JSONObject().apply {
    // Send a 32-bit sRGB color. Compose's packed Color.value is 64-bit and
    // JSON.parse/stringify in the Worker can round it and corrupt its color-space bits.
    put("id", id); put("name", name); put("type", type); put("colorArgb", color.toArgb())
    put("tapped", tapped); put("imageUrl", imageUrl ?: JSONObject.NULL); put("isToken", isToken)
    put("counters", JSONObject().apply { counters.forEach { (name, amount) -> put(name, amount) } })
    put("isFaceDown", isFaceDown); put("oracleText", oracleText ?: JSONObject.NULL)
    put("manaValue", manaValue ?: JSONObject.NULL); put("importIssue", importIssue ?: JSONObject.NULL)
    put("artCropUrl", artCropUrl ?: JSONObject.NULL)
    put("manaCost", manaCost ?: JSONObject.NULL)
    put("power", power ?: JSONObject.NULL); put("toughness", toughness ?: JSONObject.NULL)
    put("keywords", JSONArray().apply { keywords.forEach { put(it) } })
    put("colors", JSONArray().apply { colors.forEach { put(it) } })
    put("chosenCreatureType", chosenCreatureType ?: JSONObject.NULL)
}

private fun JSONObject.toGameCard(): GameCard {
    // Prefer the safe ARGB wire format. Fall back to the low ARGB word for
    // older room snapshots, whose packed Compose color may have been rounded by JS.
    val argb = if (has("colorArgb")) optInt("colorArgb", 0xFF547D9A.toInt())
        else optLong("color", 0xFF547D9AL).toInt()
    val countersJson = optJSONObject("counters") ?: JSONObject()
    val counters = buildMap { countersJson.keys().forEach { key -> put(key, countersJson.optInt(key)) } }
    return GameCard(
        id = optInt("id"), name = optString("name", "CARD"), type = optString("type", "CARD"), color = Color(argb),
        tapped = optBoolean("tapped"), imageUrl = optString("imageUrl").nullableString(), isToken = optBoolean("isToken"),
        counters = counters, isFaceDown = optBoolean("isFaceDown"), oracleText = optString("oracleText").nullableString(),
        manaValue = if (isNull("manaValue")) null else optDouble("manaValue"), importIssue = optString("importIssue").nullableString(),
        artCropUrl = optString("artCropUrl").nullableString(), manaCost = if (has("manaCost") && !isNull("manaCost")) optString("manaCost") else null,
        power = optString("power").nullableString(), toughness = optString("toughness").nullableString(),
        keywords = optJSONArray("keywords")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
        chosenCreatureType = optString("chosenCreatureType").nullableString(),
        colors = optJSONArray("colors")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
    )
}

private fun String?.nullableString(): String? = this?.takeUnless { it.isBlank() || it == "null" }
