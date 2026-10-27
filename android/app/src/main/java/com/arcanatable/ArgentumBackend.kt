package com.arcanatable

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/** Argentum's production API origin. Keep transport and rules decisions on the Argentum server. */
const val ARGENTUM_SERVER_ORIGIN = "https://magic.wingedsheep.com"
const val ARGENTUM_GAME_SOCKET = "wss://magic.wingedsheep.com/game"

/**
 * Resolves which Argentum server Arcana talks to. Default is the public server; a self-hosted
 * Argentum (see its README, `just server`) can be set in the "argentum_session" preferences under
 * [PREF_ORIGIN]. The origin is never written to logs (DiagnosticRedactor strips addresses).
 */
object ArgentumEndpoints {
    const val PREF_ORIGIN = "server_origin"

    fun origin(preferences: SharedPreferences): String =
        preferences.getString(PREF_ORIGIN, null)?.trim()?.trimEnd('/')
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: ARGENTUM_SERVER_ORIGIN

    fun socketUrl(origin: String): String =
        origin.replaceFirst(Regex("^https://"), "wss://").replaceFirst(Regex("^http://"), "ws://") + "/game"
}

/** Build the 99-card Commander library payload; Argentum receives the commander separately. */
fun argentumCommanderLibraryDeck(cardNames: Iterable<String>, commander: String): MutableMap<String, Int> {
    val deck = cardNames.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .groupingBy { it }
        .eachCount()
        .toMutableMap()
    val commanderEntry = deck.entries.firstOrNull { it.key.equals(commander.trim(), ignoreCase = true) }
    if (commanderEntry != null) {
        if (commanderEntry.value <= 1) deck.remove(commanderEntry.key)
        else deck[commanderEntry.key] = commanderEntry.value - 1
    }
    return deck
}

data class ArgentumDeckIssue(val code: String, val message: String, val cardName: String?)

data class ArgentumExampleDeck(
    val id: String,
    val name: String,
    val description: String,
    val note: String?,
    val commander: String,
    val cards: Map<String, Int>,
) {
    /** Format understood by Arcana's existing importer, with the commander in its own zone. */
    fun asImportText(): String = buildString {
        appendLine("[Commander]")
        appendLine("1 $commander")
        appendLine("[Mainboard]")
        cards.toSortedMap().forEach { (name, count) -> if (count > 0) appendLine("$count $name") }
    }
}

data class ArgentumDeckValidation(
    val valid: Boolean,
    val totalCards: Int,
    val errors: List<ArgentumDeckIssue>,
    val warnings: List<ArgentumDeckIssue>
) {
    /** Cards rejected by Argentum's registry. Scryfall matches alone do not imply support. */
    val unsupportedCards: Set<String>
        get() = errors.filter { it.code == "UNKNOWN_CARD" }.mapNotNull { it.cardName }.toSet()

    companion object {
        /** A pass with no server check (premade decks). */
        fun skipped(totalCards: Int) = ArgentumDeckValidation(valid = true, totalCards = totalCards, errors = emptyList(), warnings = emptyList())

        fun parse(source: String): ArgentumDeckValidation {
            val json = JSONObject(source)
            fun issues(key: String): List<ArgentumDeckIssue> {
                val array = json.optJSONArray(key) ?: JSONArray()
                return (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    ArgentumDeckIssue(
                        code = item.optString("code"),
                        message = item.optString("message"),
                        cardName = item.optString("cardName").takeUnless { it.isBlank() || it == "null" }
                    )
                }
            }
            return ArgentumDeckValidation(
                valid = json.optBoolean("valid", false),
                totalCards = json.optInt("totalCards", 0),
                errors = issues("errors"),
                warnings = issues("warnings")
            )
        }
    }
}

data class ArgentumTargetRef(val id: String, val name: String, val type: String, val ownerId: String? = null, val zone: String? = null, val controllerId: String? = null)

/** A spell or ability on Argentum's stack, top first. */
data class ArgentumStackItem(val id: String, val name: String, val controllerName: String, val isYours: Boolean, val imageUrl: String?, val targetNames: List<String>, val targetIds: List<String> = emptyList())

/** One line of Argentum's own game log (ClientEvent.description). */
data class ArgentumLogLine(val description: String, val type: String, val isYours: Boolean?, val groupingKey: String? = null) {
    /** Argentum's "--- Turn N ---" separators. */
    val isTurnMarker: Boolean get() = type == "turnChanged" || type == "gameRestarted"

    companion object {
        private val OWNER_KEYS = listOf("controllerId", "casterId", "playerId", "ownerId")

        /** Conservatively distinguish different targets, sources and results. Exclude only
         * delivery metadata, not any card or player identities. No source event is discarded.
         */
        private val DELIVERY_FIELDS = setOf("eventId", "eventIndex", "sequence", "timestamp", "timestampMs")
        private fun semanticEventKey(e: JSONObject): String = e.keys().asSequence()
            .filterNot { it in DELIVERY_FIELDS }.sorted()
            .joinToString(separator = "\u001F") { key -> "${key}=${e.opt(key)}" }

        /**
         * Parses `state.gameLog` (oldest first). Ownership comes from Argentum's own `isYours`
         * flag when present, else from the event's controller/caster/player matching the viewer.
         */
        fun parseAll(log: JSONArray, viewerId: String?): List<ArgentumLogLine> =
            (0 until log.length()).mapNotNull { log.optJSONObject(it) }.mapNotNull { e ->
                val text = e.optString("description").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
                val type = e.optString("type")
                val yours = when {
                    e.has("isYours") && !e.isNull("isYours") -> e.optBoolean("isYours")
                    e.has("isYourTurn") && !e.isNull("isYourTurn") -> e.optBoolean("isYourTurn")
                    viewerId.isNullOrBlank() -> null
                    else -> OWNER_KEYS.firstNotNullOfOrNull { key -> e.optString(key).takeIf { it.isNotBlank() && it != "null" } }?.let { it == viewerId }
                }
                ArgentumLogLine(text, type, yours, semanticEventKey(e))
            }
    }
}

/** Display projection of Argentum's masked state. It carries no rules logic. */
data class ArgentumTableSnapshot(
    val players: List<TablePlayer>,
    val viewingPlayer: Int,
    val activePlayer: Int,
    val turnNumber: Int,
    val phaseName: String,
    val stepName: String,
    val gameOver: Boolean,
    val legalActions: List<JSONObject>,
    val interactionEpoch: String?,
    val serverCardIds: Map<Int, String>,
    val targets: Map<String, ArgentumTargetRef>,
    /** Only the viewer's known position-zero library card, as authorized by Argentum's masked
     * ClientZone.positions. This is informational even when the viewer lacks priority; it is NOT
     * a play permission. Never guess from the first item of a partially known library zone. */
    val topLibraryCard: GameCard? = null,
    val stack: List<ArgentumStackItem> = emptyList(),
    val recentLog: List<ArgentumLogLine> = emptyList(),
    /** Argentum's whole game log for this seat, oldest first. */
    val fullLog: List<ArgentumLogLine> = emptyList(),
    val priorityPlayer: Int = -1,
    /** Argentum's Step name, e.g. DECLARE_ATTACKERS. */
    val stepKey: String = "",
    val winnerName: String? = null,
) {
    companion object {
        fun parse(state: JSONObject, legalActions: JSONArray = JSONArray(), interactionEpoch: String? = null): ArgentumTableSnapshot {
            val serverPlayers = state.optJSONArray("players") ?: JSONArray()
            val cards = state.optJSONObject("cards") ?: JSONObject()
            val zones = state.optJSONArray("zones") ?: JSONArray()
            val playerIds = (0 until serverPlayers.length()).mapNotNull { serverPlayers.optJSONObject(it)?.optString("playerId") }
            val playerTargets = (0 until serverPlayers.length()).mapNotNull { serverPlayers.optJSONObject(it) }.associate { player ->
                val id = player.optString("playerId")
                id to ArgentumTargetRef(id, player.optString("name", "Player"), "Player")
            }
            val cardTargets = buildMap {
                cards.keys().forEach { id ->
                    val card = cards.optJSONObject(id) ?: return@forEach
                    val zoneId = card.optJSONObject("zone") ?: return@forEach
                    val zoneType = zoneId.optString("zoneType").uppercase()
                    val targetType = when (zoneType) {
                        "BATTLEFIELD" -> "Permanent"
                        "STACK" -> "Spell"
                        else -> "Card"
                    }
                    // Keep Argentum's zone spelling ("Graveyard"): ChosenTarget.Card deserializes Zone by its serial name.
                    put(id, ArgentumTargetRef(
                        id, card.optString("name", "Card"), targetType,
                        zoneId.optString("ownerId"), zoneId.optString("zoneType"),
                        card.optString("controllerId").takeIf { it.isNotBlank() && it != "null" },
                    ))
                }
            }
            val displayIds = linkedMapOf<String, Int>()
            val usedIds = mutableSetOf<Int>()
            fun cardId(raw: String): Int = displayIds.getOrPut(raw) {
                var id = (raw.hashCode() and 0x7fffffff).coerceAtLeast(1)
                while (!usedIds.add(id)) id = if (id == Int.MAX_VALUE) 1 else id + 1
                id
            }
            val zoneCards = mutableMapOf<Pair<String, String>, MutableList<GameCard>>()
            val zoneSizes = mutableMapOf<Pair<String, String>, Int>()
            val knownViewerTop = mutableMapOf<String, String>()
            for (i in 0 until zones.length()) {
                val zone = zones.optJSONObject(i) ?: continue
                val zoneId = zone.optJSONObject("zoneId") ?: continue
                val zoneType = zoneId.optString("zoneType").uppercase()
                val owner = zoneId.optString("ownerId")
                val key = owner to zoneType
                zoneSizes[key] = zone.optInt("size", 0)
                val ids = zone.optJSONArray("cardIds") ?: JSONArray()
                // Argentum sends only individually visible library cards, with their real
                // indices in positions. CardIds[0] could be position 5, not the top.
                // Never accept an absent position list or an opponent's library as a look.
                if (zoneType == "LIBRARY" && owner == state.optString("viewingPlayerId")) {
                    val positions = zone.optJSONArray("positions")
                    if (positions != null) for (j in 0 until minOf(ids.length(), positions.length())) {
                        if (positions.optInt(j, -1) == 0 && cards.has(ids.optString(j))) {
                            knownViewerTop[owner] = ids.optString(j)
                            break
                        }
                    }
                }
                val output = zoneCards.getOrPut(key) { mutableListOf() }
                for (j in 0 until ids.length()) {
                    val rawId = ids.optString(j)
                    val card = cards.optJSONObject(rawId) ?: continue
                    val typeLine = card.optString("typeLine", "CARD")
                    val colorNames = card.optJSONArray("colors")
                    val color = (0 until (colorNames?.length() ?: 0)).asSequence()
                        .map { colorNames!!.optString(it).uppercase() }.mapNotNull(::colorFor)
                        .firstOrNull() ?: Color(0xFFB8C4BE)
                    val countersJson = card.optJSONObject("counters") ?: JSONObject()
                    val counters = buildMap {
                        countersJson.keys().forEach { keyName -> put(counterLabel(keyName), countersJson.optInt(keyName)) }
                    }
                    // Only creatures show power/toughness (a non-creature with stats, like an uncrewed Vehicle, doesn't).
                    fun JSONArray?.names() = (0 until (this?.length() ?: 0)).map { this!!.optString(it) }
                    val creatureNow = (card.optJSONArray("cardTypes").names() + card.optJSONArray("grantedCardTypes").names()).any { it.equals("CREATURE", true) } ||
                        typeLine.contains("Creature", ignoreCase = true)
                    output += GameCard(
                        id = cardId(rawId),
                        name = card.optString("name", "Card"),
                        type = typeLine,
                        color = color,
                        tapped = card.optBoolean("isTapped"),
                        imageUrl = card.optString("imageUri").takeUnless { it.isBlank() || it == "null" } ?: artByName(card.optString("name"), card.optBoolean("isToken")),
                        isToken = card.optBoolean("isToken"),
                        counters = counters,
                        isFaceDown = card.optBoolean("isFaceDown"),
                        oracleText = card.optString("oracleText").takeUnless { it.isBlank() || it == "null" },
                        manaValue = card.optDouble("manaValue").takeIf { card.has("manaValue") },
                        artCropUrl = card.optString("imageUri").takeUnless { it.isBlank() || it == "null" },
                        manaCost = card.optString("manaCost").takeUnless { it.isBlank() || it == "null" },
                        power = card.opt("power")?.toString()?.takeUnless { it == "null" || !creatureNow },
                        toughness = card.opt("toughness")?.toString()?.takeUnless { it == "null" || !creatureNow },
                        keywords = card.optJSONArray("keywords")?.let { values ->
                            (0 until values.length()).map { values.optString(it) }.filter(String::isNotBlank)
                        }.orEmpty(),
                        chosenCreatureType = card.optString("chosenCreatureType").takeUnless { it.isBlank() || it == "null" },
                        isCommander = card.optBoolean("isCommander"),
                        combatRole = when { card.optBoolean("isAttacking") -> "ATTACKING"; card.optBoolean("isBlocking") -> "BLOCKING"; else -> null },
                        damage = card.optInt("damage", 0),
                        summoningSick = card.optBoolean("hasSummoningSickness") && typeLine.contains("Creature", ignoreCase = true)
                    )
                }
            }
            fun zone(owner: String, type: String) = zoneCards[owner to type].orEmpty().toList()
            val projected = (0 until serverPlayers.length()).mapNotNull { index ->
                val source = serverPlayers.optJSONObject(index) ?: return@mapNotNull null
                val serverId = source.optString("playerId")
                fun hiddenBacks(zoneType: String, current: List<GameCard>, count: Int): List<GameCard> =
                    if (current.size >= count) current else current + List(count - current.size) { offset ->
                        GameCard(id = cardId("hidden:$serverId:$zoneType:$offset"), name = "Hidden card", type = "Unknown", color = Color(0xFF51615A))
                    }
                val battlefield = playerIds.flatMap { owner -> zone(owner, "BATTLEFIELD").filter { card ->
                    (cards.optJSONObject(displayIds.entries.firstOrNull { it.value == card.id }?.key.orEmpty())?.optString("controllerId") ?: owner) == serverId
                } }
                val handVisible = zone(serverId, "HAND")
                val hand = hiddenBacks("HAND", handVisible, source.optInt("handSize", handVisible.size))
                val library = List(source.optInt("librarySize", zoneSizes[serverId to "LIBRARY"] ?: 0)) { offset ->
                    GameCard(id = cardId("hidden:$serverId:LIBRARY:$offset"), name = "Hidden card", type = "Unknown", color = Color(0xFF51615A))
                }
                val graveyard = zone(serverId, "GRAVEYARD")
                val exile = zone(serverId, "EXILE")
                val command = zone(serverId, "COMMAND")
                val commander = command.firstOrNull() ?: (battlefield + hand + graveyard + exile).firstOrNull { it.isCommander }
                val pool = source.optJSONObject("manaPool")?.let { mana ->
                    buildMap { mana.keys().forEach { put(it, mana.optInt(it)) } }
                }.orEmpty()
                val commanderDamage = source.optJSONArray("commanderDamage")?.let { damage ->
                    buildMap {
                        for (damageIndex in 0 until damage.length()) {
                            val entry = damage.optJSONObject(damageIndex) ?: continue
                            put(entry.optString("commanderName", entry.optString("commanderId")), entry.optInt("amount"))
                        }
                    }
                }.orEmpty()
                TablePlayer(
                    name = source.optString("name", "PLAYER"), id = serverId, isOnline = true,
                    commanderName = commander?.name, commanderCard = commander,
                    deckLoaded = true, openingHandKept = true, life = source.optInt("life", 40), manaPool = pool,
                    commanderDamageReceived = commanderDamage,
                    library = library, hand = hand, battlefield = battlefield, graveyard = graveyard,
                    exile = exile, revealed = emptyList(), isEliminated = source.optBoolean("hasLost")
                )
            }
            val nameById = projected.associate { it.id to it.name }
            val viewerId = state.optString("viewingPlayerId")
            fun targetName(t: JSONObject): String? {
                val id = t.optString("playerId").ifBlank { t.optString("entityId") }.ifBlank { t.optString("spellEntityId") }.ifBlank { t.optString("cardId") }
                return nameById[id] ?: cards.optJSONObject(id)?.optString("name")
            }
            val stackIds = (0 until zones.length()).mapNotNull { zones.optJSONObject(it) }
                .firstOrNull { it.optJSONObject("zoneId")?.optString("zoneType").equals("Stack", ignoreCase = true) }
                ?.optJSONArray("cardIds")
            val stack = (0 until (stackIds?.length() ?: 0)).mapNotNull { i ->
                val id = stackIds!!.optString(i)
                val card = cards.optJSONObject(id) ?: return@mapNotNull null
                val controller = card.optString("controllerId")
                val chosen = card.optJSONArray("targets")
                ArgentumStackItem(
                    id = id, name = card.optString("name", "Spell"), controllerName = nameById[controller] ?: "Player", isYours = controller == viewerId,
                    imageUrl = card.optString("imageUri").takeUnless { it.isBlank() || it == "null" },
                    targetNames = (0 until (chosen?.length() ?: 0)).mapNotNull { chosen!!.optJSONObject(it)?.let(::targetName) },
                    targetIds = (0 until (chosen?.length() ?: 0)).mapNotNull { pos ->
                        chosen!!.optJSONObject(pos)?.let { t ->
                            listOf("playerId", "entityId", "spellEntityId", "cardId")
                                .firstNotNullOfOrNull { key -> t.optString(key).takeIf { it.isNotBlank() && it != "null" } }
                        }
                    },
                )
            }.asReversed() // top of the stack first
            val fullLog = ArgentumLogLine.parseAll(state.optJSONArray("gameLog") ?: JSONArray(), viewerId)
            val recentLog = fullLog.takeLast(12).asReversed()
            val activeId = state.optString("activePlayerId")
            val phase = state.optString("currentPhase", "MAIN").replace('_', ' ')
            val step = state.optString("currentStep", "MAIN").replace('_', ' ')
            return ArgentumTableSnapshot(
                players = projected,
                viewingPlayer = playerIds.indexOf(state.optString("viewingPlayerId")).coerceAtLeast(0),
                activePlayer = playerIds.indexOf(activeId).coerceAtLeast(0),
                turnNumber = state.optInt("turnNumber", 1),
                phaseName = phase,
                stepName = step,
                gameOver = state.optBoolean("isGameOver"),
                legalActions = (0 until legalActions.length()).mapNotNull { legalActions.optJSONObject(it) },
                interactionEpoch = interactionEpoch?.takeIf(String::isNotBlank),
                serverCardIds = displayIds.entries.associate { it.value to it.key },
                targets = playerTargets + cardTargets,
                topLibraryCard = knownViewerTop[viewerId]?.let { rawId ->
                    zoneCards[viewerId to "LIBRARY"]?.firstOrNull { it.id == displayIds[rawId] }
                },
                stack = stack,
                recentLog = recentLog,
                fullLog = fullLog,
                priorityPlayer = playerIds.indexOf(state.optString("priorityPlayerId")),
                stepKey = state.optString("currentStep"),
                winnerName = state.optString("winnerId").takeIf { it.isNotBlank() && it != "null" }?.let { nameById[it] ?: "Unknown" },
            )
        }

        private fun colorFor(name: String): Color? = when (name) {
            "WHITE", "W" -> Color(0xFFF3EAD2)
            "BLUE", "U" -> Color(0xFF4E93C8)
            "BLACK", "B" -> Color(0xFF69636F)
            "RED", "R" -> Color(0xFFC85450)
            "GREEN", "G" -> Color(0xFF4F9363)
            "COLORLESS", "C" -> Color(0xFF87918D)
            else -> null
        }
    }
}

/**
 * Small HTTP adapter over Argentum's authoritative card/deck APIs. Network failure is surfaced to
 * the caller; it must never turn into a permissive "supported" result.
 */
class ArgentumDeckApi(
    private val origin: String = ARGENTUM_SERVER_ORIGIN,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    /**
     * Fetch Argentum's own Commander examples and retain only the lists the live Argentum
     * validator accepts. The examples endpoint includes the commander in `cards`; validation
     * expects it separately in the command zone, so remove exactly one copy before checking.
     */
    fun commanderExamples(): List<ArgentumExampleDeck> {
        val request = Request.Builder().url("${origin.trimEnd('/')}/api/decks/examples").get()
            .header("Accept", "application/json").build()
        val examples = http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Could not load Argentum's premade decks (HTTP ${response.code}).")
            JSONArray(text)
        }
        return (0 until examples.length()).mapNotNull { examples.optJSONObject(it) }
            .mapNotNull { item ->
                val commander = item.optString("commander").takeIf(String::isNotBlank) ?: return@mapNotNull null
                if (!item.optString("format").equals("COMMANDER", ignoreCase = true)) return@mapNotNull null
                val sourceCards = item.optJSONObject("cards") ?: return@mapNotNull null
                val cards = buildMap {
                    sourceCards.keys().forEach { name ->
                        val count = sourceCards.optInt(name, 0)
                        if (count > 0) put(name, count)
                    }
                }
                val total = cards.values.sum()
                if (total != 100 || (cards[commander] ?: 0) < 1) return@mapNotNull null
                val library = cards.toMutableMap().apply {
                    val remaining = (get(commander) ?: 0) - 1
                    if (remaining > 0) put(commander, remaining) else remove(commander)
                }
                val validation = validate(library, commander, "COMMANDER")
                if (!validation.valid) return@mapNotNull null
                ArgentumExampleDeck(
                    id = item.optString("id"),
                    name = item.optString("name").ifBlank { commander },
                    description = item.optString("description"),
                    note = item.optString("note").takeIf(String::isNotBlank),
                    commander = commander,
                    cards = library,
                )
            }
    }

    /** Argentum's own implemented basic lands, restricted to the commander's color identity. */
    fun supportedBasicLands(commander: String): List<String> {
        val request = Request.Builder().url("${origin.trimEnd('/')}/api/cards").get()
            .header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Could not load Argentum's supported card catalog (HTTP ${response.code}).")
            val catalog = JSONArray(text)
            val commanderCard = (0 until catalog.length()).asSequence().mapNotNull(catalog::optJSONObject)
                .firstOrNull { it.optString("name").equals(commander, ignoreCase = true) }
            val identity = commanderCard?.optJSONArray("colorIdentity")?.let { colors ->
                (0 until colors.length()).map { colors.optString(it).uppercase() }.toSet()
            }.orEmpty()
            val basicNameByColor = mapOf(
                "WHITE" to "Plains", "W" to "Plains", "BLUE" to "Island", "U" to "Island",
                "BLACK" to "Swamp", "B" to "Swamp", "RED" to "Mountain", "R" to "Mountain",
                "GREEN" to "Forest", "G" to "Forest", "COLORLESS" to "Wastes", "C" to "Wastes"
            )
            val allowed = identity.mapNotNull(basicNameByColor::get).toSet().ifEmpty { setOf("Wastes") }
            return (0 until catalog.length()).mapNotNull(catalog::optJSONObject)
                .filter { it.optBoolean("basicLand") && it.optString("name") in allowed }
                .map { it.optString("name") }.distinct().sorted()
        }
    }

    /** Resolve commander-eligible candidates and their legal basic replacements in one catalog read. */
    fun compatibleCommanderOptions(candidates: Collection<String>): Map<String, List<String>> {
        if (candidates.isEmpty()) return emptyMap()
        val wanted = candidates.map(String::trim).filter(String::isNotEmpty).distinct()
        val request = Request.Builder().url("${origin.trimEnd('/')}/api/cards").get()
            .header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Could not load Argentum's supported card catalog (HTTP ${response.code}).")
            val catalog = JSONArray(text)
            val cards = (0 until catalog.length()).mapNotNull(catalog::optJSONObject)
            val byName = cards.associateBy { it.optString("name").lowercase() }
            val basicNameByColor = mapOf(
                "WHITE" to "Plains", "BLUE" to "Island", "BLACK" to "Swamp",
                "RED" to "Mountain", "GREEN" to "Forest", "COLORLESS" to "Wastes"
            )
            val basics = cards.filter { it.optBoolean("basicLand") }
            return wanted.mapNotNull { candidate ->
                val card = byName[candidate.lowercase()]?.takeIf { it.optBoolean("canBeCommander") } ?: return@mapNotNull null
                val identity = card.optJSONArray("colorIdentity")?.let { colors ->
                    (0 until colors.length()).map { colors.optString(it).uppercase() }.toSet()
                }.orEmpty()
                val allowed = identity.mapNotNull(basicNameByColor::get).toSet().ifEmpty { setOf("Wastes") }
                card.optString("name") to basics.filter { it.optString("name") in allowed }
                    .map { it.optString("name") }.distinct().sorted()
            }.toMap()
        }
    }

    fun validate(deckList: Map<String, Int>, commander: String? = null, format: String? = null): ArgentumDeckValidation {
        require(deckList.isNotEmpty()) { "Choose a deck before checking Argentum compatibility." }
        val body = JSONObject().apply {
            put("deckList", JSONObject().apply { deckList.forEach { (name, count) -> put(name, count) } })
            format?.takeIf(String::isNotBlank)?.let { put("format", it) }
            commander?.takeIf(String::isNotBlank)?.let { put("commander", it) }
        }
        val request = Request.Builder()
            .url("${origin.trimEnd('/')}/api/decks/validate")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(responseText).optString("message") }.getOrNull().orEmpty()
                throw IllegalStateException(detail.ifBlank { "Argentum deck validation failed (HTTP ${response.code})." })
            }
            return ArgentumDeckValidation.parse(responseText)
        }
    }

    /** Light reachability check for Help & diagnostics; returns the round trip in milliseconds. */
    fun ping(): Long {
        val started = System.nanoTime()
        val request = Request.Builder().url("${origin.trimEnd('/')}/api/players/online").get()
            .header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Argentum answered with HTTP ${response.code}.")
        }
        return (System.nanoTime() - started) / 1_000_000
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * PAUSED means the connection dropped while Arcana could not auto-reconnect (app in background,
 * or relaunched). Argentum resumes a game as soon as a client reconnects with its token, so the
 * socket stays down until the player explicitly chooses Resume ([ArgentumGameSocket.retryNow]).
 */
enum class ArgentumConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, PAUSED, REPLACED }

/**
 * Native OkHttp implementation of Argentum's `/game` protocol. The client only forwards server
 * messages; it does not calculate legal actions or mutate game state. Actions must be sent exactly
 * as Argentum supplied them, with the current interaction epoch.
 */
class ArgentumGameSocket(
    private val preferences: SharedPreferences,
    private val onStatus: (ArgentumConnectionStatus) -> Unit,
    private val onMessage: (JSONObject) -> Unit,
    private val onError: (String) -> Unit,
    private val url: String = ARGENTUM_GAME_SOCKET,
    /** Consulted when the socket drops; false pauses instead of reconnecting (see PAUSED). */
    private val canAutoReconnect: () -> Boolean = { true },
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    private val main = Handler(Looper.getMainLooper())
    private val enabled = AtomicBoolean(false)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var playerName: String = "PLAYER"
    @Volatile private var retryCount = 0
    @Volatile private var status = ArgentumConnectionStatus.DISCONNECTED
    @Volatile private var manualRetry: Runnable? = null
    /** Offline games: an in-app Argentum host stands in for the server (same messages both ways). */
    @Volatile var localHostFactory: (((JSONObject) -> Unit) -> com.arcanatable.engine.LocalArgentumHost)? = null
    @Volatile private var localHost: com.arcanatable.engine.LocalArgentumHost? = null
    /** Wi-Fi guest: another phone on this network runs the engine; messages go to it and back. */
    @Volatile private var lanGuest: ArgentumGuestLink? = null
    val isOffline: Boolean get() = localHost != null || localHostFactory != null || lanGuest != null
    /** The engine running on this phone, once connected (offline or Wi-Fi host). */
    val runningLocalHost: com.arcanatable.engine.LocalArgentumHost? get() = localHost

    /** Plays through a Wi-Fi host instead of the server. Call [connect] afterwards. */
    fun attachLanGuest(guest: ArgentumGuestLink) {
        lanGuest = guest
        guest.onMessage = { message -> main.post { if (enabled.get()) onMessage(message) } }
        guest.onClosed = { main.post { if (enabled.get() && lanGuest === guest) { report("Lost the connection to the host phone."); setStatus(ArgentumConnectionStatus.DISCONNECTED) } } }
    }

    fun connect(name: String) {
        playerName = name.trim().ifBlank { "PLAYER" }.take(32)
        localHostFactory?.let { factory ->
            if (localHost != null) return
            enabled.set(true)
            localHost = factory { message -> main.post { if (enabled.get()) onMessage(message) } }
            setStatus(ArgentumConnectionStatus.CONNECTED)
            localHost?.receive(JSONObject().put("type", "connect").put("playerName", playerName))
            return
        }
        lanGuest?.let { guest ->
            enabled.set(true)
            setStatus(ArgentumConnectionStatus.CONNECTED)
            guest.send(JSONObject().put("type", "connect").put("playerName", playerName))
            return
        }
        if (enabled.getAndSet(true) && status != ArgentumConnectionStatus.DISCONNECTED) return
        retryCount = 0
        openSocket()
    }

    /** Player-initiated reconnect; also the only way out of PAUSED. */
    fun retryNow() {
        if (status == ArgentumConnectionStatus.REPLACED || localHost != null || lanGuest != null) return
        enabled.set(true)
        manualRetry?.let(main::removeCallbacks)
        retryCount = 0
        socket?.cancel()
        openSocket()
    }

    fun send(message: JSONObject): Boolean {
        localHost?.let { host -> if (!enabled.get()) return false; host.receive(JSONObject(message.toString())); return true }
        lanGuest?.let { guest -> return enabled.get() && guest.send(message) }
        return enabled.get() && status == ArgentumConnectionStatus.CONNECTED && socket?.send(message.toString()) == true
    }

    fun send(type: String, fields: JSONObject = JSONObject()): Boolean =
        send(JSONObject(fields.toString()).put("type", type))

    fun createCommanderPracticeLobby(vsAi: Boolean = true): Boolean = send(
        "createQuickGameLobby",
        JSONObject().put("vsAi", vsAi).put("format", "COMMANDER")
    )

    fun leaveQuickGameLobby(): Boolean = send("leaveQuickGameLobby")
    fun joinQuickGameLobby(lobbyId: String): Boolean = send("joinQuickGameLobby", JSONObject().put("lobbyId", lobbyId))
    fun concede(): Boolean = send("concede")

    fun submitCommanderDeck(deckList: Map<String, Int>, commander: String): Boolean = send(
        "submitQuickGameLobbyDeck",
        JSONObject().put("deckList", JSONObject().apply { deckList.forEach { (name, count) -> put(name, count) } })
            .put("commander", commander)
    )

    fun markReady(): Boolean = send("setQuickGameLobbyReady", JSONObject().put("ready", true))
    fun keepHand(): Boolean = send("keepHand")
    fun mulligan(): Boolean = send("mulligan")
    fun chooseBottomCards(cardIds: List<String>): Boolean = send("chooseBottomCards", JSONObject().put("cardIds", JSONArray(cardIds)))
    fun requestResync(): Boolean = send("requestResync")
    /** Argentum undoes the player's last non-respondable action (it reports `undoAvailable`). */
    fun requestUndo(): Boolean = send("requestUndo")

    fun submitLegalAction(action: JSONObject, interactionEpoch: String): Boolean = send(
        "submitAction",
        JSONObject().put("action", JSONObject(action.toString())).put("interactionEpoch", interactionEpoch)
    )

    fun close() {
        enabled.set(false)
        localHost?.shutdown()
        localHost = null
        localHostFactory = null
        lanGuest?.close()
        lanGuest = null
        manualRetry?.let(main::removeCallbacks)
        manualRetry = null
        socket?.close(1000, "Arcana closed Argentum connection")
        socket = null
        setStatus(ArgentumConnectionStatus.DISCONNECTED)
    }

    private fun openSocket() {
        if (!enabled.get()) return
        manualRetry?.let(main::removeCallbacks)
        manualRetry = null
        setStatus(ArgentumConnectionStatus.CONNECTING)
        val request = runCatching { Request.Builder().url(url).build() }.getOrElse {
            setStatus(ArgentumConnectionStatus.DISCONNECTED)
            report("Argentum server address is invalid.")
            return
        }
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!enabled.get()) { webSocket.close(1000, "Arcana stopped connection"); return }
                retryCount = 0
                val token = preferences.getString(PREF_TOKEN, null)
                val connect = JSONObject().put("type", "connect").put("playerName", playerName)
                token?.takeIf(String::isNotBlank)?.let { connect.put("token", it) }
                if (!webSocket.send(connect.toString())) report("Could not send Argentum sign-in message.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (socket !== webSocket) return
                val message = runCatching { JSONObject(text) }.getOrElse {
                    report("Argentum sent a message Arcana could not read.")
                    return
                }
                when (message.optString("type")) {
                    "connected", "reconnected" -> {
                        message.optString("token").takeIf(String::isNotBlank)
                            ?.let { preferences.edit().putString(PREF_TOKEN, it).apply() }
                        setStatus(ArgentumConnectionStatus.CONNECTED)
                    }
                    "sessionReplaced" -> {
                        enabled.set(false)
                        setStatus(ArgentumConnectionStatus.REPLACED)
                    }
                    "error" -> report(message.optString("message", "Argentum returned an error."))
                }
                main.post { if (enabled.get() || message.optString("type") == "sessionReplaced") onMessage(message) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // Ignore late callbacks from a socket that retryNow() already replaced.
                if (socket !== webSocket) return
                socket = null
                if (enabled.get()) scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket !== webSocket) return
                socket = null
                if (enabled.get()) {
                    setStatus(ArgentumConnectionStatus.DISCONNECTED)
                    report(response?.let { "Argentum connection failed (HTTP ${it.code})." }
                        ?: "Argentum connection failed. Check the network and retry.")
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (!enabled.get() || status == ArgentumConnectionStatus.REPLACED || manualRetry != null) return
        if (!canAutoReconnect()) {
            setStatus(ArgentumConnectionStatus.PAUSED)
            return
        }
        val delayMs = min(1_000L shl retryCount.coerceAtMost(5), 30_000L)
        retryCount++
        setStatus(ArgentumConnectionStatus.DISCONNECTED)
        manualRetry = Runnable {
            manualRetry = null
            // The app may have gone to the background while waiting; re-check before resuming.
            if (canAutoReconnect()) openSocket() else setStatus(ArgentumConnectionStatus.PAUSED)
        }.also { main.postDelayed(it, delayMs) }
    }

    private fun setStatus(value: ArgentumConnectionStatus) {
        status = value
        main.post { onStatus(value) }
    }

    private fun report(value: String) { main.post { onError(value) } }

    companion object {
        private const val PREF_TOKEN = "argentum_session_token"
    }
}


/** Argentum's counter names ("PLUS_ONE_PLUS_ONE", "LOYALTY") as players say them ("+1/+1", "Loyalty"). */
internal fun counterLabel(raw: String): String = when (raw.uppercase()) {
    "PLUS_ONE_PLUS_ONE" -> "+1/+1"
    "MINUS_ONE_MINUS_ONE" -> "-1/-1"
    "PLUS_ONE_PLUS_ZERO" -> "+1/+0"
    "PLUS_ZERO_PLUS_ONE" -> "+0/+1"
    "PLUS_ONE_PLUS_TWO" -> "+1/+2"
    "PLUS_TWO_PLUS_TWO" -> "+2/+2"
    "MINUS_ZERO_MINUS_ONE" -> "-0/-1"
    "MINUS_ONE_MINUS_ZERO" -> "-1/-0"
    "MINUS_TWO_MINUS_TWO" -> "-2/-2"
    else -> if (raw.any { it == '_' } || raw == raw.uppercase()) raw.lowercase().split('_').filter { it.isNotBlank() }.joinToString(" ").replaceFirstChar { it.uppercase() } else raw
}

/**
 * Art for a card that came with none (Arcana's own custom cards): Scryfall's image for that exact
 * name. Null for tokens and hidden cards, which Scryfall can't look up by name.
 */
internal fun artByName(name: String?, token: Boolean): String? {
    val clean = name?.trim()?.takeIf { it.isNotBlank() && it != "null" && it != "Card" && it != "Hidden card" } ?: return null
    if (token) return null
    return "https://api.scryfall.com/cards/named?exact=" + java.net.URLEncoder.encode(clean.substringBefore(" // "), "UTF-8") + "&format=image&version=normal"
}
