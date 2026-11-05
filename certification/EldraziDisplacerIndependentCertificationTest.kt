package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldraziDisplacer
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class EldraziDisplacerIndependentCertificationTest {
    private val name = "Eldrazi Displacer"

    private fun activateBlink(
        d: GameTestDriver,
        player: EntityId,
        displacer: EntityId,
        target: EntityId,
    ) = d.submit(
        ActivateAbility(
            player,
            displacer,
            d.cardRegistry.getCard(name)!!.script.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Permanent(target)),
        )
    )

    @Test
    fun castingRequiresWhiteAndTwoGenericManaAndDevoidLeavesAThreeThreeColorlessCreature() {
        val d = customScenario(EldraziDisplacer)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 2)

        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains(name))
        assertTrue(d.permanentsNamed(p, name).isEmpty())

        d.giveMana(p, Color.WHITE)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(card))
        assertTrue(d.projected.getColors(card).isEmpty())
        assertEquals(3, d.projected.getPower(card))
        assertEquals(3, d.projected.getToughness(card))
    }

    @Test
    fun abilityCanBlinkAnOpponentsCreatureAtInstantSpeedAndReturnsItTappedToItsOwner() {
        val d = customScenario(EldraziDisplacer, "Grizzly Bears")
        val p = d.player1
        val owner = d.player2
        val displacer = d.putPermanentOnBattlefield(p, name)
        val target = d.putCreatureOnBattlefield(owner, "Grizzly Bears")

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = owner)
        assertNull(d.passPriority(owner).error)
        d.giveMana(p, Color.WHITE, 2)
        d.giveColorlessMana(p, 1)
        assertNull(activateBlink(d, p, displacer, target).error)
        d.resolveAll()

        val returned = d.findPermanent(owner, "Grizzly Bears")
        assertNotNull(returned)
        assertTrue(d.isTapped(returned!!))
        assertEquals(owner, d.projected.getController(returned))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertFalse(d.zoneNames(owner, Zone.EXILE).contains("Grizzly Bears"))
    }

    @Test
    fun genericManaCannotReplaceTheRequiredColorlessMana() {
        val d = customScenario(EldraziDisplacer, "Grizzly Bears")
        val p = d.player1
        val displacer = d.putPermanentOnBattlefield(p, name)
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveMana(p, Color.WHITE, 3)

        assertNotNull(activateBlink(d, p, displacer, target).error)

        assertEquals(3, d.manaPool(p).white)
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").contains(target))
        assertFalse(d.isTapped(target))
    }

    @Test
    fun displacerCannotTargetItself() {
        val d = customScenario(EldraziDisplacer)
        val p = d.player1
        val displacer = d.putPermanentOnBattlefield(p, name)
        d.giveColorlessMana(p, 3)

        assertNotNull(activateBlink(d, p, displacer, displacer).error)

        assertTrue(d.permanentsNamed(p, name).contains(displacer))
        assertFalse(d.zoneNames(p, Zone.EXILE).contains(name))
    }

    @Test
    fun abilityCannotTargetANoncreature() {
        val d = customScenario(EldraziDisplacer, "Sol Ring")
        val p = d.player1
        val displacer = d.putPermanentOnBattlefield(p, name)
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        d.giveColorlessMana(p, 3)

        assertFalse(d.projected.hasType(artifact, "CREATURE"))
        assertNotNull(activateBlink(d, p, displacer, artifact).error)

        assertTrue(d.permanentsNamed(d.player2, "Sol Ring").contains(artifact))
        assertTrue(d.permanentsNamed(p, name).contains(displacer))
    }
}
