package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BastionProtector
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class BastionProtectorScenarioTest {
    @Test
    fun buffsOnlyCommanderCreaturesUnderItsControllersControlAndStopsWhenItLeaves() {
        val d = customScenario(BastionProtector, "Grizzly Bears")
        val p = d.player1
        val protector = d.putCreatureOnBattlefield(p, "Bastion Protector")
        val myCommander = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val stolenCommander = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val ordinaryCreature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemyCommander = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.replaceState(d.state.updateEntity(myCommander) { it.with(CommanderComponent(ownerId = p)) })
        d.replaceState(d.state.updateEntity(stolenCommander) { it.with(CommanderComponent(ownerId = d.player2)) })
        d.replaceState(d.state.updateEntity(enemyCommander) { it.with(CommanderComponent(ownerId = d.player2)) })

        assertEquals(3, d.projected.getPower(protector))
        assertEquals(3, d.projected.getToughness(protector))
        listOf(myCommander, stolenCommander).forEach {
            assertEquals(4, d.projected.getPower(it))
            assertEquals(4, d.projected.getToughness(it))
            assertTrue(d.projected.hasKeyword(it, Keyword.INDESTRUCTIBLE))
        }
        listOf(ordinaryCreature, enemyCommander).forEach {
            assertEquals(2, d.projected.getPower(it))
            assertFalse(d.projected.hasKeyword(it, Keyword.INDESTRUCTIBLE))
        }

        d.moveToGraveyard(protector)
        assertEquals(2, d.projected.getPower(myCommander))
        assertFalse(d.projected.hasKeyword(myCommander, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(stolenCommander, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun aCommanderThatIsNotACreatureIsNotAffected() {
        val d = customScenario(BastionProtector, "Sol Ring")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Bastion Protector")
        val artifactCommander = d.putPermanentOnBattlefield(p, "Sol Ring")
        d.replaceState(d.state.updateEntity(artifactCommander) { it.with(CommanderComponent(ownerId = p)) })
        assertFalse(d.projected.hasKeyword(artifactCommander, Keyword.INDESTRUCTIBLE))
    }
}
