package com.arcanatable.customcards

import com.arcanatable.customcards.cards.NurglesRot
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NurglesRotScenarioTest {
    @Test
    fun auraOnlyEnchantOpponentCreatureAndReturnsWithTokenWhenItDies() {
        val d = customScenario(NurglesRot, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val ownCreature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enchanted = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val unrelated = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val rot = d.putCardInHand(p, "Nurgle's Rot")
        d.giveMana(p, Color.BLACK)

        assertNotNull("An Aura can't target a creature its caster controls", d.castSpell(p, rot, listOf(ownCreature)).error)
        assertNull(d.castSpell(p, rot, listOf(enchanted)).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Nurgle's Rot"))

        val blade = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, blade, listOf(unrelated)).error)
        d.resolveAll()
        assertTrue(d.findPermanent(p, "Nurgle's Rot") != null)
        assertEquals(0, d.permanentsNamed(p, "Plaguebearer of Nurgle").size)

        val secondBlade = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, secondBlade, listOf(enchanted)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Nurgle's Rot").isEmpty())
        assertEquals(1, d.zoneNames(p, Zone.HAND).count { it == "Nurgle's Rot" })
        assertEquals(1, d.permanentsNamed(p, "Plaguebearer of Nurgle").size)
    }
}
