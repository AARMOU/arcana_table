package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MonstrousOnslaught
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.DistributeDecision
import com.wingedsheep.engine.core.DistributionResponse
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class MonstrousOnslaughtIndependentCertificationTest {
    private val name = "Monstrous Onslaught"

    private fun GameTestDriver.payCost(player: EntityId) {
        giveColorlessMana(player, 3)
        giveMana(player, Color.GREEN, 2)
    }

    private fun GameTestDriver.chooseDamage(distribution: Map<EntityId, Int>) {
        val decision = state.pendingDecision
        assertTrue("The spell must ask how to divide its damage", decision is DistributeDecision)
        decision as DistributeDecision
        assertEquals(decision.targets.toSet(), distribution.keys)
        assertEquals(decision.totalAmount, distribution.values.sum())
        assertTrue(distribution.values.all { it >= decision.minPerTarget })
        submitDecision(decision.playerId, DistributionResponse(decision.id, distribution))
    }

    @Test
    fun paysThreeGenericAndTwoGreenAndDealsItsFullDamageToOneCreature() {
        val d = customScenario(MonstrousOnslaught, "Serra Angel", "Grizzly Bears")
        val player = d.player1
        val source = d.putCreatureOnBattlefield(player, "Serra Angel")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(player, name)
        d.payCost(player)

        assertNull(d.castSpell(player, spell, listOf(target)).error)
        d.chooseDamage(mapOf(target to 4))
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(source))
        assertFalse(d.getPermanents(d.player2).contains(target))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains(name))
    }

    @Test
    fun damageIsDividedAmongTheChosenCreatureTargets() {
        val d = customScenario(MonstrousOnslaught, "Serra Angel", "Giant Spider", "Llanowar Elves")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Serra Angel")
        val spider = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val elves = d.putCreatureOnBattlefield(d.player2, "Llanowar Elves")
        val spell = d.putCardInHand(player, name)
        d.payCost(player)

        assertNull(d.castSpell(player, spell, listOf(spider, elves)).error)
        d.chooseDamage(mapOf(spider to 3, elves to 1))
        d.resolveAll()

        assertTrue("Three damage leaves the Spider alive", d.getPermanents(d.player2).contains(spider))
        assertFalse("One damage is lethal to the Elves", d.getPermanents(d.player2).contains(elves))
    }

    @Test
    fun onlyChosenTargetsAreAffected() {
        val d = customScenario(MonstrousOnslaught, "Serra Angel", "Grizzly Bears")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Serra Angel")
        val chosen = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val unchosen = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(player, name)
        d.payCost(player)

        assertNull(d.castSpell(player, spell, listOf(chosen)).error)
        d.chooseDamage(mapOf(chosen to 4))
        d.resolveAll()

        assertFalse(d.getPermanents(d.player2).contains(chosen))
        assertTrue(d.getPermanents(d.player2).contains(unchosen))
    }

    @Test
    fun zeroPowerAllowsChoosingNoCreatureTargets() {
        val d = customScenario(MonstrousOnslaught)
        val player = d.player1
        val spell = d.putCardInHand(player, name)
        d.payCost(player)

        assertNull(d.castSpell(player, spell).error)
        d.resolveAll()

        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains(name))
        assertTrue(d.getCreatures(player).isEmpty())
    }

    @Test
    fun greatestPowerIsAmongCreaturesYouControlNotAnOpponentsCreature() {
        val d = customScenario(MonstrousOnslaught, "Grizzly Bears", "Serra Angel", "Giant Spider")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val target = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val spell = d.putCardInHand(player, name)
        d.payCost(player)

        assertNull(d.castSpell(player, spell, listOf(target)).error)
        d.chooseDamage(mapOf(target to 2))
        d.resolveAll()

        assertTrue("The opponent's greater creature does not increase X", d.getPermanents(d.player2).contains(target))
    }

    @Test
    fun greatestPowerIsLockedWhenTheSpellIsCast() {
        val d = customScenario(MonstrousOnslaught, "Serra Angel", "Giant Spider", "Murder")
        val player = d.player1
        val source = d.putCreatureOnBattlefield(player, "Serra Angel")
        val target = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val spell = d.putCardInHand(player, name)
        d.payCost(player)

        assertNull(d.castSpell(player, spell, listOf(target)).error)
        d.chooseDamage(mapOf(target to 4))
        val murder = d.putCardInHand(d.player2, "Murder")
        d.giveMana(d.player2, Color.BLACK, 2)
        d.giveColorlessMana(d.player2, 1)
        assertNull(d.passPriority(player).error)
        assertEquals(d.player2, d.priorityPlayer)
        assertNull(d.castSpell(d.player2, murder, listOf(source)).error)
        d.resolveAll()

        assertFalse(d.getPermanents(player).contains(source))
        assertFalse("X remains four after its source leaves the battlefield", d.getPermanents(d.player2).contains(target))
    }

    @Test
    fun aPlayerIsNotALegalTarget() {
        val d = customScenario(MonstrousOnslaught)
        val player = d.player1
        val spell = d.putCardInHand(player, name)
        d.payCost(player)

        assertNotNull(d.castSpell(player, spell, listOf(d.player2)).error)
        assertTrue(d.getHand(player).contains(spell))
    }

    @Test
    fun aNoncreaturePermanentIsNotALegalTarget() {
        val d = customScenario(MonstrousOnslaught, "Sol Ring")
        val player = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(player, name)
        d.payCost(player)

        assertNotNull(d.castSpell(player, spell, listOf(artifact)).error)
        assertTrue(d.getHand(player).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(artifact))
    }

    @Test
    fun threeGenericAndOnlyOneGreenCannotPayTheCost() {
        val d = customScenario(MonstrousOnslaught, "Grizzly Bears")
        val player = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.GREEN)

        assertNotNull(d.castSpell(player, spell, listOf(target)).error)
        assertTrue(d.getHand(player).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(target))
    }

    @Test
    fun twoGenericAndTwoGreenCannotPayTheCost() {
        val d = customScenario(MonstrousOnslaught, "Grizzly Bears")
        val player = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.GREEN, 2)

        assertNotNull(d.castSpell(player, spell, listOf(target)).error)
        assertTrue(d.getHand(player).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(target))
    }

    @Test
    fun sorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(MonstrousOnslaught, "Grizzly Bears")
        val player = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(player, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.payCost(player)

        assertNotNull(d.castSpell(player, spell, listOf(target)).error)
        assertTrue(d.getHand(player).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(target))
    }
}
