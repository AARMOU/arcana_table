package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Aberrant
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class AberrantIndependentCertificationTest {
    private fun GameTestDriver.counterCount(permanent: EntityId): Int =
        state.getEntity(permanent)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    private fun GameTestDriver.castAberrantForX(x: Int): EntityId {
        val p = player1
        val aberrant = putCardInHand(p, "Aberrant")
        giveMana(p, Color.GREEN)
        giveColorlessMana(p, x + 1)
        assertNull(castXSpell(p, aberrant, x).error)
        resolveAll()
        return aberrant
    }

    private fun GameTestDriver.finishCombat(target: EntityId) {
        repeat(80) {
            if (state.step == Step.POSTCOMBAT_MAIN &&
                state.pendingDecision == null && state.stack.isEmpty()) return
            if (state.pendingDecision != null) decide(Choices(targets = listOf(target)))
            else assertNull(bothPass().error)
        }
        error("Combat did not settle: step=${state.step} decision=${state.pendingDecision}")
    }

    @Test
    fun xThreePaysTheGenericAndGreenCostAndEntersWithExactlyThreeCounters() {
        val d = customScenario(Aberrant)
        val aberrant = d.castAberrantForX(3)

        assertEquals(3, d.counterCount(aberrant))
        assertEquals(3, d.projected.getPower(aberrant))
        assertEquals(3, d.projected.getToughness(aberrant))
    }

    @Test
    fun ravenousAtXFiveDrawsExactlyOneCard() {
        val d = customScenario(Aberrant, "Forest", "Island")
        val p = d.player1
        val aberrant = d.putCardInHand(p, "Aberrant")
        val handBeforeCast = d.getHandSize(p)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 6)

        assertNull(d.castXSpell(p, aberrant, 5).error)
        d.resolveAll()

        assertEquals(5, d.counterCount(aberrant))
        assertEquals(5, d.projected.getPower(aberrant))
        assertEquals(5, d.projected.getToughness(aberrant))
        assertEquals("Casting spends one card and Ravenous draws exactly one", handBeforeCast, d.getHandSize(p))
    }

    @Test
    fun ravenousAtXFourAddsFourCountersButDoesNotDraw() {
        val d = customScenario(Aberrant, "Forest")
        val p = d.player1
        val aberrant = d.putCardInHand(p, "Aberrant")
        val handBeforeCast = d.getHandSize(p)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 5)

        assertNull(d.castXSpell(p, aberrant, 4).error)
        d.resolveAll()

        assertEquals(4, d.counterCount(aberrant))
        assertEquals(4, d.projected.getPower(aberrant))
        assertEquals(4, d.projected.getToughness(aberrant))
        assertEquals(handBeforeCast - 1, d.getHandSize(p))
    }

    @Test
    fun xGenericAndGreenManaAreBothRequired() {
        val d = customScenario(Aberrant)
        val p = d.player1
        val aberrant = d.putCardInHand(p, "Aberrant")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 2)

        assertNotNull("X=2 also costs the additional {1}", d.castXSpell(p, aberrant, 2).error)
        assertTrue(d.getHand(p).contains(aberrant))
        d.giveColorlessMana(p, 1)
        assertNull(d.castXSpell(p, aberrant, 2).error)
        d.resolveAll()
        assertEquals(2, d.counterCount(aberrant))

        val withoutGreen = customScenario(Aberrant)
        val unpaid = withoutGreen.putCardInHand(withoutGreen.player1, "Aberrant")
        withoutGreen.giveColorlessMana(withoutGreen.player1, 3)
        assertNotNull(withoutGreen.castXSpell(withoutGreen.player1, unpaid, 2).error)
        assertTrue(withoutGreen.getHand(withoutGreen.player1).contains(unpaid))
    }

    @Test
    fun trampleDealsExcessCombatDamagePastABlocker() {
        val d = customScenario(Aberrant, "Grizzly Bears")
        val aberrant = d.castAberrantForX(4)
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(aberrant)
        assertTrue(d.projected.hasKeyword(aberrant, Keyword.TRAMPLE))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(aberrant), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(aberrant))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(18, d.getLifeTotal(d.player2))
        assertFalse(d.getPermanents(d.player2).contains(blocker))
    }

    @Test
    fun combatDamageToPlayerDestroysThatPlayersArtifact() {
        val d = customScenario(Aberrant, "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val aberrant = d.castAberrantForX(1)
        val ownArtifact = d.putPermanentOnBattlefield(p, "Sol Ring")
        val targetArtifact = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        d.removeSummoningSickness(aberrant)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(aberrant), opponent).error)
        d.finishCombat(targetArtifact)

        assertEquals(19, d.getLifeTotal(opponent))
        assertFalse(d.getPermanents(opponent).contains(targetArtifact))
        assertTrue(d.getGraveyard(opponent).contains(targetArtifact))
        assertTrue(d.getPermanents(p).contains(ownArtifact))
    }

    @Test
    fun combatDamageToPlayerCanDestroyThatPlayersEnchantment() {
        val d = customScenario(Aberrant, "Phyrexian Arena")
        val p = d.player1
        val opponent = d.player2
        val aberrant = d.castAberrantForX(1)
        val arena = d.putPermanentOnBattlefield(opponent, "Phyrexian Arena")
        d.removeSummoningSickness(aberrant)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(aberrant), opponent).error)
        d.finishCombat(arena)

        assertFalse(d.getPermanents(opponent).contains(arena))
        assertTrue(d.getGraveyard(opponent).contains(arena))
    }

    @Test
    fun combatDamageOnlyToACreatureDoesNotTriggerTheHammer() {
        val d = customScenario(Aberrant, "Giant Spider", "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val aberrant = d.castAberrantForX(3)
        val blocker = d.putCreatureOnBattlefield(opponent, "Giant Spider")
        val artifact = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        d.removeSummoningSickness(aberrant)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(aberrant), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, mapOf(blocker to listOf(aberrant))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(20, d.getLifeTotal(opponent))
        assertTrue(d.getPermanents(opponent).contains(blocker))
        assertTrue(d.getPermanents(opponent).contains(artifact))
    }

    @Test
    fun hammerCannotTargetAnArtifactControlledByTheOtherPlayer() {
        val d = customScenario(Aberrant, "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val aberrant = d.castAberrantForX(1)
        val ownArtifact = d.putPermanentOnBattlefield(p, "Sol Ring")
        d.removeSummoningSickness(aberrant)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(aberrant), opponent).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(19, d.getLifeTotal(opponent))
        assertTrue(d.getPermanents(p).contains(ownArtifact))
    }
}
