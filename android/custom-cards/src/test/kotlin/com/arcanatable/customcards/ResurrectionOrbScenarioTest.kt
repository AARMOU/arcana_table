package com.arcanatable.customcards

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

class ResurrectionOrbScenarioTest {
    @Test
    fun equippedCreatureHasLifelinkAndOtherCreaturesDoNot() {
        val d = customScenario(ResurrectionOrb, "Grizzly Bears")
        val player = d.player1
        val bearer = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val other = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val orb = d.putPermanentOnBattlefield(player, "Resurrection Orb")
        d.giveColorlessMana(player, 4)

        val equip = d.cardRegistry.getCard("Resurrection Orb")!!.script.activatedAbilities.single()
        assertNull(
            d.submit(
                ActivateAbility(player, orb, equip.id, targets = listOf(ChosenTarget.Permanent(bearer))),
            ).error,
        )
        d.resolveAll(Choices(targets = listOf(bearer)))

        assertTrue(d.projected.hasKeyword(bearer, Keyword.LIFELINK))
        assertFalse(d.projected.hasKeyword(other, Keyword.LIFELINK))
    }

    @Test
    fun equipRequiresFourMana() {
        val d = customScenario(ResurrectionOrb, "Grizzly Bears")
        val player = d.player1
        val bearer = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val orb = d.putPermanentOnBattlefield(player, "Resurrection Orb")
        d.giveColorlessMana(player, 3)

        val equip = d.cardRegistry.getCard("Resurrection Orb")!!.script.activatedAbilities.single()
        assertNotNull(
            d.submit(
                ActivateAbility(player, orb, equip.id, targets = listOf(ChosenTarget.Permanent(bearer))),
            ).error,
        )
        assertFalse(d.projected.hasKeyword(bearer, Keyword.LIFELINK))
    }

    @Test
    fun onlyEquippedCreatureReturnsAtTheBeginningOfTheNextEndStep() {
        val d = customScenario(ResurrectionOrb, "Act of Treason", "Grizzly Bears", "Craw Wurm", "Murder")
        val player = d.player1
        val owner = d.player2
        val bearer = d.putCreatureOnBattlefield(owner, "Grizzly Bears")
        d.putCreatureOnBattlefield(player, "Craw Wurm")
        val orb = d.putPermanentOnBattlefield(player, "Resurrection Orb")

        val actOfTreason = d.putCardInHand(player, "Act of Treason")
        d.giveMana(player, Color.RED)
        d.giveColorlessMana(player, 2)
        assertNull(
            d.castSpellWithTargets(player, actOfTreason, listOf(ChosenTarget.Permanent(bearer))).error,
        )
        d.resolveAll()
        assertEquals(player, d.getController(bearer))
        val controlledBearer = bearer
        d.giveColorlessMana(player, 4)

        val equip = d.cardRegistry.getCard("Resurrection Orb")!!.script.activatedAbilities.single()
        assertNull(
            d.submit(
                ActivateAbility(player, orb, equip.id, targets = listOf(ChosenTarget.Permanent(controlledBearer))),
            ).error,
        )
        d.resolveAll(Choices(targets = listOf(controlledBearer)))

        val murder = d.putCardInHand(player, "Murder")
        d.giveMana(player, Color.BLACK, 2)
        d.giveColorlessMana(player, 1)
        val crawWurm = d.findPermanent(player, "Craw Wurm")!!
        assertNull(d.castSpellWithTargets(player, murder, listOf(ChosenTarget.Permanent(crawWurm))).error)
        d.resolveAll()
        assertNull(d.findPermanent(player, "Craw Wurm"))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Craw Wurm"))

        val secondMurder = d.putCardInHand(player, "Murder")
        d.giveMana(player, Color.BLACK, 2)
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpellWithTargets(player, secondMurder, listOf(ChosenTarget.Permanent(bearer))).error)
        d.resolveAll()
        assertNull(d.findPermanent(owner, "Grizzly Bears"))
        assertTrue(d.zoneNames(owner, Zone.GRAVEYARD).contains("Grizzly Bears"))

        d.passPriorityUntil(Step.END)
        d.resolveAll()

        val returnedBearer = d.findPermanent(owner, "Grizzly Bears")
        assertNotNull(returnedBearer)
        assertEquals(owner, d.getController(returnedBearer!!))
        assertNull(d.findPermanent(player, "Grizzly Bears"))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Craw Wurm"))
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertFalse(d.zoneNames(owner, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }
}
