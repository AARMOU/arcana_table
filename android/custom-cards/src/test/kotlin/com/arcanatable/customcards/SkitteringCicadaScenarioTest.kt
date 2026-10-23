package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SkitteringCicada
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class SkitteringCicadaScenarioTest {
    @Test fun flashPermissionLetsControllerCastColorlessCreatureDuringCombat() {
        val d = customScenario(SkitteringCicada, "Ornithopter", "Grizzly Bears")
        val p = d.player1
        val cicada = d.putCreatureOnBattlefield(p, "Skittering Cicada")
        assertTrue(d.projected.hasKeyword(cicada, Keyword.FLASH))
        val thopter = d.putCardInHand(p, "Ornithopter")
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNotNull("The permission is limited to colorless spells", d.castSpell(p, bear).error)
        assertNull(d.castSpell(p, thopter).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Ornithopter"))
        assertTrue("Zero-mana-value spells still grant trample", d.projected.hasKeyword(cicada, Keyword.TRAMPLE))
        assertEquals(2, d.projected.getPower(cicada))
    }

    @Test fun castTriggerUsesSpellManaValueIncludingChosenXAndBonusesExpire() {
        val d = customScenario(SkitteringCicada, "Walking Ballista", "Sol Ring")
        val p = d.player1
        val cicada = d.putCreatureOnBattlefield(p, "Skittering Cicada")
        val ballista = d.putCardInHand(p, "Walking Ballista")
        d.giveColorlessMana(p, 6)
        assertNull(d.castXSpell(p, ballista, 3).error)
        d.resolveAll()
        assertEquals(8, d.projected.getPower(cicada))
        assertEquals(8, d.projected.getToughness(cicada))
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()
        assertEquals(9, d.projected.getPower(cicada))
        assertTrue(d.projected.hasKeyword(cicada, Keyword.TRAMPLE))
        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        assertEquals(2, d.projected.getPower(cicada))
        assertEquals(2, d.projected.getToughness(cicada))
        assertFalse(d.projected.hasKeyword(cicada, Keyword.TRAMPLE))
    }

    @Test fun coloredSpellDoesNotTriggerBonus() {
        val d = customScenario(SkitteringCicada, "Grizzly Bears")
        val p = d.player1
        val cicada = d.putCreatureOnBattlefield(p, "Skittering Cicada")
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()
        assertEquals(2, d.projected.getPower(cicada))
        assertFalse(d.projected.hasKeyword(cicada, Keyword.TRAMPLE))
    }

    @Test fun opponentsGetNeitherTheFlashPermissionNorACastBonusForItsController() {
        val d = customScenario(SkitteringCicada, "Ornithopter")
        val p = d.player1
        val cicada = d.putCreatureOnBattlefield(p, "Skittering Cicada")
        val thopter = d.putCardInHand(d.player2, "Ornithopter")
        d.passPriorityUntil(Step.BEGIN_COMBAT)
        assertNull(d.passPriority(p).error)
        assertNotNull("Cicada grants flash only to its controller", d.castSpell(d.player2, thopter).error)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertNull(d.castSpell(d.player2, thopter).error)
        d.resolveAll()
        assertEquals(2, d.projected.getPower(cicada))
        assertFalse(d.projected.hasKeyword(cicada, Keyword.TRAMPLE))
    }

}
