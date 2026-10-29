package com.arcanatable.customcards

// Independently authored trusted-main checks, written from the exact Oracle text only:
// "This spell costs {1} less to cast for each creature that died this turn. Discard your hand,
// then draw eight cards. Blood for the Blood God! deals 8 damage to each opponent. Exile
// Blood for the Blood God!" ({8}{B}{B}{R} instant). Never copied from the Copilot PR.
import com.arcanatable.customcards.cards.BloodForTheBloodGod
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class BloodForTheBloodGodIndependentCertificationTest {
    private val name = "Blood for the Blood God!"

    private fun GameTestDriver.payColored() {
        giveMana(player1, Color.BLACK, 2)
        giveMana(player1, Color.RED, 1)
    }

    /** Kills one Grizzly Bears on each side with Pyroclasm, so two creatures died this turn. */
    private fun GameTestDriver.killTwoBears() {
        val mine = putCreatureOnBattlefield(player1, "Grizzly Bears")
        val theirs = putCreatureOnBattlefield(player2, "Grizzly Bears")
        val sweep = putCardInHand(player1, "Pyroclasm")
        giveMana(player1, Color.RED, 1)
        giveColorlessMana(player1, 1)
        assertNull(castSpell(player1, sweep).error)
        resolveAll()
        assertFalse(getPermanents(player1).contains(mine))
        assertFalse(getPermanents(player2).contains(theirs))
        assertTrue(manaPool(player1).let { it.red == 0 && it.colorless == 0 })
    }

    @Test
    fun withNoDeathsItNeedsTheFullElevenMana() {
        val d = customScenario(BloodForTheBloodGod)
        val spell = d.putCardInHand(d.player1, name)
        d.payColored()
        d.giveColorlessMana(d.player1, 7)
        assertNotNull(d.castSpell(d.player1, spell).error)
        assertTrue(d.getHand(d.player1).contains(spell))
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, spell).error)
    }

    @Test
    fun eachCreatureThatDiedThisTurnOnEitherSideReducesGenericCostByOne() {
        val d = customScenario(BloodForTheBloodGod, "Grizzly Bears", "Pyroclasm")
        d.killTwoBears()
        val spell = d.putCardInHand(d.player1, name)
        d.payColored()
        d.giveColorlessMana(d.player1, 6)
        assertNull(d.castSpell(d.player1, spell).error)
    }

    @Test
    fun reductionIsOnlyOnePerDeath() {
        val d = customScenario(BloodForTheBloodGod, "Grizzly Bears", "Pyroclasm")
        d.killTwoBears()
        val spell = d.putCardInHand(d.player1, name)
        d.payColored()
        d.giveColorlessMana(d.player1, 5)
        assertNotNull(d.castSpell(d.player1, spell).error)
        assertTrue(d.getHand(d.player1).contains(spell))
    }

    @Test
    fun discardsHandDrawsEightHitsOnlyOpponentsAndExilesItself() {
        val d = customScenario(BloodForTheBloodGod, "Grizzly Bears", "Pyroclasm")
        val p1 = d.player1
        val p2 = d.player2
        val enemyBear = d.putCreatureOnBattlefield(p2, "Grizzly Bears")
        d.putCardInHand(p1, "Grizzly Bears")
        d.putCardInHand(p1, "Pyroclasm")
        val spell = d.putCardInHand(p1, name)
        val handBefore = d.getHand(p1).size
        assertTrue(handBefore >= 3)
        d.payColored()
        d.giveColorlessMana(d.player1, 8)
        assertNull(d.castSpell(p1, spell).error)
        d.resolveAll()

        assertEquals(8, d.getHand(p1).size)
        assertFalse(d.zoneNames(p1, Zone.HAND).contains("Grizzly Bears"))
        assertFalse(d.zoneNames(p1, Zone.HAND).contains("Pyroclasm"))
        val graveyard = d.zoneNames(p1, Zone.GRAVEYARD)
        assertTrue(graveyard.contains("Grizzly Bears"))
        assertTrue(graveyard.contains("Pyroclasm"))
        assertEquals(handBefore - 1, graveyard.size)
        assertFalse(graveyard.contains(name))
        assertTrue(d.zoneNames(p1, Zone.EXILE).contains(name))

        assertEquals(12, d.getLifeTotal(p2))
        assertEquals(20, d.getLifeTotal(p1))
        assertTrue(d.getPermanents(p2).contains(enemyBear))
    }
}
