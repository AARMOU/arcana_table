package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TheWarInHeaven
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.SagaComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * The War in Heaven (40K #69), Saga. I — draw three, lose 3 life. II — mill three. III — up to three target
 * creature cards with total mana value 8 or less in your graveyard return with a necrodermis counter, and
 * they're artifacts in addition to their other types. Ruling (2022-10-07) quoted in [TheWarInHeaven].
 * CR 714 (Sagas), CR 601.2c (total-mana-value target cap), CR 608.2b (an illegal target is not affected).
 *
 * Library cards: Hill Giant {3}{R}, Bottle Gnomes {3}, Grizzly Bears {1}{G}, Llanowar Elves {G}, Shock.
 */
class TheWarInHeavenScenarioTest {

    private val saga = "The War in Heaven"
    private val necrodermis = CounterType.of("necrodermis")

    private fun game() =
        customScenario(TheWarInHeaven, "Hill Giant", "Bottle Gnomes", "Grizzly Bears", "Llanowar Elves", "Shock")

    private fun GameTestDriver.nextOwnPrecombatMain(p: EntityId) {
        passPriorityUntil(Step.UPKEEP, activePlayer = getOpponent(p))
        passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
    }

    /** The Saga on [p]'s battlefield with chapters I and II done; their next main phase adds lore 3. */
    private fun GameTestDriver.readyForChapterThree(p: EntityId): ChooseTargetsDecision {
        val id = putPermanentOnBattlefield(p, saga)
        replaceState(state.updateEntity(id) {
            it.with(CountersComponent().withAdded(CounterType.LORE, 2)).with(SagaComponent(triggeredChapters = setOf(1, 2)))
        })
        nextOwnPrecombatMain(p)
        val decision = state.pendingDecision
        assertTrue("expected chapter III's targets, got $decision", decision is ChooseTargetsDecision)
        return decision as ChooseTargetsDecision
    }

    private fun GameTestDriver.choose(p: EntityId, decision: ChooseTargetsDecision, targets: List<EntityId>) =
        submitDecision(p, TargetsResponse(decision.id, mapOf(0 to targets)))

    private fun GameTestDriver.necrodermisOn(id: EntityId) =
        state.getEntity(id)?.get<CountersComponent>()?.getCount(necrodermis) ?: 0

    private fun GameTestDriver.assertReturnedAsArtifact(p: EntityId, id: EntityId) {
        assertTrue("${getCardName(id)} is on the battlefield", id in getPermanents(p))
        assertEquals("one necrodermis counter", 1, necrodermisOn(id))
        assertTrue("an artifact", projected.hasType(id, "ARTIFACT"))
        assertTrue("still a creature", projected.isCreature(id))
    }

    @Test
    fun chapterOneDrawsThreeAndLosesThreeThenChapterTwoMillsThree() {
        val d = game()
        val p = d.player1
        val card = d.putCardInHand(p, saga)
        d.giveMana(p, Color.BLACK, 3)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, card).error)
        d.bothPass() // the Saga resolves; chapter I triggers
        val handBefore = d.getHandSize(p)
        val libraryBefore = d.state.getZone(p, Zone.LIBRARY).size
        d.resolveAll()
        assertEquals(handBefore + 3, d.getHandSize(p))
        assertEquals(libraryBefore - 3, d.state.getZone(p, Zone.LIBRARY).size)
        assertEquals(17, d.getLifeTotal(p))
        assertEquals("the opponent is untouched", 20, d.getLifeTotal(d.player2))

        d.nextOwnPrecombatMain(p) // lore 2: chapter II is on the stack
        val library = d.state.getZone(p, Zone.LIBRARY).size
        val graveyard = d.getGraveyard(p).size
        val hand = d.getHandSize(p)
        d.resolveAll()
        assertEquals("milled three", library - 3, d.state.getZone(p, Zone.LIBRARY).size)
        assertEquals(graveyard + 3, d.getGraveyard(p).size)
        assertEquals("chapter II draws nothing", hand, d.getHandSize(p))
        assertEquals("chapter II loses no life", 17, d.getLifeTotal(p))
        assertNotNull("the Saga stays until III", d.findPermanent(p, saga))
    }

    @Test
    fun chapterThreeReturnsUpToEightTotalWithACounterAsArtifactsAndThenTheSagaIsSacrificed() {
        val d = game()
        val p = d.player1
        val giant = d.putCardInGraveyard(p, "Hill Giant")
        val giant2 = d.putCardInGraveyard(p, "Hill Giant")
        val gnomes = d.putCardInGraveyard(p, "Bottle Gnomes")
        val bears = d.putCardInGraveyard(p, "Grizzly Bears")
        val shock = d.putCardInGraveyard(p, "Shock")
        val theirs = d.putCardInGraveyard(d.player2, "Grizzly Bears")

        val decision = d.readyForChapterThree(p)
        val legal = decision.legalTargets[0].orEmpty()
        assertTrue(legal.containsAll(listOf(giant, giant2, gnomes, bears)))
        assertFalse("noncreature card", shock in legal)
        assertFalse("opponent's graveyard", theirs in legal)

        assertNotNull("4 + 3 + 2 = 9 is over the cap", d.choose(p, decision, listOf(giant, gnomes, bears)).error)
        assertNull("4 + 4 = 8 is allowed", d.choose(p, decision, listOf(giant, giant2)).error)
        d.resolveAll()

        d.assertReturnedAsArtifact(p, giant)
        d.assertReturnedAsArtifact(p, giant2)
        assertTrue("untargeted cards stay", gnomes in d.getGraveyard(p) && bears in d.getGraveyard(p))
        assertNull("sacrificed after III", d.findPermanent(p, saga))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(saga))

        // The type comes from the resolved ability, not the Saga: it lasts after the Saga is gone.
        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        assertTrue(d.projected.hasType(giant, "ARTIFACT") && d.projected.hasType(giant2, "ARTIFACT"))
        assertEquals(1, d.necrodermisOn(giant))
    }

    @Test
    fun zeroTargetsIsFine() {
        val d = game()
        val p = d.player1
        val bears = d.putCardInGraveyard(p, "Grizzly Bears")

        val decision = d.readyForChapterThree(p)
        assertNull(d.choose(p, decision, emptyList()).error)
        d.resolveAll()
        assertTrue(bears in d.getGraveyard(p))
        assertNull("still sacrificed after III", d.findPermanent(p, saga))
    }

    @Test
    fun aTargetThatLeftTheGraveyardIsSkippedAndTheOthersStillReturn() {
        val d = game()
        val p = d.player1
        val giant = d.putCardInGraveyard(p, "Hill Giant")
        val bears = d.putCardInGraveyard(p, "Grizzly Bears")
        val elves = d.putCardInGraveyard(p, "Llanowar Elves")

        val decision = d.readyForChapterThree(p)
        assertNull("4 + 2 + 1 = 7, three targets", d.choose(p, decision, listOf(giant, bears, elves)).error)
        d.moveToExile(giant) // leaves the graveyard before chapter III resolves
        d.resolveAll()

        assertTrue("the illegal target stays in exile", giant in d.getExile(p))
        assertFalse(giant in d.getPermanents(p))
        d.assertReturnedAsArtifact(p, bears)
        d.assertReturnedAsArtifact(p, elves)
        assertNull(d.findPermanent(p, saga))
    }
}
