package com.arcanatable.customcards

// Independent real-engine scenarios written only from Sautekh Immortal's Oracle record:
// "Flash. Elite Troops — This creature enters with a +1/+1 counter on it for each creature
// that died this turn."
import com.arcanatable.customcards.cards.SautekhImmortal
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class SautekhImmortalIndependentCertificationTest {
    private val name = "Sautekh Immortal"

    private fun GameTestDriver.plusOneCounters(permanent: EntityId): Int =
        state.getEntity(permanent)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    @Test
    fun flashAllowsCastingDuringAnOpponentsTurn() {
        val d = customScenario(SautekhImmortal)
        val p = d.player1
        val immortal = d.putCardInHand(p, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertEquals(d.player2, d.activePlayer)
        d.passPriority(d.player2)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, immortal).error)
        d.resolveAll()

        assertTrue(d.getPermanents(p).contains(immortal))
        assertEquals(2, d.projected.getPower(immortal))
        assertEquals(2, d.projected.getToughness(immortal))
    }

    @Test
    fun aCreatureWithoutFlashCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(SautekhImmortal, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.passPriority(d.player2)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        assertNotNull(d.castSpell(p, bear).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Grizzly Bears"))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
    }

    @Test
    fun threeColorlessManaCannotReplaceTheRequiredBlackMana() {
        val d = customScenario(SautekhImmortal)
        val p = d.player1
        val immortal = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 3)

        assertNotNull(d.castSpell(p, immortal).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains(name))
        assertTrue(d.permanentsNamed(p, name).isEmpty())

        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, immortal).error)
        d.resolveAll()
        assertTrue(d.getPermanents(p).contains(immortal))
        assertEquals(0, d.plusOneCounters(immortal))
        assertEquals(2, d.projected.getPower(immortal))
        assertEquals(2, d.projected.getToughness(immortal))
    }

    @Test
    fun oneBlackAndOneGenericManaCannotPayTheFullCost() {
        val d = customScenario(SautekhImmortal)
        val p = d.player1
        val immortal = d.putCardInHand(p, name)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)

        assertNotNull(d.castSpell(p, immortal).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains(name))
        assertTrue(d.permanentsNamed(p, name).isEmpty())

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, immortal).error)
        d.resolveAll()
        assertTrue(d.getPermanents(p).contains(immortal))
    }

    @Test
    fun entersWithOnePlusOneCounterForEachCreatureThatDiedThisTurn() {
        val d = customScenario(SautekhImmortal, "Grizzly Bears", "Pyroclasm")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val pyroclasm = d.putCardInHand(p, "Pyroclasm")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, pyroclasm).error)
        d.resolveAll()

        assertEquals(1, d.zoneNames(p, Zone.GRAVEYARD).count { it == "Grizzly Bears" })
        assertEquals(1, d.zoneNames(opponent, Zone.GRAVEYARD).count { it == "Grizzly Bears" })

        val immortal = d.putCardInHand(p, name)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, immortal).error)
        assertFalse(d.getPermanents(p).contains(immortal))
        d.resolveAll()

        assertTrue(d.getPermanents(p).contains(immortal))
        assertEquals(2, d.plusOneCounters(immortal))
        assertEquals(4, d.projected.getPower(immortal))
        assertEquals(4, d.projected.getToughness(immortal))
    }

    @Test
    fun entersWithoutPlusOnePlusOneCountersWhenNoCreatureDiedThisTurn() {
        val d = customScenario(SautekhImmortal)
        val p = d.player1
        val immortal = d.putCardInHand(p, name)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, immortal).error)
        d.resolveAll()

        assertTrue(d.getPermanents(p).contains(immortal))
        assertEquals(0, d.plusOneCounters(immortal))
        assertEquals(2, d.projected.getPower(immortal))
        assertEquals(2, d.projected.getToughness(immortal))
    }

    @Test
    fun aCreatureThatDiedOnThePreviousTurnDoesNotAddAnEnteringCounter() {
        val d = customScenario(SautekhImmortal, "Grizzly Bears", "Murder")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, murder, listOf(bear)).error)
        d.resolveAll()
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Grizzly Bears"))

        val immortal = d.putCardInHand(p, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.passPriority(d.player2)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, immortal).error)
        d.resolveAll()

        assertTrue(d.getPermanents(p).contains(immortal))
        assertEquals(0, d.plusOneCounters(immortal))
        assertEquals(2, d.projected.getPower(immortal))
        assertEquals(2, d.projected.getToughness(immortal))
    }
}
