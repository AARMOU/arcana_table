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
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
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
 * Debug builds only: hard-coded boards for checking one interaction on a device, started as an
 * offline game against the AI through the normal resume path. The board is made by the engine
 * itself (GameInitializer, then cards moved from your library with ZoneTransitionService), and
 * the engine runs everything from there. Nothing is faked: summoning sickness stays as entered.
 */
internal object TestScenarios {
    val IRON_SPIDER = TestScenario(
        id = "iron-spider-mixed-counters",
        title = "Iron Spider — mixed counters",
        description = "Iron Spider, an Ornithopter with two +1/+1 and three charge counters, and a Sol Ring with one " +
            "+1/+1 counter, two Islands untapped. Activate “{2}, Remove two +1/+1 counters…: Draw a card.”",
    )

    val WHALE_JUST_CAST = TestScenario(
        id = "lunar-whale-just-cast",
        title = "Lunar Whale — just cast, Crew",
        description = "The Lunar Whale in hand, four Islands and a Llanowar Elves that entered this turn. Cast the " +
            "Whale, then Crew 1 with the Elves. It can't attack this turn (summoning sick).",
    )

    val WHALE_HELD = TestScenario(
        id = "lunar-whale-held",
        title = "Lunar Whale — held since last turn, Crew then attack",
        description = "Your turn 3: The Lunar Whale and a Llanowar Elves have been yours since your last turn. " +
            "Crew 1 with the Elves, then attack with the Whale.",
    )

    val DISPLACER_BLINK = TestScenario(
        id = "eldrazi-displacer-blink",
        title = "Eldrazi Displacer — blink and reset",
        description = "Eldrazi Displacer, Elvish Visionary with two +1/+1 counters, six Wastes, and an opposing " +
            "Grizzly Bears. Pay {2}{C} to blink Visionary: it returns tapped, loses its counters, and draws a card. " +
            "You can also blink the Bears; it returns tapped to the AI.",
    )

    val available: List<TestScenario> = listOf(IRON_SPIDER, WHALE_JUST_CAST, WHALE_HELD, DISPLACER_BLINK)

    private const val SPIDER = "Iron Spider, Stark Upgrade"
    private const val WHALE = "The Lunar Whale"
    private const val ELVES = "Llanowar Elves"

    fun build(scenario: TestScenario, library: OfflineCardLibrary): TestScenarioGame = when (scenario.id) {
        IRON_SPIDER.id -> ironSpider(library)
        WHALE_JUST_CAST.id -> whaleJustCast(library)
        WHALE_HELD.id -> whaleHeld(library)
        DISPLACER_BLINK.id -> displacerBlink(library)
        else -> error("Unknown test scenario ${scenario.id}")
    }

    private fun ironSpider(library: OfflineCardLibrary): TestScenarioGame {
        val you = LocalSeat("You", listOf(SPIDER, "Ornithopter", "Sol Ring") + List(37) { "Island" }, null)
        val ai = LocalSeat("Scenario AI", List(40) { "Island" }, null)
        val registry = CardRegistry().apply { register(library.load((you.deck + ai.deck).toSet())) }
        val services = EngineServices(registry)
        var state = mainPhaseOfYourFirstTurn(registry, services, you, ai)
        val zones = ZoneTransitionService(registry, services.predicateEvaluator)
        fun put(name: String): EntityId {
            val id = yourCard(state, name)
            state = zones.moveToZone(state, id, Zone.BATTLEFIELD).state
            return id
        }
        put(SPIDER)
        val thopter = put("Ornithopter")
        val ring = put("Sol Ring")
        put("Island"); put("Island")
        state = withCounters(state, thopter, CounterType.PLUS_ONE_PLUS_ONE to 2, CounterType.CHARGE to 3)
        state = withCounters(state, ring, CounterType.PLUS_ONE_PLUS_ONE to 1)
        return TestScenarioGame(listOf(you, ai), registry, checkpoint(state, you, ai))
    }

    private fun displacerBlink(library: OfflineCardLibrary): TestScenarioGame {
        val you = LocalSeat("You", listOf("Eldrazi Displacer", "Elvish Visionary") + List(38) { "Wastes" }, null)
        val ai = LocalSeat("Scenario AI", listOf("Grizzly Bears") + List(39) { "Island" }, null)
        val registry = CardRegistry().apply { register(library.load((you.deck + ai.deck).toSet())) }
        val services = EngineServices(registry)
        var state = mainPhaseOfYourFirstTurn(registry, services, you, ai)
        val zones = ZoneTransitionService(registry, services.predicateEvaluator)
        state = zones.moveToZone(state, yourCard(state, "Eldrazi Displacer"), Zone.BATTLEFIELD).state
        val visionary = yourCard(state, "Elvish Visionary")
        state = zones.moveToZone(state, visionary, Zone.BATTLEFIELD).state
        state = withCounters(state, visionary, CounterType.PLUS_ONE_PLUS_ONE to 2)
        repeat(6) { state = zones.moveToZone(state, yourCard(state, "Wastes"), Zone.BATTLEFIELD).state }
        val bear = listOf(Zone.LIBRARY, Zone.HAND).asSequence()
            .flatMap { state.getZone(LocalArgentumHost.AI_ID, it).asSequence() }
            .single { state.getEntity(it)?.get<CardComponent>()?.name == "Grizzly Bears" }
        state = zones.moveToZone(state, bear, Zone.BATTLEFIELD).state
        return TestScenarioGame(listOf(you, ai), registry, checkpoint(state, you, ai))
    }

    /** A real two-player game started by the engine, passed forward to your first precombat main phase. */
    private fun mainPhaseOfYourFirstTurn(registry: CardRegistry, services: EngineServices, you: LocalSeat, ai: LocalSeat): GameState {
        val config = GameConfig(
            players = listOf(
                PlayerConfig(name = you.name, deck = Deck(you.deck), playerId = LocalArgentumHost.HUMAN_ID, startingLife = 20, commanderCardNames = emptyList()),
                PlayerConfig(name = ai.name, deck = Deck(ai.deck), playerId = LocalArgentumHost.AI_ID, startingLife = 20, commanderCardNames = emptyList()),
            ),
            skipMulligans = true,
            startingPlayerIndex = 0,
            format = Format.Standard,
        )
        val state = GameInitializer(registry, services.printingRegistry).initializeGame(config).state
        return passToYourMainPhase(state, services, afterTurn = 0)
    }

    /**
     * Both players pass priority, as the engine runs it, until your precombat main phase of a turn
     * after [afterTurn] with an empty stack. Nobody plays anything, so turns pass for real: untap,
     * draw, and summoning sickness wear off exactly as in a game.
     */
    private fun passToYourMainPhase(start: GameState, services: EngineServices, afterTurn: Int): GameState {
        var state = start
        val processor = ActionProcessor(services)
        var guard = 0
        while (!(state.turnNumber > afterTurn && state.step == Step.PRECOMBAT_MAIN && state.activePlayerId == LocalArgentumHost.HUMAN_ID &&
                state.priorityPlayerId == LocalArgentumHost.HUMAN_ID && state.stack.isEmpty())) {
            check(guard++ < 120) { "Scenario setup didn't reach your main phase (turn ${state.turnNumber}, ${state.step})." }
            check(state.pendingDecision == null) { "Scenario setup stopped at a decision (turn ${state.turnNumber}, ${state.step})." }
            val priority = checkNotNull(state.priorityPlayerId) { "No player has priority during scenario setup (at ${state.step})." }
            val result = processor.process(state, PassPriority(priority)).result
            check(result.error == null) { "Scenario setup: ${result.error}" }
            state = result.state
        }
        return state
    }

    /** Whale in hand, cast for real by the player; the crew entered this turn (summoning sick). */
    private fun whaleJustCast(library: OfflineCardLibrary): TestScenarioGame {
        val you = LocalSeat("You", listOf(WHALE, ELVES) + List(38) { "Island" }, null)
        val ai = LocalSeat("Scenario AI", List(40) { "Island" }, null)
        val registry = CardRegistry().apply { register(library.load((you.deck + ai.deck).toSet())) }
        val services = EngineServices(registry)
        var state = mainPhaseOfYourFirstTurn(registry, services, you, ai)
        val zones = ZoneTransitionService(registry, services.predicateEvaluator)
        state = zones.moveToZone(state, yourCard(state, WHALE), Zone.HAND).state
        state = zones.moveToZone(state, yourCard(state, ELVES), Zone.BATTLEFIELD).state
        repeat(4) { state = zones.moveToZone(state, yourCard(state, "Island", Zone.LIBRARY), Zone.BATTLEFIELD).state }
        return TestScenarioGame(listOf(you, ai), registry, checkpoint(state, you, ai))
    }

    /**
     * Whale and Elves put onto your battlefield on turn 1, then both players pass through the AI's
     * turn to your turn 3: they've been under your control continuously since that turn began, so
     * the engine itself clears summoning sickness. The AI has one Island out (from its hand) so it
     * doesn't have to discard at its cleanup step.
     */
    private fun whaleHeld(library: OfflineCardLibrary): TestScenarioGame {
        val you = LocalSeat("You", listOf(WHALE, ELVES) + List(38) { "Island" }, null)
        val ai = LocalSeat("Scenario AI", List(40) { "Island" }, null)
        val registry = CardRegistry().apply { register(library.load((you.deck + ai.deck).toSet())) }
        val services = EngineServices(registry)
        var state = mainPhaseOfYourFirstTurn(registry, services, you, ai)
        val zones = ZoneTransitionService(registry, services.predicateEvaluator)
        state = zones.moveToZone(state, yourCard(state, WHALE), Zone.BATTLEFIELD).state
        state = zones.moveToZone(state, yourCard(state, ELVES), Zone.BATTLEFIELD).state
        repeat(2) { state = zones.moveToZone(state, yourCard(state, "Island", Zone.LIBRARY), Zone.BATTLEFIELD).state }
        val aiIsland = state.getZone(LocalArgentumHost.AI_ID, Zone.HAND).first()
        state = zones.moveToZone(state, aiIsland, Zone.BATTLEFIELD).state
        state = passToYourMainPhase(state, services, afterTurn = state.turnNumber)
        return TestScenarioGame(listOf(you, ai), registry, checkpoint(state, you, ai))
    }

    /** One of your cards with this name, from your library first (then your hand). */
    private fun yourCard(state: GameState, name: String, vararg from: Zone = arrayOf(Zone.LIBRARY, Zone.HAND)): EntityId {
        val you = LocalArgentumHost.HUMAN_ID
        return from.asSequence()
            .flatMap { state.getZone(you, it).asSequence() }
            .firstOrNull { state.getEntity(it)?.get<CardComponent>()?.name == name }
            ?: error("Scenario setup: no $name left in your ${from.joinToString("/") { it.name.lowercase() }}.")
    }

    private fun withCounters(state: GameState, id: EntityId, vararg counters: Pair<CounterType, Int>): GameState =
        state.updateEntity(id) { c ->
            c.with(counters.fold(c.get<CountersComponent>() ?: CountersComponent()) { acc, (type, n) -> acc.withAdded(type, n) })
        }

    /** The same checkpoint shape LocalArgentumHost saves and restores (OfflineCheckpointStore.FORMAT). */
    private fun checkpoint(state: GameState, you: LocalSeat, ai: LocalSeat): JSONObject {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true; classDiscriminator = "type"; serializersModule = engineSerializersModule }
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
