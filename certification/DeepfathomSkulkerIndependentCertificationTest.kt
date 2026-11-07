package com.arcanatable.customcards

// Trusted-main, real-Argentum acceptance scenarios for Deepfathom Skulker.
// Written from the Oracle record, not the implementation:
// "Devoid. Whenever a creature you control deals combat damage to a player, you may draw a card.
//  {3}{C}: Target creature can't be blocked this turn."
import com.arcanatable.customcards.cards.DeepfathomSkulker
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class DeepfathomSkulkerIndependentCertificationTest {
    private val name = "Deepfathom Skulker"

    @Test
    fun castingRequiresFiveGenericAndBlueAndDevoidLeavesItColorless() {
        val d = customScenario(DeepfathomSkulker)
        val p = d.player1
        val skulker = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.BLUE)

        assertNull(d.castSpell(p, skulker).error)
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(0, d.manaPool(p).blue)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(skulker))
        assertTrue(d.projected.getColors(skulker).isEmpty())
        assertEquals(4, d.projected.getPower(skulker))
        assertEquals(4, d.projected.getToughness(skulker))
    }

    @Test
    fun colorlessManaCannotPayTheBlueManaCost() {
        val d = customScenario(DeepfathomSkulker)
        val p = d.player1
        val skulker = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 6)

        assertNotNull(d.castSpell(p, skulker).error)

        assertTrue(d.getHand(p).contains(skulker))
        assertEquals(6, d.manaPool(p).colorless)
        assertTrue(d.permanentsNamed(p, name).isEmpty())
    }

    @Test
    fun combatDamageFromAnyCreatureYouControlDrawsACard() {
        val d = customScenario(DeepfathomSkulker, "Grizzly Bears", "Island")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, name)
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Island")
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = p)

        assertNull(d.declareAttackers(p, listOf(bear), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareNoBlockers(opponent).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Island"))
        assertFalse(d.zoneNames(p, Zone.LIBRARY).contains("Island"))
        assertEquals(18, d.getLifeTotal(opponent))
    }

    @Test
    fun theCombatDamageDrawMayBeDeclined() {
        val d = customScenario(DeepfathomSkulker, "Grizzly Bears", "Island")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, name)
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Island")
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = p)

        assertNull(d.declareAttackers(p, listOf(bear), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareNoBlockers(opponent).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll(Choices(yes = false))

        assertFalse(d.zoneNames(p, Zone.HAND).contains("Island"))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Island"))
        assertEquals(18, d.getLifeTotal(opponent))
    }

    @Test
    fun anOpponentsCreatureDealingCombatDamageDoesNotTriggerYourSkulker() {
        val d = customScenario(DeepfathomSkulker, "Grizzly Bears", "Island")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, name)
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Island")
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)

        assertNull(d.declareAttackers(opponent, listOf(bear), p).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareNoBlockers(p).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll()

        assertFalse(d.zoneNames(p, Zone.HAND).contains("Island"))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Island"))
        assertEquals(18, d.getLifeTotal(p))
    }

    @Test
    fun combatDamageToACreatureDoesNotTriggerTheDraw() {
        val d = customScenario(DeepfathomSkulker, "Grizzly Bears", "Island")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, name)
        val attacker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val blocker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Island")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = p)

        assertNull(d.declareAttackers(p, listOf(attacker), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, mapOf(blocker to listOf(attacker))).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll()

        assertFalse(d.zoneNames(p, Zone.HAND).contains("Island"))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Island"))
        assertEquals(20, d.getLifeTotal(opponent))
    }

    @Test
    fun payingThreeGenericAndOneColorlessMakesTheTargetUnblockableThisTurn() {
        val d = customScenario(DeepfathomSkulker, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val skulker = d.putCreatureOnBattlefield(p, name)
        val attacker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val blocker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        d.giveMana(p, Color.BLUE, 3)
        d.giveColorlessMana(p, 1)

        assertNull(d.activate(p, skulker).error)
        d.resolveAll(Choices(targets = listOf(attacker)))
        assertEquals(0, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).colorless)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = p)
        assertNull(d.declareAttackers(p, listOf(attacker), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(opponent, mapOf(blocker to listOf(attacker))).error)
        assertNull(d.declareNoBlockers(opponent).error)
    }

    @Test
    fun coloredManaCannotReplaceTheColorlessSymbolInTheActivationCost() {
        val d = customScenario(DeepfathomSkulker, "Grizzly Bears")
        val p = d.player1
        val skulker = d.putCreatureOnBattlefield(p, name)
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.giveMana(p, Color.BLUE, 4)

        assertNotNull(d.activate(p, skulker).error)

        assertEquals(4, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.permanentsNamed(p, name).contains(skulker))
    }

    @Test
    fun twoGenericAndOneColorlessManaCannotPayTheActivationCost() {
        val d = customScenario(DeepfathomSkulker, "Grizzly Bears")
        val p = d.player1
        val skulker = d.putCreatureOnBattlefield(p, name)
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.giveMana(p, Color.BLUE, 2)
        d.giveColorlessMana(p, 1)

        assertNotNull(d.activate(p, skulker).error)

        assertEquals(2, d.manaPool(p).blue)
        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.permanentsNamed(p, name).contains(skulker))
    }

    @Test
    fun theAbilityCanBeActivatedDuringAnOpponentsTurnOnTheirCreature() {
        val d = customScenario(DeepfathomSkulker, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val skulker = d.putCreatureOnBattlefield(p, name)
        val attacker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val blocker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        d.giveMana(p, Color.BLUE, 3)
        d.giveColorlessMana(p, 1)
        d.passPriority(opponent)

        assertNull(d.activate(p, skulker).error)
        d.resolveAll(Choices(targets = listOf(attacker)))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        assertNull(d.declareAttackers(opponent, listOf(attacker), p).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(p, mapOf(blocker to listOf(attacker))).error)
    }

    @Test
    fun theUnblockableEffectEndsAtTheEndOfTheTurn() {
        val d = customScenario(DeepfathomSkulker, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val skulker = d.putCreatureOnBattlefield(p, name)
        val attacker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val blocker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        d.giveMana(p, Color.BLUE, 3)
        d.giveColorlessMana(p, 1)

        assertNull(d.activate(p, skulker).error)
        d.resolveAll(Choices(targets = listOf(attacker)))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = p)
        assertNull(d.declareAttackers(p, listOf(attacker), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(opponent, mapOf(blocker to listOf(attacker))).error)
        assertNull(d.declareNoBlockers(opponent).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll(Choices(yes = false))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = p)

        assertNull(d.declareAttackers(p, listOf(attacker), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, mapOf(blocker to listOf(attacker))).error)
    }
}
