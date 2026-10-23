package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MatterReshaper
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class MatterReshaperScenarioTest {
    @Test
    fun deathMayPutLowManaValuePermanentOntoBattlefield() {
        val d = customScenario(MatterReshaper, "Forest", "Wrath of God")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Matter Reshaper")
        d.putCardOnTopOfLibrary(player, "Forest")
        val wrath = d.putCardInHand(player, "Wrath of God")
        d.giveMana(player, Color.WHITE, 2)
        d.giveColorlessMana(player, 2)

        assertNull(d.castSpell(player, wrath).error)
        d.resolveAll()

        assertEquals("The revealed land is a qualifying permanent and enters untapped", 1,
            d.permanentsNamed(player, "Forest").size)
        assertFalse(d.isTapped(d.permanentsNamed(player, "Forest").single()))
        assertTrue(d.zoneNames(player, Zone.LIBRARY).none { it == "Forest" })
    }

    @Test
    fun nonpermanentAndHighManaValueCardsGoToHandAndPlayerMayDecline() {
        val d = customScenario(MatterReshaper, "Counterspell", "Darksteel Colossus", "Forest", "Wrath of God")
        val player = d.player1

        fun resolveDeath(revealed: String, decline: Boolean = false) {
            d.putCreatureOnBattlefield(player, "Matter Reshaper")
            d.putCardOnTopOfLibrary(player, revealed)
            val wrath = d.putCardInHand(player, "Wrath of God")
            d.giveMana(player, Color.WHITE, 2)
            d.giveColorlessMana(player, 2)
            assertNull(d.castSpell(player, wrath).error)
            d.resolveAll(Choices(yes = !decline))
        }

        resolveDeath("Counterspell")
        assertTrue("An instant cannot be put onto the battlefield",
            d.zoneNames(player, Zone.HAND).contains("Counterspell"))

        resolveDeath("Darksteel Colossus")
        assertTrue("A permanent above mana value 3 goes into its owner's hand",
            d.zoneNames(player, Zone.HAND).contains("Darksteel Colossus"))

        resolveDeath("Forest", decline = true)
        assertTrue("Declining to put the qualifying permanent onto the battlefield puts it into hand",
            d.zoneNames(player, Zone.HAND).contains("Forest"))
        assertTrue(d.permanentsNamed(player, "Forest").isEmpty())
    }
}
