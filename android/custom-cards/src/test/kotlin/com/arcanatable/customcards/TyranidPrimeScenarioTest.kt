package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TyranidPrime
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class TyranidPrimeScenarioTest {
    @Test
    fun otherCreaturesGainWorkingEvolveAndPrimeEvolvesItself() {
        val d = customScenario(TyranidPrime, "Grizzly Bears", "Craw Wurm")
        val p = d.player1
        val prime = d.putCreatureOnBattlefield(p, "Tyranid Prime")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opponentBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        assertTrue(d.projected.hasKeyword(prime, Keyword.EVOLVE))
        assertTrue(d.projected.hasKeyword(bear, Keyword.EVOLVE))
        assertFalse(d.projected.hasKeyword(opponentBear, Keyword.EVOLVE))

        val wurm = d.putCardInHand(p, "Craw Wurm")
        d.giveMana(p, Color.GREEN)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, wurm).error)
        d.resolveAll()

        assertEquals(1, d.projected.getPower(prime))
        assertEquals(3, d.projected.getPower(bear))
        assertEquals(2, d.projected.getPower(opponentBear))
    }
}
