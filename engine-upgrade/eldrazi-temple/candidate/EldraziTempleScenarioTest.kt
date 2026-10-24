package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldraziTemple
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

/**
 * These scenarios must run against fresh, locally built SDK/rules jars with the
 * exact #158 mana restriction. Merely compiling the card is insufficient.
 */
class EldraziTempleScenarioTest {
    @Test fun bothModesExistAndUnrestrictedModePaysForOrdinaryArtifact() {
        val d = customScenario(EldraziTemple, "Sol Ring")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val abilities = d.cardRegistry.getCard("Eldrazi Temple")!!.script.activatedAbilities
        assertEquals(2, abilities.size)
        assertNull(d.activate(p, land, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.manaPool(p).restrictedMana.isEmpty())
        val solRing = d.putCardInHand(p, "Sol Ring")
        assertNull(d.castSpell(p, solRing).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Sol Ring"))
    }

    @Test fun restrictedModeYieldsExactlyTwoAndCannotTapAgain() {
        val d = customScenario(EldraziTemple)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll()
        val pool = d.manaPool(p)
        assertEquals(0, pool.colorless)
        assertEquals(2, pool.restrictedMana.size)
        assertTrue(d.isTapped(land))
        assertNotNull("One land cannot activate both tap modes in a turn", d.activate(p, land, index = 0).error)
    }

    @Test fun restrictedManaPaysForColorlessEldraziCreature() {
        val d = customScenario(EldraziTemple, "Ulamog's Crusher")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val crusher = d.putCardInHand(p, "Ulamog's Crusher")
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll()
        d.giveColorlessMana(p, 6)
        assertNull(d.castSpell(p, crusher).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Ulamog's Crusher").size)
    }

    @Test fun restrictedManaDoesNotPayForOtherColorlessSpells() {
        val d = customScenario(EldraziTemple, "Sol Ring")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val ring = d.putCardInHand(p, "Sol Ring")
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll()
        assertNotNull("Sol Ring is colorless but not an Eldrazi", d.castSpell(p, ring).error)
        assertEquals(2, d.manaPool(p).restrictedMana.size)
    }

    @Test fun restrictedManaCannotFundGenericPartOfColoredSpell() {
        val d = customScenario(EldraziTemple, "Grizzly Bears")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val bear = d.putCardInHand(p, "Grizzly Bears")
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll()
        d.giveMana(p, Color.GREEN)
        assertNotNull("Restricted {C} must not launder generic payment for colored spells", d.castSpell(p, bear).error)
    }
}
