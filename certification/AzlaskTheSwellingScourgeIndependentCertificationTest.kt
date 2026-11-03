package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AzlaskTheSwellingScourge
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class AzlaskTheSwellingScourgeIndependentCertificationTest {
    private val name = "Azlask, the Swelling Scourge"

    private fun GameTestDriver.giveWubrg(player: EntityId) {
        giveMana(player, Color.WHITE)
        giveMana(player, Color.BLUE)
        giveMana(player, Color.BLACK)
        giveMana(player, Color.RED)
        giveMana(player, Color.GREEN)
    }

    private fun GameTestDriver.destroyWithMurder(
        player: EntityId,
        creature: EntityId,
    ) {
        val murder = putCardInHand(player, "Murder")
        giveMana(player, Color.BLACK, 2)
        giveColorlessMana(player, 1)
        assertNull(castSpell(player, murder, listOf(creature)).error)
        resolveAll()
    }

    @Test
    fun azlaskDyingGivesAnExperienceCounter() {
        val d = customScenario(AzlaskTheSwellingScourge, "Murder", "Reanimate", "Grizzly Bears")
        val player = d.player1
        val azlask = d.putCreatureOnBattlefield(player, name)
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.destroyWithMurder(player, azlask)

        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains(name))
        val reanimate = d.putCardInHand(player, "Reanimate")
        d.giveMana(player, Color.BLACK)
        assertNull(
            d.castSpellWithTargets(
                player,
                reanimate,
                listOf(ChosenTarget.Card(azlask, player, Zone.GRAVEYARD)),
            ).error,
        )
        d.resolveAll()
        assertTrue(d.permanentsNamed(player, name).contains(azlask))

        d.giveWubrg(player)
        assertNull(d.activate(player, azlask).error)
        d.resolveAll()

        assertEquals(3, d.projected.getPower(bear))
        assertEquals(3, d.projected.getToughness(bear))
    }

    @Test
    fun anotherColorlessCreatureYouControlDyingBuffsAllYourCreaturesByOne() {
        val d = customScenario(AzlaskTheSwellingScourge, "Murder", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val azlask = d.putCreatureOnBattlefield(player, name)
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val opposingBear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spawn = d.putToken(player, "Eldrazi Spawn")
        d.destroyWithMurder(player, spawn)

        d.giveWubrg(player)
        assertNull(d.activate(player, azlask).error)
        d.resolveAll()

        for (creature in listOf(azlask, bear)) {
            assertEquals(3, d.projected.getPower(creature))
            assertEquals(3, d.projected.getToughness(creature))
        }
        assertEquals(2, d.projected.getPower(opposingBear))
        assertEquals(2, d.projected.getToughness(opposingBear))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertEquals(2, d.projected.getPower(azlask))
        assertEquals(2, d.projected.getToughness(bear))
    }

    @Test
    fun aColoredCreatureYouControlDyingDoesNotGiveAnExperienceCounter() {
        val d = customScenario(AzlaskTheSwellingScourge, "Murder", "Grizzly Bears", "Llanowar Elves")
        val player = d.player1
        val azlask = d.putCreatureOnBattlefield(player, name)
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val elf = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        d.destroyWithMurder(player, bear)

        d.giveWubrg(player)
        assertNull(d.activate(player, azlask).error)
        d.resolveAll()

        assertEquals(2, d.projected.getPower(azlask))
        assertEquals(1, d.projected.getPower(elf))
        assertEquals(1, d.projected.getToughness(elf))
    }

    @Test
    fun anOpponentsColorlessCreatureDyingDoesNotGiveYouAnExperienceCounter() {
        val d = customScenario(AzlaskTheSwellingScourge, "Murder", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val azlask = d.putCreatureOnBattlefield(player, name)
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val spawn = d.putToken(opponent, "Eldrazi Spawn")
        d.destroyWithMurder(player, spawn)

        d.giveWubrg(player)
        assertNull(d.activate(player, azlask).error)
        d.resolveAll()

        assertEquals(2, d.projected.getPower(azlask))
        assertEquals(2, d.projected.getPower(bear))
    }

    @Test
    fun theActivationRequiresAllFiveColoredManaSymbols() {
        val d = customScenario(AzlaskTheSwellingScourge)
        val player = d.player1
        val azlask = d.putCreatureOnBattlefield(player, name)
        d.giveColorlessMana(player, 5)

        assertNotNull(d.activate(player, azlask).error)

        assertEquals(5, d.manaPool(player).colorless)
        assertEquals(2, d.projected.getPower(azlask))
        assertEquals(2, d.projected.getToughness(azlask))
    }

    @Test
    fun scionsAndSpawnsGainIndestructibleAndAnnihilatorOneUntilEndOfTurn() {
        val d = customScenario(AzlaskTheSwellingScourge, "Scion Summoner", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val summoner = d.putCardInHand(player, "Scion Summoner")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.GREEN)
        assertNull(d.castSpell(player, summoner).error)
        d.resolveAll()
        val scion = d.permanentsNamed(player, "Eldrazi Scion").single()

        val azlask = d.putCreatureOnBattlefield(player, name)
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val spawn = d.putToken(player, "Eldrazi Spawn")
        val opposingSpawn = d.putToken(opponent, "Eldrazi Spawn")
        repeat(3) { d.putPermanentOnBattlefield(opponent, "Plains") }
        d.giveWubrg(player)

        assertNull(d.activate(player, azlask).error)
        d.resolveAll()

        assertTrue(d.projected.hasKeyword(scion, Keyword.INDESTRUCTIBLE))
        assertTrue(d.projected.hasKeyword(spawn, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(bear, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(opposingSpawn, Keyword.INDESTRUCTIBLE))

        d.removeSummoningSickness(scion)
        d.removeSummoningSickness(spawn)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(scion, spawn), opponent).error)
        d.resolveAll()
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals("Each of the two attackers has annihilator 1", 2, d.getPermanents(opponent).size)
        assertTrue(d.getPermanents(player).containsAll(listOf(scion, spawn)))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertFalse(d.projected.hasKeyword(scion, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(spawn, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun theAbilityCanBeActivatedDuringAnOpponentsTurn() {
        val d = customScenario(AzlaskTheSwellingScourge)
        val player = d.player1
        val opponent = d.player2
        val azlask = d.putCreatureOnBattlefield(player, name)
        val spawn = d.putToken(player, "Eldrazi Spawn")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        d.giveWubrg(player)
        assertNull(d.passPriority(opponent).error)

        assertNull(d.activate(player, azlask).error)
        d.resolveAll()

        assertTrue(d.projected.hasKeyword(spawn, Keyword.INDESTRUCTIBLE))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = player)
        assertFalse(d.projected.hasKeyword(spawn, Keyword.INDESTRUCTIBLE))
    }
}
