package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CorruptedCrossroads
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

/**
 * Runs with the temporary source-built SDK/rules jars containing DevoidSpellsOnly.
 * No assertions are relaxed when the engine doesn't recognize the keyword.
 */
class CorruptedCrossroadsScenarioTest {
    @Test fun normalColorlessModeCastsNondevoidArtifact() {
        val d = customScenario(CorruptedCrossroads, "Sol Ring")
        val p = d.player1
        val crossroads = d.putPermanentOnBattlefield(p, "Corrupted Crossroads")
        assertEquals(2, d.cardRegistry.getCard("Corrupted Crossroads")!!.script.activatedAbilities.size)
        assertNull(d.activate(p, crossroads, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.manaPool(p).restrictedMana.isEmpty())
        val ring = d.putCardInHand(p, "Sol Ring")
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Sol Ring"))
    }

    @Test fun coloredModePaysOneLifeAndCannotTapAgain() {
        val d = customScenario(CorruptedCrossroads)
        val p = d.player1
        val crossroads = d.putPermanentOnBattlefield(p, "Corrupted Crossroads")
        val lifeBefore = d.getLifeTotal(p)
        assertNull(d.activate(p, crossroads, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))
        assertEquals(lifeBefore - 1, d.getLifeTotal(p))
        assertEquals(1, d.manaPool(p).restrictedMana.size)
        assertTrue(d.isTapped(crossroads))
        assertNotNull(d.activate(p, crossroads, index = 0).error)
    }

    @Test fun blueRestrictedManaCanCastActualDevoidSpell() {
        // Deepfathom Skulker costs {5}{U}, has printed Devoid, and is
        // already included among the existing registered Arcana custom cards.
        val d = customScenario(CorruptedCrossroads)
        val p = d.player1
        val crossroads = d.putPermanentOnBattlefield(p, "Corrupted Crossroads")
        val skulker = d.putCardInHand(p, "Deepfathom Skulker")
        d.giveColorlessMana(p, 5)
        assertNull(d.activate(p, crossroads, index = 1).error)
        d.resolveAll(Choices(color = Color.BLUE))
        assertEquals(1, d.manaPool(p).restrictedMana.size)
        assertNull("The colored source must be spendable on Devoid", d.castSpell(p, skulker).error)
        assertTrue(d.manaPool(p).restrictedMana.isEmpty())
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Deepfathom Skulker"))
    }

    @Test fun greenRestrictedManaCannotPayOrdinaryColoredCreature() {
        val d = customScenario(CorruptedCrossroads, "Grizzly Bears")
        val p = d.player1
        val crossroads = d.putPermanentOnBattlefield(p, "Corrupted Crossroads")
        val bears = d.putCardInHand(p, "Grizzly Bears")
        assertNull(d.activate(p, crossroads, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))
        d.giveColorlessMana(p, 1)
        assertNotNull("Ordinary green creatures are not Devoid", d.castSpell(p, bears).error)
        assertEquals(1, d.manaPool(p).restrictedMana.size)
    }

    @Test fun coloredRestrictedManaCannotPayForNondevoidColorlessArtifact() {
        val d = customScenario(CorruptedCrossroads, "Sol Ring")
        val p = d.player1
        val crossroads = d.putPermanentOnBattlefield(p, "Corrupted Crossroads")
        val ring = d.putCardInHand(p, "Sol Ring")
        assertNull(d.activate(p, crossroads, index = 1).error)
        d.resolveAll(Choices(color = Color.BLUE))
        assertNotNull("Colorless does NOT imply Devoid", d.castSpell(p, ring).error)
        assertEquals(1, d.manaPool(p).restrictedMana.size)
    }

    @Test fun independentlyProvidedUnrestrictedManaDoesNotConsumeDevoidRestrictedMana() {
        val d = customScenario(CorruptedCrossroads, "Sol Ring")
        val p = d.player1
        val crossroads = d.putPermanentOnBattlefield(p, "Corrupted Crossroads")
        val ring = d.putCardInHand(p, "Sol Ring")
        assertNull(d.activate(p, crossroads, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ring).error)
        assertEquals("Restricted colored mana must survive an unrelated payment", 1, d.manaPool(p).restrictedMana.size)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Sol Ring"))
    }
}
