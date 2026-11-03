package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CagedSun
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.battlefield.chosenColor
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import org.junit.Assert.*
import org.junit.Test

/**
 * Caged Sun: choose a color as it enters; creatures you control of that color get +1/+1; whenever a
 * land's ability causes you to add one or more mana of that color, add one more of it (engine patch
 * 0038). Official ruling quoted in [CagedSun].
 */
class CagedSunScenarioTest {

    private fun game() = customScenario(
        CagedSun, "Island", "Forest", "Tundra", "Orzhov Basilica", "Gaea's Cradle", "Marble Diamond",
        "Glory Seeker", "Watchwolf", "Grizzly Bears",
    )

    /** A Caged Sun on [p]'s battlefield that chose [color] as it entered. */
    private fun GameTestDriver.sun(p: EntityId, color: Color): EntityId {
        val id = putPermanentOnBattlefield(p, "Caged Sun")
        replaceState(state.updateEntity(id) {
            it.with(CastChoicesComponent(chosen = mapOf(ChoiceSlot.COLOR to ChoiceValue.ColorChoice(color))))
        })
        return id
    }

    private fun GameTestDriver.pool(p: EntityId, color: Color) = manaPool(p).getAmount(color)

    /** Activates the mana ability of [land] that adds [color] (a dual has one per color). */
    private fun GameTestDriver.tapFor(p: EntityId, land: EntityId, color: Color) {
        val saved = state
        val before = pool(p, color)
        for (legal in abilityActions(p, land)) {
            assertNull(submit(legal.action as ActivateAbility).error)
            resolveAll()
            if (pool(p, color) > before) return
            replaceState(saved)
        }
        fail("no mana ability of ${getCardName(land)} adds $color")
    }

    private fun GameTestDriver.stats(id: EntityId) = projected.getPower(id) to projected.getToughness(id)

    // --- As it enters, choose a color -------------------------------------------------------------

    @Test fun castingItRecordsTheChosenColorAndTheAnthemFollowsIt() {
        val d = game()
        val p = d.player1
        val seeker = d.putCreatureOnBattlefield(p, "Glory Seeker")
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val card = d.putCardInHand(p, "Caged Sun")
        d.giveColorlessMana(p, 6)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll(Choices(color = Color.GREEN))
        val sun = d.permanentsNamed(p, "Caged Sun").single()
        assertEquals(Color.GREEN, d.state.getEntity(sun)!!.chosenColor())
        assertEquals("green creature +1/+1", 3 to 3, d.stats(bears))
        assertEquals("white creature untouched", 2 to 2, d.stats(seeker))
    }

    // --- Anthem -------------------------------------------------------------------------------------

    @Test fun anthemBuffsOnlyYourCreaturesOfTheChosenColorIncludingMulticolored() {
        val d = game()
        val p = d.player1
        d.sun(p, Color.WHITE)
        val seeker = d.putCreatureOnBattlefield(p, "Glory Seeker")
        val wolf = d.putCreatureOnBattlefield(p, "Watchwolf")
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val theirs = d.putCreatureOnBattlefield(d.player2, "Glory Seeker")
        assertEquals(3 to 3, d.stats(seeker))
        assertEquals("green-white Watchwolf has white", 4 to 4, d.stats(wolf))
        assertEquals("off-color", 2 to 2, d.stats(bears))
        assertEquals("opponent's white creature", 2 to 2, d.stats(theirs))
    }

    // --- Mana ---------------------------------------------------------------------------------------

    @Test fun aBasicOfTheChosenColorAddsTwo() {
        val d = game()
        val p = d.player1
        d.sun(p, Color.WHITE)
        d.tapFor(p, d.putLandOnBattlefield(p, "Plains"), Color.WHITE)
        assertEquals(2, d.pool(p, Color.WHITE))
    }

    @Test fun aLandOfAnotherColorAddsOne() {
        val d = game()
        val p = d.player1
        d.sun(p, Color.WHITE)
        d.tapFor(p, d.putLandOnBattlefield(p, "Island"), Color.BLUE)
        assertEquals(1, d.pool(p, Color.BLUE))
        assertEquals(0, d.pool(p, Color.WHITE))
    }

    @Test fun aDualTriggersOnlyWhenItProducesTheChosenColor() {
        val d = game()
        val p = d.player1
        d.sun(p, Color.WHITE)
        val tundra = d.putLandOnBattlefield(p, "Tundra")
        d.tapFor(p, tundra, Color.BLUE)
        assertEquals("tapped for {U}", 1, d.pool(p, Color.BLUE))
        assertEquals(0, d.pool(p, Color.WHITE))
        d.untapPermanent(tundra)
        d.tapFor(p, tundra, Color.WHITE)
        assertEquals("tapped for {W}", 2, d.pool(p, Color.WHITE))
        assertEquals(1, d.pool(p, Color.BLUE))
    }

    @Test fun aBounceLandAddingTheColorAmongOthersAddsOneMore() {
        val d = game()
        val p = d.player1
        d.sun(p, Color.WHITE)
        d.tapFor(p, d.putLandOnBattlefield(p, "Orzhov Basilica"), Color.WHITE)
        assertEquals(2, d.pool(p, Color.WHITE))
        assertEquals(1, d.pool(p, Color.BLACK))
    }

    @Test fun aLandAddingTwoOfTheColorStillAddsExactlyOneMore() {
        val d = game()
        val p = d.player1
        d.sun(p, Color.GREEN)
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.tapFor(p, d.putLandOnBattlefield(p, "Gaea's Cradle"), Color.GREEN)
        assertEquals("{G}{G} from two creatures plus one", 3, d.pool(p, Color.GREEN))
    }

    @Test fun aNonlandManaRockOfTheColorDoesNotTrigger() {
        val d = game()
        val p = d.player1
        d.sun(p, Color.WHITE)
        val diamond = d.putPermanentOnBattlefield(p, "Marble Diamond")
        d.untapPermanent(diamond)
        d.tapFor(p, diamond, Color.WHITE)
        assertEquals(1, d.pool(p, Color.WHITE))
    }

    @Test fun twoCagedSunsAddTwo() {
        val d = game()
        val p = d.player1
        d.sun(p, Color.WHITE)
        d.sun(p, Color.WHITE)
        d.tapFor(p, d.putLandOnBattlefield(p, "Plains"), Color.WHITE)
        assertEquals(3, d.pool(p, Color.WHITE))
    }

    @Test fun twoCagedSunsOfDifferentColorsEachWatchTheirOwn() {
        val d = game()
        val p = d.player1
        d.sun(p, Color.WHITE)
        d.sun(p, Color.BLUE)
        d.tapFor(p, d.putLandOnBattlefield(p, "Plains"), Color.WHITE)
        assertEquals(2, d.pool(p, Color.WHITE))
        assertEquals(0, d.pool(p, Color.BLUE))
    }

    @Test fun theOpponentsLandsDoNotTriggerIt() {
        val d = game()
        d.sun(d.player1, Color.WHITE)
        val theirs = d.putLandOnBattlefield(d.player2, "Plains")
        assertNull(d.passPriority(d.player1).error)
        assertEquals(d.player2, d.priorityPlayer)
        d.tapFor(d.player2, theirs, Color.WHITE)
        assertEquals(1, d.pool(d.player2, Color.WHITE))
        assertEquals(0, d.pool(d.player1, Color.WHITE))
    }

    @Test fun withoutCagedSunNoExtraMana() {
        val d = game()
        val p = d.player1
        d.tapFor(p, d.putLandOnBattlefield(p, "Plains"), Color.WHITE)
        assertEquals(1, d.pool(p, Color.WHITE))
    }

    // --- Auto-pay -----------------------------------------------------------------------------------

    @Test fun autoPayUsesTheExtraMana() {
        val d = game()
        val p = d.player1
        d.sun(p, Color.WHITE)
        val plains = d.putLandOnBattlefield(p, "Plains")
        val seeker = d.putCardInHand(p, "Glory Seeker")
        assertNull("one Plains pays {1}{W}", d.castSpell(p, seeker).error)
        assertTrue(d.isTapped(plains))
        d.resolveStack()
        assertTrue(d.permanentsNamed(p, "Glory Seeker").isNotEmpty())
    }

    @Test fun autoPayUsesADualsExtraManaForTheChosenColor() {
        val d = game()
        val p = d.player1
        d.sun(p, Color.WHITE)
        d.putLandOnBattlefield(p, "Tundra")
        val seeker = d.putCardInHand(p, "Glory Seeker")
        assertNull("one Tundra tapped for {W} pays {1}{W}", d.castSpell(p, seeker).error)
    }

    @Test fun autoPayGetsNoExtraManaFromAnotherColor() {
        val d = game()
        val p = d.player1
        d.sun(p, Color.BLUE)
        d.putLandOnBattlefield(p, "Plains")
        val seeker = d.putCardInHand(p, "Glory Seeker")
        assertNotNull("one Plains can't pay {1}{W} under a blue Caged Sun", d.castSpell(p, seeker).error)
    }
}
