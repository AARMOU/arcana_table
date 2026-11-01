package com.arcanatable.customcards

// Independent real-engine certification, issue 384, implementation PR 385.
import com.arcanatable.customcards.cards.LokhustHeavyDestroyer
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class LokhustHeavyDestroyerIndependentCertificationTest {
    @Test fun eachPlayerSacrificesOneCreature() {
        val d = customScenario(LokhustHeavyDestroyer, "Grizzly Bears", "Llanowar Elves")
        d.putCreatureOnBattlefield(d.player1, "Llanowar Elves")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val card = d.putCardInHand(d.player1, "Lokhust Heavy Destroyer")
        d.giveMana(d.player1, Color.BLACK, 3)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll(Choices(pick = listOf("Llanowar Elves", "Grizzly Bears")))
        assertTrue(d.zoneNames(d.player1, Zone.GRAVEYARD).contains("Llanowar Elves"))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test fun unearthRequiresThreeBlackMana() {
        val d = customScenario(LokhustHeavyDestroyer)
        val card = d.putCardInGraveyard(d.player1, "Lokhust Heavy Destroyer")
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 6)
        assertNotNull(d.activate(d.player1, card).error)
        assertTrue(d.zoneNames(d.player1, Zone.GRAVEYARD).contains("Lokhust Heavy Destroyer"))
    }
    @Test fun unearthGrantsHasteAndExilesAtTheNextEndStep() {
        val d = customScenario(LokhustHeavyDestroyer, "Llanowar Elves", "Grizzly Bears")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Llanowar Elves")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val card = d.putCardInGraveyard(player, "Lokhust Heavy Destroyer")
        d.giveColorlessMana(player, 5)
        d.giveMana(player, Color.BLACK, 3)
        assertNull(d.activate(player, card).error)
        d.resolveAll(Choices(pick = listOf("Llanowar Elves", "Grizzly Bears")))
        val creature = d.findPermanent(player, "Lokhust Heavy Destroyer")
        assertNotNull(creature)
        assertTrue(d.projected.hasKeyword(creature!!, Keyword.HASTE))
        d.passPriorityUntil(Step.END, activePlayer = player)
        d.resolveAll()
        assertTrue(d.permanentsNamed(player, "Lokhust Heavy Destroyer").isEmpty())
        assertTrue(d.zoneNames(player, Zone.EXILE).contains("Lokhust Heavy Destroyer"))
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains("Lokhust Heavy Destroyer"))
    }
}
