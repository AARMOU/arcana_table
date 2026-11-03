package com.arcanatable.customcards

import com.arcanatable.customcards.cards.OpalPalace
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class OpalPalaceIndependentCertificationTest {
    private val commanderName = "Fixture Simic Commander"
    private val commander = CardDefinition.creature(
        name = commanderName,
        manaCost = ManaCost.parse("{G}{U}"),
        subtypes = setOf(Subtype("Merfolk")),
        power = 2,
        toughness = 2,
        supertypes = setOf(Supertype.LEGENDARY),
    ).copy(colorIdentityOverride = setOf(Color.GREEN, Color.BLUE))

    private fun commanderScenario(vararg libraryCards: String): GameTestDriver =
        customScenario(OpalPalace, *libraryCards).apply {
            registerCards(listOf(commander))
            initMultiplayer(
                decks = listOf(Deck.of("Forest" to 50, "Island" to 49), Deck.of("Forest" to 50, "Island" to 49)),
                format = Format.Commander(),
                commanders = listOf(commanderName, commanderName),
            )
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }

    private fun commandZoneCommander(d: GameTestDriver, player: EntityId): EntityId =
        d.state.getZone(player, Zone.COMMAND).single { d.getCardName(it) == commanderName }

    private fun plusOneCounters(d: GameTestDriver, creature: EntityId): Int =
        d.state.getEntity(creature)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    @Test
    fun tapAbilityAddsOneColorlessManaAndCannotBeActivatedWhileTapped() {
        val d = customScenario(OpalPalace)
        val p = d.player1
        val palace = d.putPermanentOnBattlefield(p, "Opal Palace")

        assertNull(d.activate(p, palace, index = 0).error)
        d.resolveAll()

        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.isTapped(palace))
        assertNotNull(d.activate(p, palace, index = 0).error)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun coloredManaRequiresOneGenericAndTapAndIsLimitedToCommandersColorIdentity() {
        val d = commanderScenario()
        val p = d.player1
        val palace = d.putPermanentOnBattlefield(p, "Opal Palace")

        assertNotNull(d.activate(p, palace, index = 1).error)
        assertFalse(d.isTapped(palace))
        assertEquals(0, d.manaPool(p).colorless)

        d.giveColorlessMana(p, 1)
        assertNull(d.activate(p, palace, index = 1).error)
        d.resolveAll(Choices(color = Color.BLUE))
        assertEquals(1, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.isTapped(palace))
        assertNotNull(d.activate(p, palace, index = 1).error)

        d.untapPermanent(palace)
        d.giveColorlessMana(p, 1)
        assertNull(d.activate(p, palace, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))
        assertEquals(1, d.manaPool(p).green)

        d.untapPermanent(palace)
        d.giveColorlessMana(p, 1)
        assertNull(d.activate(p, palace, index = 1).error)
        d.resolveAll(Choices(color = Color.RED))
        assertEquals(0, d.manaPool(p).red)
        assertEquals(3, d.manaPool(p).blue + d.manaPool(p).green)
    }

    @Test
    fun manaSpentToCastCommanderAddsCountersEqualToItsCommandZoneCastCount() {
        val d = commanderScenario("Murder")
        val p = d.player1
        val palace = d.putPermanentOnBattlefield(p, "Opal Palace")
        val commander = commandZoneCommander(d, p)

        d.giveColorlessMana(p, 1)
        assertNull(d.activate(p, palace, index = 1).error)
        d.resolveAll(Choices(color = Color.BLUE))
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, commander).error)
        d.resolveAll()
        assertEquals(1, plusOneCounters(d, commander))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val murder = d.putCardInHand(d.player2, "Murder")
        d.giveMana(d.player2, Color.BLACK, 2)
        d.giveColorlessMana(d.player2, 1)
        assertNull(d.castSpell(d.player2, murder, listOf(commander)).error)
        d.resolveAll(Choices(yes = true))
        assertTrue(d.zoneNames(p, Zone.COMMAND).contains(commanderName))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        d.giveColorlessMana(p, 3)
        assertNull(d.activate(p, palace, index = 1).error)
        d.resolveAll(Choices(color = Color.BLUE))
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, commander).error)
        d.resolveAll()
        assertEquals(2, plusOneCounters(d, commander))
    }

    @Test
    fun manaSpentOnAnotherSpellDoesNotGiveTheCommanderCounters() {
        val d = commanderScenario("Sol Ring")
        val p = d.player1
        val palace = d.putPermanentOnBattlefield(p, "Opal Palace")

        d.giveColorlessMana(p, 1)
        assertNull(d.activate(p, palace, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))
        val ring = d.putCardInHand(p, "Sol Ring")
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Sol Ring").isNotEmpty())

        val commander = commandZoneCommander(d, p)
        d.giveMana(p, Color.GREEN)
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, commander).error)
        d.resolveAll()
        assertEquals(0, plusOneCounters(d, commander))
    }

    @Test
    fun commanderCastWithoutSpendingOpalPalaceManaEntersWithoutAdditionalCounters() {
        val d = commanderScenario()
        val p = d.player1
        val commander = commandZoneCommander(d, p)
        d.giveMana(p, Color.GREEN)
        d.giveMana(p, Color.BLUE)

        assertNull(d.castSpell(p, commander).error)
        d.resolveAll()

        assertEquals(0, plusOneCounters(d, commander))
    }
}
