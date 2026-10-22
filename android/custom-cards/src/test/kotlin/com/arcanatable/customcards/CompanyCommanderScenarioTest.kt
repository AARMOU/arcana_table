package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CompanyCommander
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class CompanyCommanderScenarioTest {
    @Test fun etbMakesOneWhiteSoldierForEachOpponent() {
        val d = customScenario(CompanyCommander)
        val p = d.player1
        val spell = d.putCardInHand(p, "Company Commander")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()
        val commander = d.findPermanent(p, "Company Commander")!!
        val soldiers = d.getCreatures(p).filter {
            it != commander && d.getCardName(it)?.contains("Soldier") == true
        }
        assertEquals(1, soldiers.size)
        assertEquals(1, d.projected.getPower(soldiers.single()))
        assertEquals(1, d.projected.getToughness(soldiers.single()))
        assertTrue(d.getCreatures(d.player2).isEmpty())
    }

    @Test fun attackingGrantsDeathtouchToFriendlyAttackersAndReservesButNotOpponents() {
        val d = customScenario(CompanyCommander, "Grizzly Bears")
        val p = d.player1
        val spell = d.putCardInHand(p, "Company Commander")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()
        val commander = d.findPermanent(p, "Company Commander")!!
        val soldier = d.getCreatures(p).single { it != commander }
        val reserve = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(commander)
        assertFalse(d.projected.hasKeyword(reserve, Keyword.DEATHTOUCH))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(commander), d.player2).error)
        d.resolveAll()
        listOf(commander, soldier, reserve).forEach {
            assertTrue(d.projected.hasKeyword(it, Keyword.DEATHTOUCH))
        }
        assertFalse(d.projected.hasKeyword(enemy, Keyword.DEATHTOUCH))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        listOf(commander, soldier, reserve).forEach {
            assertFalse(d.projected.hasKeyword(it, Keyword.DEATHTOUCH))
        }
    }
}
