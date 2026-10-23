package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CompySwarm
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class CompySwarmScenarioTest {
    @Test
    fun creatureDeathThisTurnCreatesTappedCopyAtEndStep() {
        val d = CardPool.driver("Grizzly Bears", "Doom Blade").apply {
            registerCard(CompySwarm)
            initMirrorMatch(Deck.of("Forest" to 40))
        }
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Compy Swarm")
        val doomed = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val doomBlade = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, com.wingedsheep.sdk.core.Color.BLACK)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, doomBlade, listOf(doomed)).error)
        d.resolveAll()
        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()
        val swarmCopies = d.getCreatures(p).filter { d.getCardName(it) == "Compy Swarm" }
        assertEquals(2, swarmCopies.size)
        assertTrue(swarmCopies.any { d.state.getEntity(it)!!.has<TappedComponent>() })
    }
}
