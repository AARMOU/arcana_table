package com.arcanatable.customcards

// Independently authored Oracle checks, not copied from the Copilot scenarios.
// WotC Commander Masters Release Notes, Darksteel Monolith (pp. 115-116):
// https://media.wizards.com/2023/downloads/CMM_Release_Notes/EN_MTGCMM_ReleaseNotes_20230616.pdf
import com.arcanatable.customcards.cards.DarksteelMonolith
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class DarksteelMonolithIndependentCertificationTest {
    @Test
    fun eightManaArtifactResistsDestructionButCanBeExiled() {
        val d = customScenario(DarksteelMonolith, "Shatter", "Revoke Existence")
        val p = d.player1
        val card = d.putCardInHand(p, "Darksteel Monolith")
        d.giveColorlessMana(p, 7)
        assertNotNull(d.castSpell(p, card).error)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        val permanent = d.findPermanent(p, "Darksteel Monolith")!!
        assertTrue(d.projected.hasKeyword(permanent, Keyword.INDESTRUCTIBLE))
        val shatter = d.putCardInHand(p, "Shatter")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, shatter, listOf(permanent)).error)
        d.resolveAll()
        assertTrue(d.getPermanents(p).contains(permanent))
        val revoke = d.putCardInHand(p, "Revoke Existence")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, revoke, listOf(permanent)).error)
        d.resolveAll()
        assertFalse(d.getPermanents(p).contains(permanent))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Darksteel Monolith"))
    }

    @Test
    fun payingForAnEarlierSpellDoesNotConsumeTheOptionalZeroCost() {
        val d = customScenario(DarksteelMonolith, "Sol Ring", "Kozilek's Channeler")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()
        assertEquals(0, d.manaPool(p).colorless)
        val creature = d.putCardInHand(p, "Kozilek's Channeler")
        assertNull(d.submit(CastSpell(p, creature, useWithoutPayingManaCost = true)).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Kozilek's Channeler"))
    }

    @Test
    fun firstZeroCostSpellSucceedsAndSecondIsRefusedWithoutMana() {
        val d = customScenario(DarksteelMonolith, "Sol Ring")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        val first = d.putCardInHand(p, "Sol Ring")
        assertNull(d.submit(CastSpell(p, first, useWithoutPayingManaCost = true)).error)
        d.resolveAll()
        val second = d.putCardInHand(p, "Sol Ring")
        assertNotNull(d.submit(CastSpell(p, second, useWithoutPayingManaCost = true)).error)
        assertEquals(1, d.permanentsNamed(p, "Sol Ring").size)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun coloredSpellIsRefusedButColorlessNonartifactCreatureIsAllowed() {
        val d = customScenario(DarksteelMonolith, "Grizzly Bears", "Kozilek's Channeler")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        val bears = d.putCardInHand(p, "Grizzly Bears")
        assertNotNull(d.submit(CastSpell(p, bears, useWithoutPayingManaCost = true)).error)
        val creature = d.putCardInHand(p, "Kozilek's Channeler")
        assertNull(d.submit(CastSpell(p, creature, useWithoutPayingManaCost = true)).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Kozilek's Channeler"))
        assertNull(d.findPermanent(p, "Grizzly Bears"))
    }

    @Test
    fun allowanceResetsOnOpponentTurnForAnInstantButDoesNotGrantFlash() {
        val d = customScenario(DarksteelMonolith, "Sol Ring", "Scour from Existence", "Forest")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        val ring = d.putCardInHand(p, "Sol Ring")
        assertNull(d.submit(CastSpell(p, ring, useWithoutPayingManaCost = true)).error)
        d.resolveAll()
        val land = d.putPermanentOnBattlefield(d.player2, "Forest")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertNull(d.passPriority(d.player2).error)
        val sorcerySpeed = d.putCardInHand(p, "Sol Ring")
        assertNotNull(d.submit(CastSpell(p, sorcerySpeed, useWithoutPayingManaCost = true)).error)
        val instant = d.putCardInHand(p, "Scour from Existence")
        assertNull(d.submit(CastSpell(p, instant, targets = listOf(ChosenTarget.Permanent(land)),
            useWithoutPayingManaCost = true)).error)
        d.resolveAll()
        assertFalse(d.getPermanents(d.player2).contains(land))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        assertNull(d.submit(CastSpell(p, sorcerySpeed, useWithoutPayingManaCost = true)).error)
        d.resolveAll()
        assertEquals(2, d.permanentsNamed(p, "Sol Ring").size)
    }

    @Test
    fun permissionIsControllerOnlyAndDoesNotApplyFromGraveyardOrExile() {
        val d = customScenario(DarksteelMonolith, "Sol Ring")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        val buried = d.putCardInGraveyard(p, "Sol Ring")
        val exiled = d.putCardInExile(p, "Sol Ring")
        assertNotNull(d.submit(CastSpell(p, buried, useWithoutPayingManaCost = true)).error)
        assertNotNull(d.submit(CastSpell(p, exiled, useWithoutPayingManaCost = true)).error)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val opponentsRing = d.putCardInHand(d.player2, "Sol Ring")
        assertNotNull(d.submit(CastSpell(d.player2, opponentsRing, useWithoutPayingManaCost = true)).error)
        assertTrue(d.getGraveyard(p).contains(buried))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Sol Ring"))
    }

    @Test
    fun twoMonolithsProvideTwoIndependentAllowances() {
        val d = customScenario(DarksteelMonolith, "Sol Ring")
        val p = d.player1
        repeat(2) { d.putPermanentOnBattlefield(p, "Darksteel Monolith") }
        repeat(2) {
            val ring = d.putCardInHand(p, "Sol Ring")
            assertNull(d.submit(CastSpell(p, ring, useWithoutPayingManaCost = true)).error)
            d.resolveAll()
        }
        val third = d.putCardInHand(p, "Sol Ring")
        assertNotNull(d.submit(CastSpell(p, third, useWithoutPayingManaCost = true)).error)
        assertEquals(2, d.permanentsNamed(p, "Sol Ring").size)
    }

    @Test
    fun zeroManaAlternativeDoesNotWaiveMandatoryAdditionalCosts() {
        val d = customScenario(DarksteelMonolith, "Titan's Presence", "Grizzly Bears")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Titan's Presence")
        // The hand contains no colorless creature card to reveal for the additional cost.
        assertNotNull(d.submit(CastSpell(p, spell, targets = listOf(ChosenTarget.Permanent(target)),
            useWithoutPayingManaCost = true)).error)
        assertTrue(d.getPermanents(d.player2).contains(target))
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Titan's Presence"))
    }
}
