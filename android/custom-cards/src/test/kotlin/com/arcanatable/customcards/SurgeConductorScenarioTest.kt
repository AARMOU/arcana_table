package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SurgeConductor
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.*
import org.junit.Assert.*
import org.junit.Test

class SurgeConductorScenarioTest {
    @Test
    fun anotherOwnedNontokenArtifactEnteringProliferatesButItsOwnEntryDoesNot() {
        val d = customScenario(SurgeConductor, "Jace Beleren", "Sol Ring")
        val p = d.player1
        val walker = d.putPermanentOnBattlefield(p, "Jace Beleren")
        d.addComponent(walker, CountersComponent(mapOf(CounterType.LOYALTY to 3)))

        val conductor = d.putCardInHand(p, "Surge Conductor")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, conductor).error)
        d.resolveAll(Choices(pick = listOf("Jace Beleren")))
        assertEquals(3, d.state.getEntity(walker)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY))

        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll(Choices(pick = listOf("Jace Beleren")))
        assertEquals(4, d.state.getEntity(walker)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY))
    }

    @Test
    fun otherPermanentTokenArtifactAndOpponentArtifactEntriesDoNotProliferate() {
        val d = customScenario(SurgeConductor, "Jace Beleren", "Grizzly Bears", "Sol Ring")
        d.registerCard(card("Scenario Treasure Maker") {
            manaCost = "{1}"
            typeLine = "Creature — Human"
            power = 1
            toughness = 1
            oracleText = "When this creature enters, create a Treasure token."
            triggeredAbility {
                trigger = Triggers.self.enters()
                effect = Effects.CreateTreasure(1)
            }
        })

        val p = d.player1
        val q = d.player2
        val walker = d.putPermanentOnBattlefield(p, "Jace Beleren")
        d.addComponent(walker, CountersComponent(mapOf(CounterType.LOYALTY to 3)))
        val conductor = d.putCardInHand(p, "Surge Conductor")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, conductor).error)
        d.resolveAll()
        assertEquals(3, d.state.getEntity(walker)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY))

        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll(Choices(pick = listOf("Jace Beleren")))
        assertEquals(3, d.state.getEntity(walker)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY))

        val maker = d.putCardInHand(p, "Scenario Treasure Maker")
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, maker).error)
        d.resolveAll(Choices(pick = listOf("Jace Beleren")))
        assertEquals(1, d.permanentsNamed(p, "Treasure").size)
        assertEquals(3, d.state.getEntity(walker)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY))

        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN, activePlayer = q)
        val opponentRing = d.putCardInHand(q, "Sol Ring")
        d.giveColorlessMana(q, 1)
        assertNull(d.castSpell(q, opponentRing).error)
        d.resolveAll(Choices(pick = listOf("Jace Beleren")))
        assertEquals(3, d.state.getEntity(walker)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY))
    }
}
