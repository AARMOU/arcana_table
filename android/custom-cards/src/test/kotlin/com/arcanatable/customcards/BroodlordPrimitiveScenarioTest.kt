package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.ravenous
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import org.junit.Assert.*
import org.junit.Test

/**
 * #359: real-Argentum proof for the combination Broodlord needs.
 *
 * Test-local card, deliberately NOT registered or exported. The pinned engine
 * must preserve cast X through Ravenous and a second ETB trigger, while
 * restricting distributed counters to *other* friendly creatures.
 * Passing these tests does not itself certify the entire printed card.
 */
class BroodlordPrimitiveScenarioTest {
    private val candidate = card("Broodlord Review Candidate") {
        manaCost = "{X}{3}{G}"
        colorIdentity = "G"
        typeLine = "Creature — Tyranid"
        power = 3
        toughness = 3
        oracleText = "Ravenous\nWhen this creature enters, distribute X +1/+1 counters " +
            "among any number of other target creatures you control."
        ravenous()
        triggeredAbility {
            trigger = Triggers.self.enters()
            targets(
                TargetFilter.OtherCreatureYouControl,
                unlimited = true,
                dynamicMaxCount = DynamicAmounts.castX(),
            )
            effect = Effects.DistributeCountersAmongTargets(DynamicAmounts.castX())
        }
    }

    @Test
    fun xZeroDoesNotRequireTargetOrDraw() {
        val d = customScenario(candidate, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val broodlord = d.putCardInHand(p, "Broodlord Review Candidate")
        val before = d.getHand(p).size
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 3)
        assertNull(d.castXSpell(p, broodlord, 0).error)
        d.resolveAll()
        assertEquals(3, d.projected.getPower(broodlord))
        assertEquals(2, d.projected.getPower(bear))
        assertEquals(before - 1, d.getHand(p).size)
    }

    @Test
    fun xTwoAddsCountersToAnotherFriendlyCreatureWithoutDrawing() {
        val d = customScenario(candidate, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val broodlord = d.putCardInHand(p, "Broodlord Review Candidate")
        val before = d.getHand(p).size
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 5)
        assertNull(d.castXSpell(p, broodlord, 2).error)
        d.resolveAll(Choices(targets = listOf(bear)))
        assertEquals(5, d.projected.getPower(broodlord))
        assertEquals(4, d.projected.getPower(bear))
        assertEquals(before - 1, d.getHand(p).size)
    }

    @Test
    fun xFiveDrawsOnceAndDistributesFiveSeparately() {
        val d = customScenario(candidate, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val broodlord = d.putCardInHand(p, "Broodlord Review Candidate")
        val before = d.getHand(p).size
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 8)
        assertNull(d.castXSpell(p, broodlord, 5).error)
        d.resolveAll(Choices(targets = listOf(bear)))
        assertEquals(8, d.projected.getPower(broodlord))
        assertEquals(7, d.projected.getPower(bear))
        assertEquals(before, d.getHand(p).size)
    }

    @Test
    fun opponentsCreaturesAreNotValidDistributionRecipients() {
        val d = customScenario(candidate, "Grizzly Bears")
        val p = d.player1
        val opponentBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val broodlord = d.putCardInHand(p, "Broodlord Review Candidate")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 5)
        assertNull(d.castXSpell(p, broodlord, 2).error)
        // The helper declines optional targets when the preferred opponent is
        // not legal. A real target-legality assertion is still needed.
        d.resolveAll(Choices(targets = listOf(opponentBear)))
        assertEquals(2, d.projected.getPower(opponentBear))
        assertEquals(5, d.projected.getPower(broodlord))
    }
}
