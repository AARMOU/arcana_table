package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GhostArk
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.unearthAbility
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Ghost Ark (40K #156): Flying; Repair Barge — whenever it becomes crewed, each artifact creature card
 * in your graveyard gains unearth {3} until end of turn; Crew 2. Rulings quoted in [GhostArk].
 */
class GhostArkScenarioTest {

    private val unearthId = unearthAbility(ManaCost.parse("{3}")).id

    private fun game() = customScenario(GhostArk, "Ornithopter", "Phyrexian Walker", "Memnite", "Grizzly Bears", "Sol Ring", "Shock")

    private fun GameTestDriver.hasUnearth(card: EntityId): Boolean =
        state.grantedActivatedAbilities.any { it.entityId == card && it.ability.id == unearthId }

    private fun GameTestDriver.unearthActions(player: EntityId, card: EntityId) =
        abilityActions(player, card).filter { (it.action as ActivateAbility).abilityId == unearthId }

    private fun GameTestDriver.crew(player: EntityId, ark: EntityId, crew: List<EntityId>) {
        assertNull(submit(CrewVehicle(player, ark, crew)).error)
        resolveAll()
    }

    private fun GameTestDriver.unearth(player: EntityId, card: EntityId) =
        submit(ActivateAbility(player, card, unearthId))

    @Test
    fun becomingCrewedGrantsUnearthToEachArtifactCreatureCardInYourGraveyardOnly() {
        val d = game()
        val p = d.player1
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val ornithopter = d.putCardInGraveyard(p, "Ornithopter")
        val walker = d.putCardInGraveyard(p, "Phyrexian Walker")
        val nonArtifactCreature = d.putCardInGraveyard(p, "Grizzly Bears")
        val noncreatureArtifact = d.putCardInGraveyard(p, "Sol Ring")
        val opponents = d.putCardInGraveyard(d.player2, "Ornithopter")

        assertFalse("nothing before it is crewed", d.hasUnearth(ornithopter))
        d.crew(p, ark, listOf(bears))

        assertTrue(d.hasUnearth(ornithopter))
        assertTrue(d.hasUnearth(walker))
        assertFalse("non-artifact creature card", d.hasUnearth(nonArtifactCreature))
        assertFalse("noncreature artifact card", d.hasUnearth(noncreatureArtifact))
        assertFalse("opponent's graveyard", d.hasUnearth(opponents))
        d.giveColorlessMana(p, 3)
        assertEquals(1, d.unearthActions(p, ornithopter).size)
        assertTrue(d.unearthActions(p, nonArtifactCreature).isEmpty())
        assertTrue(d.unearthActions(p, noncreatureArtifact).isEmpty())
        assertTrue(d.unearthActions(d.player2, opponents).isEmpty())
        assertNotNull("the opponent can't unearth their own card", d.unearth(d.player2, opponents).error)
    }

    @Test
    fun unearthReturnsTheCardWithHasteAndExilesItAtTheEndStep() {
        val d = game()
        val p = d.player1
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val walker = d.putCardInGraveyard(p, "Phyrexian Walker")
        d.crew(p, ark, listOf(bears))

        assertNotNull("unearth costs {3}", d.unearth(p, walker).error)
        d.giveColorlessMana(p, 3)
        assertNull(d.unearth(p, walker).error)
        d.resolveAll()

        assertTrue(walker in d.state.getBattlefield())
        assertTrue(d.projected.hasKeyword(walker, Keyword.HASTE))
        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Phyrexian Walker"))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Phyrexian Walker"))
    }

    @Test
    fun unearthIsSorcerySpeedEvenWhenTheArkIsCrewedOnAnOpponentsTurn() {
        val d = game()
        val p = d.player1
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val walker = d.putCardInGraveyard(p, "Phyrexian Walker")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.passPriority(d.player2)
        assertEquals(p, d.state.priorityPlayerId)

        d.crew(p, ark, listOf(bears))
        assertTrue(d.hasUnearth(walker))
        d.giveColorlessMana(p, 3)
        assertTrue(d.unearthActions(p, walker).isEmpty())
        assertNotNull(d.unearth(p, walker).error)
    }

    @Test
    fun aCardPutIntoTheGraveyardAfterResolutionDoesNotGainUnearthUntilTheArkIsCrewedAgain() {
        val d = game()
        val p = d.player1
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val bears2 = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val ornithopter = d.putCreatureOnBattlefield(p, "Ornithopter")
        d.putCardInGraveyard(p, "Phyrexian Walker")
        d.crew(p, ark, listOf(bears))

        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, shock, listOf(ornithopter)).error)
        d.resolveAll()
        assertTrue(ornithopter in d.state.getGraveyard(p))
        assertFalse("it reached the graveyard after Repair Barge resolved", d.hasUnearth(ornithopter))
        d.giveColorlessMana(p, 3)
        assertTrue(d.unearthActions(p, ornithopter).isEmpty())

        // Crewing again (it is already crewed) triggers again and now sees the Ornithopter.
        d.crew(p, ark, listOf(bears2))
        assertTrue(d.hasUnearth(ornithopter))
        assertEquals(1, d.unearthActions(p, ornithopter).size)
    }

    @Test
    fun crewingTwiceInATurnIsHarmless() {
        val d = game()
        val p = d.player1
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val bears2 = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val walker = d.putCardInGraveyard(p, "Phyrexian Walker")
        d.crew(p, ark, listOf(bears))
        d.crew(p, ark, listOf(bears2))

        d.giveColorlessMana(p, 6)
        assertEquals("one unearth, not two", 1, d.unearthActions(p, walker).size)
        assertNull(d.unearth(p, walker).error)
        d.resolveAll()
        assertTrue(walker in d.state.getBattlefield())
        assertFalse("the card left the graveyard: nothing is left to activate", d.hasUnearth(walker))
        assertTrue(d.unearthActions(p, walker).isEmpty())
    }

    @Test
    fun theUnearthGrantIsGoneAfterTheTurn() {
        val d = game()
        val p = d.player1
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val walker = d.putCardInGraveyard(p, "Phyrexian Walker")
        d.crew(p, ark, listOf(bears))
        assertTrue(d.hasUnearth(walker))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        assertFalse(d.hasUnearth(walker))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        d.giveColorlessMana(p, 3)
        assertTrue(walker in d.state.getGraveyard(p))
        assertTrue(d.unearthActions(p, walker).isEmpty())
        assertNotNull(d.unearth(p, walker).error)
    }

    @Test
    fun flyingAndCrewTwoNeedsTotalPowerTwo() {
        val d = game()
        val p = d.player1
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val memnite = d.putCreatureOnBattlefield(p, "Memnite")
        val memnite2 = d.putCreatureOnBattlefield(p, "Memnite")
        val ornithopter = d.putCreatureOnBattlefield(p, "Ornithopter")
        assertFalse(d.projected.isCreature(ark))
        assertTrue(d.projected.hasKeyword(ark, Keyword.FLYING))

        assertNotNull("total power 1", d.submit(CrewVehicle(p, ark, listOf(memnite, ornithopter))).error)
        assertFalse(d.projected.isCreature(ark))
        assertFalse(d.isTapped(memnite))

        d.crew(p, ark, listOf(memnite, memnite2))
        assertTrue(d.isTapped(memnite) && d.isTapped(memnite2))
        assertTrue(d.projected.isCreature(ark))
        assertTrue(d.projected.hasType(ark, "ARTIFACT"))
        assertEquals(3, d.projected.getPower(ark))
        assertEquals(3, d.projected.getToughness(ark))
        assertTrue(d.projected.hasKeyword(ark, Keyword.FLYING))
    }
}
