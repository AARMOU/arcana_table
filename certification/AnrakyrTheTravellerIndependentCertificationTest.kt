package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AnrakyrTheTraveller
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class AnrakyrTheTravellerIndependentCertificationTest {
    private val name = "Anrakyr the Traveller"

    private fun GameTestDriver.attackAnrakyr() {
        val anrakyr = putCreatureOnBattlefield(player1, name)
        removeSummoningSickness(anrakyr)
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(declareAttackers(player1, listOf(anrakyr), player2).error)
    }

    @Test
    fun castingAnrakyrRequiresFourGenericAndOneBlackMana() {
        val d = customScenario(AnrakyrTheTraveller)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.BLACK)

        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.getHand(p).contains(card))

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(card))
        assertEquals(4, d.projected.getPower(card))
        assertEquals(4, d.projected.getToughness(card))
    }

    @Test
    fun attackingMayCastAnArtifactFromHandByPayingItsManaValueInLife() {
        val d = customScenario(AnrakyrTheTraveller, "Sol Ring")
        val p = d.player1
        val ring = d.putCardInHand(p, "Sol Ring")
        d.attackAnrakyr()
        d.resolveAll(Choices(yes = true, pick = listOf("Sol Ring")))

        assertEquals(19, d.getLifeTotal(p))
        assertTrue(d.permanentsNamed(p, "Sol Ring").contains(ring))
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
    }

    @Test
    fun attackingMayCastAnArtifactFromItsControllersGraveyardForItsManaValueInLife() {
        val d = customScenario(AnrakyrTheTraveller, "Cranial Plating")
        val p = d.player1
        val plating = d.putCardInGraveyard(p, "Cranial Plating")
        d.attackAnrakyr()
        d.resolveAll(Choices(yes = true, pick = listOf("Cranial Plating")))

        assertEquals(18, d.getLifeTotal(p))
        assertTrue(d.getPermanents(p).contains(plating))
        assertFalse(d.getGraveyard(p).contains(plating))
    }

    @Test
    fun decliningTheOptionalCastDoesNotPayLifeOrCastTheArtifact() {
        val d = customScenario(AnrakyrTheTraveller, "Sol Ring")
        val p = d.player1
        d.putCardInHand(p, "Sol Ring")
        d.attackAnrakyr()
        d.resolveAll(Choices(yes = false))

        assertEquals(20, d.getLifeTotal(p))
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertTrue(d.permanentsNamed(p, "Sol Ring").isEmpty())
    }

    @Test
    fun notAttackingAnrakyrDoesNotTriggerTheAbility() {
        val d = customScenario(AnrakyrTheTraveller, "Sol Ring")
        val p = d.player1
        val anrakyr = d.putCreatureOnBattlefield(p, name)
        d.removeSummoningSickness(anrakyr)
        d.putCardInHand(p, "Sol Ring")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, emptyList(), d.player2).error)
        d.resolveAll(Choices(yes = true, pick = listOf("Sol Ring")))

        assertEquals(20, d.getLifeTotal(p))
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertTrue(d.permanentsNamed(p, "Sol Ring").isEmpty())
    }

    @Test
    fun anotherCreatureAttackingDoesNotTriggerAnrakyrsAbility() {
        val d = customScenario(AnrakyrTheTraveller, "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(bear)
        d.putCreatureOnBattlefield(p, name)
        d.putCardInHand(p, "Sol Ring")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(bear), d.player2).error)
        d.resolveAll(Choices(yes = true, pick = listOf("Sol Ring")))

        assertEquals(20, d.getLifeTotal(p))
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertTrue(d.permanentsNamed(p, "Sol Ring").isEmpty())
    }

    @Test
    fun aNonartifactCardInHandCannotBeCastByTheAttackTrigger() {
        val d = customScenario(AnrakyrTheTraveller, "Grizzly Bears")
        val p = d.player1
        d.putCardInHand(p, "Grizzly Bears")
        d.attackAnrakyr()
        d.resolveAll(Choices(yes = true, pick = listOf("Grizzly Bears")))

        assertEquals(20, d.getLifeTotal(p))
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Grizzly Bears"))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
    }

    @Test
    fun anArtifactInAnOpponentsGraveyardCannotBeCastByTheTrigger() {
        val d = customScenario(AnrakyrTheTraveller, "Sol Ring")
        val opponent = d.player2
        val ring = d.putCardInGraveyard(opponent, "Sol Ring")
        d.attackAnrakyr()
        d.resolveAll(Choices(yes = true, pick = listOf("Sol Ring")))

        assertEquals(20, d.getLifeTotal(d.player1))
        assertTrue(d.getGraveyard(opponent).contains(ring))
        assertTrue(d.permanentsNamed(d.player1, "Sol Ring").isEmpty())
    }

    @Test
    fun anArtifactInAnOpponentsHandCannotBeCastByTheTrigger() {
        val d = customScenario(AnrakyrTheTraveller, "Sol Ring")
        val opponent = d.player2
        val ring = d.putCardInHand(opponent, "Sol Ring")
        d.attackAnrakyr()
        d.resolveAll(Choices(yes = true, pick = listOf("Sol Ring")))

        assertEquals(20, d.getLifeTotal(d.player1))
        assertTrue(d.getHand(opponent).contains(ring))
        assertTrue(d.permanentsNamed(d.player1, "Sol Ring").isEmpty())
    }
}
