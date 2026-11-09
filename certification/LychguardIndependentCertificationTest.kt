package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Lychguard
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class LychguardIndependentCertificationTest {
    @Test
    fun returnsAllLegendaryCreatureCardsFromItsControllersGraveyard() {
        val d = customScenario(
            Lychguard,
            "Isamaru, Hound of Konda",
            "Captain Sisay",
            "Grizzly Bears",
            "Mox Amber",
        )
        val player = d.player1
        val opponent = d.player2
        val lychguard = d.putCreatureOnBattlefield(player, "Lychguard")
        d.putCardInGraveyard(player, "Isamaru, Hound of Konda")
        d.putCardInGraveyard(player, "Captain Sisay")
        d.putCardInGraveyard(player, "Grizzly Bears")
        d.putCardInGraveyard(player, "Mox Amber")
        d.putCardInGraveyard(opponent, "Isamaru, Hound of Konda")
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK)

        assertNull(d.activate(player, lychguard).error)
        assertFalse(d.permanentsNamed(player, "Lychguard").contains(lychguard))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Lychguard"))
        d.resolveAll()

        assertTrue(d.zoneNames(player, Zone.HAND).contains("Isamaru, Hound of Konda"))
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Captain Sisay"))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Mox Amber"))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Lychguard"))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Isamaru, Hound of Konda"))
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains("Captain Sisay"))
    }

    @Test
    fun activationRequiresThreeGenericAndOneBlackMana() {
        val missingBlack = customScenario(Lychguard)
        val player = missingBlack.player1
        val lychguard = missingBlack.putCreatureOnBattlefield(player, "Lychguard")
        missingBlack.giveColorlessMana(player, 3)

        assertNotNull(missingBlack.activate(player, lychguard).error)
        assertTrue(missingBlack.permanentsNamed(player, "Lychguard").contains(lychguard))
        assertFalse(missingBlack.zoneNames(player, Zone.GRAVEYARD).contains("Lychguard"))

        val missingGeneric = customScenario(Lychguard)
        val otherPlayer = missingGeneric.player1
        val otherLychguard = missingGeneric.putCreatureOnBattlefield(otherPlayer, "Lychguard")
        missingGeneric.giveColorlessMana(otherPlayer, 2)
        missingGeneric.giveMana(otherPlayer, Color.BLACK)

        assertNotNull(missingGeneric.activate(otherPlayer, otherLychguard).error)
        assertTrue(missingGeneric.permanentsNamed(otherPlayer, "Lychguard").contains(otherLychguard))
        assertFalse(missingGeneric.zoneNames(otherPlayer, Zone.GRAVEYARD).contains("Lychguard"))
    }

    @Test
    fun onlyItsControllerCanActivateTheAbility() {
        val d = customScenario(Lychguard, "Isamaru, Hound of Konda")
        val player = d.player1
        val opponent = d.player2
        val lychguard = d.putCreatureOnBattlefield(player, "Lychguard")
        d.putCardInGraveyard(player, "Isamaru, Hound of Konda")
        d.giveColorlessMana(opponent, 3)
        d.giveMana(opponent, Color.BLACK)
        assertNull(d.passPriority(player).error)

        assertNotNull(d.activate(opponent, lychguard).error)
        assertTrue(d.permanentsNamed(player, "Lychguard").contains(lychguard))
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains("Lychguard"))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Isamaru, Hound of Konda"))
    }

    @Test
    fun canBeActivatedDuringAnOpponentsTurn() {
        val d = customScenario(Lychguard, "Isamaru, Hound of Konda")
        val player = d.player1
        val opponent = d.player2
        val lychguard = d.putCreatureOnBattlefield(player, "Lychguard")
        d.putCardInGraveyard(player, "Isamaru, Hound of Konda")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK)
        assertNull(d.passPriority(opponent).error)

        assertNull(d.activate(player, lychguard).error)
        d.resolveAll()

        assertTrue(d.zoneNames(player, Zone.HAND).contains("Isamaru, Hound of Konda"))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Lychguard"))
    }
}
