package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ZhulodokVoidGorger
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Zhulodok, Void Gorger: colorless spells you cast from your hand with mana value 7 or greater have
 * "Cascade, cascade" — two cascade triggers, each casting a cheaper nonland card for free.
 */
class ZhulodokVoidGorgerScenarioTest {

    private val zhulodok = "Zhulodok, Void Gorger"

    private fun GameTestDriver.cascadeTriggers(): Int = state.stack.count { id ->
        state.getEntity(id)?.get<TriggeredAbilityOnStackComponent>()?.let { "ascade" in it.description } == true
    }

    /** A game with Zhulodok out and Grizzly Bears then Llanowar Elves on top of player 1's library. */
    private fun withZhulodok(vararg library: String): GameTestDriver =
        customScenario(ZhulodokVoidGorger, "Grizzly Bears", "Llanowar Elves", *library).also { d ->
            d.putCreatureOnBattlefield(d.player1, zhulodok)
            d.putCardOnTopOfLibrary(d.player1, "Llanowar Elves")
            d.putCardOnTopOfLibrary(d.player1, "Grizzly Bears")
        }

    private fun GameTestDriver.castFromHand(name: String, colorless: Int = 0, vararg colored: Color): EntityId {
        val card = putCardInHand(player1, name)
        if (colorless > 0) giveColorlessMana(player1, colorless)
        colored.forEach { giveMana(player1, it) }
        assertNull(castSpell(player1, card).error)
        return card
    }

    @Test
    fun definitionIsAColorlessSixDropWithTwoCascadeGrants() {
        assertEquals(6, ZhulodokVoidGorger.manaCost.cmc)
        assertTrue(ZhulodokVoidGorger.colorIdentity.isEmpty())
        assertEquals(2, ZhulodokVoidGorger.script.staticAbilities.size)
    }

    @Test
    fun colorlessSevenPlusFromHandCascadesTwice() {
        val d = withZhulodok("Eldrazi Devastator")
        d.castFromHand("Eldrazi Devastator", colorless = 8)
        assertEquals("Cascade, cascade: two separate triggers", 2, d.cascadeTriggers())

        d.resolveAll(Choices(yes = true))
        assertNotNull(d.findPermanent(d.player1, "Eldrazi Devastator"))
        assertNotNull("First cascade cast Grizzly Bears for free", d.findPermanent(d.player1, "Grizzly Bears"))
        assertNotNull("Second cascade cast Llanowar Elves for free", d.findPermanent(d.player1, "Llanowar Elves"))
    }

    @Test
    fun cascadeCastIsOptional() {
        val d = withZhulodok("Eldrazi Devastator")
        d.castFromHand("Eldrazi Devastator", colorless = 8)
        d.resolveAll(Choices(yes = false))
        assertNull(d.findPermanent(d.player1, "Grizzly Bears"))
        assertNull(d.findPermanent(d.player1, "Llanowar Elves"))
        assertNotNull(d.findPermanent(d.player1, "Eldrazi Devastator"))
    }

    @Test
    fun manaValueBelowSevenDoesNotCascade() {
        val d = withZhulodok("Kozilek's Channeler")
        d.castFromHand("Kozilek's Channeler", colorless = 5)
        assertEquals(0, d.cascadeTriggers())
    }

    @Test
    fun coloredSevenDropDoesNotCascade() {
        val d = withZhulodok("Avenger of Zendikar")
        d.castFromHand("Avenger of Zendikar", 5, Color.GREEN, Color.GREEN)
        assertEquals("Only colorless spells get cascade", 0, d.cascadeTriggers())
    }

    @Test
    fun xIsCountedAtCastTime() {
        val d = withZhulodok("Walking Ballista")
        val ballista = d.putCardInHand(d.player1, "Walking Ballista")
        d.giveColorlessMana(d.player1, 8)
        assertNull(d.castXSpell(d.player1, ballista, xValue = 4).error) // {X}{X} with X=4: mana value 8
        assertEquals(2, d.cascadeTriggers())
    }

    @Test
    fun xBelowThresholdDoesNotCascade() {
        val d = withZhulodok("Walking Ballista")
        val ballista = d.putCardInHand(d.player1, "Walking Ballista")
        d.giveColorlessMana(d.player1, 6)
        assertNull(d.castXSpell(d.player1, ballista, xValue = 3).error) // mana value 6
        assertEquals(0, d.cascadeTriggers())
    }

    @Test
    fun noCascadeWithoutZhulodok() {
        val d = customScenario(ZhulodokVoidGorger, "Eldrazi Devastator")
        val card = d.putCardInHand(d.player1, "Eldrazi Devastator")
        d.giveColorlessMana(d.player1, 8)
        assertNull(d.castSpell(d.player1, card).error)
        assertEquals(0, d.cascadeTriggers())
    }

    @Test
    fun opponentsSpellsDoNotCascade() {
        val d = customScenario(ZhulodokVoidGorger, "Eldrazi Devastator", "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, zhulodok) // the opponent's Zhulodok
        val card = d.putCardInHand(d.player1, "Eldrazi Devastator")
        d.giveColorlessMana(d.player1, 8)
        assertNull(d.castSpell(d.player1, card).error)
        assertEquals("Zhulodok only grants to its controller's spells", 0, d.cascadeTriggers())
    }
}
