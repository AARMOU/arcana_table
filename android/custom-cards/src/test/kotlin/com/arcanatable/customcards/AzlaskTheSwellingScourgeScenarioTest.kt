package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AzlaskTheSwellingScourge
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Azlask, the Swelling Scourge on the real engine. Library cards: Eldrazi Devastator (colorless 8/9),
 * Sifter of Skulls (devoid, makes Eldrazi Scion tokens), Grizzly Bears (green), Doom Blade; the
 * Eldrazi Spawn token is always loaded.
 */
class AzlaskTheSwellingScourgeScenarioTest {
    private val pool = arrayOf("Eldrazi Devastator", "Sifter of Skulls", "Grizzly Bears", "Doom Blade")

    private fun experience(d: GameTestDriver, p: EntityId): Int =
        d.state.getEntity(p)?.get<CountersComponent>()?.getCount(CounterType.EXPERIENCE) ?: 0

    private fun giveWubrg(d: GameTestDriver, p: EntityId) {
        listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN).forEach { d.giveMana(p, it) }
    }

    /** [caster] casts Doom Blade on [victim]; the caller decides when to resolve. */
    private fun castBlade(d: GameTestDriver, caster: EntityId, victim: EntityId) {
        val blade = d.putCardInHand(caster, "Doom Blade")
        d.giveColorlessMana(caster, 1)
        d.giveMana(caster, Color.BLACK)
        assertNull(d.castSpell(caster, blade, listOf(victim)).error)
    }

    private fun kill(d: GameTestDriver, caster: EntityId, victim: EntityId) {
        castBlade(d, caster, victim)
        d.resolveAll()
    }

    @Test
    fun onlyFriendlyColorlessDeathsGiveExperienceIncludingAzlaskItself() {
        val d = customScenario(AzlaskTheSwellingScourge, *pool)
        val p = d.player1
        val q = d.player2
        val azlask = d.putCreatureOnBattlefield(p, "Azlask, the Swelling Scourge")
        val friendly = d.putCreatureOnBattlefield(p, "Eldrazi Devastator")
        val devoid = d.putCreatureOnBattlefield(p, "Sifter of Skulls")
        val enemy = d.putCreatureOnBattlefield(q, "Eldrazi Devastator")
        val colored = d.putCreatureOnBattlefield(p, "Grizzly Bears")

        kill(d, p, enemy)
        assertEquals("An opponent's colorless creature must not give us experience", 0, experience(d, p))
        assertEquals("Azlask's controller gets the counter, never the dead creature's owner", 0, experience(d, q))
        kill(d, p, colored)
        assertEquals("A green creature we control is not colorless", 0, experience(d, p))
        kill(d, p, friendly)
        assertEquals(1, experience(d, p))
        kill(d, p, devoid)
        assertEquals("A devoid creature is colorless", 2, experience(d, p))
        assertEquals("Experience counters go on the player, not on Azlask", 0,
            d.state.getEntity(azlask)?.get<CountersComponent>()?.getCount(CounterType.EXPERIENCE) ?: 0)
        kill(d, p, azlask)
        assertEquals("Azlask also triggers from its own death", 3, experience(d, p))
        assertEquals(0, experience(d, q))

        // The counters stay with the player after Azlask is gone; with no Azlask, deaths give nothing.
        val later = d.putCreatureOnBattlefield(p, "Eldrazi Devastator")
        kill(d, p, later)
        assertEquals("Without Azlask nothing triggers, but the counters remain", 3, experience(d, p))
    }

    @Test
    fun wubrgIsRequiredAndPumpUsesExperienceAtResolutionOnExistingFriendlyCreatures() {
        val d = customScenario(AzlaskTheSwellingScourge, *pool)
        val p = d.player1
        val azlask = d.putCreatureOnBattlefield(p, "Azlask, the Swelling Scourge")
        val colorless = d.putCreatureOnBattlefield(p, "Eldrazi Devastator")
        val colored = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val victim = d.putCreatureOnBattlefield(p, "Eldrazi Devastator")

        kill(d, p, victim)
        assertEquals(1, experience(d, p))

        listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED).forEach { d.giveMana(p, it) }
        assertNotNull("A missing green pip cannot be substituted", d.activate(p, azlask).error)
        d.giveMana(p, Color.GREEN)
        assertNull(d.activate(p, azlask).error)
        d.resolveAll()

        assertEquals(3, d.projected.getPower(azlask))
        assertEquals(3, d.projected.getToughness(azlask))
        assertEquals(9, d.projected.getPower(colorless))
        assertEquals(3, d.projected.getPower(colored))
        assertEquals("Opponents' creatures are not pumped", 2, d.projected.getPower(enemy))
        val late = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        assertEquals("The one-shot buff does not reach future creatures", 2, d.projected.getPower(late))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        assertEquals("The buff expires at end of turn", 2, d.projected.getPower(azlask))
        assertEquals(8, d.projected.getPower(colorless))
    }

    @Test
    fun xAndAffectedCreaturesAreFixedWhenTheAbilityResolves() {
        val d = customScenario(AzlaskTheSwellingScourge, *pool)
        val p = d.player1
        val azlask = d.putCreatureOnBattlefield(p, "Azlask, the Swelling Scourge")
        val victim = d.putCreatureOnBattlefield(p, "Eldrazi Devastator")
        val second = d.putCreatureOnBattlefield(p, "Sifter of Skulls")
        assertEquals(0, experience(d, p))

        giveWubrg(d, p)
        assertNull(d.activate(p, azlask).error)
        // In response, a colorless creature dies: X counts it because X is read on resolution.
        castBlade(d, p, victim)
        repeat(20) {
            when {
                d.state.pendingDecision != null -> d.decide()
                d.state.stack.size > 1 -> d.bothPass()
            }
        }
        assertEquals("Only Azlask's ability is left on the stack", 1, d.state.stack.size)
        assertEquals(1, experience(d, p))
        // A creature that arrives before resolution is a creature you control at resolution.
        val arrived = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.resolveAll()

        assertEquals(3, d.projected.getPower(azlask))
        assertEquals(3, d.projected.getPower(arrived))
        assertEquals(5, d.projected.getPower(second))

        // A later counter does not change an X that was already locked in.
        kill(d, p, second)
        assertEquals(2, experience(d, p))
        assertEquals("X is not recomputed after resolution", 3, d.projected.getPower(azlask))
        assertEquals(3, d.projected.getPower(arrived))
    }

    @Test
    fun scionsAndSpawnsGainIndestructibleAndAnnihilatorButUnrelatedCreaturesDoNot() {
        val d = customScenario(AzlaskTheSwellingScourge, *pool)
        val p = d.player1
        val q = d.player2
        val azlask = d.putCreatureOnBattlefield(p, "Azlask, the Swelling Scourge")
        d.putCreatureOnBattlefield(p, "Sifter of Skulls")
        val victim = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        kill(d, p, victim)
        val scion = d.permanentsNamed(p, "Eldrazi Scion").single()

        val spawn = d.putToken(p, "Eldrazi Spawn")
        val unrelated = d.putCreatureOnBattlefield(p, "Eldrazi Devastator")
        val opponent = d.putCreatureOnBattlefield(q, "Grizzly Bears")
        val opposingSpawn = d.putToken(q, "Eldrazi Spawn")

        giveWubrg(d, p)
        assertNull(d.activate(p, azlask).error)
        d.resolveAll()
        listOf(scion, spawn).forEach {
            assertTrue("Scions and Spawns must gain indestructible", d.projected.hasKeyword(it, Keyword.INDESTRUCTIBLE))
            assertTrue("Scions and Spawns must gain annihilator", d.projected.hasKeyword(it, Keyword.ANNIHILATOR))
        }
        assertFalse("A non-Scion Eldrazi gets neither", d.projected.hasKeyword(unrelated, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(unrelated, Keyword.ANNIHILATOR))
        assertFalse(d.projected.hasKeyword(azlask, Keyword.ANNIHILATOR))
        assertFalse(d.projected.hasKeyword(opponent, Keyword.INDESTRUCTIBLE))
        assertFalse("An opponent's Spawn is not ours", d.projected.hasKeyword(opposingSpawn, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(opposingSpawn, Keyword.ANNIHILATOR))

        // Indestructible is real: Doom Blade does not destroy the Spawn.
        kill(d, p, spawn)
        assertTrue("The indestructible Spawn survives destroy", spawn in d.permanentsNamed(p, "Eldrazi Spawn"))

        d.removeSummoningSickness(spawn)
        d.putPermanentOnBattlefield(q, "Plains")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(spawn), q).error)
        d.bothPass()
        val decision = d.state.pendingDecision as? com.wingedsheep.engine.core.SelectCardsDecision
        assertNotNull("Granted annihilator 1 must offer a real permanent-sacrifice choice", decision)
        assertEquals("The defending player chooses what to sacrifice", q, decision!!.playerId)
        assertEquals("Annihilator 1 sacrifices exactly one permanent", 1, decision.maxSelections)
        d.resolveAll(Choices(pick = listOf("Plains")))
        assertTrue("The defender's chosen Plains must be sacrificed", d.permanentsNamed(q, "Plains").isEmpty())
        assertEquals("Annihilator must not also sacrifice the unchosen creature", 1, d.permanentsNamed(q, "Grizzly Bears").size)
        assertEquals(1, d.permanentsNamed(q, "Eldrazi Spawn").size)
    }

    @Test
    fun grantedAnnihilatorAndIndestructibleEndAtEndOfTurn() {
        val d = customScenario(AzlaskTheSwellingScourge, *pool)
        val p = d.player1
        val azlask = d.putCreatureOnBattlefield(p, "Azlask, the Swelling Scourge")
        val spawn = d.putToken(p, "Eldrazi Spawn")
        giveWubrg(d, p)
        assertNull(d.activate(p, azlask).error)
        d.resolveAll()
        assertTrue(d.projected.hasKeyword(spawn, Keyword.INDESTRUCTIBLE))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        assertFalse(d.projected.hasKeyword(spawn, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(spawn, Keyword.ANNIHILATOR))

        // Back on our turn, the Spawn attacking must not trigger an expired annihilator.
        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.removeSummoningSickness(spawn)
        d.putPermanentOnBattlefield(d.player2, "Plains")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(spawn), d.player2).error)
        d.bothPass()
        assertNull("No annihilator trigger after the grant expired",
            d.state.pendingDecision as? com.wingedsheep.engine.core.SelectCardsDecision)
        assertEquals(1, d.permanentsNamed(d.player2, "Plains").size)
    }
}
