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

    // The two Lunar Whale scenarios wait for draft PR #109 (Crew), per GPT's sequencing on #81.
    val available: List<TestScenario> = listOf(IRON_SPIDER)

    private const val SPIDER = "Iron Spider, Stark Upgrade"

    fun build(scenario: TestScenario, library: OfflineCardLibrary): TestScenarioGame = when (scenario.id) {
        IRON_SPIDER.id -> ironSpider(library)
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
        var state = GameInitializer(registry, services.printingRegistry).initializeGame(config).state
        val processor = ActionProcessor(services)
        var guard = 0
        while (!(state.step == Step.PRECOMBAT_MAIN && state.activePlayerId == LocalArgentumHost.HUMAN_ID &&
                state.priorityPlayerId == LocalArgentumHost.HUMAN_ID && state.stack.isEmpty())) {
            check(guard++ < 50) { "Scenario setup didn't reach your main phase (at ${state.step})." }
            val priority = checkNotNull(state.priorityPlayerId) { "No player has priority during scenario setup (at ${state.step})." }
            val result = processor.process(state, PassPriority(priority)).result
            check(result.error == null) { "Scenario setup: ${result.error}" }
            state = result.state
        }
        return state
    }

    /** One of your cards with this name, from your library first (then your hand). */
    private fun yourCard(state: GameState, name: String): EntityId {
        val you = LocalArgentumHost.HUMAN_ID
        return listOf(Zone.LIBRARY, Zone.HAND).asSequence()
            .flatMap { state.getZone(you, it).asSequence() }
            .firstOrNull { state.getEntity(it)?.get<CardComponent>()?.name == name }
            ?: error("Scenario setup: no $name left in your library or hand.")
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
