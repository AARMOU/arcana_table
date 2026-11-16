package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DeathleaperTerrorWeapon
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class DeathleaperTerrorWeaponIndependentCertificationTest {
    @Test
    fun castingRequiresTwoGenericRedAndGreenAndEntersAsAThreeThreeTyranid() {
        val d = customScenario(DeathleaperTerrorWeapon)
        val player = d.player1
        val deathleaper = d.putCardInHand(player, "Deathleaper, Terror Weapon")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.RED)
        d.giveMana(player, Color.GREEN)

        assertNull(d.castSpell(player, deathleaper).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(deathleaper))
        assertEquals(3, d.projected.getPower(deathleaper))
        assertEquals(3, d.projected.getToughness(deathleaper))
        assertTrue(d.projected.hasSubtype(deathleaper, "TYRANID"))
        assertTrue(d.projected.hasKeyword(deathleaper, Keyword.HASTE))
    }

    @Test
    fun threeManaWithoutGreenCannotPayTheFourManaCost() {
        val d = customScenario(DeathleaperTerrorWeapon)
        val player = d.player1
        val deathleaper = d.putCardInHand(player, "Deathleaper, Terror Weapon")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.RED)

        assertNotNull(d.castSpell(player, deathleaper).error)

        assertTrue(d.getHand(player).contains(deathleaper))
        assertFalse(d.getPermanents(player).contains(deathleaper))
    }

    @Test
    fun flashAllowsCastingDeathleaperDuringAnOpponentsTurn() {
        val d = customScenario(DeathleaperTerrorWeapon)
        val player = d.player1
        val deathleaper = d.putCardInHand(player, "Deathleaper, Terror Weapon")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertNull(d.passPriority(d.player2).error)
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.RED)
        d.giveMana(player, Color.GREEN)

        assertNull(d.castSpell(player, deathleaper).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(deathleaper))
    }

    @Test
    fun hasteAllowsDeathleaperToAttackTheTurnItEnters() {
        val d = customScenario(DeathleaperTerrorWeapon)
        val player = d.player1
        val deathleaper = d.putCardInHand(player, "Deathleaper, Terror Weapon")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.RED)
        d.giveMana(player, Color.GREEN)
        assertNull(d.castSpell(player, deathleaper).error)
        d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(deathleaper), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(14, d.getLifeTotal(d.player2))
    }

    @Test
    fun creaturesYouControlThatEnteredThisTurnHaveDoubleStrike() {
        val d = customScenario(DeathleaperTerrorWeapon, "Grizzly Bears")
        val player = d.player1
        val deathleaper = d.putCreatureOnBattlefield(player, "Deathleaper, Terror Weapon")
        val bears = d.putCreatureOnBattlefield(player, "Grizzly Bears")

        assertTrue(d.projected.hasKeyword(deathleaper, Keyword.DOUBLE_STRIKE))
        assertTrue(d.projected.hasKeyword(bears, Keyword.DOUBLE_STRIKE))
    }

    @Test
    fun fleshHooksDoesNotGrantDoubleStrikeToAnOpponentOrAnOlderCreature() {
        val d = customScenario(DeathleaperTerrorWeapon, "Grizzly Bears")
        val player = d.player1
        val olderCreature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val deathleaper = d.putCreatureOnBattlefield(player, "Deathleaper, Terror Weapon")
        val newerCreature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val opponentCreature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        assertTrue(d.projected.hasKeyword(deathleaper, Keyword.DOUBLE_STRIKE))
        assertTrue(d.projected.hasKeyword(newerCreature, Keyword.DOUBLE_STRIKE))
        assertFalse(d.projected.hasKeyword(olderCreature, Keyword.DOUBLE_STRIKE))
        assertFalse(d.projected.hasKeyword(opponentCreature, Keyword.DOUBLE_STRIKE))
    }

    @Test
    fun creaturesLoseFleshHooksDoubleStrikeWhenTheTurnChanges() {
        val d = customScenario(DeathleaperTerrorWeapon, "Grizzly Bears")
        val player = d.player1
        val deathleaper = d.putCreatureOnBattlefield(player, "Deathleaper, Terror Weapon")
        val bears = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        assertTrue(d.projected.hasKeyword(deathleaper, Keyword.DOUBLE_STRIKE))
        assertTrue(d.projected.hasKeyword(bears, Keyword.DOUBLE_STRIKE))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)

        assertFalse(d.projected.hasKeyword(deathleaper, Keyword.DOUBLE_STRIKE))
        assertFalse(d.projected.hasKeyword(bears, Keyword.DOUBLE_STRIKE))
    }
}
