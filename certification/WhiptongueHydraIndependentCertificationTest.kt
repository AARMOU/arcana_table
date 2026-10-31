package com.arcanatable.customcards

import com.arcanatable.customcards.cards.WhiptongueHydra
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class WhiptongueHydraIndependentCertificationTest {
    private fun GameTestDriver.counterCount(permanent: EntityId): Int =
        state.getEntity(permanent)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    private fun GameTestDriver.castWhiptongueHydra(): EntityId {
        val hydra = putCardInHand(player1, "Whiptongue Hydra")
        giveMana(player1, Color.GREEN)
        giveColorlessMana(player1, 5)
        assertNull(castSpell(player1, hydra).error)
        resolveAll()
        return hydra
    }

    @Test
    fun fiveGenericManaWithoutGreenCannotCastButFiveAndGreenCan() {
        val d = customScenario(WhiptongueHydra)
        val player = d.player1
        val hydra = d.putCardInHand(player, "Whiptongue Hydra")
        d.giveColorlessMana(player, 5)

        assertNotNull("The {G} in {5}{G} cannot be paid with generic mana", d.castSpell(player, hydra).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Whiptongue Hydra"))
        assertTrue(d.permanentsNamed(player, "Whiptongue Hydra").isEmpty())

        d.giveMana(player, Color.GREEN)
        assertNull(d.castSpell(player, hydra).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(hydra))
        assertTrue(d.projected.hasKeyword(hydra, Keyword.REACH))
        assertFalse(d.projected.hasKeyword(hydra, Keyword.FLYING))
        assertEquals(4, d.projected.getPower(hydra))
        assertEquals(4, d.projected.getToughness(hydra))
    }

    @Test
    fun fourGenericManaAndGreenCannotPayTheFiveGenericCost() {
        val d = customScenario(WhiptongueHydra)
        val player = d.player1
        val hydra = d.putCardInHand(player, "Whiptongue Hydra")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 4)

        assertNotNull("Four generic mana cannot pay the {5} portion", d.castSpell(player, hydra).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Whiptongue Hydra"))
        assertTrue(d.permanentsNamed(player, "Whiptongue Hydra").isEmpty())

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, hydra).error)
        d.resolveAll()
        assertTrue(d.getPermanents(player).contains(hydra))
    }

    @Test
    fun enteringHydraDestroysEveryFlyingCreatureAndGetsOneCounterPerCreatureDestroyed() {
        val d = customScenario(WhiptongueHydra, "Serra Angel", "Ornithopter", "Grizzly Bears", "Giant Spider")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Serra Angel")
        d.putCreatureOnBattlefield(opponent, "Ornithopter")
        d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putCreatureOnBattlefield(opponent, "Giant Spider")

        val hydra = d.castWhiptongueHydra()

        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Serra Angel"))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Ornithopter"))
        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isNotEmpty())
        assertTrue(d.permanentsNamed(opponent, "Giant Spider").isNotEmpty())
        assertEquals(2, d.counterCount(hydra))
        assertEquals(6, d.projected.getPower(hydra))
        assertEquals(6, d.projected.getToughness(hydra))
    }

    @Test
    fun enteringWithoutFlyingCreaturesDestroysNothingAndAddsNoCounters() {
        val d = customScenario(WhiptongueHydra, "Grizzly Bears", "Giant Spider")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putCreatureOnBattlefield(opponent, "Giant Spider")

        val hydra = d.castWhiptongueHydra()

        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isNotEmpty())
        assertTrue(d.permanentsNamed(opponent, "Giant Spider").isNotEmpty())
        assertEquals(0, d.counterCount(hydra))
        assertEquals(4, d.projected.getPower(hydra))
        assertEquals(4, d.projected.getToughness(hydra))
    }

    @Test
    fun flyingCreatureEnteringAfterHydraDoesNotTriggerItsEntersAbility() {
        val d = customScenario(WhiptongueHydra, "Serra Angel")
        val hydra = d.castWhiptongueHydra()
        val angel = d.putCreatureOnBattlefield(d.player2, "Serra Angel")

        assertTrue(d.getPermanents(d.player2).contains(angel))
        assertEquals(0, d.counterCount(hydra))
        assertEquals(4, d.projected.getPower(hydra))
        assertEquals(4, d.projected.getToughness(hydra))
    }
}
