package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PantlazaSunFavored
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class PantlazaSunFavoredScenarioTest {
    @Test
    fun dinosaurEntryMayDiscoverUsingItsToughnessOnlyOnceEachTurn() {
        val d = customScenario(PantlazaSunFavored, "Grizzly Bears", "Dinosaur Egg")
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        val pantlaza = d.putCardInHand(p, "Pantlaza, Sun-Favored")
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, pantlaza).error)
        d.resolveAll()

        val egg = d.putCreatureOnBattlefield(p, "Dinosaur Egg")
        d.resolveAll()
        assertNull("The once-per-turn limit prevents a second discover", d.state.pendingDecision)
        assertNotNull(egg)
    }
}
