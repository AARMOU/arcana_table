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

    @Test fun mixedPoolNeverLaundersRestrictedManaIntoUnrelatedCosts() {
        val d = customScenario(EldraziTemple, "Sol Ring", "Grizzly Bears")
        val p = d.player1
        val temple = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val ring = d.putCardInHand(p, "Sol Ring")
        val bears = d.putCardInHand(p, "Grizzly Bears")

        assertNull(d.activate(p, temple, index = 1).error)
        d.resolveAll()
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        // A non-Eldrazi colorless spell must not borrow even one restricted {C}.
        assertNotNull(d.castSpell(p, ring).error)
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        // An unrestricted {C} pays for Sol Ring; both restricted units survive.
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Sol Ring"))
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        // The same restriction holds for a colored spell's generic portion.
        d.giveMana(p, Color.GREEN)
        assertNotNull(d.castSpell(p, bears).error)
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bears).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Grizzly Bears"))
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(2, d.manaPool(p).restrictedMana.size)
    }

    @Test fun restrictedManaPaysForColorlessNoncreatureEldraziKindredSpell() {
        // Echoes of Eternity is a {3}{C}{C}{C} Kindred Enchantment — Eldrazi.
        // The Temple restriction applies to colorless Eldrazi spells, not only creatures.
        val d = customScenario(EldraziTemple, "Echoes of Eternity")
        val p = d.player1
        val temple = d.putPermanentOnBattlefield(p, "Eldrazi Temple")
        val echoes = d.putCardInHand(p, "Echoes of Eternity")

        assertNull(d.activate(p, temple, index = 1).error)
        d.resolveAll()
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        // Three unrestricted {C} plus two restricted {C} cannot pay six total.
        d.giveColorlessMana(p, 3)
        assertNotNull("One mana is still missing for the Kindred Eldrazi spell", d.castSpell(p, echoes).error)
        assertEquals(3, d.manaPool(p).colorless)
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        // The exact same restricted mana must now be eligible to fund this
        // noncreature Eldrazi spell; neither generic nor {C} costs may be waived.
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, echoes).error)
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.manaPool(p).restrictedMana.isEmpty())
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Echoes of Eternity"))
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
