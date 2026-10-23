package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Cryptothrall
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class CryptothrallScenarioTest {
    @Test
    fun protectsOnlyOtherArtifactCreaturesItsControllerControls() {
        val d = customScenario(Cryptothrall, "Ornithopter", "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val protector = d.putCreatureOnBattlefield(p, "Cryptothrall")
        val ally = d.putCreatureOnBattlefield(p, "Ornithopter")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Ornithopter")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val ring = d.putPermanentOnBattlefield(p, "Sol Ring")

        assertTrue(d.projected.hasKeyword(ally, Keyword.HEXPROOF))
        assertFalse(d.projected.hasKeyword(protector, Keyword.HEXPROOF))
        assertFalse(d.projected.hasKeyword(enemy, Keyword.HEXPROOF))
        assertFalse(d.projected.hasKeyword(bear, Keyword.HEXPROOF))
        assertFalse(d.projected.hasKeyword(ring, Keyword.HEXPROOF))
    }

    @Test
    fun destroyingTheProtectorRemovesItsProtectionImmediately() {
        val d = customScenario(Cryptothrall, "Ornithopter", "Doom Blade")
        val p = d.player1
        val protector = d.putCreatureOnBattlefield(p, "Cryptothrall")
        val ally = d.putCreatureOnBattlefield(p, "Ornithopter")
        assertTrue(d.projected.hasKeyword(ally, Keyword.HEXPROOF))

        val blade = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, blade, listOf(protector)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Cryptothrall").isEmpty())
        assertFalse(d.projected.hasKeyword(ally, Keyword.HEXPROOF))
    }
}
