package com.arcanatable

import com.arcanatable.engine.EmbeddedEngineTrial
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * #153: the Android app consumes exported JSONL, not the Kotlin DSL used by the
 * isolated Ugin rules scenarios. Exercise that real deserialization/registry
 * boundary and a game initialized from the exported card. No runtime swaps.
 */
class UginExportedRuntimeTest {
    private fun exportedUgin() =
        File("src/main/assets/argentum/custom-cards.jsonl")
            .bufferedReader().useLines { lines ->
                EmbeddedEngineTrial.loadCards(lines).single { it.name == "Ugin, the Ineffable" }
            }

    @Test fun exportedUginLoadsIntoAndroidRuntimeRegistry() {
        val ugin = exportedUgin()
        assertEquals(1, ugin.script.staticAbilities.size)
        assertEquals(2, ugin.script.activatedAbilities.size)
        val registry = CardRegistry().apply { register(listOf(ugin)) }
        assertNotNull("Android runtime must register the exported Ugin definition",
            registry.getCard("Ugin, the Ineffable"))
    }

    @Test fun exportedUginCanInitializeRealArgentumGame() {
        val plains = File("src/main/assets/argentum/por-cards.jsonl")
            .bufferedReader().useLines { lines ->
                EmbeddedEngineTrial.loadCards(lines).single { it.name == "Plains" }
            }
        val registry = CardRegistry().apply { register(listOf(plains, exportedUgin())) }
        val humanDeck = Deck(List(39) { "Plains" } + "Ugin, the Ineffable")
        val opponentDeck = Deck(List(40) { "Plains" })
        val initialized = GameInitializer(registry).initializeGame(
            GameConfig(
                players = listOf(
                    PlayerConfig("Ugin owner", humanDeck),
                    PlayerConfig("Opponent", opponentDeck)
                ),
                skipMulligans = true,
                startingPlayerIndex = 0
            )
        )
        assertEquals(2, initialized.state.turnOrder.size)
        assertFalse("Exported Ugin must not prevent game initialization", initialized.state.gameOver)
    }
}
