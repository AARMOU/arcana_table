package com.arcanatable.customcards

// Trusted-main, real-Argentum acceptance scenarios for And They Shall Know No Fear.
// Written from the printed Oracle, not the implementation:
// "{1}{W} Instant. Choose a creature type. Creatures you control of the chosen type get +1/+0 and
// gain indestructible until end of turn."
import com.arcanatable.customcards.cards.AndTheyShallKnowNoFear
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class AndTheyShallKnowNoFearIndependentCertificationTest {
    private val spell = "And They Shall Know No Fear"

    private fun GameTestDriver.payAndCast(player: EntityId): EntityId {
        val card = putCardInHand(player, spell)
        giveMana(player, Color.WHITE)
        giveColorlessMana(player, 1)
        assertNull(castSpell(player, card).error)
        return card
    }

    /** Resolves the spell on top of the stack, answering its creature-type choice with [type]. */
    private fun GameTestDriver.resolveChoosing(player: EntityId, type: String) {
        var passes = 0
        while (state.pendingDecision !is ChooseOptionDecision) {
            assertTrue("creature-type choice never appeared", passes++ < 10 && state.stack.isNotEmpty())
            bothPass()
        }
        val choice = state.pendingDecision as ChooseOptionDecision
        assertEquals("the spell's controller chooses", player, choice.playerId)
        val index = choice.options.indexOfFirst { it.equals(type, ignoreCase = true) }
        assertTrue("$type is a choosable creature type: ${choice.options}", index >= 0)
        submitDecision(player, OptionChosenResponse(choice.id, index))
        resolveAll()
    }

    private fun GameTestDriver.castAndChoose(player: EntityId, type: String) {
        payAndCast(player)
        resolveChoosing(player, type)
    }

    private fun GameTestDriver.shock(player: EntityId, target: EntityId) {
        val shock = putCardInHand(player, "Shock")
        giveMana(player, Color.RED)
        assertNull(castSpell(player, shock, listOf(target)).error)
        resolveAll()
    }

    @Test
    fun costsOneGenericAndOneWhiteAndGoesToTheGraveyardAfterResolving() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.castAndChoose(p, "Bear")
        assertTrue(d.getGraveyardCardNames(p).contains(spell))
        assertTrue(d.zoneNames(p, com.wingedsheep.sdk.core.Zone.HAND).none { it == spell })
    }

    @Test
    fun creaturesOfTheChosenTypeGetPlusOnePowerAndNoToughness() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears")
        val p = d.player1
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        assertEquals(2, d.projected.getPower(bears))
        d.castAndChoose(p, "Bear")
        assertEquals(3, d.projected.getPower(bears))
        assertEquals(2, d.projected.getToughness(bears))
    }

    @Test
    fun creaturesOfTheChosenTypeGainIndestructible() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears")
        val p = d.player1
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        assertFalse(d.projected.hasKeyword(bears, Keyword.INDESTRUCTIBLE))
        d.castAndChoose(p, "Bear")
        assertTrue(d.projected.hasKeyword(bears, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun indestructibleSavesAChosenCreatureFromLethalDamage() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears", "Shock")
        val p = d.player1
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.castAndChoose(p, "Bear")
        d.shock(p, bears)
        assertTrue("indestructible bears survived lethal damage", d.permanentsNamed(p, "Grizzly Bears").isNotEmpty())
    }

    @Test
    fun withoutTheSpellTheSameDamageKillsTheCreature() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears", "Shock")
        val p = d.player1
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.shock(p, bears)
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.getGraveyardCardNames(p).contains("Grizzly Bears"))
    }

    @Test
    fun everyCreatureYouControlOfTheChosenTypeIsAffected() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears")
        val p = d.player1
        val first = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val second = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.castAndChoose(p, "Bear")
        for (bears in listOf(first, second)) {
            assertEquals(3, d.projected.getPower(bears))
            assertTrue(d.projected.hasKeyword(bears, Keyword.INDESTRUCTIBLE))
        }
    }

    @Test
    fun aCreatureWithSeveralTypesIsAffectedWhenAnyOfThemIsChosen() {
        val d = customScenario(AndTheyShallKnowNoFear, "Llanowar Elves")
        val p = d.player1
        val elves = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        d.castAndChoose(p, "Druid")
        assertEquals(2, d.projected.getPower(elves))
        assertEquals(1, d.projected.getToughness(elves))
        assertTrue(d.projected.hasKeyword(elves, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun creaturesNotOfTheChosenTypeAreUnaffected() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears", "Llanowar Elves")
        val p = d.player1
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val elves = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        d.castAndChoose(p, "Bear")
        assertEquals(3, d.projected.getPower(bears))
        assertEquals(1, d.projected.getPower(elves))
        assertFalse(d.projected.hasKeyword(elves, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun creaturesOpponentsControlOfTheChosenTypeAreUnaffected() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears")
        val mine = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val theirs = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.castAndChoose(d.player1, "Bear")
        assertEquals(3, d.projected.getPower(mine))
        assertEquals(2, d.projected.getPower(theirs))
        assertFalse(d.projected.hasKeyword(theirs, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun aCreatureThatEntersAfterTheSpellResolvedIsNotAffected() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.castAndChoose(p, "Bear")
        val late = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        assertEquals(2, d.projected.getPower(late))
        assertFalse(d.projected.hasKeyword(late, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun theBonusAndIndestructibleLastOnlyUntilEndOfTurn() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears")
        val p = d.player1
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.castAndChoose(p, "Bear")
        assertEquals(3, d.projected.getPower(bears))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, d.player2)
        assertEquals(2, d.projected.getPower(bears))
        assertFalse(d.projected.hasKeyword(bears, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun canBeCastAtInstantSpeedInResponseToLethalDamage() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears", "Shock")
        val p = d.player1
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, shock, listOf(bears)).error)
        assertEquals(1, d.stackSize)
        d.payAndCast(p)
        assertEquals("cast with another spell on the stack", 2, d.stackSize)
        d.resolveChoosing(p, "Bear")
        assertTrue("the response saved the bears from Shock", d.permanentsNamed(p, "Grizzly Bears").isNotEmpty())
    }

    @Test
    fun oneManaIsNotEnoughToCast() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val card = d.putCardInHand(p, spell)
        d.giveMana(p, Color.WHITE)
        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.zoneNames(p, com.wingedsheep.sdk.core.Zone.HAND).contains(spell))
    }

    @Test
    fun twoGenericManaWithoutWhiteIsNotEnoughToCast() {
        val d = customScenario(AndTheyShallKnowNoFear, "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val card = d.putCardInHand(p, spell)
        d.giveColorlessMana(p, 2)
        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.zoneNames(p, com.wingedsheep.sdk.core.Zone.HAND).contains(spell))
    }
}
