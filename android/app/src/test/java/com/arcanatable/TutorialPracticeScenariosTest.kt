package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.OfflineCardLibrary
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ship gate: actually build each tutorial state using the real pinned Argentum engine.
 * Debug-only TestScenarios don't count here: these drills are compiled into release.
 */
class TutorialPracticeScenariosTest {
    private val assets = File("src/main/assets")
    private fun library() = OfflineCardLibrary(open = { path ->
        File(assets, path).takeIf(File::exists)?.inputStream()
    })

    private val codec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = true
        classDiscriminator = "type"
        serializersModule = engineSerializersModule
    }

    private fun state(id: String): GameState {
        val game = TutorialPracticeScenarios.build(id, library())
        assertEquals(2, game.seats.size)
        assertEquals("local-ai", game.checkpoint.getString("mode"))
        assertEquals(OfflineCheckpointStore.FORMAT, game.checkpoint.getInt("schema"))
        assertEquals(false, game.checkpoint.getBoolean("mulliganPhase"))
        return codec.decodeFromString(GameState.serializer(), game.checkpoint.getJSONObject("gameState").toString())
    }

    private fun names(state: GameState, zone: Zone) =
        state.getZone(LocalArgentumHost.HUMAN_ID, zone).mapNotNull {
            state.getEntity(it)?.get<CardComponent>()?.name
        }

    @Test fun firstTurnHasLandAndCastableOneManaCreatureInRealHand() {
        val state = state(TutorialPracticeScenarios.FIRST_TURN)
        assertEquals("You should start with priority", LocalArgentumHost.HUMAN_ID, state.priorityPlayerId)
        assertTrue("Forest must be playable from hand", "Forest" in names(state, Zone.HAND))
        assertTrue("Elvish Mystic must be castable from hand", "Elvish Mystic" in names(state, Zone.HAND))
    }

    @Test fun combatBeginsWithActualGrizzlyBearsPermanent() {
        val state = state(TutorialPracticeScenarios.COMBAT)
        assertTrue("Real creature missing from battlefield", "Grizzly Bears" in names(state, Zone.BATTLEFIELD))
        assertEquals(LocalArgentumHost.HUMAN_ID, state.activePlayerId)
    }

    @Test fun stackDrillHasValidTargetInstantAndManaSources() {
        val state = state(TutorialPracticeScenarios.STACK)
        assertTrue("Giant Growth missing from hand", "Giant Growth" in names(state, Zone.HAND))
        assertTrue("Grizzly Bears missing", "Grizzly Bears" in names(state, Zone.BATTLEFIELD))
        assertTrue("Need two Forests for paying costs", names(state, Zone.BATTLEFIELD).count { it == "Forest" } >= 2)
        assertEquals(LocalArgentumHost.HUMAN_ID, state.priorityPlayerId)
    }

    @Test fun mulliganDrillUsesTwoRegisteredSeats() {
        val (seats, registry) = TutorialPracticeScenarios.openingHand(library())
        assertEquals(2, seats.size)
        assertEquals(40, seats[0].deck.size)
        assertTrue(registry.getCard("Forest") != null)
    }
}
