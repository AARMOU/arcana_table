package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.LocalSeat
import com.arcanatable.engine.OfflineCardLibrary
import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shippable, bounded tutorial states. All board changes go through the actual Argentum engine
 * and are resumed via LocalArgentumHost; no mirrored or fake rules are used. Each session is
 * independent from the user's saved offline game and never auto-saves a checkpoint.
 */
internal object TutorialPracticeScenarios {
    const val FIRST_TURN = "first-turn"
    const val COMBAT = "combat"
    const val MULLIGAN = "opening-hand"

    val supported = setOf(FIRST_TURN, COMBAT, MULLIGAN)

    fun build(id: String, library: OfflineCardLibrary): TestScenarioGame = when (id) {
        FIRST_TURN -> firstTurn(library)
        COMBAT -> combat(library)
        else -> error("Unknown checkpoint tutorial $id")
    }

    fun openingHand(library: OfflineCardLibrary): Pair<List<LocalSeat>, CardRegistry> {
        val you = LocalSeat("You", listOf("Elvish Mystic", "Grizzly Bears") + List(38) { "Forest" }, null)
        val opponent = LocalSeat("Training Opponent", List(40) { "Island" }, null)
        val registry = registry(library, you, opponent)
        return listOf(you, opponent) to registry
    }

    private fun registry(library: OfflineCardLibrary, you: LocalSeat, ai: LocalSeat) =
        CardRegistry().apply { register(library.load((you.deck + ai.deck).toSet())) }

    private fun prepare(library: OfflineCardLibrary, creature: String): Prepared {
        val you = LocalSeat("You", listOf(creature, "Elvish Mystic") + List(38) { "Forest" }, null)
        val ai = LocalSeat("Training Opponent", List(40) { "Island" }, null)
        val registry = registry(library, you, ai)
        val services = EngineServices(registry)
        val config = GameConfig(
            players = listOf(
                PlayerConfig(name = you.name, deck = Deck(you.deck), playerId = LocalArgentumHost.HUMAN_ID, startingLife = 20, commanderCardNames = emptyList()),
                PlayerConfig(name = ai.name, deck = Deck(ai.deck), playerId = LocalArgentumHost.AI_ID, startingLife = 20, commanderCardNames = emptyList()),
            ),
            skipMulligans = true,
            startingPlayerIndex = 0,
            format = Format.Standard,
            seed = 12025L,
        )
        val initialized = GameInitializer(registry, services.printingRegistry).initializeGame(config).state
        val state = passToMain(initialized, services, afterTurn = 0)
        return Prepared(you, ai, registry, services, state)
    }

    private data class Prepared(
        val you: LocalSeat, val ai: LocalSeat, val registry: CardRegistry,
        val services: EngineServices, val state: GameState
    )

    private fun firstTurn(library: OfflineCardLibrary): TestScenarioGame {
        val prepared = prepare(library, "Grizzly Bears")
        var state = prepared.state
        val zones = ZoneTransitionService(prepared.registry, prepared.services.predicateEvaluator)
        for (name in listOf("Forest", "Elvish Mystic")) {
            if (state.getZone(LocalArgentumHost.HUMAN_ID, Zone.HAND).none {
                state.getEntity(it)?.get<CardComponent>()?.name == name
            }) state = zones.moveToZone(state, yourCard(state, name, Zone.LIBRARY), Zone.HAND).state
        }
        return TestScenarioGame(listOf(prepared.you, prepared.ai), prepared.registry,
            checkpoint(state, prepared.you, prepared.ai))
    }

    private fun combat(library: OfflineCardLibrary): TestScenarioGame {
        val prepared = prepare(library, "Grizzly Bears")
        val zones = ZoneTransitionService(prepared.registry, prepared.services.predicateEvaluator)
        var state = zones.moveToZone(prepared.state,
            yourCard(prepared.state, "Grizzly Bears", Zone.LIBRARY, Zone.HAND), Zone.BATTLEFIELD).state
        // Advance a full turn cycle through the real engine so summoning sickness is removed.
        state = passToMain(state, prepared.services, afterTurn = state.turnNumber)
        return TestScenarioGame(listOf(prepared.you, prepared.ai), prepared.registry,
            checkpoint(state, prepared.you, prepared.ai))
    }

    private fun passToMain(start: GameState, services: EngineServices, afterTurn: Int): GameState {
        var state = start
        val processor = ActionProcessor(services)
        var guard = 0
        while (!(state.turnNumber > afterTurn && state.step == Step.PRECOMBAT_MAIN &&
                    state.activePlayerId == LocalArgentumHost.HUMAN_ID &&
                    state.priorityPlayerId == LocalArgentumHost.HUMAN_ID &&
                    state.stack.isEmpty())) {
            check(guard++ < 160) { "Tutorial setup failed to reach main phase." }
            check(state.pendingDecision == null) { "Tutorial setup encountered an unexpected decision." }
            val priority = checkNotNull(state.priorityPlayerId)
            val result = processor.process(state, PassPriority(priority)).result
            check(result.error == null) { "Tutorial setup rejected PassPriority: ${result.error}" }
            state = result.state
        }
        return state
    }

    private fun yourCard(state: GameState, name: String, vararg zones: Zone): EntityId =
        zones.asSequence()
            .flatMap { state.getZone(LocalArgentumHost.HUMAN_ID, it).asSequence() }
            .firstOrNull { state.getEntity(it)?.get<CardComponent>()?.name == name }
            ?: error("Tutorial setup cannot find $name")

    private fun checkpoint(state: GameState, you: LocalSeat, ai: LocalSeat): JSONObject {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            allowStructuredMapKeys = true
            classDiscriminator = "type"
            serializersModule = engineSerializersModule
        }
        return JSONObject()
            .put("schema", OfflineCheckpointStore.FORMAT)
            .put("mode", "local-ai")
            .put("seats", JSONArray(listOf(you, ai).map(OfflineCheckpointStore::seatToJson)))
            .put("gameState", JSONObject(json.encodeToString(GameState.serializer(), state)))
            .put("version", 0L)
            .put("mulliganPhase", false)
            .put("quietOpponentTurns", false)
            .put("peopleOutSent", false)
            .put("gameLogs", JSONObject())
            .put("undoCheckpoint", JSONObject.NULL)
            .put("preCombatCheckpoint", JSONObject.NULL)
            .put("shockOffers", JSONArray())
    }
}
