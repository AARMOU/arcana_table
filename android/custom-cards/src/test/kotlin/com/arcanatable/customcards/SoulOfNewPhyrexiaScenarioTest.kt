package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SoulOfNewPhyrexia
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SoulOfNewPhyrexiaScenarioTest {
    @Test fun battlefieldAbilityProtectsEveryExistingFriendlyPermanentUntilEndOfTurn() {
        val d = customScenario(SoulOfNewPhyrexia, "Grizzly Bears", "Sol Ring", "Doom Blade")
        val p = d.player1
        val soul = d.putCreatureOnBattlefield(p, "Soul of New Phyrexia")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val land = d.putPermanentOnBattlefield(p, "Plains")
        val artifact = d.putPermanentOnBattlefield(p, "Sol Ring")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        assertTrue(d.projected.hasKeyword(soul, Keyword.TRAMPLE))
        d.giveColorlessMana(p, 5)
        assertNull(d.activate(p, soul).error)
        d.resolveAll()
        listOf(soul, bear, land, artifact).forEach { assertTrue(d.projected.hasKeyword(it, Keyword.INDESTRUCTIBLE)) }
        assertFalse(d.projected.hasKeyword(enemy, Keyword.INDESTRUCTIBLE))
        val lateCreature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        assertFalse("A later permanent was not present when the effect resolved", d.projected.hasKeyword(lateCreature, Keyword.INDESTRUCTIBLE))
        val blade = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, blade, listOf(bear)).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Soul of New Phyrexia"))
        assertEquals(2, d.permanentsNamed(p, "Grizzly Bears").size)
        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        listOf(soul, bear, land, artifact).forEach { assertFalse(d.projected.hasKeyword(it, Keyword.INDESTRUCTIBLE)) }
    }

    @Test fun graveyardAbilityExilesSoulAsCostAndProtectsFriendlyPermanents() {
        val d = customScenario(SoulOfNewPhyrexia, "Grizzly Bears")
        val p = d.player1
        val soul = d.putCardInGraveyard(p, "Soul of New Phyrexia")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveColorlessMana(p, 5)
        assertNull(d.activate(p, soul, index = 1).error)
        assertEquals(listOf("Soul of New Phyrexia"), d.zoneNames(p, Zone.EXILE))
        assertFalse(d.projected.hasKeyword(bear, Keyword.INDESTRUCTIBLE))
        d.resolveAll()
        assertTrue(d.projected.hasKeyword(bear, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(enemy, Keyword.INDESTRUCTIBLE))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).isEmpty())
    }
}
