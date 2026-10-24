package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BoneSabres
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoneSabresScenarioTest {
    @Test
    fun equippedAttackerGetsFourPlusOneCountersButOtherAttackersDoNot() {
        val d = customScenario(BoneSabres, "Grizzly Bears")
        val p = d.player1
        val equippedAttacker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val otherAttacker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val sabres = d.putPermanentOnBattlefield(p, "Bone Sabres")
        d.giveColorlessMana(p, 3)

        val equip = d.cardRegistry.getCard("Bone Sabres")!!.script.activatedAbilities.single()
        assertNull(
            d.submit(
                ActivateAbility(p, sabres, equip.id, targets = listOf(ChosenTarget.Permanent(equippedAttacker))),
            ).error,
        )
        d.resolveAll(Choices(targets = listOf(equippedAttacker)))
        d.removeSummoningSickness(equippedAttacker)
        d.removeSummoningSickness(otherAttacker)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(equippedAttacker, otherAttacker), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(6, d.projected.getPower(equippedAttacker))
        assertEquals(6, d.projected.getToughness(equippedAttacker))
        assertEquals(2, d.projected.getPower(otherAttacker))
        assertEquals(2, d.projected.getToughness(otherAttacker))
        assertEquals(12, d.getLifeTotal(d.player2))
    }
}
