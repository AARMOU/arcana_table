package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SeekerOfSlaanesh
import com.arcanatable.customcards.support.*
import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

/**
 * Seeker of Slaanesh (40K #85), Oracle: "Haste / Allure of Slaanesh — Each opponent must attack with
 * at least one creature each combat if able."
 *
 * The requirement is on each opponent as the attacking player (CR 508.1d): one attacking creature,
 * aimed at anyone, satisfies it; it lapses when none of their creatures can attack without paying a
 * cost; the Seeker's controller is not bound by it.
 */
class SeekerOfSlaaneshScenarioTest {

    private val seeker = "Seeker of Slaanesh"

    @Test
    fun hasteLetsItAttackTheTurnItArrives() {
        val d = customScenario(SeekerOfSlaanesh)
        val s = d.putCreatureOnBattlefield(d.player1, seeker)
        assertTrue(d.projected.hasKeyword(s, Keyword.HASTE))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, mapOf(s to d.player2)).error)
    }

    @Test
    fun anOpponentWithAnAbleCreatureMustAttackWithOne() {
        val d = customScenario(SeekerOfSlaanesh, "Centaur Courser", "Savannah Lions")
        d.putCreatureOnBattlefield(d.player1, seeker)
        val courser = d.putCreatureOnBattlefield(d.player2, "Centaur Courser")
        val lions = d.putCreatureOnBattlefield(d.player2, "Savannah Lions")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)

        assertNotNull("no attack is illegal", d.declareAttackers(d.player2, emptyMap()).error)
        assertNull("one creature is enough", d.declareAttackers(d.player2, mapOf(lions to d.player1)).error)
        assertFalse("the other may stay home", d.state.getEntity(courser)!!.has<com.wingedsheep.engine.state.components.combat.AttackingComponent>())
    }

    @Test
    fun noCreatureNamedAsMandatory() {
        val d = customScenario(SeekerOfSlaanesh, "Centaur Courser")
        d.putCreatureOnBattlefield(d.player1, seeker)
        d.putCreatureOnBattlefield(d.player2, "Centaur Courser")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)
        val action = LegalActionEnumerator.create(d.cardRegistry).enumerate(d.state, d.player2)
            .single { it.actionType == "DeclareAttackers" }
        assertNull(action.mandatoryAttackers)
    }

    @Test
    fun theControllerIsNotBound() {
        val d = customScenario(SeekerOfSlaanesh, "Centaur Courser")
        d.putCreatureOnBattlefield(d.player1, seeker)
        val own = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        d.removeSummoningSickness(own)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, emptyMap()).error)
    }

    @Test
    fun lapsesWhenNoCreatureCanAttack() {
        val d = customScenario(SeekerOfSlaanesh, "Centaur Courser")
        d.putCreatureOnBattlefield(d.player1, seeker)
        val courser = d.putCreatureOnBattlefield(d.player2, "Centaur Courser")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)
        d.tapPermanent(courser)
        assertNull("a tapped creature can't attack, so nothing is required", d.declareAttackers(d.player2, emptyMap()).error)
    }

    @Test
    fun anyDefenderCountsInMultiplayer() {
        val d = CardPool.driver("Centaur Courser")
        val (p1, p2, p3) = d.initMultiplayer(List(3) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        d.putCreatureOnBattlefield(p1, seeker)
        val courser = d.putCreatureOnBattlefield(p2, "Centaur Courser")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = p2)
        assertNotNull(d.declareAttackers(p2, emptyMap()).error)
        assertNull(
            "attacking a player other than the Seeker's controller obeys it",
            d.declareAttackers(p2, listOf(courser), p3).error,
        )
    }

    @Test
    fun theAiAttacksWithOneCreatureRatherThanStallingCombat() {
        val d = customScenario(SeekerOfSlaanesh, "Savannah Lions", "Centaur Courser", "Force of Nature")
        d.putCreatureOnBattlefield(d.player1, seeker)
        d.putCreatureOnBattlefield(d.player1, "Force of Nature")
        d.putCreatureOnBattlefield(d.player2, "Savannah Lions")
        d.putCreatureOnBattlefield(d.player2, "Centaur Courser")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)

        val action = LegalActionEnumerator.create(d.cardRegistry).enumerate(d.state, d.player2)
            .single { it.actionType == "DeclareAttackers" }
        val chosen = AIPlayer.create(d.cardRegistry, d.player2).chooseFrom(d.state, listOf(action)).action as DeclareAttackers
        assertEquals(1, chosen.attackers.size)
        assertNull(d.submit(chosen).error)
    }
}
