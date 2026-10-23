package com.arcanatable.customcards

import com.arcanatable.customcards.cards.YavimayaHollow
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class YavimayaHollowScenarioTest {
    @Test
    fun regenerationShieldSavesTargetFromDestruction() {
        val d = CardPool.driver("Grizzly Bears", "Murder").apply {
            registerCard(YavimayaHollow)
            initMirrorMatch(Deck.of("Plains" to 40))
            passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN)
        }
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val hollow = d.putPermanentOnBattlefield(player, "Yavimaya Hollow")
        d.giveMana(player, Color.GREEN)
        val regenerate = d.cardRegistry.getCard("Yavimaya Hollow")!!.script.activatedAbilities[1]
        assertNull(d.submit(ActivateAbility(player, hollow, regenerate.id, targets = listOf(ChosenTarget.Permanent(bear)))).error)
        d.resolveAll(Choices(targets = listOf(bear)))

        val murder = d.putCardInHand(d.player2, "Murder")
        d.giveMana(d.player2, Color.BLACK, 3)
        d.passPriority(player)
        assertNull(d.castSpell(d.player2, murder, targets = listOf(bear)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Grizzly Bears").contains(bear))
        assertTrue(d.isTapped(bear))
    }
}
