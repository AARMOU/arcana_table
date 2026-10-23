package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DeathleaperTerrorWeapon
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.EnteredThisTurnComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class DeathleaperTerrorWeaponScenarioTest {
    @Test
    fun grantsDoubleStrikeToItselfAndLaterEntrantsButNotOlderOrEnemyCreatures() {
        val d = customScenario(DeathleaperTerrorWeapon, "Grizzly Bears")
        val p = d.player1
        val oldBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemyBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.addComponent(enemyBear, EnteredThisTurnComponent)
        val spell = d.putCardInHand(p, "Deathleaper, Terror Weapon")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()
        val source = d.findPermanent(p, "Deathleaper, Terror Weapon")!!
        val bearSpell = d.putCardInHand(p, "Grizzly Bears")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, bearSpell).error)
        d.resolveAll()
        val newBear = d.permanentsNamed(p, "Grizzly Bears").single { it != oldBear }
        assertTrue(d.projected.hasKeyword(source, Keyword.FLASH))
        assertTrue(d.projected.hasKeyword(source, Keyword.HASTE))
        assertTrue(d.projected.hasKeyword(source, Keyword.DOUBLE_STRIKE))
        assertTrue(d.projected.hasKeyword(newBear, Keyword.DOUBLE_STRIKE))
        assertFalse(d.projected.hasKeyword(oldBear, Keyword.DOUBLE_STRIKE))
        assertFalse(d.projected.hasKeyword(enemyBear, Keyword.DOUBLE_STRIKE))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertFalse(d.projected.hasKeyword(source, Keyword.DOUBLE_STRIKE))
        assertFalse(d.projected.hasKeyword(newBear, Keyword.DOUBLE_STRIKE))
    }

    @Test
    fun hasteAndDoubleStrikeDealSixDamageOnTheTurnItIsCast() {
        val d = customScenario(DeathleaperTerrorWeapon)
        val p = d.player1
        val spell = d.putCardInHand(p, "Deathleaper, Terror Weapon")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()
        val source = d.findPermanent(p, "Deathleaper, Terror Weapon")!!
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(source), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertEquals(14, d.getLifeTotal(d.player2))
    }
}
