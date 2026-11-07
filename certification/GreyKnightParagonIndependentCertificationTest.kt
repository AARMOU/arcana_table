package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GreyKnightParagon
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class GreyKnightParagonIndependentCertificationTest {
    private val spell = "Grey Knight Paragon"

    @Test
    fun flashLetsItDestroyAnAttackingNonDemonDuringAnOpponentsCombat() {
        val d = customScenario(GreyKnightParagon, "Grizzly Bears", "Llanowar Elves")
        val player = d.player1
        val opponent = d.player2
        val attacker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val nonAttacker = d.putCreatureOnBattlefield(opponent, "Llanowar Elves")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        assertNull(d.declareAttackers(opponent, listOf(attacker), player).error)
        assertNull(d.passPriority(opponent).error)

        val paragon = d.putCardInHand(player, spell)
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 4)
        assertNull(d.castSpell(player, paragon).error)
        d.resolveAll(Choices(targets = listOf(attacker)))

        assertTrue(d.getPermanents(player).contains(paragon))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertTrue(d.getPermanents(opponent).contains(nonAttacker))
        assertFalse(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Llanowar Elves"))
    }

    @Test
    fun anAttackingDemonIsExiledInsteadOfDestroyed() {
        val d = customScenario(GreyKnightParagon, "Demon of Catastrophes")
        val player = d.player1
        val opponent = d.player2
        val demon = d.putCreatureOnBattlefield(opponent, "Demon of Catastrophes")
        d.removeSummoningSickness(demon)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        assertNull(d.declareAttackers(opponent, listOf(demon), player).error)
        assertNull(d.passPriority(opponent).error)

        val paragon = d.putCardInHand(player, spell)
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 4)
        assertNull(d.castSpell(player, paragon).error)
        d.resolveAll(Choices(targets = listOf(demon)))

        assertTrue(d.getPermanents(player).contains(paragon))
        assertTrue(d.zoneNames(opponent, Zone.EXILE).contains("Demon of Catastrophes"))
        assertFalse(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Demon of Catastrophes"))
        assertTrue(d.permanentsNamed(opponent, "Demon of Catastrophes").isEmpty())
    }

    @Test
    fun aNonattackingCreatureIsNotAValidTarget() {
        val d = customScenario(GreyKnightParagon, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)

        val paragon = d.putCardInHand(player, spell)
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 4)
        assertNull(d.castSpell(player, paragon).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(paragon))
        assertTrue(d.getPermanents(opponent).contains(bear))
        assertFalse(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun threeGenericAndOneWhiteManaAreNotEnoughToCast() {
        val d = customScenario(GreyKnightParagon)
        val player = d.player1
        val paragon = d.putCardInHand(player, spell)
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 3)

        assertNotNull(d.castSpell(player, paragon).error)
        assertTrue(d.getHand(player).contains(paragon))
        assertFalse(d.getPermanents(player).contains(paragon))
    }

    @Test
    fun fourColorlessManaCannotReplaceTheWhiteManaInItsCost() {
        val d = customScenario(GreyKnightParagon)
        val player = d.player1
        val paragon = d.putCardInHand(player, spell)
        d.giveColorlessMana(player, 4)

        assertNotNull(d.castSpell(player, paragon).error)
        assertTrue(d.getHand(player).contains(paragon))
        assertFalse(d.getPermanents(player).contains(paragon))
    }
}
