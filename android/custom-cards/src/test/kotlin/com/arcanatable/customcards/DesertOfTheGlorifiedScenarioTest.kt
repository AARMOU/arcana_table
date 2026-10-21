package com.arcanatable.customcards
import com.arcanatable.customcards.cards.DesertOfTheGlorified
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.scripting.KeywordAbility
import org.junit.Assert.*
import org.junit.Test
class DesertOfTheGlorifiedScenarioTest {
    @Test fun landEntersTappedAndHasCyclingAndBlackManaAbility() {
        val d = customScenario(DesertOfTheGlorified)
        val p = d.player1
        val land = d.putCardInHand(p, "Desert of the Glorified")
        assertNull(d.playLand(p, land).error)
        d.resolveAll()
        val inPlay = d.findPermanent(p, "Desert of the Glorified")!!
        assertTrue(d.isTapped(inPlay))
        val def = d.cardRegistry.getCard("Desert of the Glorified")!!
        assertTrue(def.keywordAbilities.any { it is KeywordAbility.Cycling })
        assertTrue(def.script.activatedAbilities.any { it.manaAbility })
    }
}
