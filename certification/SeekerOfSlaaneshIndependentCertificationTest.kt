package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SeekerOfSlaanesh
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class SeekerOfSlaaneshIndependentCertificationTest {
    @Test
    fun costsThreeGenericAndOneRedAndItsHasteAllowsItToAttackImmediately() {
        val d = customScenario(SeekerOfSlaanesh)
        val p = d.player1
        val seeker = d.putCardInHand(p, "Seeker of Slaanesh")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 2)

        assertNotNull(d.castSpell(p, seeker).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Seeker of Slaanesh"))

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, seeker).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Seeker of Slaanesh").contains(seeker))
        assertEquals(3, d.projected.getPower(seeker))
        assertEquals(3, d.projected.getToughness(seeker))
        assertTrue(d.projected.hasKeyword(seeker, Keyword.HASTE))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = p)
        assertNull(d.declareAttackers(p, listOf(seeker), d.player2).error)
        assertTrue(d.isTapped(seeker))
    }

    @Test
    fun everyOpponentMustAttackWithAtLeastOneCreatureWhenAble() {
        val d = customScenario(SeekerOfSlaanesh, "Grizzly Bears")
        val deck = Deck.of("Plains" to 40)
        val players = d.initMultiplayer(listOf(deck, deck, deck))
        val controller = players[0]
        val firstOpponent = players[1]
        val secondOpponent = players[2]
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        d.putCreatureOnBattlefield(controller, "Seeker of Slaanesh")

        val firstAttacker = d.putCreatureOnBattlefield(firstOpponent, "Grizzly Bears")
        val firstExtra = d.putCreatureOnBattlefield(firstOpponent, "Grizzly Bears")
        val secondAttacker = d.putCreatureOnBattlefield(secondOpponent, "Grizzly Bears")
        val secondExtra = d.putCreatureOnBattlefield(secondOpponent, "Grizzly Bears")
        listOf(firstAttacker, firstExtra, secondAttacker, secondExtra).forEach(d::removeSummoningSickness)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = firstOpponent)
        assertNotNull(d.declareAttackers(firstOpponent, emptyList(), controller).error)
        assertNull(d.declareAttackers(firstOpponent, listOf(firstAttacker), controller).error)
        assertFalse(d.isTapped(firstExtra))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = secondOpponent)
        assertNotNull(d.declareAttackers(secondOpponent, emptyList(), controller).error)
        assertNull(d.declareAttackers(secondOpponent, listOf(secondAttacker), controller).error)
        assertFalse(d.isTapped(secondExtra))
    }

    @Test
    fun theAttackRequirementAppliesAgainOnEachCombat() {
        val d = customScenario(SeekerOfSlaanesh, "Grizzly Bears")
        val controller = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(controller, "Seeker of Slaanesh")
        val firstAttacker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val nextAttacker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(firstAttacker)
        d.removeSummoningSickness(nextAttacker)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        assertNotNull(d.declareAttackers(opponent, emptyList(), controller).error)
        assertNull(d.declareAttackers(opponent, listOf(firstAttacker), controller).error)
        assertTrue(d.isTapped(firstAttacker))
        assertFalse(d.isTapped(nextAttacker))

        d.passPriorityUntil(Step.POSTCOMBAT_MAIN, activePlayer = opponent)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        assertNotNull(d.declareAttackers(opponent, emptyList(), controller).error)
        assertNull(d.declareAttackers(opponent, listOf(nextAttacker), controller).error)
    }

    @Test
    fun anOpponentWithNoUntappedCreatureCanDeclareNoAttackers() {
        val d = customScenario(SeekerOfSlaanesh, "Grizzly Bears")
        val controller = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(controller, "Seeker of Slaanesh")
        val tappedCreature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        d.tapPermanent(tappedCreature)
        assertNull(d.declareAttackers(opponent, emptyList(), controller).error)
        assertTrue(d.isTapped(tappedCreature))
    }
}
