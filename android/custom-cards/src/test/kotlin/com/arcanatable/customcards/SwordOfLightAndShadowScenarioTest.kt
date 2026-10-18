package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SwordOfLightAndShadow
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SwordOfLightAndShadowScenarioTest {
    @Test
    fun combatDamageGainsLifeAndReturnsCreatureFromGraveyard() {
        val d = customScenario(SwordOfLightAndShadow, "Grizzly Bears")
        val player = d.player1
        val bearer = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val sword = d.putPermanentOnBattlefield(player, "Sword of Light and Shadow")
        val graveyardBear = d.putCardInGraveyard(player, "Grizzly Bears")
        d.giveColorlessMana(player, 2)
        val equip = d.cardRegistry.getCard("Sword of Light and Shadow")!!.script.activatedAbilities.single()
        assertNull(d.submit(ActivateAbility(player, sword, equip.id, targets = listOf(ChosenTarget.Permanent(bearer)))).error)
        d.resolveAll(Choices(targets = listOf(bearer)))
        d.removeSummoningSickness(bearer)
        val lifeBefore = d.getLifeTotal(player)
        val handBefore = d.getHandSize(player)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(bearer), d.player2).error)
        while (d.currentStep != Step.POSTCOMBAT_MAIN) {
            if (d.state.pendingDecision != null) d.decide(Choices(targets = listOf(graveyardBear)))
            else d.bothPass()
        }
        d.resolveAll(Choices(targets = listOf(graveyardBear)))

        assertEquals(lifeBefore + 3, d.getLifeTotal(player))
        assertEquals(handBefore + 1, d.getHandSize(player))
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Grizzly Bears"))
    }
}
