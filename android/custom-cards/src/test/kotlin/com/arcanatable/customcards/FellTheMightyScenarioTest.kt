package com.arcanatable.customcards

import com.arcanatable.customcards.cards.FellTheMighty
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class FellTheMightyScenarioTest {
    @Test
    fun destroysOnlyCreaturesAboveTargetsCurrentPowerOnEitherSide() {
        val d = customScenario(FellTheMighty, "Grizzly Bears", "Hill Giant", "Llanowar Elves")
        val p = d.player1
        val chosen = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val allyLarge = d.putCreatureOnBattlefield(p, "Hill Giant")
        val enemyLarge = d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        val enemyEqual = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val enemySmall = d.putCreatureOnBattlefield(d.player2, "Llanowar Elves")
        val spell = d.putCardInHand(p, "Fell the Mighty")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.WHITE)
        assertNull(d.castSpell(p, spell, listOf(chosen)).error)
        d.resolveAll()
        assertTrue(chosen in d.getCreatures(p))
        assertTrue(enemyEqual in d.getCreatures(d.player2))
        assertTrue(enemySmall in d.getCreatures(d.player2))
        assertFalse(allyLarge in d.getCreatures(p))
        assertFalse(enemyLarge in d.getCreatures(d.player2))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Hill Giant"))
    }

    @Test
    fun targetWithHighPowerSparesAllSmallerCreatures() {
        val d = customScenario(FellTheMighty, "Hill Giant", "Grizzly Bears")
        val p = d.player1
        val chosen = d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        val smaller = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Fell the Mighty")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.WHITE)
        assertNull(d.castSpell(p, spell, listOf(chosen)).error)
        d.resolveAll()
        assertTrue(chosen in d.getCreatures(d.player2))
        assertTrue(smaller in d.getCreatures(p))
    }
}
