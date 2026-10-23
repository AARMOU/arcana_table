package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BarretAvalancheLeader
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class BarretAvalancheLeaderScenarioTest {
    @Test
    fun equipmentCreatesRebelAndBeginningOfCombatAttachesIt() {
        val d = CardPool.driver("Swiftfoot Boots").apply {
            registerCard(BarretAvalancheLeader)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Barret, Avalanche Leader")
        val boots = d.putCardInHand(p, "Swiftfoot Boots")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, boots).error)
        d.resolveAll()

        val rebel = d.getCreatures(p).firstOrNull { d.projected.hasSubtype(it, "Rebel") }
        assertNotNull("an Equipment entering created a Rebel token", rebel)
        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.resolveAll(Choices(targets = listOf(boots, rebel!!)))

        assertEquals(rebel, d.state.getEntity(boots)!!.get<AttachedToComponent>()?.targetId)
    }
}
