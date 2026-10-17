package com.arcanatable.engine

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.Concede
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
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
 */
class LocalArgentumHost(
    private val registry: CardRegistry,
    private val human: LocalSeat,
    private val opponent: LocalSeat,
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
                val action = json.decodeFromString(GameAction.serializer(), message.getJSONObject("action").toString())
                if (action.playerId != humanId) { sendError("That action belongs to another seat."); return }
                val result = processor.process(current, action).result
                if (result.error != null) { sendError(result.error!!); sendState(emptyList()); return }
                state = result.state
                val events = result.events.toMutableList()
                drive(events)
                sendState(events)
            }
            "concede" -> {
                val current = state ?: return
                val result = processor.process(current, Concede(humanId)).result
                state = result.state
                finishIfOver(result.events)
            }
            "requestUndo" -> sendError("Undo is not available in offline games yet.")
            "ping" -> emit(JSONObject().put("type", "pong"))
            else -> Unit // lobby, deck and preference messages need no answer offline
        }
    }

    private fun startGame() {
        val config = GameConfig(
            players = listOf(seatConfig(human, HUMAN_ID), seatConfig(opponent, AI_ID)),
            skipMulligans = true,
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
        val events = mutableListOf<GameEvent>()
        drive(events)
        sendState(events)
    }

    private fun seatConfig(seat: LocalSeat, id: EntityId) = PlayerConfig(
        name = seat.name, deck = Deck(seat.deck), playerId = id,
        startingLife = if (seat.commander != null) 40 else 20,
        commanderCardNames = listOfNotNull(seat.commander),
    )

    /** Plays the AI and Argentum's auto-pass until the human must act or the game ends. */
    private fun drive(events: MutableList<GameEvent>) {
        var steps = 0
        while (steps++ < 2_000) {
            val current = state ?: return
            if (current.gameOver) break
            val decision = current.pendingDecision
            if (decision != null) {
                if (current.actorFor(decision.playerId) == humanId) break
                if (!apply(SubmitDecision(decision.playerId, ai.respondToDecision(current, decision)), events)) break
                continue
            }
            val priority = current.priorityPlayerId ?: break
            if (priority == aiId) {
                val chosen = ai.chooseAction(current)
                if (!apply(chosen, events) && !apply(safePass(current, aiId), events)) break
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

    private fun apply(action: GameAction, events: MutableList<GameEvent>): Boolean {
        val current = state ?: return false
        val result = processor.process(current, action).result
        if (result.error != null) return false
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

    private fun sendState(events: List<GameEvent>) {
        val current = state ?: return
        val clientEvents = ClientEventTransformer.transform(events, humanId, current, visibility)
        gameLog += clientEvents.filter { it !is ClientEvent.PermanentTapped && it !is ClientEvent.PermanentUntapped && it !is ClientEvent.ManaAdded }
        val clientState: ClientGameState = transformer.transform(current, humanId).copy(gameLog = gameLog.toList())
        val legal = legalActions(current)
        val decision: PendingDecision? = current.pendingDecision?.takeIf { current.actorFor(it.playerId) == humanId }?.let { masker.maskFor(it, current, humanId) }
        val nextStop = if (current.priorityPlayerId == humanId && current.pendingDecision == null) {
            autoPass.getNextStopPoint(current, humanId, autoPass.getMeaningfulActions(legal).isNotEmpty())
        } else null
        val message = buildJsonObject {
            put("type", "stateUpdate")
            put("state", json.encodeToJsonElement(ClientGameState.serializer(), clientState))
            put("events", json.encodeToJsonElement(ListSerializer(ClientEvent.serializer()), clientEvents))
            put("legalActions", json.encodeToJsonElement(ListSerializer(LegalActionInfo.serializer()), legal))
            put("pendingDecision", decision?.let { json.encodeToJsonElement(PendingDecision.serializer(), it) } ?: JsonNull)
            put("nextStopPoint", nextStop?.let { JsonPrimitive(it) } ?: JsonNull)
            put("undoAvailable", false)
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
