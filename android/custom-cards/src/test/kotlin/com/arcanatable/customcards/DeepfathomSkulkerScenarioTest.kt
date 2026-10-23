package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DeepfathomSkulker
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class DeepfathomSkulkerScenarioTest {
    @Test fun eachCreatureDealingCombatDamageMayDrawOneCard() {
        val d = customScenario(DeepfathomSkulker, "Grizzly Bears")
        val p = d.player1
        val skulker = d.putCreatureOnBattlefield(p, "Deepfathom Skulker")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(skulker)
        d.removeSummoningSickness(bear)
        assertTrue(d.cardRegistry.getCard("Deepfathom Skulker")!!.colors.isEmpty())
        val before = d.getHand(p).size
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(skulker, bear), d.player2).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll(Choices(yes = true))
        assertEquals(before + 2, d.getHand(p).size)
    }

    @Test fun controllerMayDeclineTheCombatDraw() {
        val d = customScenario(DeepfathomSkulker)
        val p = d.player1
        val skulker = d.putCreatureOnBattlefield(p, "Deepfathom Skulker")
        d.removeSummoningSickness(skulker)
        val before = d.getHand(p).size
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(skulker), d.player2).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll(Choices(yes = false))
        assertEquals(before, d.getHand(p).size)
    }

    @Test fun unblockableAbilityRequiresColorlessManaAndMayTargetAnOpponentCreature() {
        val d = customScenario(DeepfathomSkulker, "Grizzly Bears")
        val p = d.player1
        val source = d.putCreatureOnBattlefield(p, "Deepfathom Skulker")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val id = d.cardRegistry.getCard("Deepfathom Skulker")!!.script.activatedAbilities.single().id
        repeat(4) { d.giveMana(p, Color.BLUE) }
        val action = ActivateAbility(p, source, id, targets = listOf(ChosenTarget.Permanent(target)))
        assertNotNull("Colored mana cannot pay the required {C}", d.submit(action).error)
        d.giveColorlessMana(p, 1)
        assertNull(d.submit(action).error)
        d.resolveAll()
        assertTrue(d.projected.hasKeyword(target, AbilityFlag.CANT_BE_BLOCKED))
        assertFalse(d.projected.hasKeyword(source, AbilityFlag.CANT_BE_BLOCKED))
        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        assertFalse(d.projected.hasKeyword(target, AbilityFlag.CANT_BE_BLOCKED))
    }
}
