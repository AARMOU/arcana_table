package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AkromasWill
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class AkromasWillScenarioTest {
    @Test
    fun commanderLetsYouChooseBothModesAtCast() {
        val d = customScenario(AkromasWill, "Grizzly Bears", "Craw Wurm")
        val p = d.player1
        val commander = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.replaceState(d.state.updateEntity(commander) { it.with(CommanderComponent(ownerId = p)) })
        val anotherCreature = d.putCreatureOnBattlefield(p, "Craw Wurm")
        val will = d.putCardInHand(p, "Akroma's Will")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 3)

        val result = d.submit(
            CastSpell(
                playerId = p,
                cardId = will,
                chosenModes = listOf(0, 1),
                paymentStrategy = PaymentStrategy.FromPool,
            ),
        )
        assertNull(result.error)
        d.resolveStack()

        for (creature in listOf(commander, anotherCreature)) {
            assertTrue(d.projected.hasKeyword(creature, Keyword.FLYING))
            assertTrue(d.projected.hasKeyword(creature, Keyword.DOUBLE_STRIKE))
            assertTrue(d.projected.hasKeyword(creature, Keyword.LIFELINK))
            assertTrue(d.projected.hasKeyword(creature, Keyword.INDESTRUCTIBLE))
            val granted = d.projected.getKeywords(creature).toString()
            for (color in listOf("WHITE", "BLUE", "BLACK", "RED", "GREEN")) {
                assertTrue("Has protection from $color", granted.contains("PROTECTION_FROM_$color"))
            }
        }
    }

    @Test
    fun withoutACommanderOnlyOneModeCanBeChosen() {
        val d = customScenario(AkromasWill, "Grizzly Bears")
        val p = d.player1
        val creature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val will = d.putCardInHand(p, "Akroma's Will")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 3)

        val result = d.submit(
            CastSpell(
                playerId = p,
                cardId = will,
                chosenModes = listOf(1),
                paymentStrategy = PaymentStrategy.FromPool,
            ),
        )
        assertNull(result.error)
        d.resolveStack()

        assertFalse(d.projected.hasKeyword(creature, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(creature, Keyword.LIFELINK))
        assertTrue(d.projected.hasKeyword(creature, Keyword.INDESTRUCTIBLE))
    }
    @Test
    fun withoutACommanderCannotChooseBothModes() {
        val d = customScenario(AkromasWill)
        val p = d.player1
        val will = d.putCardInHand(p, "Akroma's Will")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 3)

        val result = d.submit(
            CastSpell(
                playerId = p,
                cardId = will,
                chosenModes = listOf(0, 1),
                paymentStrategy = PaymentStrategy.FromPool,
            ),
        )
        assertNotNull("Without a commander the spell permits only one mode", result.error)
    }

}
