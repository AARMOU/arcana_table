package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PrimarisChaplain
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class PrimarisChaplainIndependentCertificationTest {
    @Test
    fun castingForTwoGenericWhiteAndBlackEntersAsAThreeThreeAstartesCleric() {
        val d = customScenario(PrimarisChaplain)
        val p = d.player1
        val chaplain = d.putCardInHand(p, "Primaris Chaplain")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLACK)

        assertNull(d.castSpell(p, chaplain).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Primaris Chaplain").contains(chaplain))
        assertEquals(3, d.projected.getPower(chaplain))
        assertEquals(3, d.projected.getToughness(chaplain))
        assertTrue(d.projected.hasColor(chaplain, Color.WHITE))
        assertTrue(d.projected.hasColor(chaplain, Color.BLACK))
        assertTrue(d.projected.hasType(chaplain, "CREATURE"))
        assertTrue(d.projected.hasSubtype(chaplain, "ASTARTES"))
        assertTrue(d.projected.hasSubtype(chaplain, "CLERIC"))
    }

    @Test
    fun twoGenericAndWhiteManaCannotPayTheRequiredBlackMana() {
        val d = customScenario(PrimarisChaplain)
        val p = d.player1
        val chaplain = d.putCardInHand(p, "Primaris Chaplain")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.WHITE)

        assertNotNull(d.castSpell(p, chaplain).error)

        assertTrue(d.getHand(p).contains(chaplain))
        assertTrue(d.permanentsNamed(p, "Primaris Chaplain").isEmpty())
    }

    @Test
    fun battleCryGivesEachOtherAttackerPlusOnePowerUntilEndOfTurn() {
        val d = customScenario(PrimarisChaplain, "Grizzly Bears")
        val p = d.player1
        val chaplain = d.putCreatureOnBattlefield(p, "Primaris Chaplain")
        val attackingBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val nonattackingBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        listOf(chaplain, attackingBear, nonattackingBear).forEach(d::removeSummoningSickness)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(chaplain, attackingBear), d.player2).error)
        d.resolveAll()

        assertEquals(3, d.projected.getPower(chaplain))
        assertEquals(4, d.projected.getPower(attackingBear))
        assertEquals(2, d.projected.getToughness(attackingBear))
        assertEquals(2, d.projected.getPower(nonattackingBear))
        assertEquals(2, d.projected.getToughness(nonattackingBear))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)

        assertEquals(2, d.projected.getPower(attackingBear))
        assertEquals(2, d.projected.getToughness(attackingBear))
    }

    @Test
    fun rosariusGrantsIndestructibleOnlyToTheAttackingChaplainUntilEndOfTurn() {
        val d = customScenario(PrimarisChaplain, "Grizzly Bears")
        val p = d.player1
        val chaplain = d.putCreatureOnBattlefield(p, "Primaris Chaplain")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(chaplain)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertFalse(d.projected.hasKeyword(chaplain, Keyword.INDESTRUCTIBLE))
        assertNull(d.declareAttackers(p, listOf(chaplain), d.player2).error)
        d.resolveAll()

        assertTrue(d.projected.hasKeyword(chaplain, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(bear, Keyword.INDESTRUCTIBLE))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)

        assertFalse(d.projected.hasKeyword(chaplain, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun anotherCreatureAttackingWithoutTheChaplainTriggersNeitherAbility() {
        val d = customScenario(PrimarisChaplain, "Grizzly Bears")
        val p = d.player1
        val chaplain = d.putCreatureOnBattlefield(p, "Primaris Chaplain")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertFalse(d.projected.hasKeyword(chaplain, Keyword.INDESTRUCTIBLE))
        assertNull(d.declareAttackers(p, listOf(bear), d.player2).error)
        d.resolveAll()

        assertEquals(2, d.projected.getPower(bear))
        assertFalse(d.projected.hasKeyword(chaplain, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(bear, Keyword.INDESTRUCTIBLE))
    }
}
