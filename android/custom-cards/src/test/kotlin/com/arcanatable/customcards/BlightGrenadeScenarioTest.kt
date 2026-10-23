package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BlightGrenade
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class BlightGrenadeScenarioTest {
    @Test
    fun destroysTargetAndShrinksAllOtherCreatures() {
        val d = customScenario(BlightGrenade, "Craw Wurm", "Grizzly Bears", "Centaur Courser")
        val p = d.player1
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(opponent, "Craw Wurm")
        d.putCreatureOnBattlefield(opponent, "Centaur Courser")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val grenade = d.putCardInHand(p, "Blight Grenade")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 4)

        assertNull(d.castSpell(p, grenade, listOf(target)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(opponent, "Craw Wurm").isEmpty())
        assertTrue(d.permanentsNamed(opponent, "Centaur Courser").isEmpty())
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
    }
}
