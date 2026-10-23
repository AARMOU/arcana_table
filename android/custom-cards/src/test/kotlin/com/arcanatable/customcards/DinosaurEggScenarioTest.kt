package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DinosaurEgg
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class DinosaurEggScenarioTest {
    @Test
    fun evolveAndDeathDiscoverArePresent() {
        val d = customScenario(DinosaurEgg, "Grizzly Bears", "Wrath of God")
        val egg = d.putCreatureOnBattlefield(d.player1, "Dinosaur Egg")
        d.putCardOnTopOfLibrary(d.player1, "Grizzly Bears")
        val wrath = d.putCardInHand(d.player1, "Wrath of God")
        d.giveMana(d.player1, Color.WHITE, 2)
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, wrath).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(d.player1, "Dinosaur Egg").isEmpty())
        assertTrue(
            "Discover casts or puts the qualifying card into hand",
            d.zoneNames(d.player1, com.wingedsheep.sdk.core.Zone.HAND).contains("Grizzly Bears") ||
                d.permanentsNamed(d.player1, "Grizzly Bears").isNotEmpty(),
        )
    }

    @Test
    fun evolvesWhenABiggerCreatureEnters() {
        val d = customScenario(DinosaurEgg, "Grizzly Bears")
        val egg = d.putCreatureOnBattlefield(d.player1, "Dinosaur Egg")
        assertEquals(0, d.projected.getPower(egg))
        val bears = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.GREEN)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, bears).error)
        d.resolveAll()
        assertEquals("evolve put a +1/+1 counter on the egg", 1, d.projected.getPower(egg))
        assertEquals(4, d.projected.getToughness(egg))
    }
}
