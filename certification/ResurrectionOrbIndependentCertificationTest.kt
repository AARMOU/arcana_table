package com.arcanatable.customcards

// Trusted-reviewer scenarios, maintained on main rather than on the Copilot card PR.
// They are copied into an isolated exact-head checkout by the certification workflow.
// An implementation that merely declares Oracle text without functioning in Argentum fails.
import com.arcanatable.customcards.cards.ResurrectionOrb
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class ResurrectionOrbIndependentCertificationTest {
    @Test
    fun independentEquipmentKeywordAndEquipCostChecks() {
        val d = customScenario(ResurrectionOrb, "Grizzly Bears")
        val player = d.player1
        val bearer = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val other = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val orb = d.putPermanentOnBattlefield(player, "Resurrection Orb")
        val ability = d.cardRegistry.getCard("Resurrection Orb")!!.script.activatedAbilities.single()
        assertFalse(d.projected.hasKeyword(bearer, Keyword.LIFELINK))
        assertFalse(d.projected.hasKeyword(other, Keyword.LIFELINK))

        d.giveColorlessMana(player, 3)
        assertNotNull(d.submit(
            ActivateAbility(player, orb, ability.id, targets = listOf(ChosenTarget.Permanent(bearer))),
        ).error)
        assertFalse(d.projected.hasKeyword(bearer, Keyword.LIFELINK))

        d.giveColorlessMana(player, 1)
        assertNull(d.submit(
            ActivateAbility(player, orb, ability.id, targets = listOf(ChosenTarget.Permanent(bearer))),
        ).error)
        d.resolveAll(Choices(targets = listOf(bearer)))
        assertTrue(d.projected.hasKeyword(bearer, Keyword.LIFELINK))
        assertFalse(d.projected.hasKeyword(other, Keyword.LIFELINK))
    }

    @Test
    fun independentUnattachedCreatureDeathCannotResurrect() {
        val d = customScenario(ResurrectionOrb, "Grizzly Bears", "Murder")
        val player = d.player1
        val victim = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putPermanentOnBattlefield(player, "Resurrection Orb")
        val murder = d.putCardInHand(player, "Murder")
        d.giveMana(player, Color.BLACK, 2)
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpellWithTargets(player, murder, listOf(ChosenTarget.Permanent(victim))).error)
        d.resolveAll()
        assertNull(d.findPermanent(player, "Grizzly Bears"))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Grizzly Bears"))
        d.passPriorityUntil(Step.END)
        d.resolveAll()
        assertNull(d.findPermanent(player, "Grizzly Bears"))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun independentDeathTriggerIsDelayedUntilNextEndStep() {
        val d = customScenario(ResurrectionOrb, "Grizzly Bears", "Murder")
        val player = d.player1
        val bearer = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val orb = d.putPermanentOnBattlefield(player, "Resurrection Orb")
        val ability = d.cardRegistry.getCard("Resurrection Orb")!!.script.activatedAbilities.single()
        d.giveColorlessMana(player, 4)
        assertNull(d.submit(
            ActivateAbility(player, orb, ability.id, targets = listOf(ChosenTarget.Permanent(bearer))),
        ).error)
        d.resolveAll(Choices(targets = listOf(bearer)))

        val murder = d.putCardInHand(player, "Murder")
        d.giveMana(player, Color.BLACK, 2)
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpellWithTargets(player, murder, listOf(ChosenTarget.Permanent(bearer))).error)
        d.resolveAll()

        // No immediate resurrection: the card must remain in the graveyard until the end step.
        assertNull(d.findPermanent(player, "Grizzly Bears"))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Grizzly Bears"))
        d.passPriorityUntil(Step.END)
        d.resolveAll()
        assertNotNull(d.findPermanent(player, "Grizzly Bears"))
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }
}
