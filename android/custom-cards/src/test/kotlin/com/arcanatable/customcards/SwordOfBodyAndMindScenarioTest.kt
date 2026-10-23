package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SwordOfBodyAndMind
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SwordOfBodyAndMindScenarioTest {
    @Test
    fun equippedCreatureMakesWolfAndDamagesPlayerLibraryOnCombatDamage() {
        val d = customScenario(SwordOfBodyAndMind, "Grizzly Bears")
        val player = d.player1
        val bearer = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val sword = d.putPermanentOnBattlefield(player, "Sword of Body and Mind")
        d.giveColorlessMana(player, 2)

        val equip = d.cardRegistry.getCard("Sword of Body and Mind")!!.script.activatedAbilities.single()
        assertNull(d.submit(ActivateAbility(player, sword, equip.id, targets = listOf(ChosenTarget.Permanent(bearer)))).error)
        d.resolveAll(Choices(targets = listOf(bearer)))
        d.removeSummoningSickness(bearer)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(bearer), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(1, d.permanentsNamed(player, "Wolf").size)
        assertEquals(10, d.state.getZone(d.player2, Zone.GRAVEYARD).size)
    }
}
