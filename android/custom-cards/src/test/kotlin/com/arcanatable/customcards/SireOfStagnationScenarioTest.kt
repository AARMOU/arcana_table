package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SireOfStagnation
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class SireOfStagnationScenarioTest {
    @Test
    fun devoidIsColorlessButKeepsItsBlueBlackIdentityInEveryZone() {
        val exported = CardPool.custom.single { it.name == "Sire of Stagnation" }
        assertEquals(6, exported.cmc)
        assertEquals(4, exported.manaCost.genericAmount)
        assertEquals(mapOf(Color.BLUE to 1, Color.BLACK to 1), exported.manaCost.colorCount)
        assertEquals(5, exported.creatureStats!!.power)
        assertEquals(7, exported.creatureStats!!.toughness)
        assertEquals(setOf(Color.BLUE, Color.BLACK), exported.colorIdentity)
        assertTrue(exported.colors.isEmpty())
        assertTrue(exported.hasKeyword(Keyword.DEVOID))

        val d = customScenario(SireOfStagnation)
        val player = d.player1
        val copies = listOf(
            d.putCardOnTopOfLibrary(player, "Sire of Stagnation"),
            d.putCardInHand(player, "Sire of Stagnation"),
            d.putPermanentOnBattlefield(player, "Sire of Stagnation"),
            d.putCardInGraveyard(player, "Sire of Stagnation"),
            d.putCardInExile(player, "Sire of Stagnation"),
        )
        copies.forEach { id ->
            val card = d.state.getEntity(id)!!.get<CardComponent>()!!
            assertTrue("${d.getCardName(id)} is colorless in its zone", card.colors.isEmpty())
        }
    }

    @Test
    fun opponentLandEntryExilesOnlyAvailableCardsAndAlwaysDrawsTwo() {
        listOf(
            emptyList(),
            listOf("Island"),
            listOf("Island", "Swamp", "Opt"),
        ).forEach { library ->
            val d = customScenario(SireOfStagnation, "Opt")
            val controller = d.player1
            val opponent = d.player2
            d.putCreatureOnBattlefield(controller, "Sire of Stagnation")
            setLibrary(d, opponent, library)
            val land = d.putCardInHand(opponent, "Forest")
            val handSizeBeforeDraw = d.zoneNames(controller, Zone.HAND).size
            beginOpponentMain(d)

            assertNull(d.playLand(opponent, land).error)
            d.resolveAll()

            val expectedExiled = library.take(2)
            assertEquals(expectedExiled.size, d.zoneNames(opponent, Zone.EXILE).size)
            assertEquals(expectedExiled.toSet(), d.zoneNames(opponent, Zone.EXILE).toSet())
            assertEquals(handSizeBeforeDraw + 2, d.zoneNames(controller, Zone.HAND).size)
            assertEquals(opponent, d.activePlayer)
            assertExiledCardsArePublicAndOwnedBy(d, opponent)
        }
    }

    @Test
    fun enteringOpponentControlledLandsTriggerSeparatelyEvenWhenNotPlayedAndNotOwnedByThatOpponent() {
        val d = customScenario(
            SireOfStagnation,
            "Oblivion Sower",
            "Forest",
            "Island",
            "Opt",
            "Divination",
            "Sol Ring",
            "Grizzly Bears",
        )
        val controller = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(controller, "Sire of Stagnation")
        listOf("Forest", "Island", "Sol Ring", "Grizzly Bears").forEach {
            d.putCardOnTopOfLibrary(controller, it)
        }
        setLibrary(
            d,
            opponent,
            listOf("Opt", "Divination", "Sol Ring", "Grizzly Bears", "Opt", "Divination", "Sol Ring", "Grizzly Bears"),
        )
        beginOpponentMain(d)
        val sower = d.putCardInHand(opponent, "Oblivion Sower")
        d.giveColorlessMana(opponent, 6)
        val handSizeBeforeDraw = d.zoneNames(controller, Zone.HAND).size

        assertNull(d.castSpell(opponent, sower).error)
        d.resolveAll(Choices(targets = listOf(controller), pick = listOf("Forest", "Island")))

        val lands = d.permanentsNamed(opponent, "Forest") + d.permanentsNamed(opponent, "Island")
        assertEquals(2, lands.size)
        lands.forEach { id ->
            assertEquals(controller.value, d.state.getEntity(id)!!.get<OwnerComponent>()!!.playerId)
            assertEquals(opponent.value, d.state.getEntity(id)!!.get<ControllerComponent>()!!.playerId)
        }
        assertEquals(handSizeBeforeDraw + 4, d.zoneNames(controller, Zone.HAND).size)
        assertEquals(4, d.zoneNames(opponent, Zone.EXILE).size)
        assertEquals(
            listOf("Opt", "Divination", "Sol Ring", "Grizzly Bears").toSet(),
            d.zoneNames(opponent, Zone.EXILE).toSet(),
        )
        assertExiledCardsArePublicAndOwnedBy(d, opponent)
        assertEquals(opponent, d.activePlayer)
    }

    @Test
    fun ownLandAndOpponentNonlandDoNotTrigger() {
        val d = customScenario(SireOfStagnation, "Grizzly Bears", "Island", "Swamp")
        val controller = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(controller, "Sire of Stagnation")
        setLibrary(d, opponent, listOf("Island", "Swamp"))
        val ownLand = d.putCardInHand(controller, "Forest")
        val handSizeAfterPlayingLand = d.zoneNames(controller, Zone.HAND).size - 1

        assertNull(d.playLand(controller, ownLand).error)
        d.resolveAll()
        assertEquals(handSizeAfterPlayingLand, d.zoneNames(controller, Zone.HAND).size)
        assertTrue(d.zoneNames(opponent, Zone.EXILE).isEmpty())

        beginOpponentMain(d)
        val bears = d.putCardInHand(opponent, "Grizzly Bears")
        d.giveMana(opponent, Color.GREEN)
        d.giveColorlessMana(opponent, 1)
        assertNull(d.castSpell(opponent, bears).error)
        d.resolveAll()

        assertEquals(handSizeAfterPlayingLand, d.zoneNames(controller, Zone.HAND).size)
        assertTrue(d.zoneNames(opponent, Zone.EXILE).isEmpty())
        assertEquals(listOf("Island", "Swamp"), d.zoneNames(opponent, Zone.LIBRARY))
    }

    @Test
    fun cardInHandDoesNotTriggerAndAnEmptyControllerLibraryStillCausesTheDrawLoss() {
        val d = customScenario(SireOfStagnation, "Island", "Swamp")
        val controller = d.player1
        val opponent = d.player2
        d.putCardInHand(controller, "Sire of Stagnation")
        setLibrary(d, opponent, listOf("Island", "Swamp"))
        val land = d.putCardInHand(opponent, "Forest")
        val controllerHandSize = d.zoneNames(controller, Zone.HAND).size
        beginOpponentMain(d)
        assertNull(d.playLand(opponent, land).error)
        d.resolveAll()
        assertEquals(controllerHandSize, d.zoneNames(controller, Zone.HAND).size)
        assertTrue(d.zoneNames(opponent, Zone.EXILE).isEmpty())

        d.putCreatureOnBattlefield(controller, "Sire of Stagnation")
        d.passPriorityUntil(Step.END)
        beginOpponentMain(d)
        setLibrary(d, controller, emptyList())
        val nextLand = d.putCardInHand(opponent, "Forest")
        assertNull(d.playLand(opponent, nextLand).error)
        d.resolveAll()

        assertTrue("Drawing from an empty library follows the engine's loss rule", d.state.gameOver)
    }

    private fun beginOpponentMain(d: com.arcanatable.customcards.support.GameTestDriver) {
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, d.player2)
        assertEquals(d.player2, d.activePlayer)
    }

    private fun setLibrary(
        d: com.arcanatable.customcards.support.GameTestDriver,
        player: EntityId,
        cards: List<String>,
    ) {
        val library = ZoneKey(player, Zone.LIBRARY)
        d.replaceState(d.state.copy(zones = d.state.zones + (library to emptyList())))
        cards.asReversed().forEach { d.putCardOnTopOfLibrary(player, it) }
    }

    private fun assertExiledCardsArePublicAndOwnedBy(
        d: com.arcanatable.customcards.support.GameTestDriver,
        owner: EntityId,
    ) {
        d.state.getZone(owner, Zone.EXILE).forEach { id ->
            val entity = d.state.getEntity(id)!!
            assertEquals(owner.value, entity.get<OwnerComponent>()!!.playerId)
            assertFalse(entity.has<FaceDownComponent>())
        }
    }
}
