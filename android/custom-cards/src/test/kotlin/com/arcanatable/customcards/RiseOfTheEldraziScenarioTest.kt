package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RiseOfTheEldrazi
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class RiseOfTheEldraziScenarioTest {
    @Test
    fun uncounterableSorceryDestroysCreaturesWithoutRegenerationAndDrawsThree() {
        val d = customScenario(RiseOfTheEldrazi, "Grizzly Bears")
        val p = d.player1
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        val spell = d.putCardInHand(p, "Rise of the Eldrazi")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opposingCreature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val hollow = d.putPermanentOnBattlefield(d.player2, "Yavimaya Hollow")
        d.giveMana(d.player2, Color.GREEN)
        val regenerate = d.cardRegistry.getCard("Yavimaya Hollow")!!.script.activatedAbilities[1]
        assertNull(d.passPriority(p).error)
        assertNull(
            d.submit(
                ActivateAbility(
                    d.player2,
                    hollow,
                    regenerate.id,
                    targets = listOf(ChosenTarget.Permanent(opposingCreature)),
                ),
            ).error,
        )
        d.resolveAll(Choices(targets = listOf(opposingCreature)))
        val handSizeBeforeCast = d.getHand(p).size
        d.giveColorlessMana(p, 11)

        assertTrue(RiseOfTheEldrazi.script.cantBeCountered)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()

        assertTrue(d.getCreatures(p).isEmpty())
        assertTrue(d.getCreatures(d.player2).isEmpty())
        assertNotNull(d.findPermanent(d.player2, "Yavimaya Hollow"))
        assertEquals(handSizeBeforeCast + 2, d.getHand(p).size)
    }
}
