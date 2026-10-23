package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SpringleafParade
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class SpringleafParadeScenarioTest {
    @Test
    fun createsChangelingAndGrantsTokenManaAbility() {
        val d = CardPool.driver().apply {
            registerCard(SpringleafParade)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val parade = d.putCardInHand(p, "Springleaf Parade")
        d.giveMana(p, Color.GREEN, 2)
        d.giveColorlessMana(p, 2)
        assertNull(d.castXSpell(p, parade, 2).error)
        d.resolveAll()
        val tokens = d.getCreatures(p).filter { d.getCardName(it) == "Shapeshifter Token" }
        assertEquals(2, tokens.size)
        d.removeSummoningSickness(tokens.first())
        assertTrue(d.abilityActions(p, tokens.first()).isNotEmpty())
        val ability = d.abilityActions(p, tokens.first()).first().action
        assertNull(d.submit(ability).error)
        d.decide(Choices(color = Color.GREEN))
        val pool = d.manaPool(p)
        assertTrue(
            pool.white + pool.blue + pool.black + pool.red + pool.green + pool.colorless > 0 ||
                pool.restrictedMana.isNotEmpty(),
        )
    }
}
