package com.arcanatable.customcards
import com.arcanatable.customcards.cards.UlamogsCrusher
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.engine.state.components.combat.AttackersDeclaredThisCombatComponent
import org.junit.Assert.*
import org.junit.Test
class UlamogsCrusherScenarioTest {
    @Test fun attackMakesDefendingPlayerSacrificeTwoPermanents() {
        val d = customScenario(UlamogsCrusher)
        val p = d.player1
        val crusher = d.putCreatureOnBattlefield(p, "Ulamog's Crusher")
        d.removeSummoningSickness(crusher)
        repeat(2) { d.putPermanentOnBattlefield(d.player2, "Plains") }
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(p, listOf(crusher), d.player2)
        d.resolveAll()
        assertTrue(d.permanentsNamed(d.player2, "Plains").isEmpty())
    }

    @Test fun ableCrusherCannotBeOmittedFromAttackDeclaration() {
        val d = customScenario(UlamogsCrusher)
        val crusher = d.putCreatureOnBattlefield(d.player1, "Ulamog's Crusher")
        d.removeSummoningSickness(crusher)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNotNull(d.declareAttackers(d.player1, emptyList(), d.player2).error)
        assertFalse(d.isTapped(crusher))
        assertNull(d.declareAttackers(d.player1, listOf(crusher), d.player2).error)
    }

    @Test fun tappedCrusherIsNotRequiredToAttackAndCreatesNoAnnihilatorTrigger() {
        val d = customScenario(UlamogsCrusher)
        val crusher = d.putCreatureOnBattlefield(d.player1, "Ulamog's Crusher")
        d.removeSummoningSickness(crusher)
        d.tapPermanent(crusher)
        repeat(2) { d.putPermanentOnBattlefield(d.player2, "Plains") }
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNotNull(d.declareAttackers(d.player1, listOf(crusher), d.player2).error)
        // With no able attackers, the engine may already declare the empty attack.
        if (d.state.getEntity(d.player1)!!.get<AttackersDeclaredThisCombatComponent>() == null) {
            assertNull(d.declareAttackers(d.player1, emptyList(), d.player2).error)
        }
        d.resolveAll()
        assertEquals(2, d.permanentsNamed(d.player2, "Plains").size)
        assertTrue(d.state.getZone(d.player2, Zone.GRAVEYARD).isEmpty())
    }

    @Test fun summoningSickCrusherIsNotRequiredToAttack() {
        val d = customScenario(UlamogsCrusher)
        val crusher = d.putCreatureOnBattlefield(d.player1, "Ulamog's Crusher")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNotNull(d.declareAttackers(d.player1, listOf(crusher), d.player2).error)
        if (d.state.getEntity(d.player1)!!.get<AttackersDeclaredThisCombatComponent>() == null) {
            assertNull(d.declareAttackers(d.player1, emptyList(), d.player2).error)
        }
        assertTrue(d.state.stack.isEmpty())
    }


    @Test fun annihilatorIsExactlyTwoAndOnlyTheDefenderSacrifices() {
        val d = customScenario(UlamogsCrusher)
        val crusher = d.putCreatureOnBattlefield(d.player1, "Ulamog's Crusher")
        d.removeSummoningSickness(crusher)
        repeat(4) { d.putPermanentOnBattlefield(d.player2, "Plains") }
        repeat(3) { d.putPermanentOnBattlefield(d.player1, "Plains") }
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(crusher), d.player2).error)
        d.resolveAll()
        assertEquals(2, d.permanentsNamed(d.player2, "Plains").size)
        assertEquals(2, d.zoneNames(d.player2, Zone.GRAVEYARD).count { it == "Plains" })
        assertEquals(3, d.permanentsNamed(d.player1, "Plains").size)
    }
}
