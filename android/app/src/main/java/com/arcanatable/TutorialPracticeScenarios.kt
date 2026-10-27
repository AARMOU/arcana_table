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
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
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
    const val STACK = "stack"
    const val MULLIGAN = "opening-hand"
    /** A ready Commander board for lessons that explain the table rather than drill one action. */
    const val TOUR = "tour"

    /** Drills: every step waits for the learner to do it in the real game. */
    val supported = setOf(FIRST_TURN, COMBAT, STACK, MULLIGAN)

    fun build(id: String, library: OfflineCardLibrary): TestScenarioGame = when (id) {
        FIRST_TURN -> firstTurn(library)
        COMBAT -> combat(library)
        STACK -> stack(library)
        TOUR -> tour(library)
        else -> error("Unknown checkpoint tutorial $id")
    }

    const val YOUR_COMMANDER = "Ayula, Queen Among Bears"
    const val OPPONENT_COMMANDER = "Tetsuko Umezawa, Fugitive"

    /**
     * Your third turn of a two-player Commander game, main phase, with something in every zone:
     * commanders in the command zone, Forests and a Grizzly Bears (with a +1/+1 counter, able to
     * attack) on the battlefield, Forest / Elvish Mystic / Giant Growth in hand, a card in the
     * graveyard and one in exile. The opponent passes every turn.
     */
    private fun tour(library: OfflineCardLibrary): TestScenarioGame {
        val you = LocalSeat("You", listOf("Grizzly Bears", "Elvish Mystic", "Giant Growth", "Llanowar Elves") + List(36) { "Forest" }, YOUR_COMMANDER)
        val ai = LocalSeat("Training Opponent", List(40) { "Island" }, OPPONENT_COMMANDER)
        val registry = CardRegistry().apply { register(library.load((you.deck + ai.deck + listOf(YOUR_COMMANDER, OPPONENT_COMMANDER)).toSet())) }
        val services = EngineServices(registry)
        val config = GameConfig(
            players = listOf(
                PlayerConfig(name = you.name, deck = Deck(you.deck), playerId = LocalArgentumHost.HUMAN_ID, startingLife = 40, commanderCardNames = listOf(YOUR_COMMANDER)),
                PlayerConfig(name = ai.name, deck = Deck(ai.deck), playerId = LocalArgentumHost.AI_ID, startingLife = 40, commanderCardNames = listOf(OPPONENT_COMMANDER)),
            ),
            skipMulligans = true,
            startingPlayerIndex = 0,
            format = Format.Commander(),
            seed = 12025L,
        )
        var state = GameInitializer(registry, services.printingRegistry).initializeGame(config).state
        state = passToMain(state, services, afterTurn = 0)
        val zones = ZoneTransitionService(registry, services.predicateEvaluator)
        val bears = yourCard(state, "Grizzly Bears", Zone.LIBRARY, Zone.HAND)
        state = zones.moveToZone(state, bears, Zone.BATTLEFIELD).state
        repeat(2) { state = zones.moveToZone(state, yourCard(state, "Forest", Zone.HAND, Zone.LIBRARY), Zone.BATTLEFIELD).state }
        // The passive opponent keeps seven cards through its turn, so setup never stops for a discard.
        state = zones.moveToZone(state, state.getZone(LocalArgentumHost.AI_ID, Zone.HAND).first(), Zone.BATTLEFIELD).state
        // A full real turn cycle: the Bears lose summoning sickness and it is your turn again.
        state = passToMain(state, services, afterTurn = state.turnNumber)
        for (name in listOf("Forest", "Elvish Mystic", "Giant Growth")) {
            if (state.getZone(LocalArgentumHost.HUMAN_ID, Zone.HAND).none { state.getEntity(it)?.get<CardComponent>()?.name == name })
                state = zones.moveToZone(state, yourCard(state, name, Zone.LIBRARY), Zone.HAND).state
        }
        state = zones.moveToZone(state, yourCard(state, "Llanowar Elves", Zone.LIBRARY, Zone.HAND), Zone.GRAVEYARD).state
        state = zones.moveToZone(state, yourCard(state, "Forest", Zone.LIBRARY), Zone.EXILE).state
        state = state.updateEntity(bears) { c ->
            c.with((c.get<CountersComponent>() ?: CountersComponent()).withAdded(CounterType.PLUS_ONE_PLUS_ONE, 1))
        }
        return TestScenarioGame(listOf(you, ai), registry, checkpoint(state, you, ai))
    }

    fun openingHand(library: OfflineCardLibrary): Pair<List<LocalSeat>, CardRegistry> {
        val you = LocalSeat("You", listOf("Elvish Mystic", "Grizzly Bears") + List(38) { "Forest" }, null)
        val opponent = LocalSeat("Training Opponent", List(40) { "Island" }, null)
        val registry = registry(library, you, opponent)
        return listOf(you, opponent) to registry
    }

    private fun registry(library: OfflineCardLibrary, you: LocalSeat, ai: LocalSeat) =
        CardRegistry().apply { register(library.load((you.deck + ai.deck).toSet())) }

    private fun prepare(library: OfflineCardLibrary, creature: String, additional: String? = null): Prepared {
        val specials = listOf(creature, "Elvish Mystic") + listOfNotNull(additional)
        val you = LocalSeat("You", specials + List(40 - specials.size) { "Forest" }, null)
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
        // The passive opponent will draw an eighth card before cleanup. Give it a land
        // from its opening hand, as in the existing held-Vehicle scenarios, so setup
        // can advance through a real turn without stopping at a discard decision.
        val opponentIsland = state.getZone(LocalArgentumHost.AI_ID, Zone.HAND).first()
        state = zones.moveToZone(state, opponentIsland, Zone.BATTLEFIELD).state
        // Advance a full turn cycle through the real engine so summoning sickness is removed.
        state = passToMain(state, prepared.services, afterTurn = state.turnNumber)
        return TestScenarioGame(listOf(prepared.you, prepared.ai), prepared.registry,
            checkpoint(state, prepared.you, prepared.ai))
    }

    private fun stack(library: OfflineCardLibrary): TestScenarioGame {
        val prepared = prepare(library, "Grizzly Bears", "Giant Growth")
        val zones = ZoneTransitionService(prepared.registry, prepared.services.predicateEvaluator)
        var state = zones.moveToZone(prepared.state,
            yourCard(prepared.state, "Grizzly Bears", Zone.LIBRARY, Zone.HAND), Zone.BATTLEFIELD).state
        repeat(2) {
            state = zones.moveToZone(state, yourCard(state, "Forest", Zone.LIBRARY, Zone.HAND), Zone.BATTLEFIELD).state
        }
        if (state.getZone(LocalArgentumHost.HUMAN_ID, Zone.HAND).none {
            state.getEntity(it)?.get<CardComponent>()?.name == "Giant Growth"
        }) state = zones.moveToZone(state, yourCard(state, "Giant Growth", Zone.LIBRARY), Zone.HAND).state
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
