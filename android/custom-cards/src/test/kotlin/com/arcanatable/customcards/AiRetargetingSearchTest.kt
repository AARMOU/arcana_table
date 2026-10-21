package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.arcanatable.engine.ai.OfflineAiPolicy
import com.wingedsheep.ai.engine.RetargetingCandidateFilter
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class AiRetargetingSearchTest {
    @Test(timeout = 60000)
    fun targetlessSpellKeepsItsLegalActivationButDoesNotStartRetargetSearch() {
        val d = scenario("Spellskite", "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Spellskite")
        d.putLandOnBattlefield(d.player2, "Island")
        val spell = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.GREEN, 2)
        assertNull(d.castSpell(d.player1, spell).error)
        assertNull(d.submit(PassPriority(d.player1)).error)
        val offer = d.legalActions(d.player2).first { it.actionType == "ActivateAbility" && it.affordable }
        assertTrue(offer.validTargets.orEmpty().isNotEmpty())
        assertFalse(RetargetingCandidateFilter.hasPotentialEffect(d.state, offer, d.cardRegistry))
        assertTrue(OfflineAiPolicy.create(d.cardRegistry, d.player2).chooseAction(d.state) is PassPriority)
    }

    @Test(timeout = 60000)
    fun usefulSpellskiteRedirectIsRetainedAndSavesTheThreatenedCreature() {
        val d = scenario("Spellskite", "Grizzly Bears", "Murder")
        val skite = d.putCreatureOnBattlefield(d.player2, "Spellskite")
        val bears = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putLandOnBattlefield(d.player2, "Island")
        val murder = d.putCardInHand(d.player1, "Murder")
        d.giveMana(d.player1, Color.BLACK, 3)
        assertNull(d.castSpell(d.player1, murder, listOf(bears)).error)
        assertNull(d.submit(PassPriority(d.player1)).error)
        val offer = d.legalActions(d.player2).first { it.actionType == "ActivateAbility" && it.affordable }
        assertTrue(RetargetingCandidateFilter.hasPotentialEffect(d.state, offer, d.cardRegistry))
        val target = offer.validTargets!!.single()
        assertNull(d.submit((offer.action as ActivateAbility).copy(targets = listOf(ChosenTarget.Spell(target)))).error)
        d.resolveAll()
        assertTrue(bears in d.state.getBattlefield())
        assertFalse(skite in d.state.getBattlefield())
    }

    @Test
    fun burstExpiryIsSharedAndRestoresAfterAnException() {
        val d = scenario("Grizzly Bears")
        val normal = OfflineAiPolicy.budgetForDecision(d.state, d.player1)
        assertTrue(normal.millis > 0)
        try {
            OfflineAiPolicy.withThinkingBurst(searchMillis = 0) {
                assertTrue(OfflineAiPolicy.budgetForDecision(d.state, d.player1).expired())
                OfflineAiPolicy.withThinkingBurst(searchMillis = 600) {
                    assertTrue(OfflineAiPolicy.budgetForDecision(d.state, d.player1).expired())
                }
                error("scope test")
            }
        } catch (e: IllegalStateException) { assertEquals("scope test", e.message) }
        assertTrue(OfflineAiPolicy.budgetForDecision(d.state, d.player1).millis > 0)
    }
}
