package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UginTheIneffable
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.MayLookAtInExileComponent
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

/** These assertions must execute on B131, not be inferred from SDK source similarities. */
class UginTheIneffableScenarioTest {
    @Test fun plusOneExilesOneFaceDownForControllerOnlyAndCreatesLinkedSpirit() {
        val d = customScenario(UginTheIneffable, "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val ugin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        assertEquals(2, d.cardRegistry.getCard("Ugin, the Ineffable")!!.script.activatedAbilities.size)
        assertNull(d.activate(p, ugin, index = 0).error)
        d.resolveAll()

        val spirits = d.permanentsNamed(p, "Spirit")
        assertEquals(1, spirits.size)
        val cardInExile = d.getExile(p).single()
        assertNotNull("The exiled card must really be face down",
            d.state.getEntity(cardInExile)!!.get<FaceDownComponent>())
        val viewers = d.state.getEntity(cardInExile)!!
            .get<MayLookAtInExileComponent>()?.playerIds.orEmpty()
        assertTrue("Ugin's controller may look at the private card", p in viewers)
        assertFalse("Opponent must not learn a face-down exile card", opponent in viewers)
        val links = d.state.getEntity(spirits.single())!!
            .get<LinkedExileComponent>()?.exiledIds.orEmpty()
        assertEquals("Link must be on the Spirit, not on Ugin", listOf(cardInExile), links)
        assertNull(d.state.getEntity(ugin)!!.get<LinkedExileComponent>())
    }

    @Test fun colorlessSpellDiscountDoesNotPayColoredMana() {
        val d = customScenario(UginTheIneffable, "Sol Ring", "Grizzly Bears")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        val ring = d.putCardInHand(p, "Sol Ring")
        assertNull("One-generic-mana Sol Ring should cost zero", d.castSpell(p, ring).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Sol Ring"))
        val bear = d.putCardInHand(p, "Grizzly Bears")
        assertNotNull("Discount must not invent green mana", d.castSpell(p, bear).error)
    }

    @Test fun plusOneWithEmptyLibraryStillCreatesSpiritWithoutFakeLinkedCard() {
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        // Remove the library as a setup operation (not through game actions).
        d.state.getLibrary(p).toList().forEach(d::moveToGraveyard)
        val ugin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        assertNull(d.activate(p, ugin, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Spirit").size)
        assertTrue(d.getExile(p).isEmpty())
        assertTrue(d.state.getEntity(d.permanentsNamed(p, "Spirit").single())!!
            .get<LinkedExileComponent>()?.exiledIds.orEmpty().isEmpty())
    }
}
