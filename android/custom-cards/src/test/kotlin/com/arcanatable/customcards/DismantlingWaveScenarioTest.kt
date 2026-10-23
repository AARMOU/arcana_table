package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DismantlingWave
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CycleCard
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DismantlingWaveScenarioTest {
    @Test
    fun spellCanDestroyOneTargetArtifactControlledByOpponent() {
        val d = customScenario(DismantlingWave, "Sol Ring")
        val opponentRing = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        d.putPermanentOnBattlefield(d.player1, "Sol Ring")
        val wave = d.putCardInHand(d.player1, "Dismantling Wave")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 2)

        assertNull(d.castSpell(d.player1, wave, listOf(opponentRing)).error)
        d.resolveAll()

        assertNull("the opponent's selected artifact was destroyed", d.findPermanent(d.player2, "Sol Ring"))
        assertNotNull("the spell only targets opponents' artifacts or enchantments", d.findPermanent(d.player1, "Sol Ring"))
    }

    @Test
    fun cyclingDestroysAllArtifactsAndEnchantments() {
        val d = customScenario(DismantlingWave, "Sol Ring")
        d.putPermanentOnBattlefield(d.player1, "Sol Ring")
        d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val wave = d.putCardInHand(d.player1, "Dismantling Wave")
        d.giveMana(d.player1, Color.WHITE, 2)
        d.giveColorlessMana(d.player1, 6)

        val cycle = CycleCard(d.player1, wave, PaymentStrategy.FromPool)
        assertNull(d.submit(cycle).error)
        d.resolveAll()

        assertNull("cycling also destroys artifacts controlled by its controller", d.findPermanent(d.player1, "Sol Ring"))
        assertNull("cycling destroys artifacts controlled by opponents", d.findPermanent(d.player2, "Sol Ring"))
    }
}
