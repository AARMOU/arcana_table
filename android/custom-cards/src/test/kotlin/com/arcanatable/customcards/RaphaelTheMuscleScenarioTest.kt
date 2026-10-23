package com.arcanatable.customcards

import com.arcanatable.customcards.support.Choices
import com.arcanatable.customcards.support.activate
import com.arcanatable.customcards.support.permanentsNamed
import com.arcanatable.customcards.support.resolveAll
import com.arcanatable.customcards.support.scenario
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RaphaelTheMuscleScenarioTest {
    @Test
    fun mutagenCounterEnablesDamageDoublingAndRaphaelCreatesMutagen() {
        val d = scenario("Grizzly Bears", filler = "Forest")
        val player = d.player1
        d.giveMana(player, Color.RED, 5)
        val raphael = d.putCardInHand(player, "Raphael, the Muscle")
        assertNull(d.castSpell(player, raphael).error)
        d.resolveAll()
        val mutagen = d.permanentsNamed(player, "Mutagen").single()
        d.giveMana(player, Color.RED)

        val bears = listOf(
            d.putCreatureOnBattlefield(player, "Grizzly Bears"),
            d.putCreatureOnBattlefield(player, "Grizzly Bears"),
        )
        val mutagenAbility = d.cardRegistry.getCard("Mutagen")!!.script.activatedAbilities.single()
        assertNull(d.submit(ActivateAbility(player, mutagen, mutagenAbility.id, targets = listOf(ChosenTarget.Permanent(bears.first())))).error)
        d.resolveAll(Choices(targets = listOf(bears.first())))
        bears.forEach(d::removeSummoningSickness)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, bears, d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        val life = d.getLifeTotal(d.player2)
        assertEquals("one countered 3/3 deals 6 and the other bear deals 2", 12, life)
        assertEquals(1, d.permanentsNamed(player, "Raphael, the Muscle").size)
    }
}
