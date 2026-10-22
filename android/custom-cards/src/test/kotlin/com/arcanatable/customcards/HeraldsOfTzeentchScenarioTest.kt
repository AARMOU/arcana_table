package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HeraldsOfTzeentch
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class HeraldsOfTzeentchScenarioTest {
    @Test
    fun cascadeSkipsLandAndCastsLowerManaValueSpellBeforeHeraldEnters() {
        val d = customScenario(HeraldsOfTzeentch, "Grizzly Bears")
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Plains")
        val herald = d.putCardInHand(p, "Heralds of Tzeentch")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, herald).error)
        assertEquals(2, d.state.stack.size)
        assertNull(d.findPermanent(p, "Heralds of Tzeentch"))
        d.resolveAll()

        assertEquals(1, d.permanentsNamed(p, "Grizzly Bears").size)
        val source = d.findPermanent(p, "Heralds of Tzeentch")!!
        assertTrue(d.projected.hasKeyword(source, Keyword.FLYING))
        assertEquals(3, d.projected.getPower(source))
        assertEquals(3, d.projected.getToughness(source))
    }

    @Test
    fun controllerMayDeclineTheCascadeSpell() {
        val d = customScenario(HeraldsOfTzeentch, "Grizzly Bears")
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        val herald = d.putCardInHand(p, "Heralds of Tzeentch")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, herald).error)
        d.resolveAll(Choices(yes = false))

        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertNotNull(d.findPermanent(p, "Heralds of Tzeentch"))
    }

    @Test
    fun enteringWithoutBeingCastDoesNotCascade() {
        val d = customScenario(HeraldsOfTzeentch, "Grizzly Bears")
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Heralds of Tzeentch")
        assertTrue(d.state.stack.isEmpty())
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
    }
}
