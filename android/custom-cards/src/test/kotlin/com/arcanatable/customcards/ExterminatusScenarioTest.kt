package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Exterminatus
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.card
import org.junit.Assert.*
import org.junit.Test

class ExterminatusScenarioTest {
    @Test
    fun removesOpponentsIndestructibleFromNoncreaturesButPreservesOwnProtectionAndLands() {
        val d = customScenario(Exterminatus, "Grizzly Bears")
        val p = d.player1
        d.registerCard(card("Indestructible Test Relic") {
            manaCost = "{3}"
            typeLine = "Artifact"
            keywords(Keyword.INDESTRUCTIBLE)
        })
        d.registerCard(card("Indestructible Test Beast") {
            manaCost = "{3}"
            typeLine = "Creature — Beast"
            power = 3
            toughness = 3
            keywords(Keyword.INDESTRUCTIBLE)
        })
        val ownRelic = d.putPermanentOnBattlefield(p, "Indestructible Test Relic")
        d.putPermanentOnBattlefield(d.player2, "Indestructible Test Relic")
        d.putCreatureOnBattlefield(d.player2, "Indestructible Test Beast")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val ownLand = d.putPermanentOnBattlefield(p, "Plains")
        val otherLand = d.putPermanentOnBattlefield(d.player2, "Plains")
        val spell = d.putCardInHand(p, "Exterminatus")
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 5)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()
        assertTrue(ownRelic in d.getPermanents(p))
        assertTrue(d.projected.hasKeyword(ownRelic, Keyword.INDESTRUCTIBLE))
        assertEquals(listOf(otherLand), d.getPermanents(d.player2))
        assertEquals(setOf(ownRelic, ownLand), d.getPermanents(p).toSet())
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Indestructible Test Relic"))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Indestructible Test Beast"))
    }
}
