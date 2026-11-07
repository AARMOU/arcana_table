package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RoyalWarden
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class RoyalWardenScenarioTest {
    @Test
    fun entryCreatesTwoTappedArtifactWarriorTokens() {
        val d = customScenario(RoyalWarden)
        val p = d.player1
        val warden = d.putCardInHand(p, "Royal Warden")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, warden).error)
        d.resolveAll()

        val warriors = d.permanentsNamed(p, "Necron Warrior")
        assertEquals(2, warriors.size)
        warriors.forEach {
            assertTrue(d.state.getEntity(it)?.has<TokenComponent>() == true)
            assertTrue(d.isTapped(it))
            assertEquals(2, d.projected.getPower(it))
            assertEquals(2, d.projected.getToughness(it))
            assertTrue(d.projected.hasType(it, "ARTIFACT"))
            assertTrue(d.projected.hasColor(it, Color.BLACK))
            assertTrue(d.projected.hasSubtype(it, "Necron"))
            assertTrue(d.projected.hasSubtype(it, "Warrior"))
        }
    }

    @Test
    fun unearthAlsoCreatesTappedWarriorsAndExilesWardenAtEndStep() {
        val d = customScenario(RoyalWarden)
        val p = d.player1
        val warden = d.putCardInGraveyard(p, "Royal Warden")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 3)

        assertNull(d.activate(p, warden).error)
        d.resolveAll()

        assertTrue(warden in d.getPermanents(p))
        assertTrue(d.projected.hasKeyword(warden, Keyword.HASTE))
        assertEquals(2, d.permanentsNamed(p, "Necron Warrior").size)
        assertTrue(d.permanentsNamed(p, "Necron Warrior").all(d::isTapped))

        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.END)
        d.resolveAll()
        assertFalse(warden in d.getPermanents(p))
        assertTrue(d.zoneNames(p, com.wingedsheep.sdk.core.Zone.EXILE).contains("Royal Warden"))
    }
}
