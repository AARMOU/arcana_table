package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DrownerOfHope
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

class DrownerOfHopeScenarioTest {
    @Test
    fun enteringCreatesTwoFunctionalEldraziScions() {
        val d = customScenario(DrownerOfHope)
        val p = d.player1
        val card = d.putCardInHand(p, "Drowner of Hope")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        val drowner = d.findPermanent(p, "Drowner of Hope")!!
        assertTrue(d.cardRegistry.getCard("Drowner of Hope")!!.colors.isEmpty())
        assertNotNull(drowner)
        val scions = d.permanentsNamed(p, "Eldrazi Scion")
        assertEquals(2, scions.size)
        assertTrue(scions.all { d.abilityActions(p, it).isNotEmpty() })
    }

    @Test
    fun sacrificingScionAsCostTapsTargetCreature() {
        val d = customScenario(DrownerOfHope, "Grizzly Bears")
        val p = d.player1
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val card = d.putCardInHand(p, "Drowner of Hope")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        val source = d.findPermanent(p, "Drowner of Hope")!!
        assertEquals(2, d.permanentsNamed(p, "Eldrazi Scion").size)
        val abilityId = d.cardRegistry.getCard("Drowner of Hope")!!.script.activatedAbilities.single().id
        assertNull(d.submit(ActivateAbility(p, source, abilityId, targets = listOf(ChosenTarget.Permanent(enemy)))).error)
        d.resolveAll(Choices(pick = listOf("Eldrazi Scion"), targets = listOf(enemy)))
        assertTrue(d.isTapped(enemy))
        assertEquals(1, d.permanentsNamed(p, "Eldrazi Scion").size)
    }

    private fun tapWith(d: GameTestDriver, source: EntityId, victim: EntityId, sacrifice: EntityId) =
        d.submit(ActivateAbility(d.player1, source,
            d.cardRegistry.requireCard("Drowner of Hope").script.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Permanent(victim)),
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(sacrifice))))

    @Test fun cannotSacrificeAnOpponentsScionOrARegularCreature() {
        val d = customScenario(DrownerOfHope, "Grizzly Bears")
        val scion = card("Drowner Cost Scion") { typeLine = "Creature — Eldrazi Scion"; power = 1; toughness = 1 }
        val spawn = card("Drowner Cost Spawn") { typeLine = "Creature — Eldrazi Spawn"; power = 1; toughness = 1 }
        val humanScion = card("Drowner Cost Human Scion") { typeLine = "Creature — Human Scion"; power = 1; toughness = 1 }
        d.registerCards(listOf(scion, spawn, humanScion).map { CardLoader.fromJsonPreservingIds(exportLine(it)) })
        val source = d.putCreatureOnBattlefield(d.player1, "Drowner of Hope")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val opposingScion = d.putToken(d.player2, scion.name)
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val wrongSubtypes = listOf(d.putToken(d.player1, spawn.name), d.putToken(d.player1, humanScion.name))
        for (cost in listOf(opposingScion, bear) + wrongSubtypes) {
            assertNotNull(tapWith(d, source, enemy, cost).error)
            assertTrue(cost in d.state.getBattlefield())
            assertFalse(d.isTapped(enemy))
            assertTrue(d.state.stack.isEmpty())
        }
    }

    @Test fun illegalNoncreatureTargetDoesNotSpendTheSacrificeCost() {
        val d = customScenario(DrownerOfHope, "Sol Ring")
        val scion = card("Drowner Target Scion") { typeLine = "Creature — Eldrazi Scion"; power = 1; toughness = 1 }
        d.registerCard(CardLoader.fromJsonPreservingIds(exportLine(scion)))
        val source = d.putCreatureOnBattlefield(d.player1, "Drowner of Hope")
        val cost = d.putToken(d.player1, scion.name)
        val ring = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        assertNotNull(tapWith(d, source, ring, cost).error)
        assertTrue(cost in d.state.getBattlefield())
        assertFalse(d.isTapped(ring))
    }

    @Test fun noncreatureEldraziScionPermanentCanPayTheOracleCost() {
        val d = customScenario(DrownerOfHope, "Grizzly Bears")
        // CR 109.2 and 205.3m: a subtype-only object description means a permanent,
        // and Kindreds share creature subtypes; Oracle does not say "Scion creature".
        val scion = card("Drowner Kindred Scion") { typeLine = "Kindred Enchantment — Eldrazi Scion" }
        d.registerCard(CardLoader.fromJsonPreservingIds(exportLine(scion)))
        val source = d.putCreatureOnBattlefield(d.player1, "Drowner of Hope")
        d.tapPermanent(source) // Neither summoning sickness nor tapped source prohibits this cost.
        val cost = d.putPermanentOnBattlefield(d.player1, scion.name)
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        assertNull(tapWith(d, source, enemy, cost).error)
        assertTrue("Cost paid before resolution", cost in d.state.getZone(d.player1, Zone.GRAVEYARD))
        assertFalse(d.isTapped(enemy))
        d.resolveAll()
        assertTrue(d.isTapped(enemy))
    }

}
