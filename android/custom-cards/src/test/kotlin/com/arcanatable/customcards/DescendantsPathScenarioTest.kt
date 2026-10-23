package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DescendantsPath
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class DescendantsPathScenarioTest {
    @Test
    fun castsSharedTypeCreatureForFreeAndBottomsUncastOrUnmatchedCards() {
        val d = customScenario(DescendantsPath, "Bear Cub", "Craw Wurm", "Grizzly Bears")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putPermanentOnBattlefield(player, "Descendants' Path")
        d.putCardOnTopOfLibrary(player, "Bear Cub")

        d.passPriorityUntil(Step.UPKEEP, player)
        d.resolveAll()

        assertEquals("A shared-type creature should be cast without paying", 1,
            d.permanentsNamed(player, "Bear Cub").size)

        d.passPriorityUntil(Step.END, activePlayer = player)
        d.putCardOnTopOfLibrary(player, "Craw Wurm")
        d.passPriorityUntil(Step.UPKEEP, player)
        d.resolveAll()

        assertTrue("A creature without a shared type should not be cast",
            d.permanentsNamed(player, "Craw Wurm").isEmpty())
        assertEquals("An unmatched card should go to the bottom of the library", "Craw Wurm",
            d.zoneNames(player, Zone.LIBRARY).last())
    }

    @Test
    fun bottomsTheRevealedCardWhenYouDeclineToCastIt() {
        val d = customScenario(DescendantsPath, "Bear Cub", "Craw Wurm", "Grizzly Bears")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putPermanentOnBattlefield(player, "Descendants' Path")
        d.putCardOnTopOfLibrary(player, "Bear Cub")

        d.passPriorityUntil(Step.UPKEEP, player)
        d.resolveAll(Choices(yes = false))

        assertTrue("Declining the free cast should leave the card out of play",
            d.permanentsNamed(player, "Bear Cub").isEmpty())
        assertEquals("A declined card should go to the bottom of the library", "Bear Cub",
            d.zoneNames(player, Zone.LIBRARY).last())
    }
}
