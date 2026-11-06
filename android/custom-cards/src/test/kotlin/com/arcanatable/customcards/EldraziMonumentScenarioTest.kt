package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldraziMonument
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class EldraziMonumentScenarioTest {
    @Test
    fun buffsOnlyYourCreaturesAndUpkeepSacrificesOne() {
        val d = customScenario(EldraziMonument, "Grizzly Bears")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Eldrazi Monument")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opposingBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        assertEquals(3, d.projected.getPower(bear))
        assertEquals(3, d.projected.getToughness(bear))
        assertTrue(d.projected.hasKeyword(bear, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(bear, Keyword.INDESTRUCTIBLE))
        assertEquals(2, d.projected.getPower(opposingBear))
        assertFalse(d.projected.hasKeyword(opposingBear, Keyword.FLYING))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll()

        assertNotNull(d.findPermanent(p, "Eldrazi Monument"))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
    }

    @Test
    fun sacrificesMonumentWhenYouControlNoCreaturesAtUpkeep() {
        val d = customScenario(EldraziMonument)
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Eldrazi Monument")

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll()

        assertNull(d.findPermanent(p, "Eldrazi Monument"))
    }
    @Test
    fun chosenSacrificeWorksEvenWhenCreaturesAreIndestructible() {
        val d = customScenario(EldraziMonument, "Grizzly Bears", "Llanowar Elves")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Eldrazi Monument")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val elf = d.putCreatureOnBattlefield(p, "Llanowar Elves")

        assertTrue(d.projected.hasKeyword(bear, Keyword.INDESTRUCTIBLE))
        assertTrue(d.projected.hasKeyword(elf, Keyword.INDESTRUCTIBLE))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll(Choices(pick = listOf("Llanowar Elves")))

        assertNotNull("Monument remains when a creature can be sacrificed", d.findPermanent(p, "Eldrazi Monument"))
        assertEquals("The unchosen creature must remain", listOf(bear), d.permanentsNamed(p, "Grizzly Bears"))
        assertTrue("The chosen indestructible creature was sacrificed, not destroyed",
            d.permanentsNamed(p, "Llanowar Elves").isEmpty())
        assertTrue("Sacrifice moves the chosen card to its owner's graveyard",
            d.zoneNames(p, Zone.GRAVEYARD).contains("Llanowar Elves"))
    }

}
