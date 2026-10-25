package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UginTheIneffable
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.CounterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Extra real-engine negative cases for the registered #153 Ugin candidate.
 *
 * These deliberately verify a legal priority window before a rejected action:
 * "you don't have priority" must not accidentally pass an Oracle legality test.
 */
class UginLoyaltyAndDevoidNegativeScenarioTest {
    @Test fun plusOneCannotBeActivatedTwiceDuringTheSameTurn() {
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        val ugin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")

        assertEquals(p, d.priorityPlayer)
        assertNull(d.activate(p, ugin, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Spirit").size)
        val loyaltyAfterFirst = d.state.getEntity(ugin)!!
            .get<CountersComponent>()!!.getCount(CounterType.LOYALTY)

        assertEquals("The active player must have priority for the negative test", p, d.priorityPlayer)
        val rejected = d.activate(p, ugin, index = 0)
        assertNotNull("A second loyalty activation in one turn must be rejected", rejected.error)
        assertEquals(1, d.permanentsNamed(p, "Spirit").size)
        assertEquals(loyaltyAfterFirst, d.state.getEntity(ugin)!!
            .get<CountersComponent>()!!.getCount(CounterType.LOYALTY))
    }

    @Test fun minusThreeCannotDestroyDevoidEldraziWithPrintedWhiteCost() {
        val d = customScenario(UginTheIneffable)
        val p = d.player1

        // Cast for the printed {6}; Ugin is not yet on the battlefield to
        // reduce its own cost. This establishes the real initial four loyalty.
        val card = d.putCardInHand(p, "Ugin, the Ineffable")
        d.giveColorlessMana(p, 6)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        val ugin = d.permanentsNamed(p, "Ugin, the Ineffable").single()
        val displacer = d.putCreatureOnBattlefield(d.player2, "Eldrazi Displacer")
        val loyaltyBefore = d.state.getEntity(ugin)!!
            .get<CountersComponent>()!!.getCount(CounterType.LOYALTY)
        assertEquals(4, loyaltyBefore)

        // Eldrazi Displacer has a printed {W} and white color identity, but
        // Devoid makes the permanent colorless. It is NOT a legal -3 target.
        assertEquals("Rejection must be for target legality, not lost priority", p, d.priorityPlayer)
        val ability = d.cardRegistry.getCard("Ugin, the Ineffable")!!
            .script.activatedAbilities[1]
        val rejected = d.submit(ActivateAbility(
            p, ugin, ability.id,
            targets = listOf(ChosenTarget.Permanent(displacer))
        ))
        assertNotNull("Devoid permanent is colorless despite printed {W}", rejected.error)
        assertEquals(1, d.permanentsNamed(d.player2, "Eldrazi Displacer").size)
        assertEquals(loyaltyBefore, d.state.getEntity(ugin)!!
            .get<CountersComponent>()!!.getCount(CounterType.LOYALTY))
    }
    @Test fun minusThreeResolvesButDoesNotDestroyIndestructibleColoredCreature() {
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        val fixture = com.wingedsheep.sdk.dsl.card("Ugin Indestructible Green Probe") {
            manaCost = "{G}"
            colorIdentity = "G"
            typeLine = "Creature — Beast"
            power = 2
            toughness = 2
            keywords(com.wingedsheep.sdk.core.Keyword.INDESTRUCTIBLE)
        }
        d.registerCard(fixture)

        val card = d.putCardInHand(p, "Ugin, the Ineffable")
        d.giveColorlessMana(p, 6)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        val ugin = d.permanentsNamed(p, "Ugin, the Ineffable").single()
        val target = d.putCreatureOnBattlefield(d.player2, fixture.name)
        val ability = d.cardRegistry.requireCard("Ugin, the Ineffable").script.activatedAbilities[1]

        assertEquals(p, d.priorityPlayer)
        assertNull("Colored indestructible permanent is a legal target",
            d.submit(ActivateAbility(p, ugin, ability.id,
                targets = listOf(ChosenTarget.Permanent(target)))).error)
        d.resolveAll()
        assertEquals("Ugin must pay the -3 loyalty cost even if destruction is prevented", 1,
            d.state.getEntity(ugin)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY))
        assertEquals("Indestructible prevents destruction, not targeting",
            listOf(target), d.permanentsNamed(d.player2, fixture.name))
        assertFalse(d.getGraveyardCardNames(d.player2).contains(fixture.name))
    }

    @Test fun minusThreeRejectsOpposingHexproofButCanTargetOwnHexproofColoredCreature() {
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        val fixture = com.wingedsheep.sdk.dsl.card("Ugin Hexproof Green Probe") {
            manaCost = "{G}"
            colorIdentity = "G"
            typeLine = "Creature — Beast"
            power = 2
            toughness = 2
            keywords(com.wingedsheep.sdk.core.Keyword.HEXPROOF)
        }
        d.registerCard(fixture)

        val card = d.putCardInHand(p, "Ugin, the Ineffable")
        d.giveColorlessMana(p, 6)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        val ugin = d.permanentsNamed(p, "Ugin, the Ineffable").single()
        val opponentTarget = d.putCreatureOnBattlefield(d.player2, fixture.name)
        val ownTarget = d.putCreatureOnBattlefield(p, fixture.name)
        val ability = d.cardRegistry.requireCard("Ugin, the Ineffable").script.activatedAbilities[1]

        assertEquals(p, d.priorityPlayer)
        assertNotNull("Opponent's hexproof creature must not be targetable",
            d.submit(ActivateAbility(p, ugin, ability.id,
                targets = listOf(ChosenTarget.Permanent(opponentTarget)))).error)
        assertEquals(4, d.state.getEntity(ugin)!!
            .get<CountersComponent>()!!.getCount(CounterType.LOYALTY))
        assertEquals(p, d.priorityPlayer)
        assertNull("Your own hexproof creature remains a legal colored target",
            d.submit(ActivateAbility(p, ugin, ability.id,
                targets = listOf(ChosenTarget.Permanent(ownTarget)))).error)
        d.resolveAll()
        assertEquals(1, d.state.getEntity(ugin)!!
            .get<CountersComponent>()!!.getCount(CounterType.LOYALTY))
        assertTrue(d.permanentsNamed(p, fixture.name).isEmpty())
        assertEquals(listOf(opponentTarget), d.permanentsNamed(d.player2, fixture.name))
    }

}
