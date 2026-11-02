package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MonstrousOnslaught
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class MonstrousOnslaughtScenarioTest {
    @Test
    fun distributesGreatestPowerAtCastEvenIfItsSourceLeaves() {
        val d = customScenario(MonstrousOnslaught, "Craw Wurm", "Murder", "Grizzly Bears", "Centaur Courser")
        val p = d.player1
        val opponent = d.player2
        val wurm = d.putCreatureOnBattlefield(p, "Craw Wurm")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val courser = d.putCreatureOnBattlefield(opponent, "Centaur Courser")
        val spell = d.putCardInHand(p, "Monstrous Onslaught")
        d.giveMana(p, Color.GREEN, 2)
        d.giveColorlessMana(p, 3)

        assertNull(
            d.submit(
                CastSpell(
                    playerId = p,
                    cardId = spell,
                    targets = listOf(ChosenTarget.Permanent(bear), ChosenTarget.Permanent(courser)),
                    damageDistribution = mapOf(bear to 3, courser to 3),
                )
            ).error
        )

        assertNull(d.passPriority(p).error)
        val murder = d.putCardInHand(opponent, "Murder")
        d.giveMana(opponent, Color.BLACK, 2)
        d.giveColorlessMana(opponent, 1)
        assertNull(d.castSpell(opponent, murder, listOf(wurm)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Craw Wurm").isEmpty())
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.permanentsNamed(opponent, "Centaur Courser").isEmpty())
    }

    @Test
    fun greatestPowerLimitsHowManyCreaturesCanBeTargeted() {
        val d = customScenario(MonstrousOnslaught, "Grizzly Bears", "Savannah Lions")
        val p = d.player1
        val opponent = d.player2
        val source = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val targets = List(3) { d.putCreatureOnBattlefield(opponent, "Savannah Lions") }
        val spell = d.putCardInHand(p, "Monstrous Onslaught")
        d.giveMana(p, Color.GREEN, 2)
        d.giveColorlessMana(p, 3)

        assertNotNull(
            d.submit(
                CastSpell(
                    playerId = p,
                    cardId = spell,
                    targets = targets.map { ChosenTarget.Permanent(it) },
                    damageDistribution = targets.associateWith { 1 },
                )
            ).error
        )
        assertNotNull(d.state.getEntity(source))
        assertNull(
            d.submit(
                CastSpell(
                    playerId = p,
                    cardId = spell,
                    targets = targets.take(2).map { ChosenTarget.Permanent(it) },
                    damageDistribution = targets.take(2).associateWith { 1 },
                )
            ).error
        )
        d.resolveAll()

        assertEquals(1, d.permanentsNamed(opponent, "Savannah Lions").size)
    }

    @Test
    fun withNoCreaturesItCanBeCastWithoutTargetsAndDealsNoDamage() {
        val d = customScenario(MonstrousOnslaught, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Monstrous Onslaught")
        d.giveMana(p, Color.GREEN, 2)
        d.giveColorlessMana(p, 3)

        assertNotNull(d.castSpell(p, spell, listOf(bear)).error)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()

        assertEquals(1, d.permanentsNamed(opponent, "Grizzly Bears").size)
    }
}
