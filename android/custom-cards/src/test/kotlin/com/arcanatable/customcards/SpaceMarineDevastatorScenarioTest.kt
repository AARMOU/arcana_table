package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SpaceMarineDevastator
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import org.junit.Assert.*
import org.junit.Test

class SpaceMarineDevastatorScenarioTest {
    @Test
    fun entryDestroysAnArtifactWithoutPayingSquad() {
        val d = customScenario(SpaceMarineDevastator, "Sol Ring")
        val target = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val card = d.putCardInHand(d.player1, "Space Marine Devastator")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 3)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll(Choices(targets = listOf(target)))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Sol Ring"))
        assertEquals(1, d.permanentsNamed(d.player1, "Space Marine Devastator").size)
    }

    @Test
    fun entryCanChooseNoTargetEvenWhenAnArtifactIsAvailable() {
        val d = customScenario(SpaceMarineDevastator, "Sol Ring")
        val target = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val card = d.putCardInHand(d.player1, "Space Marine Devastator")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 3)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll()
        assertEquals(target, d.findPermanent(d.player2, "Sol Ring"))
        assertEquals(1, d.permanentsNamed(d.player1, "Space Marine Devastator").size)
    }

    @Test
    fun entryAlsoDestroysAnEnchantmentAndLeavesAnOrdinaryCreatureAlone() {
        val d = customScenario(SpaceMarineDevastator, "Glorious Anthem", "Grizzly Bears")
        val enchantment = d.putPermanentOnBattlefield(d.player2, "Glorious Anthem")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val card = d.putCardInHand(d.player1, "Space Marine Devastator")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 3)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll(Choices(targets = listOf(enchantment)))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Glorious Anthem"))
        assertEquals(bear, d.findPermanent(d.player2, "Grizzly Bears"))
    }

    @Test
    fun twoSquadPaymentsProduceTwoCopiesAndThreeIndependentRemovalTriggers() {
        val d = customScenario(SpaceMarineDevastator, "Sol Ring")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val secondArtifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val thirdArtifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val removalTargets = listOf(artifact, secondArtifact, thirdArtifact)
        val card = d.putCardInHand(d.player1, "Space Marine Devastator")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 7)
        assertNull(d.submit(CastSpell(d.player1, card,
            declaredCostSlot = ChoiceSlot.SQUAD, declaredCostTimes = 2,
            paymentStrategy = PaymentStrategy.FromPool)).error)

        val chosen = mutableSetOf<EntityId>()
        for (step in 0 until 100) {
            val decision = d.state.pendingDecision
            when {
                decision is ChooseTargetsDecision -> {
                    val selections = decision.targetRequirements.associate { requirement ->
                        val legal = decision.legalTargets[requirement.index].orEmpty()
                        val victim = removalTargets.firstOrNull { it in legal && it !in chosen }
                        val selected = if (victim != null) listOf(victim) else legal.take(requirement.minTargets)
                        chosen.addAll(selected)
                        requirement.index to selected
                    }
                    assertNull(d.submitDecision(decision.playerId, TargetsResponse(decision.id, selections)).error)
                }
                decision != null -> d.decide()
                d.state.stack.isNotEmpty() -> d.bothPass()
                else -> break
            }
        }

        assertNull(d.state.pendingDecision)
        assertTrue(d.state.stack.isEmpty())
        val marines = d.permanentsNamed(d.player1, "Space Marine Devastator")
        assertEquals(3, marines.size)
        assertEquals(2, marines.count { d.state.getEntity(it)?.has<TokenComponent>() == true })
        assertEquals(3, chosen.size)
        assertEquals(3, d.zoneNames(d.player2, Zone.GRAVEYARD).count { it == "Sol Ring" })
        assertTrue(d.permanentsNamed(d.player2, "Sol Ring").isEmpty())
    }
}
