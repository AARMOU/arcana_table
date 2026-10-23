package com.arcanatable.customcards
import com.arcanatable.customcards.cards.AssaultIntercessor
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test
class AssaultIntercessorScenarioTest {
    @Test fun enemyCreatureDeathCostsItsControllerTwoLife() {
        val d = customScenario(AssaultIntercessor, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val intercessor = d.putCreatureOnBattlefield(p, "Assault Intercessor")
        val dying = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val removal = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, removal, listOf(dying)).error)
        d.resolveAll()
        assertEquals(18, d.getLifeTotal(d.player2))
        assertTrue(d.projected.hasKeyword(intercessor, Keyword.FIRST_STRIKE))
        assertTrue(d.projected.hasKeyword(intercessor, Keyword.MENACE))
    }
}
