package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DrownerOfHope
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class DrownerOfHopeScenarioTest {
    @Test
    fun enteringCreatesTwoFunctionalEldraziScions() {
        val d = customScenario(DrownerOfHope)
        val p = d.player1
        val card = d.putCardInHand(p, "Drowner of Hope")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        val drowner = d.findPermanent(p, "Drowner of Hope")!!
        assertTrue(d.cardRegistry.getCard("Drowner of Hope")!!.colors.isEmpty())
        assertNotNull(drowner)
        val scions = d.permanentsNamed(p, "Eldrazi Scion")
        assertEquals(2, scions.size)
        assertTrue(scions.all { d.abilityActions(p, it).isNotEmpty() })
    }

    @Test
    fun sacrificingScionAsCostTapsTargetCreature() {
        val d = customScenario(DrownerOfHope, "Grizzly Bears")
        val p = d.player1
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val card = d.putCardInHand(p, "Drowner of Hope")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        val source = d.findPermanent(p, "Drowner of Hope")!!
        assertEquals(2, d.permanentsNamed(p, "Eldrazi Scion").size)
        val abilityId = d.cardRegistry.getCard("Drowner of Hope")!!.script.activatedAbilities.single().id
        assertNull(d.submit(ActivateAbility(p, source, abilityId, targets = listOf(ChosenTarget.Permanent(enemy)))).error)
        d.resolveAll(Choices(pick = listOf("Eldrazi Scion"), targets = listOf(enemy)))
        assertTrue(d.isTapped(enemy))
        assertEquals(1, d.permanentsNamed(p, "Eldrazi Scion").size)
    }
}
