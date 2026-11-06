package com.arcanatable.customcards

// Trusted-main, real-Argentum scenarios written only from the For the Emperor! Oracle record:
// "Creatures you control get +2/+2 and gain vigilance and lifelink until end of turn."
import com.arcanatable.customcards.cards.ForTheEmperor
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class ForTheEmperorIndependentCertificationTest {
    private val spellName = "For the Emperor!"

    @Test
    fun threeGenericAndWhiteBuffsAllYourCreaturesAndNoOthers() {
        val d = customScenario(ForTheEmperor, "Grizzly Bears", "Llanowar Elves", "Sol Ring")
        val player = d.player1
        val bears = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val elves = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        val opposingBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val ring = d.putPermanentOnBattlefield(player, "Sol Ring")
        val spell = d.putCardInHand(player, spellName)
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 3)

        assertNull(d.castSpell(player, spell).error)
        d.resolveAll()

        assertEquals(4, d.projected.getPower(bears))
        assertEquals(4, d.projected.getToughness(bears))
        assertEquals(3, d.projected.getPower(elves))
        assertEquals(3, d.projected.getToughness(elves))
        for (creature in listOf(bears, elves)) {
            assertTrue(d.projected.hasKeyword(creature, Keyword.VIGILANCE))
            assertTrue(d.projected.hasKeyword(creature, Keyword.LIFELINK))
        }
        assertEquals(2, d.projected.getPower(opposingBear))
        assertEquals(2, d.projected.getToughness(opposingBear))
        assertFalse(d.projected.hasKeyword(opposingBear, Keyword.VIGILANCE))
        assertFalse(d.projected.hasKeyword(opposingBear, Keyword.LIFELINK))
        assertFalse(d.projected.isCreature(ring))
        assertFalse(d.projected.hasKeyword(ring, Keyword.VIGILANCE))
        assertFalse(d.projected.hasKeyword(ring, Keyword.LIFELINK))
        assertTrue(d.getGraveyardCardNames(player).contains(spellName))
    }

    @Test
    fun twoGenericAndWhiteAreNotEnoughToCast() {
        val d = customScenario(ForTheEmperor, "Grizzly Bears")
        val player = d.player1
        val bears = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val spell = d.putCardInHand(player, spellName)
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 2)

        assertNotNull(d.castSpell(player, spell).error)

        assertTrue(d.zoneNames(player, Zone.HAND).contains(spellName))
        assertEquals(2, d.projected.getPower(bears))
        assertEquals(2, d.projected.getToughness(bears))
        assertFalse(d.projected.hasKeyword(bears, Keyword.VIGILANCE))
        assertFalse(d.projected.hasKeyword(bears, Keyword.LIFELINK))
    }

    @Test
    fun fourColorlessManaCannotReplaceTheRequiredWhiteMana() {
        val d = customScenario(ForTheEmperor, "Grizzly Bears")
        val player = d.player1
        val bears = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val spell = d.putCardInHand(player, spellName)
        d.giveColorlessMana(player, 4)

        assertNotNull(d.castSpell(player, spell).error)

        assertTrue(d.zoneNames(player, Zone.HAND).contains(spellName))
        assertEquals(2, d.projected.getPower(bears))
        assertEquals(2, d.projected.getToughness(bears))
    }

    @Test
    fun cannotCastDuringAnOpponentsTurnEvenWithPriorityAndEnoughMana() {
        val d = customScenario(ForTheEmperor, "Grizzly Bears")
        val player = d.player1
        val bears = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val spell = d.putCardInHand(player, spellName)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertNull(d.passPriority(d.player2).error)
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 3)

        assertNotNull(d.castSpell(player, spell).error)

        assertTrue(d.zoneNames(player, Zone.HAND).contains(spellName))
        assertEquals(2, d.projected.getPower(bears))
        assertEquals(2, d.projected.getToughness(bears))
    }

    @Test
    fun cannotCastWhileAnotherSpellIsOnTheStack() {
        val d = customScenario(ForTheEmperor, "Shock", "Grizzly Bears")
        val player = d.player1
        val opposingBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val shock = d.putCardInHand(player, "Shock")
        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, shock, listOf(opposingBear)).error)
        val spell = d.putCardInHand(player, spellName)
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 3)

        assertNotNull(d.castSpell(player, spell).error)

        assertEquals(1, d.stackSize)
        assertTrue(d.zoneNames(player, Zone.HAND).contains(spellName))
        assertEquals(2, d.projected.getPower(opposingBear))
        assertEquals(2, d.projected.getToughness(opposingBear))
    }

    @Test
    fun vigilanceKeepsAnAttackingCreatureUntappedAndLifelinkGainsItsDamageInLife() {
        val d = customScenario(ForTheEmperor, "Grizzly Bears")
        val player = d.player1
        val bears = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val spell = d.putCardInHand(player, spellName)
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 3)
        assertNull(d.castSpell(player, spell).error)
        d.resolveAll()
        d.removeSummoningSickness(bears)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(bears), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(24, d.getLifeTotal(player))
        assertEquals(16, d.getLifeTotal(d.player2))
        assertFalse(d.isTapped(bears))
    }

    @Test
    fun theEffectDoesNotIncludeLaterCreaturesAndExpiresAtEndOfTurn() {
        val d = customScenario(ForTheEmperor, "Grizzly Bears", "Llanowar Elves")
        val player = d.player1
        val bears = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val spell = d.putCardInHand(player, spellName)
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 3)
        assertNull(d.castSpell(player, spell).error)
        d.resolveAll()

        val laterElf = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        assertEquals(1, d.projected.getPower(laterElf))
        assertEquals(1, d.projected.getToughness(laterElf))
        assertFalse(d.projected.hasKeyword(laterElf, Keyword.VIGILANCE))
        assertFalse(d.projected.hasKeyword(laterElf, Keyword.LIFELINK))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)

        assertEquals(2, d.projected.getPower(bears))
        assertEquals(2, d.projected.getToughness(bears))
        assertFalse(d.projected.hasKeyword(bears, Keyword.VIGILANCE))
        assertFalse(d.projected.hasKeyword(bears, Keyword.LIFELINK))
    }
}
