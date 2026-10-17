package com.arcanatable.engine

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.engine.core.ActionProcessor
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
data class LocalSeat(val name: String, val deck: List<String>, val commander: String?)

/**
 * Offline Argentum: the real rules engine and AI, run inside Arcana behind the same `/game`
 * messages the server sends (connected, quickGameLobbyState, gameStarted, stateUpdate, gameOver,
 * error). Arcana's table code is unchanged; only the transport differs.
 *
 * Mirrors the server's per-seat pipeline (GameSession.createStateUpdate): masked ClientGameState,
 * enriched legal actions, masked pending decision, client events, the accumulated game log,
 * Argentum's auto-pass for the human seat, and nextStopPoint. All engine work runs on one
 * large-stack thread; [emit] is called from that thread.
 *
 * Like the server, it also runs the London mulligan (mulliganDecision / chooseBottomCards /
 * mulliganComplete), offers undo with the server's checkpoint policy, and sends a state update after
 * every AI action and every step change, [paceMs] apart, so the AI's turn plays out on the table
 * instead of jumping straight to your next decision.
 */
class LocalArgentumHost(
    private val registry: CardRegistry,
    private val human: LocalSeat,
    private val opponent: LocalSeat,
    private val paceMs: Long = 600,
    private val emit: (JSONObject) -> Unit,
) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(null, r, "argentum-local", 256L shl 20) }
    private val services = EngineServices(registry)
    private val processor = ActionProcessor(services)
    private val initializer = GameInitializer(registry, services.printingRegistry)
    private val transformer = ClientStateTransformer(registry, predicateEvaluator = PredicateEvaluator(cardRegistry = null))
    private val enumerator = LegalActionEnumerator(registry, services.manaSolver, services.costCalculator, services.predicateEvaluator, services.conditionEvaluator, services.turnManager)
    private val enricher = LegalActionEnricher(services.manaSolver, registry)
    private val visibility = Visibility(registry, conditionEvaluator = services.conditionEvaluator)
    private val masker = DecisionMasker(visibility)
    private val autoPass = AutoPassManager(registry)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type"; serializersModule = engineSerializersModule }

    private var state: GameState? = null
    private val humanId: EntityId = HUMAN_ID
    private val aiId: EntityId = AI_ID
    private lateinit var ai: AIPlayer
    private val gameLog = mutableListOf<ClientEvent>()
    private var version = 0L

    // Undo, as Argentum's GameSession does it: one checkpoint, owned by the player whose action set it.
    private data class Checkpoint(val state: GameState, val logSize: Int)
    private var undoCheckpoint: Checkpoint? = null
    private var preCombatCheckpoint: Checkpoint? = null
    private var mulliganPhase = false

    fun receive(message: JSONObject) = worker.execute {
        runCatching { handle(message) }.onFailure { error ->
            emit(JSONObject().put("type", "error").put("code", "LOCAL_ENGINE").put("message", "Offline engine problem: ${error.javaClass.simpleName}: ${error.message?.take(200)}"))
        }
    }

    fun shutdown() { worker.shutdownNow() }

    private fun handle(message: JSONObject) {
        when (message.optString("type")) {
            "connect" -> emit(JSONObject().put("type", "connected").put("playerId", HUMAN_ID.value).put("token", "offline"))
            "createQuickGameLobby" -> startGame()
            "requestResync" -> sendState(emptyList())
            "submitAction" -> {
                val current = state ?: return
                if (mulliganPhase) { sendError("Choose your opening hand first."); return }
                val action = json.decodeFromString(GameAction.serializer(), message.getJSONObject("action").toString())
                if (action.playerId != humanId) { sendError("That action belongs to another seat."); return }
                val processed = processor.process(current, action)
                val result = processed.result
                if (result.error != null) { sendError(result.error!!); sendState(emptyList()); return }
                applyUndoPolicy(processed.undoPolicy, action, current)
                state = result.state
                val events = result.events.toMutableList()
                drive(events)
                sendState(events)
            }
            "keepHand" -> mulliganAction(KeepHand(humanId))
            "mulligan" -> mulliganAction(TakeMulligan(humanId))
            "chooseBottomCards" -> {
                val ids = message.optJSONArray("cardIds") ?: org.json.JSONArray()
                mulliganAction(BottomCards(humanId, (0 until ids.length()).map { EntityId(ids.getString(it)) }))
            }
            "concede" -> {
                val current = state ?: return
                val result = processor.process(current, Concede(humanId)).result
                state = result.state
                finishIfOver(result.events)
            }
            "requestUndo" -> undo()
            "ping" -> emit(JSONObject().put("type", "pong"))
            else -> Unit // lobby, deck and preference messages need no answer offline
        }
    }

    private fun startGame() {
        val config = GameConfig(
            players = listOf(seatConfig(human, HUMAN_ID), seatConfig(opponent, AI_ID)),
            skipMulligans = false,
            format = if (human.commander != null && opponent.commander != null) Format.Commander() else Format.Standard,
        )
        val result = initializer.initializeGame(config)
        ai = AIPlayer.create(registry, aiId)
        state = result.state
        gameLog.clear()
        emit(JSONObject().put("type", "quickGameLobbyState").put("lobbyId", "offline").put("youPlayerId", humanId.value).put("vsAi", true)
            .put("players", org.json.JSONArray().put(JSONObject().put("playerId", humanId.value).put("name", human.name)).put(JSONObject().put("playerId", aiId.value).put("name", opponent.name))))
        emit(JSONObject().put("type", "gameStarted").put("players", org.json.JSONArray()
            .put(JSONObject().put("playerId", humanId.value).put("isYou", true))
            .put(JSONObject().put("playerId", aiId.value).put("isYou", false))))
        undoCheckpoint = null
        preCombatCheckpoint = null
        // The AI settles its opening hand first; then you get yours, as on the server.
        mulliganPhase = true
        aiMulligans()
        if (mulliganDone(humanId)) finishMulligans() else sendMulliganDecision()
    }

    // ---- Mulligans (London) ----------------------------------------------------------------------

    private fun mulliganState(player: EntityId): MulliganStateComponent? = state?.getEntity(player)?.get<MulliganStateComponent>()
    private fun mulliganDone(player: EntityId): Boolean = mulliganState(player).let { it == null || (it.hasKept && it.cardsToBottom == 0) }

    private fun aiMulligans() {
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
        if (!mulliganPhase) { sendError("The opening hands are already settled."); return }
        val result = processor.process(current, action).result
        if (result.error != null) { sendError(result.error!!); return }
        state = result.state
        val mull = mulliganState(humanId)
        when {
            mulliganDone(humanId) -> finishMulligans()
            mull != null && mull.hasKept -> sendBottomCards(mull.cardsToBottom)
            else -> sendMulliganDecision()
        }
    }

    private fun finishMulligans() {
        mulliganPhase = false
        emit(JSONObject().put("type", "mulliganComplete").put("finalHandSize", state?.getHand(humanId)?.size ?: 7))
        val events = mutableListOf<GameEvent>()
        drive(events)
        sendState(events)
    }

    private fun handCards(current: GameState): Pair<org.json.JSONArray, JSONObject> {
        val hand = current.getHand(humanId)
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

    private fun sendMulliganDecision() {
        val current = state ?: return
        val mull = mulliganState(humanId)
        val (hand, cards) = handCards(current)
        emit(JSONObject().put("type", "mulliganDecision").put("hand", hand).put("cards", cards)
            .put("mulliganCount", mull?.mulligansTaken ?: 0).put("cardsToPutOnBottom", mull?.cardsToBottom ?: 0)
            .put("isOnThePlay", current.activePlayerId == humanId))
    }

    private fun sendBottomCards(count: Int) {
        val current = state ?: return
        val (hand, cards) = handCards(current)
        emit(JSONObject().put("type", "chooseBottomCards").put("hand", hand).put("cards", cards).put("cardsToPutOnBottom", count))
    }

    // ---- Undo (Argentum GameSession's checkpoint policy) -------------------------------------

    private fun applyUndoPolicy(policy: UndoCheckpointAction, action: GameAction, before: GameState) {
        val here = Checkpoint(before, gameLog.size)
        when (policy) {
            UndoCheckpointAction.SET_CHECKPOINT ->
                undoCheckpoint = if (action is DeclareAttackers && preCombatCheckpoint != null) preCombatCheckpoint else here
            UndoCheckpointAction.SET_PRECOMBAT_CHECKPOINT -> { preCombatCheckpoint = here; undoCheckpoint = here }
            UndoCheckpointAction.SET_IF_NO_EXISTING_CHECKPOINT -> if (undoCheckpoint == null) undoCheckpoint = here
            UndoCheckpointAction.PRESERVE -> Unit
            UndoCheckpointAction.CLEAR -> { undoCheckpoint = null; preCombatCheckpoint = null }
        }
    }

    private fun undoAvailable(): Boolean = undoCheckpoint?.state?.priorityPlayerId == humanId

    private fun undo() {
        val checkpoint = undoCheckpoint
        if (checkpoint == null || !undoAvailable()) { sendError("There's nothing to undo right now."); return }
        state = checkpoint.state
        while (gameLog.size > checkpoint.logSize) gameLog.removeAt(gameLog.size - 1)
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
     * Plays the AI and Argentum's auto-pass until the human must act or the game ends. Whenever the
     * AI does something or the game moves to a new step, the table gets that state ([flush]), so
     * the AI's turn and your auto-passed combat steps play out instead of being skipped.
     */
    private fun drive(events: MutableList<GameEvent>) {
        var steps = 0
        var lastShown = state?.let { it.turnNumber to it.step }
        fun flush(pause: Long) {
            val current = state ?: return
            sendState(events.toList(), interactive = false)
            events.clear()
            lastShown = current.turnNumber to current.step
            if (pause > 0) Thread.sleep(pause)
        }
        while (steps++ < 2_000) {
            val current = state ?: return
            if (current.gameOver) break
            if ((current.turnNumber to current.step) != lastShown) flush(paceMs / 3)
            val decision = current.pendingDecision
            if (decision != null) {
                if (current.actorFor(decision.playerId) == humanId) break
                if (!apply(SubmitDecision(decision.playerId, ai.respondToDecision(current, decision)), events, byAi = true)) break
                flush(paceMs)
                continue
            }
            val priority = current.priorityPlayerId ?: break
            if (priority == aiId) {
                val chosen = ai.chooseAction(current)
                val acted = apply(chosen, events, byAi = true) || apply(safePass(current, aiId), events, byAi = true)
                if (!acted) break
                if (chosen !is PassPriority) flush(paceMs)
                continue
            }
            // Human priority: pass automatically where Argentum's server would.
            val legal = legalActions(current)
            if (!autoPass.shouldAutoPass(current, priority, legal)) break
            if (!apply(safePass(current, priority), events)) break
        }
    }

    private fun safePass(state: GameState, player: EntityId): GameAction = when {
        state.step == Step.DECLARE_ATTACKERS && state.isActiveTurnFor(player) &&
            state.getEntity(player)?.get<AttackersDeclaredThisCombatComponent>() == null -> DeclareAttackers(player, emptyMap())
        state.step == Step.DECLARE_BLOCKERS && !state.isActiveTurnFor(player) &&
            state.getEntity(player)?.get<BlockersDeclaredThisCombatComponent>() == null -> DeclareBlockers(player, emptyMap())
        else -> PassPriority(player)
    }

    private fun apply(action: GameAction, events: MutableList<GameEvent>, byAi: Boolean = false): Boolean {
        val current = state ?: return false
        val processed = processor.process(current, action)
        val result = processed.result
        if (result.error != null) return false
        // As on the server: the opponent acting on what they saw ends your chance to take it back;
        // their passes don't.
        if (byAi) { if (action !is PassPriority) { undoCheckpoint = null; preCombatCheckpoint = null } }
        else applyUndoPolicy(processed.undoPolicy, action, current)
        state = result.state
        events += result.events
        return true
    }

    private fun legalActions(state: GameState): List<LegalActionInfo> {
        (state.pendingDecision as? PlayCardDecision)?.let { play ->
            if (state.actorFor(play.playerId) != humanId) return emptyList()
            return enricher.enrich(enumerator.enumerate(state, play.playerId), state, play.playerId)
        }
        if (state.pendingDecision != null || state.priorityPlayerId != humanId) return emptyList()
        return enricher.enrich(enumerator.enumerate(state, humanId), state, humanId)
    }

    private fun finishIfOver(events: List<GameEvent>) {
        sendState(events)
        val current = state ?: return
        if (current.gameOver) emit(JSONObject().put("type", "gameOver")
            .put("winnerId", (current.winnerId?.value as Any?) ?: JSONObject.NULL)
            .put("reason", if (current.winnerId == humanId) "OPPONENT_LOST" else if (current.winnerId == null) "DRAW" else "YOU_LOST"))
    }

    private fun sendError(text: String) = emit(JSONObject().put("type", "error").put("code", "INVALID_ACTION").put("message", text))

    /**
     * Sends the human's view of the game. [interactive] = false is a progress update sent while the
     * AI or auto-pass is still playing: no legal actions or prompts, like the server's in-between
     * updates, so nothing on the table can be tapped against a state that's already moving on.
     */
    private fun sendState(events: List<GameEvent>, interactive: Boolean = true) {
        val current = state ?: return
        val clientEvents = ClientEventTransformer.transform(events, humanId, current, visibility)
        gameLog += clientEvents.filter { it !is ClientEvent.PermanentTapped && it !is ClientEvent.PermanentUntapped && it !is ClientEvent.ManaAdded }
        val clientState: ClientGameState = transformer.transform(current, humanId).copy(gameLog = gameLog.toList())
        val legal = if (interactive) legalActions(current) else emptyList()
        val decision: PendingDecision? = if (!interactive) null else current.pendingDecision?.takeIf { current.actorFor(it.playerId) == humanId }?.let { masker.maskFor(it, current, humanId) }
        val nextStop = if (interactive && current.priorityPlayerId == humanId && current.pendingDecision == null) {
            autoPass.getNextStopPoint(current, humanId, autoPass.getMeaningfulActions(legal).isNotEmpty())
        } else null
        val message = buildJsonObject {
            put("type", "stateUpdate")
            put("state", json.encodeToJsonElement(ClientGameState.serializer(), clientState))
            put("events", json.encodeToJsonElement(ListSerializer(ClientEvent.serializer()), clientEvents))
            put("legalActions", json.encodeToJsonElement(ListSerializer(LegalActionInfo.serializer()), legal))
            put("pendingDecision", decision?.let { json.encodeToJsonElement(PendingDecision.serializer(), it) } ?: JsonNull)
            put("nextStopPoint", nextStop?.let { JsonPrimitive(it) } ?: JsonNull)
            put("undoAvailable", interactive && undoAvailable())
            put("priorityMode", "auto")
            put("stateVersion", ++version)
            put("interactionEpoch", "offline")
        }
        emit(JSONObject(message.toString()))
        if (current.gameOver && events.isNotEmpty()) emit(JSONObject().put("type", "gameOver")
            .put("winnerId", (current.winnerId?.value as Any?) ?: JSONObject.NULL)
            .put("reason", if (current.winnerId == humanId) "OPPONENT_LOST" else if (current.winnerId == null) "DRAW" else "YOU_LOST"))
    }

    companion object {
        val HUMAN_ID = EntityId("offline-you")
        val AI_ID = EntityId("offline-ai")
    }
}
