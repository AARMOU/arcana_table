package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MigrationPath
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class MigrationPathScenarioTest {
    @Test
    fun searchesForUpToTwoBasicLandsAndPutsThemOntoTheBattlefieldTapped() {
        val d = customScenario(MigrationPath, "Forest", "Island")
        val player = d.player1
        d.putCardOnTopOfLibrary(player, "Forest")
        d.putCardOnTopOfLibrary(player, "Island")
        val spell = d.putCardInHand(player, "Migration Path")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 3)
        assertNull(d.castSpell(player, spell).error)
        d.resolveAll(Choices(pick = listOf("Forest", "Island")))

        val lands = d.getPermanents(player).filter { d.getCardName(it) in setOf("Forest", "Island") }
        assertEquals(2, lands.size)
        assertTrue(lands.all { d.state.getEntity(it)!!.get<TappedComponent>() != null })
    }
}
