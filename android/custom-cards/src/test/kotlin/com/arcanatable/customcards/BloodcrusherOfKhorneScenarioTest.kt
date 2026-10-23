package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BloodcrusherOfKhorne
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class BloodcrusherOfKhorneScenarioTest {
    @Test
    fun itselfAndOtherAlliedCreaturesTrampleButOpponentsDoNot() {
        val d = customScenario(BloodcrusherOfKhorne, "Grizzly Bears")
        val p = d.player1
        val attacker = d.putCreatureOnBattlefield(p, "Bloodcrusher of Khorne")
        val ally = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opponent = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        assertTrue(d.projected.hasKeyword(attacker, Keyword.TRAMPLE))
        assertTrue(d.projected.hasKeyword(ally, Keyword.TRAMPLE))
        assertFalse(d.projected.hasKeyword(opponent, Keyword.TRAMPLE))
    }
}
