package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GoblinWelder
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

/**
 * Goblin Welder — "{T}: Choose target artifact a player controls and target artifact card in that
 * player's graveyard. If both targets are still legal as this ability resolves, that player
 * simultaneously sacrifices the artifact and returns the artifact card to the battlefield."
 * On engine patch 0044 (dependent target "owned by the first target's controller").
 */
class GoblinWelderScenarioTest {

    private fun scenario() = customScenario(
        GoblinWelder, "Sol Ring", "Mind Stone", "Thran Dynamo", "Tormod's Crypt", "Darksteel Colossus",
    )

    private fun GameTestDriver.readyWelder(player: EntityId) =
        putCreatureOnBattlefield(player, "Goblin Welder").also { removeSummoningSickness(it) }

    private fun GameTestDriver.weldOffer(player: EntityId, welder: EntityId) =
        abilityActions(player, welder).firstOrNull { it.requiresTargets }

    private fun GameTestDriver.weld(player: EntityId, welder: EntityId, onField: EntityId, inYard: EntityId) =
        submit(
            ActivateAbility(
                player, welder, (weldOffer(player, welder)!!.action as ActivateAbility).abilityId,
                targets = listOf(
                    ChosenTarget.Permanent(onField),
                    ChosenTarget.Card(inYard, ownerOf(inYard), Zone.GRAVEYARD),
                ),
            )
        )

    private fun GameTestDriver.ownerOf(card: EntityId): EntityId =
        state.getEntity(card)!!.get<com.wingedsheep.engine.state.components.identity.CardComponent>()!!.ownerId!!

    @Test
    fun weldsYourOwnArtifacts() {
        val d = scenario()
        val me = d.player1
        val welder = d.readyWelder(me)
        val ring = d.putPermanentOnBattlefield(me, "Sol Ring")
        val dynamo = d.putCardInGraveyard(me, "Thran Dynamo")
        assertNull(d.weld(me, welder, ring, dynamo).error)
        d.resolveAll()
        assertTrue("Sol Ring was sacrificed", d.zoneNames(me, Zone.GRAVEYARD).contains("Sol Ring"))
        assertEquals("Thran Dynamo returned under your control", 1, d.permanentsNamed(me, "Thran Dynamo").size)
        assertTrue(d.isTapped(welder))
    }

    @Test
    fun weldingAnOpponentsArtifactsIsDoneByThatPlayer() {
        val d = scenario()
        val me = d.player1; val opp = d.player2
        val welder = d.readyWelder(me)
        val stone = d.putPermanentOnBattlefield(opp, "Mind Stone")
        val crypt = d.putCardInGraveyard(opp, "Tormod's Crypt")
        assertNull(d.weld(me, welder, stone, crypt).error)
        d.resolveAll()
        assertTrue(d.zoneNames(opp, Zone.GRAVEYARD).contains("Mind Stone"))
        assertEquals("returns under that player's control", 1, d.permanentsNamed(opp, "Tormod's Crypt").size)
        assertTrue(d.permanentsNamed(me, "Tormod's Crypt").isEmpty())
    }

    @Test
    fun graveyardChoicesFollowTheChosenArtifactsController() {
        val d = scenario()
        val me = d.player1; val opp = d.player2
        val welder = d.readyWelder(me)
        val ring = d.putPermanentOnBattlefield(me, "Sol Ring")
        val stone = d.putPermanentOnBattlefield(opp, "Mind Stone")
        val dynamo = d.putCardInGraveyard(me, "Thran Dynamo")
        val crypt = d.putCardInGraveyard(opp, "Tormod's Crypt")

        val offer = d.weldOffer(me, welder)
        assertNotNull("offered", offer)
        val slots = offer!!.targetRequirements!!
        assertEquals(setOf(ring, stone), slots[0].validTargets.toSet())
        val byPrefix = slots[1].validTargetsByPrefix!!
        assertEquals("your artifact → your graveyard only", listOf(dynamo), byPrefix[ring.toString()])
        assertEquals("their artifact → their graveyard only", listOf(crypt), byPrefix[stone.toString()])

        assertNotNull("a mismatched pair is rejected", d.weld(me, welder, ring, crypt).error)
        assertTrue(d.permanentsNamed(me, "Sol Ring").isNotEmpty())
    }

    @Test
    fun notOfferedWithoutAMatchingPair() {
        val d = scenario()
        val me = d.player1; val opp = d.player2
        val welder = d.readyWelder(me)
        d.putPermanentOnBattlefield(me, "Sol Ring")
        d.putCardInGraveyard(opp, "Tormod's Crypt") // only the *other* player's graveyard has one
        assertNull("no artifact whose controller has an artifact card in their graveyard", d.weldOffer(me, welder))
    }

    @Test
    fun graveyardCardGoneInResponseMeansNothingHappens() {
        val d = scenario()
        val me = d.player1
        val welder = d.readyWelder(me)
        val ring = d.putPermanentOnBattlefield(me, "Sol Ring")
        val dynamo = d.putCardInGraveyard(me, "Thran Dynamo")
        assertNull(d.weld(me, welder, ring, dynamo).error)
        d.moveToExile(dynamo) // e.g. graveyard hate in response
        d.resolveAll()
        assertEquals("ruling 2008-08-01: nothing is sacrificed", 1, d.permanentsNamed(me, "Sol Ring").size)
    }

    @Test
    fun artifactGoneInResponseMeansNothingReturns() {
        val d = scenario()
        val me = d.player1
        val welder = d.readyWelder(me)
        val ring = d.putPermanentOnBattlefield(me, "Sol Ring")
        val dynamo = d.putCardInGraveyard(me, "Thran Dynamo")
        assertNull(d.weld(me, welder, ring, dynamo).error)
        d.moveToGraveyard(ring) // e.g. Shatter in response
        d.resolveAll()
        assertTrue("ruling 2008-08-01: nothing returns", d.permanentsNamed(me, "Thran Dynamo").isEmpty())
        assertTrue(d.zoneNames(me, Zone.GRAVEYARD).contains("Thran Dynamo"))
    }

    @Test
    fun aSacrificeReplacementStillLetsTheCardReturn() {
        // Darksteel Colossus is shuffled into its owner's library instead of going to the graveyard;
        // the sacrifice still happened, and the other half of the exchange still happens.
        val d = scenario()
        val me = d.player1
        val welder = d.readyWelder(me)
        val colossus = d.putPermanentOnBattlefield(me, "Darksteel Colossus")
        val dynamo = d.putCardInGraveyard(me, "Thran Dynamo")
        assertNull(d.weld(me, welder, colossus, dynamo).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(me, "Darksteel Colossus").isEmpty())
        assertTrue(d.zoneNames(me, Zone.LIBRARY).contains("Darksteel Colossus"))
        assertEquals(1, d.permanentsNamed(me, "Thran Dynamo").size)
    }

    @Test
    fun theSameCardCanNotBeBothHalves() {
        // The artifact card must be in the graveyard as the ability is activated; the sacrificed
        // artifact can't be returned by the same activation.
        val d = scenario()
        val me = d.player1
        val welder = d.readyWelder(me)
        val ring = d.putPermanentOnBattlefield(me, "Sol Ring")
        d.putCardInGraveyard(me, "Thran Dynamo")
        val slots = d.weldOffer(me, welder)!!.targetRequirements!!
        assertFalse(ring in slots[1].validTargetsByPrefix!![ring.toString()].orEmpty())
    }

    @Test
    fun fourPlayerTargetsAThirdPlayersArtifactsAndOnlyTheirGraveyard() {
        val d = CardPool.driver("Sol Ring", "Mind Stone", "Thran Dynamo", "Tormod's Crypt").apply {
            registerCards(listOf(CardLoader.fromJsonPreservingIds(exportLine(GoblinWelder))))
        }
        val seats = d.initMultiplayer(List(4) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val (me, a, b, c) = seats
        val welder = d.readyWelder(me)
        val stoneB = d.putPermanentOnBattlefield(b, "Mind Stone")
        val cryptB = d.putCardInGraveyard(b, "Tormod's Crypt")
        val dynamoC = d.putCardInGraveyard(c, "Thran Dynamo")
        d.putCardInGraveyard(a, "Sol Ring")

        val byPrefix = d.weldOffer(me, welder)!!.targetRequirements!![1].validTargetsByPrefix!!
        assertEquals(listOf(cryptB), byPrefix[stoneB.toString()])
        assertNotNull("another seat's graveyard card is not a legal second target", d.weld(me, welder, stoneB, dynamoC).error)

        assertNull(d.weld(me, welder, stoneB, cryptB).error)
        d.resolveAll()
        assertTrue(d.zoneNames(b, Zone.GRAVEYARD).contains("Mind Stone"))
        assertEquals("under seat B's control", 1, d.permanentsNamed(b, "Tormod's Crypt").size)
        for (other in listOf(me, a, c)) assertTrue(d.permanentsNamed(other, "Tormod's Crypt").isEmpty())
    }
}
