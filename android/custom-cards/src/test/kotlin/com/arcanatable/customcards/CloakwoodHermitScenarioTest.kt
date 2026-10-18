package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CloakwoodHermit
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class CloakwoodHermitScenarioTest {
    @Test
    fun commanderCreatesSquirrelsWhenCreatureDiesDuringTurn() {
        val d = CardPool.driver("Ayula, Queen Among Bears", "Grizzly Bears", "Doom Blade").apply {
            registerCard(CloakwoodHermit)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        // Put the commander-tagged creature and background into play to exercise the granted ability.
        val commander = d.putCreatureOnBattlefield(p, "Ayula, Queen Among Bears")
        d.replaceState(d.state.updateEntity(commander) { it.with(CommanderComponent(ownerId = p)) })
        d.putPermanentOnBattlefield(p, "Cloakwood Hermit")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val doomBlade = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, com.wingedsheep.sdk.core.Color.BLACK)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, doomBlade, listOf(bear)).error)
        d.resolveAll()
        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()
        assertTrue("The commander remains on the battlefield", d.state.getEntity(commander) != null)
        assertEquals(2, d.getCreatures(p).count { d.getCardName(it) == "Squirrel Token" })
    }
}
