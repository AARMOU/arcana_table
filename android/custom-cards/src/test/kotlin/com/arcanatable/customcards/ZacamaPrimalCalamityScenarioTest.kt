package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ZacamaPrimalCalamity
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class ZacamaPrimalCalamityScenarioTest {
    @Test
    fun castZacamaUntapsControlledLands() {
        val d = customScenario(ZacamaPrimalCalamity)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Forest")
        d.tapPermanent(land)
        val card = d.putCardInHand(p, "Zacama, Primal Calamity")
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.GREEN, 2)
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 6)

        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertFalse(d.isTapped(land))
    }

    @Test
    fun zacamaPutOntoBattlefieldWithoutCastingDoesNotUntap() {
        val d = customScenario(ZacamaPrimalCalamity)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Forest")
        d.tapPermanent(land)
        d.putCreatureOnBattlefield(p, "Zacama, Primal Calamity")
        d.resolveAll()
        assertTrue("only casting Zacama untaps lands", d.isTapped(land))
    }
}
