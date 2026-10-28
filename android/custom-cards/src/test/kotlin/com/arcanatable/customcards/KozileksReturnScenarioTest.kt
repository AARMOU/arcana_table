package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KozileksReturn
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class KozileksReturnScenarioTest {
    private fun setup(): GameTestDriver = customScenario(KozileksReturn, "Grizzly Bears", "Craw Wurm", "Pathrazer of Ulamog", "Ancient Brontodon").apply {
        registerCard(card("Return Probe Seven") { manaCost = "{7}"; typeLine = "Creature — Eldrazi"; power = 7; toughness = 7 })
        registerCard(card("Return Probe Six") { manaCost = "{6}"; typeLine = "Creature — Eldrazi"; power = 6; toughness = 6 })
    }

    private fun GameTestDriver.alive(id: EntityId) = getCreatures(player1).contains(id) || getCreatures(player2).contains(id)

    @Test fun spellDeals2ToEachCreatureOfBothPlayersAndIsColorless() {
        val d = setup()
        val myBear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val theirBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val wurm = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        val spell = d.putCardInHand(d.player1, "Kozilek's Return")
        assertTrue("devoid: no color", d.state.projectedState.getColors(spell).isEmpty())
        d.giveMana(d.player1, Color.RED); d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()
        assertFalse(d.alive(myBear)); assertFalse(d.alive(theirBear))
        assertTrue("6/4 survives 2 damage", d.alive(wurm))
        assertTrue(d.getGraveyardCardNames(d.player1).contains("Kozilek's Return"))
    }

    private fun GameTestDriver.castFromHand(name: String, colorless: Int, green: Int = 0) {
        val c = putCardInHand(player1, name)
        giveColorlessMana(player1, colorless)
        if (green > 0) giveMana(player1, Color.GREEN, green)
        assertNull(castSpell(player1, c).error)
    }

    @Test fun castingBigEldraziLetsYouExileItForFiveToEachCreature() {
        val d = setup()
        d.putCardInGraveyard(d.player1, "Kozilek's Return")
        val wurm = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        val mine = d.putCreatureOnBattlefield(d.player1, "Craw Wurm")
        d.castFromHand("Return Probe Seven", 7)
        d.resolveAll(Choices(yes = true))
        assertFalse(d.alive(wurm)); assertFalse(d.alive(mine))
        assertTrue(d.getExileCardNames(d.player1).contains("Kozilek's Return"))
        assertFalse(d.getGraveyardCardNames(d.player1).contains("Kozilek's Return"))
        assertNotNull("the Eldrazi resolves after the trigger and survives", d.findPermanent(d.player1, "Return Probe Seven"))
    }

    @Test fun decliningKeepsCardInGraveyardAndDealsNoDamage() {
        val d = setup()
        d.putCardInGraveyard(d.player1, "Kozilek's Return")
        val wurm = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        d.castFromHand("Pathrazer of Ulamog", 11)
        d.resolveAll(Choices(yes = false))
        assertTrue(d.alive(wurm))
        assertTrue(d.getGraveyardCardNames(d.player1).contains("Kozilek's Return"))
    }

    @Test fun smallEldraziOrNonEldraziDoNotTrigger() {
        val d = setup()
        d.putCardInGraveyard(d.player1, "Kozilek's Return")
        val wurm = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        d.castFromHand("Return Probe Six", 6)
        assertNull("no may-question for mana value 6", d.state.pendingDecision)
        d.resolveAll(Choices(yes = true))
        d.castFromHand("Ancient Brontodon", 6, green = 2)
        assertNull("no may-question for a non-Eldrazi", d.state.pendingDecision)
        d.resolveAll(Choices(yes = true))
        assertTrue(d.alive(wurm))
        assertTrue(d.getGraveyardCardNames(d.player1).contains("Kozilek's Return"))
    }

    @Test fun opponentsBigEldraziDoesNotTriggerYourCopy() {
        val d = setup()
        d.putCardInGraveyard(d.player1, "Kozilek's Return")
        val wurm = d.putCreatureOnBattlefield(d.player1, "Craw Wurm")
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN, d.player2)
        val c = d.putCardInHand(d.player2, "Return Probe Seven")
        d.giveColorlessMana(d.player2, 7)
        assertNull(d.castSpell(d.player2, c).error)
        d.resolveAll(Choices(yes = true))
        assertTrue(d.alive(wurm))
        assertTrue(d.getGraveyardCardNames(d.player1).contains("Kozilek's Return"))
    }

    @Test fun fromHandTheTriggerDoesNothing() {
        val d = setup()
        d.putCardInHand(d.player1, "Kozilek's Return")
        val wurm = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        d.castFromHand("Return Probe Seven", 7)
        d.resolveAll(Choices(yes = true))
        assertTrue(d.alive(wurm))
    }
}
