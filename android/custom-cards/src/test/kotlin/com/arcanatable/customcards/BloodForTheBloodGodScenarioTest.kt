package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BloodForTheBloodGod
import com.arcanatable.customcards.support.*
    import com.wingedsheep.engine.state.components.player.CreaturesDiedThisTurnComponent
    import com.wingedsheep.sdk.core.Color
    import com.wingedsheep.sdk.core.Zone
    import org.junit.Assert.*
    import org.junit.Test

    class BloodForTheBloodGodScenarioTest {
        @Test
        fun aCreatureDeathReducesGenericCostAndResolvingDiscardsDrawsDamagesOpponentsAndExilesThisSpell() {
            val d = customScenario(BloodForTheBloodGod, "Grizzly Bears", "Doom Blade", "Sol Ring")
            val p = d.player1
            val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
            val doomBlade = d.putCardInHand(p, "Doom Blade")
            d.giveMana(p, Color.BLACK)
            d.giveColorlessMana(p, 1)
            assertNull(d.castSpell(p, doomBlade, listOf(bear)).error)
            d.resolveAll()
            val deaths = d.state.turnOrder.sumOf { playerId ->
                d.state.getEntity(playerId)!!.get<CreaturesDiedThisTurnComponent>()?.count ?: 0
            }
            assertEquals(1, deaths)

            val discardedCard = d.putCardInHand(p, "Sol Ring")
            val spell = d.putCardInHand(p, "Blood for the Blood God!")
            d.giveColorlessMana(p, 7)
            d.giveMana(p, Color.BLACK, 2)
            d.giveMana(p, Color.RED)
            assertNull("One death reduces the generic cost by one", d.castSpell(p, spell).error)
            d.resolveAll()

            assertTrue("The nonspell card in hand was discarded", discardedCard in d.getGraveyard(p))
            assertEquals("The spell draws eight after discarding its hand", 8, d.getHandSize(p))
            assertEquals("Only opponents take the eight damage", 12, d.getLifeTotal(d.player2))
            assertEquals(20, d.getLifeTotal(p))
            assertTrue("The resolving spell is exiled", "Blood for the Blood God!" in d.zoneNames(p, Zone.EXILE))
        }

        @Test
        fun noCreatureDeathsDoNotReduceThePrintedGenericCost() {
            val d = customScenario(BloodForTheBloodGod)
            val p = d.player1
            val spell = d.putCardInHand(p, "Blood for the Blood God!")
            d.getHand(p).filter { it != spell }.forEach(d::moveToGraveyard)
            d.giveColorlessMana(p, 7)
            d.giveMana(p, Color.BLACK, 2)
            d.giveMana(p, Color.RED)

            assertNotNull("Without a death, seven generic mana is insufficient", d.castSpell(p, spell).error)

            d.giveColorlessMana(p, 1)
            assertNull("The full printed cost is payable", d.castSpell(p, spell).error)
            d.resolveAll()

            assertEquals("The spell draws eight even when there is no hand to discard", 8, d.getHandSize(p))
            assertEquals(12, d.getLifeTotal(d.player2))
            assertEquals(20, d.getLifeTotal(p))
            assertTrue("The resolving spell is exiled", "Blood for the Blood God!" in d.zoneNames(p, Zone.EXILE))
        }
    }
}
