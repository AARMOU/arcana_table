package com.arcanatable.engine

import com.arcanatable.engine.costs.CounterRemovalCost
import com.wingedsheep.ai.engine.AIPlayer
import com.arcanatable.engine.ai.AiCascadeThrottle
import com.arcanatable.engine.ai.AiDifficulty
import com.arcanatable.engine.ai.AiPriorityChoice
import com.arcanatable.engine.ai.OfflineAiPolicy
import com.arcanatable.OfflineCheckpointStore
import com.arcanatable.engine.priority.OfflineHumanAutoPass
import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.Concede
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.core.UndoCheckpointAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.core.PlayCardDecision
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.state.components.combat.AttackersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.combat.BlockersDeclaredThisCombatComponent
import com.wingedsheep.engine.view.ClientEvent
import com.wingedsheep.engine.view.ClientEventTransformer
import com.wingedsheep.engine.view.ClientGameState
import com.wingedsheep.engine.view.ClientStateTransformer
import com.wingedsheep.engine.view.DecisionMasker
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.engine.view.LegalActionInfo
import com.wingedsheep.engine.view.Visibility
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.util.concurrent.Executors


/** One seat of an offline game: who sits there and what they play. */
/** One seat. [difficulty] only matters when the AI plays the seat. */
data class LocalSeat(
    val name: String,
    val deck: List<String>,
    val commander: String?,
    val difficulty: AiDifficulty = AiDifficulty.DEFAULT,
)

/**
 * Offline Argentum: the real rules engine and AI, run inside Arcana behind the same `/game`
 * messages the server sends (connected, quickGameLobbyState, gameStarted, stateUpdate, gameOver,
 * error). Arcana's table code is unchanged; only the transport differs.
 *
 * Mirrors the server's per-seat pipeline (GameSession.createStateUpdate): masked ClientGameState,
 * enriched legal actions, masked pending decision, client events, the accumulated game log,
 * Argentum's auto-pass for each person's seat, and nextStopPoint. All engine work runs on one
 * large-stack thread; [emit] is called from that thread.
 *
 * Like the server, it also runs the London mulligan (mulliganDecision / chooseBottomCards /
 * mulliganComplete), offers undo with the server's checkpoint policy, and sends a state update after
 * every AI action and every step change, [paceMs] apart, so the AI's turn plays out on the table
 * instead of jumping straight to your next decision.
 *
 * Wi-Fi play: [setGuest] turns any existing opponent seat into a remote person; each guest
 * receives a separately masked Argentum view and sends messages with its stable seat ID.
 * The historical two-argument overload still registers the second seat for 1v1 callers.
 *
 * Up to four players: [extraOpponents] predeclare seats 3 and 4. Unclaimed seats use AI;
 * a lobby can register remote people for these seats before starting the engine.
 */
class LocalArgentumHost(
    private val registry: CardRegistry,
    private val human: LocalSeat,
    opponent: LocalSeat,
    private val paceMs: Long = 600,
    extraOpponents: List<LocalSeat> = emptyList(),
    private val resumeCheckpoint: String? = null,
    /** Optional reproducible shuffle for scenario tests; normal games keep the engine's randomness. */
    private val gameSeed: Long? = null,
    private val tutorialPassiveOpponent: Boolean = false,
    private val emit: (JSONObject) -> Unit,
) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(null, r, "argentum-local", 256L shl 20) }
    private val services = EngineServices(registry)
    private val processor = ActionProcessor(services)
    // AI actions never receive or use an undo checkpoint. Preserve the validated transition
    // while omitting the unconsumed UndoPolicyComputer pass for AI turns.
    // Human actions retain the original processor and full undo behavior.
    private val aiResolutionProcessor = ActionProcessor(services, computeUndo = false)
    private val initializer = GameInitializer(registry, services.printingRegistry)
    private val transformer = ClientStateTransformer(registry, predicateEvaluator = PredicateEvaluator(cardRegistry = null))
    private val enumerator = LegalActionEnumerator(registry, services.manaSolver, services.costCalculator, services.predicateEvaluator, services.conditionEvaluator, services.turnManager)
    private val enricher = LegalActionEnricher(services.manaSolver, registry)
    private val visibility = Visibility(registry, conditionEvaluator = services.conditionEvaluator)
    private val masker = DecisionMasker(visibility)
    private val autoPass = AutoPassManager(registry)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true; classDiscriminator = "type"; serializersModule = engineSerializersModule }

    private var opponent: LocalSeat = opponent
    private var state: GameState? = null
    private val humanId: EntityId = HUMAN_ID
    private val aiId: EntityId = AI_ID
    private val extraSeats: List<Pair<EntityId, LocalSeat>> = extraOpponents.take(2).mapIndexed { i, seat -> EntityId("offline-ai-${i + 2}") to seat }
    private val allSeats: List<EntityId> get() = listOf(humanId, aiId) + extraSeats.map { it.first }
    // One AI per computer seat (the second seat has none while a Wi-Fi guest sits there).
    private var ais: Map<EntityId, AIPlayer> = emptyMap()
    // AI seats skip repeat searches over copies of the same trigger (reset every host burst).
    private val aiThrottle = AiCascadeThrottle()
    // Only the serialized engine worker mutates either map. Each remote seat has its own
    // private outgoing channel, never a shared guest broadcast of masked game state.
    private val guestEmits = mutableMapOf<EntityId, (JSONObject) -> Unit>()
    private val guestSeatOverrides = mutableMapOf<EntityId, LocalSeat>()
    private val guestQuietOpponentTurns = mutableMapOf<EntityId, Boolean>()
    // Sticky for a live network match: disconnecting the last guest must not turn
    // it into a saveable offline-AI game by accident. Readable outside the worker.
    @Volatile private var networkSession = false
    private val gameLogs = mutableMapOf<EntityId, MutableList<ClientEvent>>()
    // Final engine result events must survive empty-event resyncs and terminal follow-up messages.
    private var lastGameOutcomeEvents: List<GameEvent> = emptyList()
    private val observedLossEvents = mutableListOf<com.wingedsheep.engine.core.PlayerLostEvent>()
    // Intermediate progress updates usually contain no new visible log entries. Keep their
    // immutable wire snapshot until the underlying list actually changes; do not discard events.
    private data class LogSnapshot(val source: MutableList<ClientEvent>, val size: Int, val value: List<ClientEvent>)
    private val logSnapshots = mutableMapOf<EntityId, LogSnapshot>()
    private fun stableLog(seat: EntityId, log: MutableList<ClientEvent>): List<ClientEvent> {
        val saved = logSnapshots[seat]
        if (saved != null && saved.source === log && saved.size == log.size) return saved.value
        return log.toList().also { logSnapshots[seat] = LogSnapshot(log, log.size, it) }
    }
    private var version = 0L

    // Legal actions for the last state they were worked out for, per seat (same object = same game
    // moment), so auto-pass and the state update don't enumerate them twice.
    private val legalCache = mutableMapOf<EntityId, Pair<GameState, List<LegalActionInfo>>>()
    // When the message being handled started, for the "engineMs" diagnostic.
    @Volatile private var handleStartNanos = 0L
    private val phaseNanos = mutableMapOf<String, Long>()
    private val diagnosticCounts = mutableMapOf<String, Long>()
    private fun countDiagnostic(name: String) {
        diagnosticCounts[name] = (diagnosticCounts[name] ?: 0L) + 1L
    }

    private inline fun <T> timed(phase: String, work: () -> T): T {
        val started = System.nanoTime()
        try { return work() } finally {
            phaseNanos[phase] = (phaseNanos[phase] ?: 0L) + System.nanoTime() - started
        }
    }
    // Per seat, the epoch of the last update they could act on. A submit made against an older one (a
    // tap queued while the engine was still thinking) is turned down instead of applied to a newer state.
    private val interactiveEpochs = mutableMapOf<EntityId, String>()

    // Undo, as Argentum's GameSession does it: one checkpoint, owned by the player whose action set it.
    private data class Checkpoint(val state: GameState, val logSizes: Map<EntityId, Int>, val owner: EntityId)
    private var undoCheckpoint: Checkpoint? = null
    private var preCombatCheckpoint: Checkpoint? = null
    private var mulliganPhase = false

    // Shock lands put onto the battlefield by an effect (a fetch land, reanimation): Argentum enters
    // them tapped without asking. Offline, Arcana asks "pay N life to untap it?" right after.
    private data class ShockOffer(val land: EntityId, val name: String, val life: Int, val player: EntityId)
    private val shockOffers = ArrayDeque<ShockOffer>()

    /**
     * "Don't stop for AI spells": on an opponent's turn, pass over their spells and abilities unless
     * one targets you or something you control. Blocks and prompts still stop. Host seat only.
     */
    var quietOpponentTurns: Boolean = false

    private val ownStackResponders = mutableSetOf<EntityId>()
    /** Opt-in, per-human-seat priority stops; never applied to the AI. */
    var respondToOwnStack: Boolean
        get() = humanId in ownStackResponders
        set(enabled) { if (enabled) ownStackResponders.add(humanId) else ownStackResponders.remove(humanId) }

    val isWifi: Boolean get() = networkSession

    private fun people(): List<EntityId> = listOf(humanId) + allSeats.drop(1).filter(guestEmits::containsKey)
    private fun isPerson(id: EntityId?): Boolean = id == humanId || (id != null && guestEmits.containsKey(id))
    private fun seatOf(id: EntityId): LocalSeat = when (id) {
        humanId -> human
        aiId -> opponent
        else -> guestSeatOverrides[id] ?: extraSeats.firstOrNull { it.first == id }?.second
            ?: error("Unrecognized seat: $id")
    }
    private fun emitTo(seat: EntityId, message: JSONObject) {
        if (seat == humanId) emit(message) else guestEmits[seat]?.invoke(message)
    }
    private fun logFor(seat: EntityId) = gameLogs.getOrPut(seat) { mutableListOf() }

    fun receive(message: JSONObject, from: EntityId = HUMAN_ID) = worker.execute {
        handleStartNanos = System.nanoTime()
        phaseNanos.clear()
        diagnosticCounts.clear()
        runCatching { handle(message, from) }.onFailure { error ->
            emitTo(from, JSONObject().put("type", "error").put("code", "LOCAL_ENGINE").put("message", "Offline engine problem: ${error.javaClass.simpleName}: ${error.message?.take(200)}"))
        }
    }

    /** Compatibility entry point: a legacy Wi-Fi guest occupies seat two. */
    fun setGuest(seat: LocalSeat, guest: (JSONObject) -> Unit) = setGuest(AI_ID, seat, guest)

    /** Bind a person to any predeclared opponent seat before the game begins. */
    fun setGuest(seatId: EntityId, seat: LocalSeat, guest: (JSONObject) -> Unit) = worker.execute {
        require(state == null) { "Remote seats must join before the game starts." }
        require(seatId != humanId && seatId in allSeats) { "Unknown or host-only remote seat: $seatId" }
        if (seatId == aiId) opponent = seat
        else guestSeatOverrides[seatId] = seat
        guestEmits[seatId] = guest
        networkSession = true
        guest(JSONObject().put("type", "connected").put("playerId", seatId.value).put("token", "offline"))
    }

    fun shutdown() { worker.shutdownNow() }

    private fun handle(message: JSONObject, from: EntityId) {
        if (from != humanId && !isPerson(from)) return
        when (message.optString("type")) {
            "connect" -> emitTo(from, JSONObject().put("type", "connected").put("playerId", from.value).put("token", "offline"))
            "createQuickGameLobby" -> if (from == humanId) {
                if (resumeCheckpoint != null) restoreSavedGame(resumeCheckpoint) else startGame()
            }
            "arcanaSaveOffline" -> if (from == humanId) saveCheckpoint()
            "requestResync" -> if (state != null) resync(from)
            "refineMaxX" -> refineMaxX(message.optString("cardId"), from)
            "submitAction" -> submitAction(message, from)
            "keepHand" -> mulliganAction(KeepHand(from))
            "mulligan" -> mulliganAction(TakeMulligan(from))
            "chooseBottomCards" -> {
                val ids = message.optJSONArray("cardIds") ?: org.json.JSONArray()
                mulliganAction(BottomCards(from, (0 until ids.length()).map { EntityId(ids.getString(it)) }))
            }
            "concede" -> concede(from)
            "guestLeft" -> if (isWifi && state?.gameOver != true) {
                // Network transport (host) may name a seat; remote people may only
                // leave their own seat. Old 1v1 callers omit seatId and mean seat two.
                val departed = if (from == humanId) {
                    message.optString("seatId").takeIf(String::isNotBlank)?.let(::EntityId) ?: aiId
                } else from
                if (departed in guestEmits) {
                    // 1v1 keeps its old meaning (your friend left, you win). With three or
                    // more seats, conceding would end or stall everyone else's game, so the
                    // AI keeps playing the departed person's seat instead.
                    if (allSeats.size > 2 && !message.optBoolean("concede")) handOverToAi(departed)
                    else if (state != null) {
                        guestEmits.remove(departed)
                        guestQuietOpponentTurns.remove(departed)
                        emit(JSONObject().put("type", "error").put("code", "WIFI")
                            .put("message", "${seatOf(departed).name} left the game."))
                        concede(departed)
                    }
                }
            }
            "requestUndo" -> undo(from)
            "setRespondToOwnStack" -> {
                if (message.optBoolean("enabled")) ownStackResponders.add(from) else ownStackResponders.remove(from)
            }
            "setQuietOpponentTurns" -> {
                val enabled = message.optBoolean("enabled")
                if (from == humanId) quietOpponentTurns = enabled
                else guestQuietOpponentTurns[from] = enabled
            }
            "arcanaNudge" -> if (isWifi) {
                val target = state?.let { current ->
                    current.pendingDecision?.let { current.actorFor(it.playerId) } ?: current.priorityPlayerId
                }
                if (target != null && target != from && isPerson(target))
                    emitTo(target, JSONObject().put("type", "arcanaNudge"))
            }
            "ping" -> emitTo(from, JSONObject().put("type", "pong"))
            else -> Unit // lobby, deck and preference messages need no answer offline
        }
    }

    /**
     * Sends [seat] where the game stands again (after an online reconnect, say). During opening
     * hands that's their mulligan question, if they haven't settled yet; otherwise their full view.
     */
    private fun resync(seat: EntityId) {
        if (!mulliganPhase) { sendStateFor(seat, emptyList(), true); return }
        if (mulliganDone(seat)) return
        val mull = mulliganState(seat)
        if (mull != null && mull.hasKept) sendBottomCards(seat, mull.cardsToBottom) else sendMulliganDecision(seat)
    }

    /**
     * A remote person dropped out of a 3–4 seat game. Their seat keeps its deck and becomes an
     * AI seat, so the remaining players are never left waiting on someone who isn't there.
     * Before the game starts, removing the guest is enough: startGame gives every non-person
     * seat its own AI.
     */
    private fun handOverToAi(seat: EntityId) {
        guestEmits.remove(seat)
        guestQuietOpponentTurns.remove(seat)
        interactiveEpochs.remove(seat)
        val notice = "${seatOf(seat).name} left. The AI is playing their seat now."
        for (person in people()) emitTo(person, JSONObject().put("type", "error").put("code", "WIFI").put("message", notice))
        val current = state ?: return
        if (current.gameOver || seat !in current.activePlayers) return
        ais = ais + (seat to OfflineAiPolicy.create(registry, seat))
        if (mulliganPhase) {
            aiMulligans(seat)
            if (people().all(::mulliganDone)) finishMulligans()
            return
        }
        // A pay-life question the departed person never answered: the land stays tapped.
        shockOffers.removeAll { it.player == seat }
        // If the game was waiting on them, the new AI acts now; otherwise this returns at once.
        val events = mutableListOf<GameEvent>()
        drive(events)
        sendState(events)
    }

    private fun concede(seat: EntityId) {
        val current = state ?: return
        val result = processor.process(current, Concede(seat)).result
        state = result.state
        finishIfOver(result.events)
    }

    private fun submitAction(message: JSONObject, from: EntityId) {
        val current = state ?: return
        if (mulliganPhase) { sendError(from, "Choose your opening hand first."); return }
        val epoch = message.optString("interactionEpoch")
        if (epoch.startsWith("offline-") && epoch != interactiveEpochs[from]) { sendError(from, "The game moved on before that arrived. Nothing was done; try again."); return }
        shockOffers.firstOrNull()?.let { offer ->
            if (offer.player != from) { sendError(from, "Waiting for your opponent to answer a question."); return }
            val raw = message.getJSONObject("action")
            val response = raw.optJSONObject("response")
            if (raw.optString("type") != "SubmitDecision" || response?.optString("decisionId") != shockId(offer)) { sendError(from, "Answer the shock land question first."); sendStateFor(from, emptyList(), true); return }
            answerShock(offer, response.optBoolean("choice"))
            return
        }
        val action = json.decodeFromString(GameAction.serializer(), message.getJSONObject("action").toString())
        if (action.playerId != from) { sendError(from, "That action belongs to another seat."); return }
        val events = mutableListOf<GameEvent>()
        var processed = processor.process(current, action)
        if (processed.result.error != null && needsMana(processed.result.error!!)) {
            // Argentum's auto-pay never sacrifices Treasures; offline, Arcana does it for you.
            payWithSacrificeSources(current, action, from)?.let { (paid, paidEvents) ->
                events += paidEvents
                processed = processor.process(paid, action)
            }
        }
        val result = processed.result
        if (result.error != null) { sendError(from, friendlyError(result.error!!)); sendStateFor(from, emptyList(), true); return }
        clearOthersCheckpoint(action, from)
        applyUndoPolicy(processed.undoPolicy, action, current, from)
        state = result.state
        events += result.events
        queueShockOffers(events)
        drive(events)
        sendState(events)
    }

    /** Save only at a completed engine action boundary on this serialized worker thread.
     * Includes all hidden cards, stack continuations, per-seat logs and undo checkpoints.
     * Never send this full state to a remote/Wi-Fi participant.
     */
    private fun saveCheckpoint() {
        val current = state ?: return sendError(humanId, "No offline game is running.")
        if (isWifi || current.gameOver) return sendError(humanId, "Only an active local AI game can be saved.")
        fun saveUndo(checkpoint: Checkpoint?): JSONObject? = checkpoint?.let { point ->
            JSONObject().put("state", JSONObject(json.encodeToString(GameState.serializer(), point.state)))
                .put("owner", point.owner.value)
                .put("logSizes", JSONObject().also { sizes -> point.logSizes.forEach { (id, size) -> sizes.put(id.value, size) } })
        }
        val checkpoint = JSONObject()
            .put("schema", OfflineCheckpointStore.FORMAT)
            .put("mode", "local-ai")
            .put("seats", org.json.JSONArray(allSeats.map { OfflineCheckpointStore.seatToJson(seatOf(it)) }))
            .put("gameState", JSONObject(json.encodeToString(GameState.serializer(), current)))
            .put("version", version)
            .put("mulliganPhase", mulliganPhase)
            .put("quietOpponentTurns", quietOpponentTurns)
            .put("respondToOwnStack", respondToOwnStack)
            .put("peopleOutSent", peopleOutSent)
            .put("gameLogs", JSONObject().also { logs ->
                gameLogs.forEach { (seat, events) ->
                    logs.put(seat.value, org.json.JSONArray(json.encodeToString(ListSerializer(ClientEvent.serializer()), events)))
                }
            })
            .put("undoCheckpoint", saveUndo(undoCheckpoint) ?: JSONObject.NULL)
            .put("preCombatCheckpoint", saveUndo(preCombatCheckpoint) ?: JSONObject.NULL)
            .put("shockOffers", org.json.JSONArray(shockOffers.map { offer ->
                JSONObject().put("land", offer.land.value).put("name", offer.name)
                    .put("life", offer.life).put("player", offer.player.value)
            }))
        emitTo(humanId, JSONObject().put("type", "offlineCheckpoint").put("checkpoint", checkpoint))
    }

    /** Restore an exact unmasked engine snapshot, never a projected ClientGameState. */
    private fun restoreSavedGame(raw: String) {
        require(!isWifi) { "Wi-Fi games are hosted by their original device." }
        val record = JSONObject(raw)
        require(record.optInt("schema") == OfflineCheckpointStore.FORMAT &&
            record.optString("mode") == "local-ai") { "Unsupported checkpoint format." }
        val savedSeats = OfflineCheckpointStore.savedSeats(record)
        require(savedSeats == allSeats.map(::seatOf)) { "Checkpoint seats or decks differ from the saved game." }
        val restored = json.decodeFromString(GameState.serializer(), record.getJSONObject("gameState").toString())
        require(!restored.gameOver) { "Completed games cannot be resumed." }
        fun decodeUndo(field: String): Checkpoint? = record.optJSONObject(field)?.let { jsonObject ->
            val sizes = jsonObject.optJSONObject("logSizes") ?: JSONObject()
            val logSizes = sizes.keys().asSequence().associate { id -> EntityId(id) to sizes.getInt(id) }
            Checkpoint(json.decodeFromString(GameState.serializer(), jsonObject.getJSONObject("state").toString()),
                logSizes, EntityId(jsonObject.getString("owner")))
        }
        val restoredLogs = mutableMapOf<EntityId, MutableList<ClientEvent>>()
        val logs = record.optJSONObject("gameLogs") ?: JSONObject()
        logs.keys().forEach { seat ->
            restoredLogs[EntityId(seat)] = json.decodeFromString(
                ListSerializer(ClientEvent.serializer()), logs.getJSONArray(seat).toString()
            ).toMutableList()
        }
        val restoredShock = ArrayDeque<ShockOffer>()
        val offers = record.optJSONArray("shockOffers") ?: org.json.JSONArray()
        for (i in 0 until offers.length()) {
            val offer = offers.getJSONObject(i)
            restoredShock.addLast(ShockOffer(EntityId(offer.getString("land")),
                offer.getString("name"), offer.getInt("life"), EntityId(offer.getString("player"))))
        }
        // Only commit reconstructed state after all formats have decoded successfully.
        state = restored
        gameLogs.clear(); gameLogs.putAll(restoredLogs)
        shockOffers.clear(); shockOffers.addAll(restoredShock)
        version = record.getLong("version")
        mulliganPhase = record.getBoolean("mulliganPhase")
        quietOpponentTurns = record.optBoolean("quietOpponentTurns")
        respondToOwnStack = record.optBoolean("respondToOwnStack")
        peopleOutSent = record.optBoolean("peopleOutSent")
        undoCheckpoint = decodeUndo("undoCheckpoint")
        preCombatCheckpoint = decodeUndo("preCombatCheckpoint")
        legalCache.clear()
        logSnapshots.clear()
        interactiveEpochs.clear()
        lastGameOutcomeEvents = emptyList()
        observedLossEvents.clear()
        ais = allSeats.filterNot(::isPerson).associateWith { OfflineAiPolicy.create(registry, it, seatOf(it).difficulty) }
        for (seat in people()) {
            emitTo(seat, JSONObject().put("type", "quickGameLobbyState").put("lobbyId", "offline")
                .put("youPlayerId", seat.value).put("vsAi", true)
                .put("players", org.json.JSONArray(allSeats.map {
                    JSONObject().put("playerId", it.value).put("name", seatOf(it).name)
                })))
            emitTo(seat, JSONObject().put("type", "gameStarted")
                .put("players", org.json.JSONArray(allSeats.map {
                    JSONObject().put("playerId", it.value).put("isYou", seat == it)
                })))
        }
        for (seat in people()) resync(seat)
    }

    private fun startGame() {
        val config = GameConfig(
            players = allSeats.map { seatConfig(seatOf(it), it) },
            skipMulligans = false,
            format = if (allSeats.all { seatOf(it).commander != null }) Format.Commander() else Format.Standard,
            seed = gameSeed,
        )
        val result = initializer.initializeGame(config)
        ais = allSeats.filterNot(::isPerson).associateWith { OfflineAiPolicy.create(registry, it, seatOf(it).difficulty) }
        // Arcana opening hands grant one free redraw (including two-player Commander
        // and offline-versus-AI). Set the engine's actual mulligan rule state before
        // either the AI or the person chooses a hand; changing only the UI would
        // still force the player to bottom a card after their first redraw.
        state = allSeats.fold(result.state) { current, seat ->
            current.updateEntity(seat) { player ->
                val mulligan = player.get<MulliganStateComponent>()
                if (mulligan == null || mulligan.freeMulligan) player
                else player.with(mulligan.copy(freeMulligan = true))
            }
        }
        gameLogs.clear()
        logSnapshots.clear()
        legalCache.clear()
        lastGameOutcomeEvents = emptyList()
        observedLossEvents.clear()
        for (seat in people()) {
            emitTo(seat, JSONObject().put("type", "quickGameLobbyState").put("lobbyId", "offline").put("youPlayerId", seat.value).put("vsAi", !isWifi)
                .put("players", org.json.JSONArray(allSeats.map { JSONObject().put("playerId", it.value).put("name", seatOf(it).name) })))
            emitTo(seat, JSONObject().put("type", "gameStarted").put("players", org.json.JSONArray(allSeats.map { JSONObject().put("playerId", it.value).put("isYou", seat == it) })))
        }
        undoCheckpoint = null
        preCombatCheckpoint = null
        peopleOutSent = false
        // The AIs settle their opening hands first; then each person gets theirs, as on the server.
        mulliganPhase = true
        ais.keys.forEach(::aiMulligans)
        if (people().all(::mulliganDone)) finishMulligans() else people().filterNot(::mulliganDone).forEach(::sendMulliganDecision)
    }

    // ---- Mulligans (London) ----------------------------------------------------------------------

    private fun mulliganState(player: EntityId): MulliganStateComponent? = state?.getEntity(player)?.get<MulliganStateComponent>()
    private fun mulliganDone(player: EntityId): Boolean = mulliganState(player).let { it == null || (it.hasKept && it.cardsToBottom == 0) }

    private fun aiMulligans(aiId: EntityId) {
        var guard = 0
        while (!mulliganDone(aiId) && guard++ < 10) {
            val current = state ?: return
            val mull = mulliganState(aiId) ?: return
            val hand = current.getHand(aiId)
            val action: GameAction = if (!mull.hasKept) {
                val lands = hand.count { isLand(current, it) }
                if (hand.size <= 5 || mull.mulligansTaken >= 2 || lands in 2..5) KeepHand(aiId) else TakeMulligan(aiId)
            } else {
                // Bottom the worst cards: excess lands beyond three, then the most expensive spells.
                val (lands, spells) = hand.partition { isLand(current, it) }
                val keepLands = if (hand.size - mull.cardsToBottom <= 5) 2 else 3
                val order = lands.drop(keepLands) + spells.sortedByDescending { manaValue(current, it) } + lands.take(keepLands)
                BottomCards(aiId, order.take(mull.cardsToBottom))
            }
            val result = processor.process(current, action).result
            if (result.error != null) return
            state = result.state
        }
    }

    private fun isLand(state: GameState, id: EntityId) = state.getEntity(id)?.get<CardComponent>()?.typeLine?.isLand == true
    private fun manaValue(state: GameState, id: EntityId) = state.getEntity(id)?.get<CardComponent>()?.manaCost?.cmc ?: 0

    private fun mulliganAction(action: GameAction) {
        val current = state ?: return
        val seat = action.playerId
        if (!mulliganPhase || mulliganDone(seat)) { sendError(seat, if (mulliganPhase) "Waiting for your opponent to keep their hand." else "The opening hands are already settled."); return }
        val result = processor.process(current, action).result
        if (result.error != null) { sendError(seat, result.error!!); return }
        state = result.state
        val mull = mulliganState(seat)
        when {
            people().all(::mulliganDone) -> finishMulligans()
            mulliganDone(seat) -> Unit // kept; waiting for the other person
            mull != null && mull.hasKept -> sendBottomCards(seat, mull.cardsToBottom)
            else -> sendMulliganDecision(seat)
        }
    }

    private fun finishMulligans() {
        mulliganPhase = false
        for (seat in people()) emitTo(seat, JSONObject().put("type", "mulliganComplete").put("finalHandSize", state?.getHand(seat)?.size ?: 7))
        val events = mutableListOf<GameEvent>()
        drive(events)
        sendState(events)
    }

    private fun handCards(current: GameState, seat: EntityId): Pair<org.json.JSONArray, JSONObject> {
        val hand = current.getHand(seat)
        val cards = JSONObject()
        hand.forEach { id ->
            val card = current.getEntity(id)?.get<CardComponent>() ?: return@forEach
            val image = card.imageUri ?: registry.getCard(card.cardDefinitionId)?.metadata?.imageUri
            cards.put(id.value, JSONObject().put("name", card.name).put("imageUri", image ?: JSONObject.NULL)
                .put("manaCost", card.manaCost.toString()).put("typeLine", card.typeLine.toString())
                .put("power", card.baseStats?.basePower ?: JSONObject.NULL).put("toughness", card.baseStats?.baseToughness ?: JSONObject.NULL)
                .put("oracleText", card.oracleText.ifBlank { null } ?: JSONObject.NULL))
        }
        return org.json.JSONArray(hand.map { it.value }) to cards
    }

    private fun sendMulliganDecision(seat: EntityId) {
        val current = state ?: return
        val mull = mulliganState(seat)
        val (hand, cards) = handCards(current, seat)
        emitTo(seat, JSONObject().put("type", "mulliganDecision").put("hand", hand).put("cards", cards)
            .put("mulliganCount", mull?.mulligansTaken ?: 0).put("cardsToPutOnBottom", mull?.cardsToBottom ?: 0)
            .put("isOnThePlay", current.activePlayerId == seat))
    }

    private fun sendBottomCards(seat: EntityId, count: Int) {
        val current = state ?: return
        val (hand, cards) = handCards(current, seat)
        emitTo(seat, JSONObject().put("type", "chooseBottomCards").put("hand", hand).put("cards", cards).put("cardsToPutOnBottom", count))
    }

    // ---- Paying with Treasures ---------------------------------------------------------------

    private fun needsMana(error: String) = error.contains("mana", ignoreCase = true) && (error.contains("enough", true) || error.contains("pay", true))

    private fun friendlyError(error: String) =
        if (needsMana(error)) "Not enough mana for that, even counting Treasures and other sacrifice-for-mana cards." else error

    /**
     * Sacrifices [player]'s Treasure-style mana sources ("{T}, Sacrifice: add one mana"), one at a
     * time, until [action] can be paid, and returns the state with that mana floating. Each one tries
     * every colour it can make and keeps the first that lets the action through; otherwise it adds a
     * colour the cost asks for and moves on to the next source. Null when it still can't be paid.
     */
    private fun payWithSacrificeSources(start: GameState, action: GameAction, player: EntityId): Pair<GameState, List<GameEvent>>? {
        if (action !is CastSpell && action !is ActivateAbility) return null
        if (action is CastSpell && action.paymentStrategy != PaymentStrategy.AutoPay) return null
        if (action is ActivateAbility && action.paymentStrategy != PaymentStrategy.AutoPay) return null
        // Find the sources and their mana abilities once (the full legal-action list is slow on a big
        // board), then sacrifice them in turn. A source whose ability no longer works is skipped.
        val sacrificial = services.manaSolver.findAvailableManaSources(start, player).filter { it.requiresSacrifice }.map { it.entityId }.toSet()
        if (sacrificial.isEmpty()) return null
        // Enumerate mana abilities only; scanning every cast/target/action becomes very
        // expensive on large token boards. Never cap the number of real mana sources.
        val options = enumerator.enumerateManaAbilities(start, player)
            .filter { (it.action as? ActivateAbility)?.sourceId in sacrificial }
            .distinctBy { (it.action as ActivateAbility).sourceId }
        if (options.isEmpty()) return null
        val costColors = (action as? CastSpell)?.let { start.getEntity(it.cardId)?.get<CardComponent>()?.manaCost?.colors }.orEmpty()
        var current = start
        val events = mutableListOf<GameEvent>()
        for (option in options) {
            val ability = option.action as ActivateAbility
            // "Any colour" sources (a Treasure) list no colours: try all five, never a blank choice
            // (a blank one opens a colour prompt mid-payment instead of adding mana).
            val colors = option.availableManaColors.orEmpty().ifEmpty { if (option.requiresManaColorChoice) com.wingedsheep.sdk.core.Color.entries else emptyList() }
            val tryOrder = (colors.filter { it in costColors } + colors.filterNot { it in costColors }).map { it as com.wingedsheep.sdk.core.Color? }.ifEmpty { listOf(null) }
            var fallback: Pair<GameState, List<GameEvent>>? = null
            for (color in tryOrder) {
                val activated = processor.process(current, ability.copy(manaColorChoice = color)).result
                if (activated.error != null || activated.state.pendingDecision != null) continue
                if (fallback == null) fallback = activated.state to activated.events
                if (processor.process(activated.state, action).result.error == null) return activated.state to (events + activated.events)
            }
            val (next, nextEvents) = fallback ?: continue
            current = next; events += nextEvents
        }
        return null
    }

    /**
     * "Add mana" abilities that aren't true mana abilities (Arcana's Food Chain, for one) shouldn't
     * stop auto-pass on the opponent's turn: nobody wants to be asked about them at every step.
     */
    private fun withoutManaLike(legal: List<LegalActionInfo>): List<LegalActionInfo> =
        legal.filterNot { it.actionType == "ActivateAbility" && !it.isManaAbility && it.description.substringAfter(':', "").trim().startsWith("Add ", ignoreCase = true) }

    // ---- Undo (Argentum GameSession's checkpoint policy) -------------------------------------

    private fun logSizes(): Map<EntityId, Int> = people().associateWith { logFor(it).size }

    private fun applyUndoPolicy(policy: UndoCheckpointAction, action: GameAction, before: GameState, actor: EntityId) {
        val here = Checkpoint(before, logSizes(), actor)
        when (policy) {
            UndoCheckpointAction.SET_CHECKPOINT ->
                undoCheckpoint = if (action is DeclareAttackers && preCombatCheckpoint?.owner == actor) preCombatCheckpoint else here
            UndoCheckpointAction.SET_PRECOMBAT_CHECKPOINT -> { preCombatCheckpoint = here; undoCheckpoint = here }
            UndoCheckpointAction.SET_IF_NO_EXISTING_CHECKPOINT -> if (undoCheckpoint == null) undoCheckpoint = here
            UndoCheckpointAction.PRESERVE -> Unit
            UndoCheckpointAction.CLEAR -> { undoCheckpoint = null; preCombatCheckpoint = null }
        }
    }

    /** As on the server: the opponent acting on what they saw ends your chance to take it back; their passes don't. */
    private fun clearOthersCheckpoint(action: GameAction, actor: EntityId) {
        if (action is PassPriority) return
        if (undoCheckpoint?.owner != actor) undoCheckpoint = null
        if (preCombatCheckpoint?.owner != actor) preCombatCheckpoint = null
    }

    private fun undoAvailable(seat: EntityId): Boolean = undoCheckpoint?.let { it.owner == seat && it.state.priorityPlayerId == seat } == true

    private fun undo(seat: EntityId) {
        val checkpoint = undoCheckpoint
        if (checkpoint == null || !undoAvailable(seat)) { sendError(seat, "There's nothing to undo right now."); return }
        state = checkpoint.state
        checkpoint.logSizes.forEach { (who, size) -> val log = logFor(who); while (log.size > size) log.removeAt(log.size - 1); logSnapshots.remove(who) }
        undoCheckpoint = null
        preCombatCheckpoint = null
        sendState(emptyList())
    }

    private fun seatConfig(seat: LocalSeat, id: EntityId) = PlayerConfig(
        name = seat.name, deck = Deck(seat.deck), playerId = id,
        startingLife = if (seat.commander != null) 40 else 20,
        commanderCardNames = listOfNotNull(seat.commander),
    )

    /**
     * Plays the AI and Argentum's auto-pass until a person must act or the game ends. Whenever the
     * AI does something or the game moves to a new step, the table gets that state ([flush]), so
     * the AI's turn and your auto-passed combat steps play out instead of being skipped.
     */
    private fun drive(events: MutableList<GameEvent>) = OfflineAiPolicy.withThinkingBurst(
        OfflineAiPolicy.burstMillisFor(ais.keys.map { seatOf(it).difficulty })
    ) {
        driveBurst(events)
    }

    private fun driveBurst(events: MutableList<GameEvent>) {
        aiThrottle.reset()
        var steps = 0
        var lastProgressNanos = System.nanoTime()
        var lastShown = state?.let { it.turnNumber to it.step }
        fun flush(pause: Long) {
            val current = state ?: return
            sendState(events.toList(), interactive = false)
            events.clear()
            lastShown = current.turnNumber to current.step
            lastProgressNanos = System.nanoTime()
            if (pause > 0) Thread.sleep(pause)
        }
        while (steps++ < 6_000) {
            val current = state ?: return
            if (current.gameOver || shockOffers.isNotEmpty()) break
            // Everyone at this phone (and the Wi-Fi guest) is out: stop instead of watching the AIs finish.
            if (people().none { it in current.activePlayers }) { peopleOut(events.toList()); events.clear(); break }
            if ((current.turnNumber to current.step) != lastShown) flush(paceMs / 3)
            else if (System.nanoTime() - lastProgressNanos >= 1_000_000_000L) flush(0)
            val decision = current.pendingDecision
            if (decision != null) {
                val actor = current.actorFor(decision.playerId)
                if (isPerson(actor)) break
                val bot = ais[actor] ?: break
                val response = timed("aiDecision") { aiGuarded("aiDecisionErrors") { OfflineAiPolicy.think { bot.respondToDecision(current, decision) } } }
                    ?: retryDecisionWithNormal(actor, current, decision)
                    ?: break
                if (!apply(SubmitDecision(decision.playerId, response), events, actor)) {
                    countDiagnostic("aiRejectedDecisions")
                    break
                }
                flush(paceMs)
                continue
            }
            val priority = current.priorityPlayerId ?: break
            if (!isPerson(priority)) {
                val bot = ais[priority] ?: break
                val startedChoice = System.nanoTime()
                val chosen = when {
                    tutorialPassiveOpponent -> safePass(current, priority)
                    // Another copy of the ability this AI just looked at and passed on.
                    aiThrottle.skipSearch(current, priority) -> {
                        countDiagnostic("aiCascadeSkips")
                        safePass(current, priority)
                    }
                    else -> (timed("aiChoice") { aiGuarded("aiChoiceErrors") { OfflineAiPolicy.think {
                        chooseAiAction(bot, current, priority)
                    } } } ?: safePass(current, priority))
                        .also { aiThrottle.recordSearch(current, priority, it) }
                }
                val choiceType = if (chosen is PassPriority) "aiChoicePass" else "aiChoiceAction"
                phaseNanos[choiceType] = (phaseNanos[choiceType] ?: 0L) + System.nanoTime() - startedChoice
                countDiagnostic(if (chosen is PassPriority) "aiPassChoices" else "aiActionChoices")
                val acted = apply(chosen, events, priority) ||
                    retryRejectedAiChoice(bot, current, priority, chosen, events) ||
                    apply(safePass(current, priority), events, priority)
                if (!acted) break
                if (chosen !is PassPriority) flush(paceMs)
                continue
            }
            // Practice must expose the real priority window after casting. Ordinary own-stack
            // auto-pass would otherwise resolve the lesson's spell before the learner can
            // inspect it or explicitly pass. The engine still validates every submitted action.
            if (tutorialPassiveOpponent && current.stack.isNotEmpty()) break
            // Human auto-pass needs only engine PriorityActions, not the FULL/enriched UI menu.
            // Interactive state updates still build and send the original complete legal-action DTO.
            val shouldPass = OfflineHumanAutoPass.shouldPass(
                state = current,
                player = priority,
                registry = registry,
                enumerateActionsOnly = {
                    timed("autoPassEnumerate") {
                        enumerator.enumerate(current, priority, com.wingedsheep.engine.legalactions.EnumerationMode.ACTIONS_ONLY)
                    }
                },
                quietOpponentTurn = if (priority == humanId) quietOpponentTurns else guestQuietOpponentTurns[priority] == true,
                stackTargetsPlayer = { stackTargetsYou(current, priority) },
                ownStackStops = if (priority in ownStackResponders) com.wingedsheep.engine.legalactions.OwnStackStop.entries.toSet() else emptySet(),
            )
            if (!shouldPass) break
            if (!apply(safePass(current, priority), events, priority)) break
        }
    }

    /** One AI priority choice (see [AiPriorityChoice]), with the host's timings and counters. */
    private fun chooseAiAction(bot: AIPlayer, current: GameState, priority: EntityId): GameAction =
        AiPriorityChoice.choose(
            bot, seatOf(priority).difficulty, current, priority,
            enumerate = { mode -> timed("aiEnumerate") { enumerator.enumerate(current, priority, mode) } },
            onTrivialPass = { countDiagnostic("aiTrivialPasses") },
        )

    /**
     * Runs one AI call; an exception inside the AI's own search (for example a crash while it
     * simulates combat) is counted and returns null, so the game falls back instead of stopping.
     */
    private inline fun <T : Any> aiGuarded(counter: String, work: () -> T): T? = try {
        work()
    } catch (error: StackOverflowError) {
        countDiagnostic(counter); null
    } catch (error: RuntimeException) {
        countDiagnostic(counter); null
    }

    /** A pending decision the chosen AI couldn't answer: ask a Normal-level AI for the seat once. */
    private fun retryDecisionWithNormal(actor: EntityId, state: GameState, decision: PendingDecision) =
        if (seatOf(actor).difficulty == AiDifficulty.NORMAL) null
        else aiGuarded("aiDecisionErrors") { OfflineAiPolicy.think { OfflineAiPolicy.create(registry, actor).respondToDecision(state, decision) } }

    /**
     * The engine turned down the AI's choice. Instead of passing straight away, let the same AI pick
     * again from the legal actions without the rejected one (once), so it still makes a real play.
     */
    private fun retryRejectedAiChoice(bot: AIPlayer, state: GameState, player: EntityId, rejected: GameAction, events: MutableList<GameEvent>): Boolean {
        if (rejected is PassPriority) return false
        countDiagnostic("aiRejectedActions")
        val legal = runCatching { enumerator.enumerate(state, player, com.wingedsheep.engine.legalactions.EnumerationMode.FULL) }.getOrNull() ?: return false
        val others = legal.filter { it.action != rejected }
        if (others.isEmpty()) return false
        val retry = aiGuarded("aiChoiceErrors") { OfflineAiPolicy.think { bot.chooseFrom(state, others) } } ?: return false
        if (retry.action is PassPriority) return false
        return apply(retry.action, events, player).also { if (it) countDiagnostic("aiRejectedRecovered") }
    }

    private fun safePass(state: GameState, player: EntityId): GameAction = when {
        state.step == Step.DECLARE_ATTACKERS && state.isActiveTurnFor(player) &&
            state.getEntity(player)?.get<AttackersDeclaredThisCombatComponent>() == null -> DeclareAttackers(player, emptyMap())
        state.step == Step.DECLARE_BLOCKERS && !state.isActiveTurnFor(player) &&
            state.getEntity(player)?.get<BlockersDeclaredThisCombatComponent>() == null -> DeclareBlockers(player, emptyMap())
        else -> PassPriority(player)
    }

    private fun apply(action: GameAction, events: MutableList<GameEvent>, actor: EntityId): Boolean {
        val current = state ?: return false
        val isPass = action is PassPriority
        val processed = timed("actionResolution") {
            timed(if (isPass) "passResolution" else "otherResolution") {
                (if (isPerson(actor)) processor else aiResolutionProcessor).process(current, action)
            }
        }
        countDiagnostic(if (isPass) "passResolutions" else "otherResolutions")
        val result = processed.result
        if (result.error != null) return false
        clearOthersCheckpoint(action, actor)
        if (isPerson(actor)) applyUndoPolicy(processed.undoPolicy, action, current, actor)
        state = result.state
        events += result.events
        queueShockOffers(result.events)
        return true
    }

    private fun stackTargetsYou(state: GameState, player: EntityId): Boolean = state.stack.any { item ->
        state.getEntity(item)?.get<com.wingedsheep.engine.state.components.stack.TargetsComponent>()?.targets.orEmpty().any { t ->
            when (t) {
                is com.wingedsheep.engine.state.components.stack.ChosenTarget.Player -> t.playerId == player
                is com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent ->
                    state.getEntity(t.entityId)?.get<com.wingedsheep.engine.state.components.identity.ControllerComponent>()?.playerId == player
                is com.wingedsheep.engine.state.components.stack.ChosenTarget.Card -> t.ownerId == player
                else -> false
            }
        }
    }

    private fun shockId(offer: ShockOffer) = "arcana-shock-${offer.land.value}"

    private fun queueShockOffers(events: List<GameEvent>) {
        val current = state ?: return
        events.filterIsInstance<ZoneChangeEvent>()
            .filter { it.toZone == Zone.BATTLEFIELD && it.fromZone != Zone.HAND && it.fromZone != Zone.STACK && isPerson(it.ownerId) }
            .forEach { event ->
                val entity = current.getEntity(event.entityId) ?: return@forEach
                if (entity.get<TappedComponent>() == null) return@forEach
                val card = entity.get<CardComponent>() ?: return@forEach
                val cost = registry.getCard(card.cardDefinitionId)?.script?.replacementEffects
                    ?.filterIsInstance<EntersTapped>()?.firstOrNull()?.payLifeCost ?: return@forEach
                if (shockOffers.none { it.land == event.entityId }) shockOffers.addLast(ShockOffer(event.entityId, card.name, cost, event.ownerId))
            }
    }

    private fun shockDecision(offer: ShockOffer) = YesNoDecision(
        id = shockId(offer), playerId = offer.player,
        prompt = "Pay ${offer.life} life to have ${offer.name} enter untapped?",
        context = DecisionContext(sourceId = offer.land, sourceName = offer.name),
        yesText = "Pay ${offer.life} life", noText = "Leave it tapped",
    )

    private fun answerShock(offer: ShockOffer, pay: Boolean) {
        shockOffers.removeFirst()
        val current = state ?: return
        val life = current.getEntity(offer.player)?.get<LifeTotalComponent>()?.life
        if (pay && life != null && current.getEntity(offer.land)?.get<TappedComponent>() != null) {
            state = current.updateEntity(offer.land) { it.without<TappedComponent>() }
                .updateEntity(offer.player) { it.with(LifeTotalComponent(life - offer.life)) }
        }
        val events = mutableListOf<GameEvent>()
        drive(events)
        sendState(events)
    }

    private fun legalActions(state: GameState, player: EntityId): List<LegalActionInfo> {
        legalCache[player]?.let { (cached, legal) -> if (cached === state) return legal }
        return timed("legalActions") { computeLegalActions(state, player) }.also { legalCache[player] = state to it }
    }

    private fun computeLegalActions(state: GameState, player: EntityId): List<LegalActionInfo> {
        (state.pendingDecision as? PlayCardDecision)?.let { play ->
            if (state.actorFor(play.playerId) != player) return emptyList()
            return CounterRemovalCost.fill(enricher.enrich(enumerator.enumerate(state, play.playerId), state, play.playerId), state, registry)
        }
        if (state.pendingDecision != null || state.priorityPlayerId != player) return emptyList()
        val actions = enumerator.enumerate(state, player)
        val enriched = CounterRemovalCost.fill(enricher.enrich(actions, state, player), state, registry)
        // Some mana abilities require an explicit cost (Food for Gilded Goose, or
        // the source itself for Treasure). canPay can count such abilities even
        // when the actual automatic payment path cannot execute them. Verify
        // real casting only when these sources are present.
        // Self-sacrificing Treasures are handled on demand in submitAction. Do not
        // trial-cast every card on every state update merely because a large board
        // has hundreds of Treasures; that recreates the very performance problem
        // Build 127 is supposed to address. Only sources with an explicit OTHER
        // permanent as an extra cost need this preflight (e.g. Goose + Food).
        val hasManualMana = actions.any {
            it.isManaAbility && it.additionalCostInfo?.validSacrificeTargets?.isNotEmpty() == true
        }
        if (!hasManualMana) return enriched
        return enriched.map { info ->
            val cast = info.action as? CastSpell
            if (cast == null || !info.isAffordable || info.hasXCost || info.requiresTargets) info
            else {
                val attempted = processor.process(state, cast).result
                val payable = attempted.error == null ||
                    (needsMana(attempted.error!!) && payWithSacrificeSources(state, cast, player)?.let { (paid, _) ->
                        processor.process(paid, cast).result.error == null
                    } == true)
                if (payable) info else info.copy(isAffordable = false)
            }
        }
    }

    /**
     * The X picker just opened for [cardId]: work out the X auto-pay (with Treasures) can really pay
     * and send it back as "maxXRefined". Done only on request, never on every update: it can mean
     * dozens of trial payments on a big board.
     */
    private fun refineMaxX(cardId: String, seat: EntityId) {
        val current = state
        val info = current?.let { legalActions(it, seat) }?.firstOrNull { (it.action as? CastSpell)?.cardId?.value == cardId && it.hasXCost }
        val refined = if (current != null && info != null) trueMaxX(current, info, seat).maxAffordableX else null
        emitTo(seat, JSONObject().put("type", "maxXRefined").put("cardId", cardId).put("maxX", refined ?: JSONObject.NULL).put("stateVersion", version))
    }

    /**
     * Argentum's "up to X" can be one or two higher than auto-pay (with Treasures) can really pay.
     * For untargeted X spells, step down from its number to the first X that actually works, so the
     * X picker's default never fails.
     */
    private fun trueMaxX(state: GameState, info: LegalActionInfo, seat: EntityId): LegalActionInfo {
        val cast = info.action as? CastSpell ?: return info
        val max = info.maxAffordableX ?: return info
        if (!info.hasXCost || info.requiresTargets || max <= info.minX) return info
        for (x in max downTo maxOf(info.minX, max - 3)) {
            val attempt = cast.copy(xValue = x)
            if (processor.process(state, attempt).result.error == null) return if (x == max) info else info.copy(maxAffordableX = x)
            val paid = payWithSacrificeSources(state, attempt, seat) ?: continue
            if (processor.process(paid.first, attempt).result.error == null) return if (x == max) info else info.copy(maxAffordableX = x)
        }
        return info
    }

    private fun gameOverMessage(current: GameState, seat: EntityId) =
        GameOutcomeDetails.toMessage(current, seat, { seatOf(it).name }, lastGameOutcomeEvents) { registry.getCard(it) != null }

    private var peopleOutSent = false

    /**
     * Every human-controlled seat has been eliminated. Stop the local engine for these viewers,
     * but do not fabricate a completed game or discard the authoritative elimination events.
     */
    private fun peopleOut(pendingEvents: List<GameEvent> = emptyList()) {
        if (peopleOutSent) return
        // This captures the final PlayerLostEvent before any state snapshot clears a burst.
        sendState(pendingEvents, interactive = false)
        val current = state ?: return
        peopleOutSent = true
        val evidence = observedLossEvents.toList()
        people().forEach { seat ->
            emitTo(seat, GameOutcomeDetails.toHumanEliminatedMessage(
                current, seat, { seatOf(it).name }, evidence,
            ) { registry.getCard(it) != null })
        }
    }

    private fun finishIfOver(events: List<GameEvent>) {
        sendState(events)
        val current = state ?: return
        if (current.gameOver && events.isEmpty()) people().forEach { emitTo(it, gameOverMessage(current, it)) }
        else if (!current.gameOver && people().none { it in current.activePlayers }) peopleOut()
    }

    private fun sendError(seat: EntityId, text: String) = emitTo(seat, JSONObject().put("type", "error").put("code", "INVALID_ACTION").put("message", text))

    /** Sends every person their own view of the game. */
    private fun sendState(events: List<GameEvent>, interactive: Boolean = true) {
        // Never overwrite a terminal human-elimination notice with a later non-terminal progress
        // snapshot from the still-alive AI-only state.
        if (peopleOutSent) return
        // Retain elimination events across the host's intermediate AI/progress flushes. A
        // PlayerLostEvent and the final GameEndedEvent need not be delivered in one update.
        observedLossEvents += events.filterIsInstance<com.wingedsheep.engine.core.PlayerLostEvent>()
        val final = state
        if (final?.gameOver == true) {
            lastGameOutcomeEvents = (
                observedLossEvents.filter { it.playerId !in final.activePlayers } + events
            ).distinct()
        }
        for (seat in people()) sendStateFor(seat, events, interactive)
    }

    /**
     * Sends [seat]'s view of the game. [interactive] = false is a progress update sent while the
     * AI or auto-pass is still playing: no legal actions or prompts, like the server's in-between
     * updates, so nothing on the table can be tapped against a state that's already moving on.
     */
    private fun sendStateFor(seat: EntityId, events: List<GameEvent>, interactive: Boolean) {
        val current = state ?: return
        val clientEvents = ClientEventTransformer.transform(events, seat, current, visibility)
        val log = logFor(seat)
        log += clientEvents.filter { it !is ClientEvent.PermanentTapped && it !is ClientEvent.PermanentUntapped && it !is ClientEvent.ManaAdded }
        val clientState: ClientGameState = timed("snapshot") {
            val projection = timed("snapshotProjection") { transformer.transform(current, seat) }
            projection.copy(gameLog = timed("snapshotLog") { stableLog(seat, log) })
        }
        val offer = shockOffers.firstOrNull()?.takeIf { interactive && it.player == seat }
        val blockedByOtherOffer = shockOffers.isNotEmpty() && offer == null
        val legal = if (interactive && offer == null && !blockedByOtherOffer) legalActions(current, seat) else emptyList()
        val decision: PendingDecision? = if (!interactive || blockedByOtherOffer) null else offer?.let(::shockDecision)
            ?: current.pendingDecision?.takeIf { current.actorFor(it.playerId) == seat }?.let { masker.maskFor(it, current, seat) }
        val nextStop = if (interactive && offer == null && current.priorityPlayerId == seat && current.pendingDecision == null) {
            autoPass.getNextStopPoint(current, seat, autoPass.getMeaningfulActions(withoutManaLike(legal)).isNotEmpty())
        } else null
        val stateVersion = ++version
        if (interactive) interactiveEpochs[seat] = "offline-$stateVersion"
        val message = buildJsonObject {
            put("type", "stateUpdate")
            put("state", json.encodeToJsonElement(ClientGameState.serializer(), clientState))
            put("events", json.encodeToJsonElement(ListSerializer(ClientEvent.serializer()), clientEvents))
            put("legalActions", json.encodeToJsonElement(ListSerializer(LegalActionInfo.serializer()), legal))
            put("pendingDecision", decision?.let { json.encodeToJsonElement(PendingDecision.serializer(), it) } ?: JsonNull)
            put("nextStopPoint", nextStop?.let { JsonPrimitive(it) } ?: JsonNull)
            put("undoAvailable", interactive && undoAvailable(seat))
            put("priorityMode", "auto")
            put("stateVersion", stateVersion)
            // Measured progress only: the previous update is a real, completed state.
            put("interactive", interactive)
            put("engineMs", (System.nanoTime() - handleStartNanos) / 1_000_000)
            put("engineTimingsMs", buildJsonObject {
                phaseNanos.forEach { (phase, nanos) -> put(phase, nanos / 1_000_000) }
            })
            put("engineCounts", buildJsonObject {
                diagnosticCounts.forEach { (name, total) -> put(name, total) }
            })
            put("stackSize", current.stack.size)
            put("battlefieldSize", current.getBattlefield().size)
            put("interactionEpoch", interactiveEpochs[seat] ?: "offline-0")
        }
        emitTo(seat, JSONObject(message.toString()))
        if (current.gameOver && events.isNotEmpty()) emitTo(seat, gameOverMessage(current, seat))
    }

    companion object {
        val HUMAN_ID = EntityId("offline-you")
        val AI_ID = EntityId("offline-ai")
        /** Wi-Fi: the guest sits in the second seat. */
        val GUEST_ID: EntityId get() = AI_ID
    }
}

