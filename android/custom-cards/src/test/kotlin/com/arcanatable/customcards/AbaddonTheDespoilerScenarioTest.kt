package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AbaddonTheDespoiler
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Abaddon the Despoiler — Mark of Chaos Ascendant: during your turn, spells you cast from your hand
 * with mana value X or less have cascade, X = the total life your opponents have lost this turn.
 */
class AbaddonTheDespoilerScenarioTest {

    private val abaddon = "Abaddon the Despoiler"

    private fun GameTestDriver.cascadeTriggers(): Int = state.stack.count { id ->
        state.getEntity(id)?.get<TriggeredAbilityOnStackComponent>()?.let { "ascade" in it.description } == true
    }

    /** Abaddon out for player 1, Llanowar Elves on top of player 1's library to cascade into. */
    private fun withAbaddon(vararg library: String): GameTestDriver =
        customScenario(AbaddonTheDespoiler, "Shock", "Llanowar Elves", "Grizzly Bears", *library).also { d ->
            d.putCreatureOnBattlefield(d.player1, abaddon)
            d.putCardOnTopOfLibrary(d.player1, "Llanowar Elves")
        }

    /** Player 1 Shocks [target] and lets it resolve. */
    private fun GameTestDriver.shock(target: EntityId) {
        val shock = putCardInHand(player1, "Shock")
        giveMana(player1, Color.RED)
        assertNull(castSpell(player1, shock, listOf(target)).error)
        resolveAll()
    }

    private fun GameTestDriver.castBears(): EntityId {
        val bears = putCardInHand(player1, "Grizzly Bears")
        giveColorlessMana(player1, 1); giveMana(player1, Color.GREEN)
        assertNull(castSpell(player1, bears).error)
        return bears
    }

    @Test
    fun definition() {
        assertEquals(5, AbaddonTheDespoiler.manaCost.cmc)
        assertEquals(setOf(Color.BLUE, Color.BLACK, Color.RED), AbaddonTheDespoiler.colorIdentity)
        assertTrue(AbaddonTheDespoiler.keywords.contains(Keyword.TRAMPLE))
    }

    @Test
    fun noLifeLostMeansXIsZeroAndNothingCascades() {
        val d = withAbaddon()
        d.castBears()
        assertEquals(0, d.cascadeTriggers())
    }

    @Test
    fun afterOpponentLosesTwoATwoDropCascades() {
        val d = withAbaddon()
        d.shock(d.player2)
        assertEquals(18, d.getLifeTotal(d.player2))
        d.castBears()
        assertEquals("Mana value 2 ≤ X = 2", 1, d.cascadeTriggers())
        d.resolveAll(Choices(yes = true))
        assertNotNull("Cascade cast Llanowar Elves for free", d.findPermanent(d.player1, "Llanowar Elves"))
        assertNotNull(d.findPermanent(d.player1, "Grizzly Bears"))
    }

    @Test
    fun manaValueAboveXDoesNotCascade() {
        val d = withAbaddon("Prodigal Pyromancer")
        d.shock(d.player2) // X = 2
        val pyro = d.putCardInHand(d.player1, "Prodigal Pyromancer") // mana value 3
        d.giveColorlessMana(d.player1, 2); d.giveMana(d.player1, Color.RED)
        assertNull(d.castSpell(d.player1, pyro).error)
        assertEquals(0, d.cascadeTriggers())
    }

    @Test
    fun yourOwnLifeLossDoesNotCount() {
        val d = withAbaddon()
        d.shock(d.player1) // you lose 2, opponents lost 0
        d.castBears()
        assertEquals(0, d.cascadeTriggers())
    }

    @Test
    fun notDuringYourTurn() {
        val d = withAbaddon("Lightning Bolt")
        // Move to the opponent's turn, then respond in their upkeep: they lose 2, then Bolt (mana value 1).
        d.passPriorityUntil(Step.UPKEEP, d.player2)
        if (d.state.priorityPlayerId == d.player2) d.passPriority(d.player2)
        assertEquals(d.player1, d.state.priorityPlayerId)
        d.shock(d.player2)
        if (d.state.priorityPlayerId == d.player2) d.passPriority(d.player2)
        val bolt = d.putCardInHand(d.player1, "Lightning Bolt")
        d.giveMana(d.player1, Color.RED)
        assertNull(d.castSpell(d.player1, bolt, listOf(d.player2)).error)
        assertEquals("Mark of Chaos Ascendant only works during your turn", 0, d.cascadeTriggers())
    }

    @Test
    fun opponentsAbaddonDoesNotGrantYouCascade() {
        val d = customScenario(AbaddonTheDespoiler, "Shock", "Grizzly Bears", "Llanowar Elves")
        d.putCreatureOnBattlefield(d.player2, abaddon)
        d.shock(d.player2)
        d.castBears()
        assertEquals(0, d.cascadeTriggers())
    }
}
