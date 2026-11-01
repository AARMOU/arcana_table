package com.arcanatable.customcards

// Independent real-Argentum certification for Sister Repentia.
// Queue #411, implementation PR #416. Tests written separately from the Copilot scenario suite.
// Card rules: 5/1 for {3}{W}{B}; Martyrdom on death: gain 2 life and draw two cards;
// Miracle {W}{B} only on the first card drawn in a turn.
import com.arcanatable.customcards.cards.SisterRepentia
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SisterRepentiaIndependentCertificationTest {
    @Test fun deathTriggersExactlyTwoLifeAndTwoCards() {
        val d = customScenario(SisterRepentia, "Wrath of God")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Sister Repentia")
        val wrath = d.putCardInHand(p, "Wrath of God")
        val before = d.getHandSize(p)
        d.giveMana(p, Color.WHITE, 2)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, wrath).error)
        d.resolveAll()
        d.assertLifeTotal(p, 22)
        assertEquals("Wrath costs one hand card, death draws two", before + 1, d.getHandSize(p))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Sister Repentia"))
    }

    @Test fun exileIsNotDeathAndDoesNotTriggerMartyrdom() {
        val d = customScenario(SisterRepentia, "Path to Exile")
        val p = d.player1
        val sister = d.putCreatureOnBattlefield(p, "Sister Repentia")
        val before = d.getHandSize(p)
        val path = d.putCardInHand(p, "Path to Exile")
        d.giveMana(p, Color.WHITE)
        assertNull(d.castSpell(p, path, listOf(sister)).error)
        d.resolveAll()
        d.assertLifeTotal(p, 20)
        assertEquals("Exiling Sister must not draw cards", before, d.getHandSize(p))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Sister Repentia"))
    }

    @Test fun normalCastRequiresFullFiveManaAndEntersAsFiveOne() {
        val d = customScenario(SisterRepentia)
        val p = d.player1
        val sister = d.putCardInHand(p, "Sister Repentia")
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)
        assertNotNull("Four mana must not pay the five-mana normal cost", d.castSpell(p, sister).error)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, sister).error)
        d.resolveAll()
        assertEquals(5, d.projected.getPower(sister))
        assertEquals(1, d.projected.getToughness(sister))
    }
}
