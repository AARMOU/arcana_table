package com.arcanatable.customcards
import com.arcanatable.customcards.cards.HeraldOfSlaanesh
import com.arcanatable.customcards.cards.BloodcrusherOfKhorne
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test
class HeraldOfSlaaneshScenarioTest {
    @Test fun alliedDemonsHaveHasteAndDemonSpellsCostTwoLess() {
        val d = customScenario(HeraldOfSlaanesh)
        d.registerCard(BloodcrusherOfKhorne)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Herald of Slaanesh")
        val ally = d.putCreatureOnBattlefield(p, "Bloodcrusher of Khorne")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Bloodcrusher of Khorne")
        assertTrue(d.projected.hasKeyword(ally, Keyword.HASTE))
        assertFalse(d.projected.hasKeyword(enemy, Keyword.HASTE))
        val spell = d.putCardInHand(p, "Bloodcrusher of Khorne")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 1)
        assertNull("Demon spell has its 3 generic reduced to 1", d.castSpell(p, spell).error)
        d.resolveAll()
        assertEquals(2, d.permanentsNamed(p, "Bloodcrusher of Khorne").size)
    }
}
