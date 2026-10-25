package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AzlaskTheSwellingScourge
import com.arcanatable.customcards.cards.SkitteringInvasion
import com.arcanatable.customcards.cards.SifterOfSkulls
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class AzlaskTheSwellingScourgeScenarioTest {
    private fun experience(d: GameTestDriver, p: com.wingedsheep.sdk.model.EntityId): Int =
        d.state.getEntity(p)?.get<CountersComponent>()?.getCount(CounterType.EXPERIENCE) ?: 0

    private fun giveWubrg(d: GameTestDriver, p: com.wingedsheep.sdk.model.EntityId) {
        listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN).forEach { d.giveMana(p, it) }
    }

    @Test
    fun onlyFriendlyColorlessDeathsGiveExperienceIncludingAzlaskItself() {
        val d = customScenario(AzlaskTheSwellingScourge, "Ulamog's Crusher", "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val q = d.player2
        val azlask = d.putCreatureOnBattlefield(p, "Azlask, the Swelling Scourge")
        val friendly = d.putCreatureOnBattlefield(p, "Ulamog's Crusher")
        val enemy = d.putCreatureOnBattlefield(q, "Ulamog's Crusher")
        val colored = d.putCreatureOnBattlefield(p, "Grizzly Bears")

        fun kill(victim: com.wingedsheep.sdk.model.EntityId) {
            val blade = d.putCardInHand(p, "Doom Blade")
            d.giveColorlessMana(p, 1)
            d.giveMana(p, Color.BLACK)
            assertNull(d.castSpell(p, blade, listOf(victim)).error)
            d.resolveAll()
        }

        kill(enemy)
        assertEquals("Opposing creature must not give us experience", 0, experience(d, p))
        kill(colored)
        assertEquals("A green creature we control is not colorless", 0, experience(d, p))
        kill(friendly)
        assertEquals(1, experience(d, p))
        kill(azlask)
        assertEquals("Azlask also triggers from its own death", 2, experience(d, p))
        assertEquals(0, experience(d, q))
    }

    @Test
    fun wubrgIsRequiredAndPumpUsesExperienceAtResolutionOnExistingFriendlyCreatures() {
        val d = customScenario(AzlaskTheSwellingScourge, "Ulamog's Crusher", "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val azlask = d.putCreatureOnBattlefield(p, "Azlask, the Swelling Scourge")
        val colorless = d.putCreatureOnBattlefield(p, "Ulamog's Crusher")
        val colored = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val victim = d.putCreatureOnBattlefield(p, "Ulamog's Crusher")

        val blade = d.putCardInHand(p, "Doom Blade")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, blade, listOf(victim)).error)
        d.resolveAll()
        assertEquals(1, experience(d, p))

        listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED).forEach { d.giveMana(p, it) }
        assertNotNull("A missing green pip cannot be substituted", d.activate(p, azlask).error)
        d.giveMana(p, Color.GREEN)
        assertNull(d.activate(p, azlask).error)
        d.resolveAll()

        assertEquals(3, d.projected.getPower(azlask))
        assertEquals(9, d.projected.getPower(colorless))
        assertEquals(3, d.projected.getPower(colored))
        assertEquals(2, d.projected.getPower(enemy))
        val late = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        assertEquals("The one-shot buff does not reach future creatures", 2, d.projected.getPower(late))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        assertEquals("The buff expires at end of turn", 2, d.projected.getPower(azlask))
        assertEquals(8, d.projected.getPower(colorless))
    }

    @Test
    fun scionsAndSpawnsGainIndestructibleAndAnnihilatorButUnrelatedCreaturesDoNot() {
        val d = customScenario(AzlaskTheSwellingScourge, SkitteringInvasion, SifterOfSkulls, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val q = d.player2
        val azlask = d.putCreatureOnBattlefield(p, "Azlask, the Swelling Scourge")
        d.putCreatureOnBattlefield(p, "Sifter of Skulls")
        val victim = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val blade = d.putCardInHand(p, "Doom Blade")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, blade, listOf(victim)).error)
        d.resolveAll()
        val scion = d.permanentsNamed(p, "Eldrazi Scion").single()

        val invasion = d.putCardInHand(p, "Skittering Invasion")
        d.giveColorlessMana(p, 7)
        assertNull(d.castSpell(p, invasion).error)
        d.resolveAll()
        val spawn = d.permanentsNamed(p, "Eldrazi Spawn").first()
        val unrelated = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opponent = d.putCreatureOnBattlefield(q, "Grizzly Bears")

        giveWubrg(d, p)
        assertNull(d.activate(p, azlask).error)
        d.resolveAll()
        listOf(scion, spawn).forEach {
            assertTrue("Scions and Spawns must gain indestructible", d.projected.hasKeyword(it, Keyword.INDESTRUCTIBLE))
            assertTrue("Scions and Spawns must gain annihilator", d.projected.hasKeyword(it, Keyword.ANNIHILATOR))
        }
        assertFalse(d.projected.hasKeyword(unrelated, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(unrelated, Keyword.ANNIHILATOR))
        assertFalse(d.projected.hasKeyword(opponent, Keyword.INDESTRUCTIBLE))

        d.removeSummoningSickness(spawn)
        d.putPermanentOnBattlefield(q, "Plains")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(spawn), q).error)
        d.resolveAll()
        assertTrue("The defender must sacrifice for granted annihilator 1", d.permanentsNamed(q, "Plains").isEmpty())
    }
}
