package com.arcanatable.customcards

import com.arcanatable.customcards.cards.WhiptongueHydra
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class WhiptongueHydraScenarioTest {
    @Test
    fun entersAndDestroysFlyingCreaturesOfBothPlayersThenCountsThem() {
        val d = customScenario(WhiptongueHydra, "Storm Crow", "Suntail Hawk", "Grizzly Bears")
        val p = d.player1
        val hydra = d.putCardInHand(p, "Whiptongue Hydra")
        d.putCreatureOnBattlefield(p, "Suntail Hawk")
        d.putCreatureOnBattlefield(d.player2, "Storm Crow")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 5)

        assertNull(d.castSpell(p, hydra).error)
        d.resolveAll()

        assertEquals(6, d.projected.getPower(hydra))
        assertEquals(6, d.projected.getToughness(hydra))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Suntail Hawk"))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Storm Crow"))
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isNotEmpty())
    }

    @Test
    fun entersWithoutFlyersAndGetsNoCounters() {
        val d = customScenario(WhiptongueHydra, "Grizzly Bears")
        val p = d.player1
        val hydra = d.putCardInHand(p, "Whiptongue Hydra")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 5)

        assertNull(d.castSpell(p, hydra).error)
        d.resolveAll()

        assertEquals(4, d.projected.getPower(hydra))
        assertEquals(4, d.projected.getToughness(hydra))
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isNotEmpty())
    }

    @Test
    fun reachAllowsHydraToBlockAnAttackingFlyer() {
        val d = customScenario(WhiptongueHydra, "Storm Crow")
        val hydra = d.putCreatureOnBattlefield(d.player1, "Whiptongue Hydra")
        val flyer = d.putCreatureOnBattlefield(d.player2, "Storm Crow")
        d.removeSummoningSickness(flyer)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)
        assertNull(d.declareAttackers(d.player2, listOf(flyer), d.player1).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNull(d.declareBlockers(d.player1, mapOf(hydra to listOf(flyer))).error)
    }

    @Test
    fun creatureWithoutReachCannotBlockAnAttackingFlyer() {
        val d = customScenario(WhiptongueHydra, "Storm Crow", "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val flyer = d.putCreatureOnBattlefield(d.player2, "Storm Crow")
        d.removeSummoningSickness(flyer)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)
        assertNull(d.declareAttackers(d.player2, listOf(flyer), d.player1).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(d.player1, mapOf(bear to listOf(flyer))).error)
    }
}
