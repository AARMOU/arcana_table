package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldraziConscription
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

/**
 * Rules audit for the annihilator 2 that Eldrazi Conscription grants (CR 702.86: "Whenever this
 * creature attacks, defending player sacrifices N permanents"). Complements
 * EldraziConscriptionScenarioTest, which covers a defender with exactly two permanents.
 */
class AnnihilatorRulesAuditTest {

    /** Enchants a fresh Grizzly Bears of [attackerOwner] with Eldrazi Conscription, ready to attack. */
    private fun GameTestDriver.conscriptedBear(attackerOwner: EntityId): EntityId {
        val bear = putCreatureOnBattlefield(attackerOwner, "Grizzly Bears")
        val aura = putCardInHand(attackerOwner, "Eldrazi Conscription")
        giveColorlessMana(attackerOwner, 8)
        assertNull(castSpell(attackerOwner, aura, listOf(bear)).error)
        resolveAll()
        removeSummoningSickness(bear)
        return bear
    }

    @Test
    fun defenderChoosesWhichTwoOfThreePermanentsToSacrificeBeforeBlockers() {
        val d = customScenario(EldraziConscription, "Grizzly Bears", "Forest")
        val attacker = d.conscriptedBear(d.player1)
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val keptBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val forest = d.putPermanentOnBattlefield(d.player2, "Forest")
        val defenderBefore = d.getPermanents(d.player2).toSet()

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(attacker), d.player2).error)
        assertEquals("Annihilator triggers on attack", 1, d.state.stack.size)

        // Resolve the trigger; the sacrifice is a choice for the DEFENDING player, still before blocks.
        var guard = 0
        while (d.pendingDecision !is SelectCardsDecision && d.state.stack.isNotEmpty() && guard++ < 10) d.bothPass()
        val choice = d.pendingDecision as? SelectCardsDecision
        assertNotNull("Defender must be asked which permanents to sacrifice (pending=${d.pendingDecision})", choice)
        choice!!
        assertEquals("Defending player chooses", d.player2, choice.playerId)
        assertEquals(2, choice.minSelections)
        assertEquals(2, choice.maxSelections)
        assertEquals("Every defender permanent is an option", defenderBefore, choice.options.toSet())
        assertEquals("Trigger resolves in the declare attackers step, before blockers", Step.DECLARE_ATTACKERS, d.state.step)

        val sacrificed = choice.options.filter { it != keptBear }
        d.submitCardSelection(d.player2, sacrificed)
        d.resolveAll()

        assertEquals(listOf(keptBear), d.getPermanents(d.player2))
        assertTrue("The Forest the defender chose was sacrificed", forest !in d.getPermanents(d.player2))
        assertEquals("Attacker's controller loses nothing", 2, d.getPermanents(d.player1).size)
    }

    @Test
    fun defenderWithOnePermanentSacrificesIt() {
        val d = customScenario(EldraziConscription, "Grizzly Bears")
        val attacker = d.conscriptedBear(d.player1)
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(attacker), d.player2).error)
        d.resolveAll()

        assertTrue(d.getPermanents(d.player2).isEmpty())
        assertEquals(2, d.getPermanents(d.player1).size)
    }

    @Test
    fun defenderWithNoPermanentsIsHarmless() {
        val d = customScenario(EldraziConscription, "Grizzly Bears")
        val attacker = d.conscriptedBear(d.player1)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(attacker), d.player2).error)
        d.resolveAll()

        assertTrue(d.getPermanents(d.player2).isEmpty())
        assertEquals("Attacker keeps creature and Aura", 2, d.getPermanents(d.player1).size)
    }

    @Test
    fun onlyTheEnchantedCreatureHasAnnihilator() {
        val d = customScenario(EldraziConscription, "Grizzly Bears")
        d.conscriptedBear(d.player1)
        val plainBear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.removeSummoningSickness(plainBear)
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(plainBear), d.player2).error)
        assertTrue("A non-enchanted attacker triggers nothing", d.state.stack.isEmpty())
        d.resolveAll()
        assertEquals(2, d.getPermanents(d.player2).size)
    }

    @Test
    fun annihilatorEndsWhenTheAuraLeaves() {
        val d = customScenario(EldraziConscription, "Grizzly Bears")
        val attacker = d.conscriptedBear(d.player1)
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.moveToGraveyard(d.findPermanent(d.player1, "Eldrazi Conscription")!!)

        assertEquals(2, d.projected.getPower(attacker))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(attacker), d.player2).error)
        assertTrue("No trigger once the Aura is gone", d.state.stack.isEmpty())
        d.resolveAll()
        assertEquals(2, d.getPermanents(d.player2).size)
    }

    @Test
    fun multiplayerOnlyThePlayerBeingAttackedSacrifices() {
        val d = CardPool.driver("Grizzly Bears").apply {
            registerCards(listOf(CardLoader.fromJsonPreservingIds(exportLine(EldraziConscription))))
        }
        val seats = d.initMultiplayer(List(3) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val (attackerSeat, bystander, defender) = seats
        val attacker = d.conscriptedBear(attackerSeat)
        repeat(2) { d.putCreatureOnBattlefield(bystander, "Grizzly Bears") }
        repeat(2) { d.putCreatureOnBattlefield(defender, "Grizzly Bears") }

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(attackerSeat, listOf(attacker), defender).error)
        d.resolveAll()

        assertTrue("The attacked player sacrifices", d.getPermanents(defender).isEmpty())
        assertEquals("Other opponents are untouched", 2, d.getPermanents(bystander).size)
        assertEquals(2, d.getPermanents(attackerSeat).size)
    }
}
