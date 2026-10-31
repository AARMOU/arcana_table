package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MonstrousOnslaught
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class MonstrousOnslaughtScenarioTest {
    @Test
    fun dividesCastTimeGreatestPowerAmongAnyNumberOfCreatures() {
        val d = customScenario(MonstrousOnslaught, "Grizzly Bears", "Llanowar Elves")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val firstElf = d.putCreatureOnBattlefield(opponent, "Llanowar Elves")
        val secondElf = d.putCreatureOnBattlefield(opponent, "Llanowar Elves")
        val thirdElf = d.putCreatureOnBattlefield(opponent, "Llanowar Elves")
        val spell = d.putCardInHand(p, "Monstrous Onslaught")
        d.giveMana(p, Color.GREEN, 2)
        d.giveColorlessMana(p, 3)

        val offer = d.legalActions(p).single { (it.action as? CastSpell)?.cardId == spell }
        assertTrue(offer.requiresDamageDistribution)
        assertEquals(2, offer.totalDamageToDistribute)
        assertEquals(1, offer.minDamagePerTarget)
        assertEquals(2, offer.targetCount)

        val result = d.submit(
            CastSpell(
                playerId = p,
                cardId = spell,
                targets = listOf(ChosenTarget.Permanent(firstElf), ChosenTarget.Permanent(secondElf)),
                paymentStrategy = PaymentStrategy.FromPool,
                damageDistribution = mapOf(firstElf to 1, secondElf to 1),
            )
        )
        assertNull(result.error)
        d.resolveStack()

        assertTrue(d.permanentsNamed(opponent, "Llanowar Elves").none { it == firstElf || it == secondElf })
        assertEquals(1, d.state.getEntity(thirdElf)?.get<DamageComponent>()?.amount ?: 0)
    }

    @Test
    fun greatestPowerIsCapturedWhenCastingBeforeItsSourceLeaves() {
        val d = customScenario(MonstrousOnslaught, "Craw Wurm", "Grizzly Bears", "Murder")
        val p = d.player1
        val opponent = d.player2
        val source = d.putCreatureOnBattlefield(p, "Craw Wurm")
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Monstrous Onslaught")
        d.giveMana(p, Color.GREEN, 2)
        d.giveColorlessMana(p, 3)

        val offer = d.legalActions(p).single { (it.action as? CastSpell)?.cardId == spell }
        assertEquals(6, offer.totalDamageToDistribute)
        assertNull(
            d.submit(
                CastSpell(
                    playerId = p,
                    cardId = spell,
                    targets = listOf(ChosenTarget.Permanent(bear)),
                    paymentStrategy = PaymentStrategy.FromPool,
                    damageDistribution = mapOf(bear to 6),
                )
            ).error
        )

        d.passPriority(p)
        val murder = d.putCardInHand(opponent, "Murder")
        d.giveMana(opponent, Color.BLACK, 2)
        d.giveColorlessMana(opponent, 1)
        assertNull(d.castSpell(opponent, murder, listOf(source)).error)
        d.resolveStack()

        assertTrue(d.permanentsNamed(p, "Craw Wurm").isEmpty())
        assertTrue(d.permanentsNamed(opponent, "Grizzly Bears").isEmpty())
    }

    @Test
    fun zeroGreatestPowerAllowsNoTargetsAndDealsNoDamage() {
        val d = customScenario(MonstrousOnslaught, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Monstrous Onslaught")
        d.giveMana(p, Color.GREEN, 2)
        d.giveColorlessMana(p, 3)

        val offer = d.legalActions(p).single { (it.action as? CastSpell)?.cardId == spell }
        assertEquals(0, offer.targetCount)
        assertNotNull(d.castSpell(p, spell, listOf(bear)).error)
        assertNull(d.castSpell(p, spell).error)
        d.resolveStack()

        assertEquals(listOf(bear), d.permanentsNamed(opponent, "Grizzly Bears"))
        assertEquals(0, d.state.getEntity(bear)?.get<DamageComponent>()?.amount ?: 0)
    }
}
