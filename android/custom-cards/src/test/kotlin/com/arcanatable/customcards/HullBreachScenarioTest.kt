package com.arcanatable.customcards
import com.arcanatable.customcards.cards.HullBreach
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test
class HullBreachScenarioTest {
    @Test fun thirdModeDestroysBothChosenArtifactAndEnchantment() {
        val d = customScenario(HullBreach, "Sol Ring", "Pacifism")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val enchantment = d.putPermanentOnBattlefield(d.player2, "Pacifism")
        val card = d.putCardInHand(p, "Hull Breach")
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)
        val chosen = listOf(ChosenTarget.Permanent(artifact), ChosenTarget.Permanent(enchantment))
        assertNull(d.submit(CastSpell(
            playerId = p,
            cardId = card,
            paymentStrategy = PaymentStrategy.FromPool,
            targets = chosen,
            chosenModes = listOf(2),
            modeTargetsOrdered = listOf(chosen)
        )).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(d.player2, "Sol Ring").isEmpty())
        assertTrue(d.permanentsNamed(d.player2, "Pacifism").isEmpty())
    }
}
