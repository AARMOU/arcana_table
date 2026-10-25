package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ChitteringDispatcher
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * CR 702.116a regression gate for issue #34/#36 and the original Incursion deck (#150).
 *
 * A two-player no-copy test is insufficient: in multiplayer, myriad MUST let the
 * attacking creature's controller choose each other opponent OR a planeswalker
 * that opponent controls. The pinned SDK's documented player-only simplification
 * cannot be counted as complete Oracle behavior. This test deliberately fails
 * if the integrated runtime has not incorporated the reviewed #41 engine patch.
 */
class ChitteringDispatcherMyriadDefenderScenarioTest {
    private fun GameTestDriver.untilDecision() {
        repeat(80) {
            if (state.pendingDecision != null || state.stack.isEmpty()) return
            bothPass()
        }
        fail("Myriad trigger did not reach a decision within 80 priority passes")
    }

    @Test fun threePlayerMyriadOffersOpponentOrTheirPlaneswalkerAndExilesCopyAtEndOfCombat() {
        val d = customScenario(ChitteringDispatcher)
        d.registerCard(card("Myriad Defender Probe Walker") {
            manaCost = "{3}"
            typeLine = "Legendary Planeswalker — Test"
            startingLoyalty = 5
        })
        val players = d.initMultiplayer(List(3) { Deck.of("Plains" to 40) })
        val (attackerPlayer, defendingPlayer, otherOpponent) = players
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        fun walkerFor(player: EntityId): EntityId =
            d.putPermanentOnBattlefield(player, "Myriad Defender Probe Walker").also { id ->
                d.replaceState(d.state.updateEntity(id) {
                    it.with(CountersComponent().withAdded(CounterType.LOYALTY, 5))
                })
            }

        val wrongWalker = walkerFor(defendingPlayer)
        val correctWalker = walkerFor(otherOpponent)
        val dispatcher = d.putCreatureOnBattlefield(attackerPlayer, "Chittering Dispatcher")
        d.removeSummoningSickness(dispatcher)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(attackerPlayer, listOf(dispatcher), defendingPlayer).error)

        d.untilDecision()
        val may = d.state.pendingDecision as? YesNoDecision
        assertNotNull("Myriad must offer its optional copy to the source controller", may)
        assertEquals(attackerPlayer, may!!.playerId)
        assertNull(d.submitYesNo(attackerPlayer, true).error)

        d.untilDecision()
        val targetChoice = d.state.pendingDecision as? ChooseTargetsDecision
        assertNotNull(
            "CR 702.116a requires a defender choice: other opponent OR their planeswalker; " +
                "a player-only auto-attacker is an unresolved engine blocker (#36)",
            targetChoice
        )
        assertEquals(attackerPlayer, targetChoice!!.playerId)
        assertEquals(
            "Only the visited opponent and that opponent's planeswalker are legal",
            setOf(otherOpponent, correctWalker),
            targetChoice.legalTargets[0].orEmpty().toSet()
        )
        assertNotNull("Cannot attack the already defending player's walker",
            d.submitTargetSelection(attackerPlayer, listOf(wrongWalker)).error)
        assertNull(d.submitTargetSelection(attackerPlayer, listOf(correctWalker)).error)

        d.untilDecision()
        val copies = d.getCreatures(attackerPlayer).filter {
            d.getCardName(it) == "Chittering Dispatcher" && it != dispatcher
        }
        assertEquals("One optional copy for one nondefending opponent", 1, copies.size)
        assertEquals(correctWalker, d.state.getEntity(copies.single())!!
            .get<AttackingComponent>()?.defenderId)

        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertEquals("Original remains after the copy is exiled", 1,
            d.getCreatures(attackerPlayer).count { d.getCardName(it) == "Chittering Dispatcher" })
        assertEquals("Exiled copy leaves and creates one Eldrazi Spawn", 1,
            d.getCreatures(attackerPlayer).count { d.getCardName(it) == "Eldrazi Spawn" })
    }
}
