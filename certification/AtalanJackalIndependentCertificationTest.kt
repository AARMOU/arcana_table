package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AtalanJackal
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class AtalanJackalIndependentCertificationTest {
    @Test
    fun castingForOneGenericRedAndGreenEntersAsATwoTwoHumanTyranidScoutWithTrampleAndHaste() {
        val d = customScenario(AtalanJackal)
        val p = d.player1
        val jackal = d.putCardInHand(p, "Atalan Jackal")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)

        assertNull(d.castSpell(p, jackal).error)
        d.resolveAll()

        assertTrue(d.getPermanents(p).contains(jackal))
        assertEquals(2, d.projected.getPower(jackal))
        assertEquals(2, d.projected.getToughness(jackal))
        assertTrue(d.projected.hasSubtype(jackal, "HUMAN"))
        assertTrue(d.projected.hasSubtype(jackal, "TYRANID"))
        assertTrue(d.projected.hasSubtype(jackal, "SCOUT"))
        assertTrue(d.projected.hasKeyword(jackal, Keyword.TRAMPLE))
        assertTrue(d.projected.hasKeyword(jackal, Keyword.HASTE))
    }

    @Test
    fun twoGenericAndRedWithoutGreenCannotPayTheCastingCost() {
        val d = customScenario(AtalanJackal)
        val p = d.player1
        val jackal = d.putCardInHand(p, "Atalan Jackal")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.RED)

        assertNotNull(d.castSpell(p, jackal).error)

        assertTrue(d.getHand(p).contains(jackal))
        assertFalse(d.getPermanents(p).contains(jackal))
    }

    @Test
    fun twoGenericAndGreenWithoutRedCannotPayTheCastingCost() {
        val d = customScenario(AtalanJackal)
        val p = d.player1
        val jackal = d.putCardInHand(p, "Atalan Jackal")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.GREEN)

        assertNotNull(d.castSpell(p, jackal).error)

        assertTrue(d.getHand(p).contains(jackal))
        assertFalse(d.getPermanents(p).contains(jackal))
    }

    @Test
    fun redAndGreenWithoutGenericCannotPayTheCastingCost() {
        val d = customScenario(AtalanJackal)
        val p = d.player1
        val jackal = d.putCardInHand(p, "Atalan Jackal")
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)

        assertNotNull(d.castSpell(p, jackal).error)

        assertTrue(d.getHand(p).contains(jackal))
        assertFalse(d.getPermanents(p).contains(jackal))
    }

    @Test
    fun hasteAllowsAnImmediateAttackThatDealsDamageAndFindsATappedBasicLand() {
        val d = customScenario(AtalanJackal, "Command Tower")
        val p = d.player1
        val opponent = d.player2
        val jackal = d.putCardInHand(p, "Atalan Jackal")
        d.putCardOnTopOfLibrary(p, "Command Tower")
        d.putCardOnTopOfLibrary(p, "Forest")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, jackal).error)
        d.resolveAll()

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(jackal), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, emptyMap()).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)

        assertEquals(18, d.getLifeTotal(opponent))
        d.resolveAll(Choices(yes = true, pick = listOf("Forest")))

        val forest = d.permanentsNamed(p, "Forest").single()
        assertTrue(d.isTapped(forest))
        assertFalse(d.zoneNames(p, Zone.LIBRARY).contains("Forest"))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Command Tower"))
    }

    @Test
    fun trampleDealsExcessCombatDamageToThePlayerAndTriggersTheBasicLandSearch() {
        val d = customScenario(AtalanJackal, "Llanowar Elves")
        val p = d.player1
        val opponent = d.player2
        val jackal = d.putCreatureOnBattlefield(p, "Atalan Jackal")
        val blocker = d.putCreatureOnBattlefield(opponent, "Llanowar Elves")
        d.putCardOnTopOfLibrary(p, "Forest")
        d.removeSummoningSickness(jackal)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(jackal), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, mapOf(blocker to listOf(jackal))).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)

        assertEquals(19, d.getLifeTotal(opponent))
        assertTrue(d.getGraveyardCardNames(opponent).contains("Llanowar Elves"))
        d.resolveAll(Choices(yes = true, pick = listOf("Forest")))

        val forest = d.permanentsNamed(p, "Forest").single()
        assertTrue(d.isTapped(forest))
        assertFalse(d.zoneNames(p, Zone.LIBRARY).contains("Forest"))
    }

    @Test
    fun damageToOnlyABlockingCreatureDoesNotTriggerThePlayerDamageAbility() {
        val d = customScenario(AtalanJackal, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val jackal = d.putCreatureOnBattlefield(p, "Atalan Jackal")
        val blocker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Forest")
        d.removeSummoningSickness(jackal)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(jackal), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, mapOf(blocker to listOf(jackal))).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(opponent))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Forest"))
        assertTrue(d.permanentsNamed(p, "Forest").isEmpty())
    }

    @Test
    fun decliningTheOptionalSearchLeavesTheBasicLandInTheLibrary() {
        val d = customScenario(AtalanJackal)
        val p = d.player1
        val opponent = d.player2
        val jackal = d.putCreatureOnBattlefield(p, "Atalan Jackal")
        d.putCardOnTopOfLibrary(p, "Forest")
        d.removeSummoningSickness(jackal)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(jackal), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, emptyMap()).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll(Choices(yes = false))

        assertEquals(18, d.getLifeTotal(opponent))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Forest"))
        assertTrue(d.permanentsNamed(p, "Forest").isEmpty())
    }
}
