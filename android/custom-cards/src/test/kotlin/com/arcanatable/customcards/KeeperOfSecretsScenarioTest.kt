package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KeeperOfSecrets
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeeperOfSecretsScenarioTest {
    @Test
    fun spellsCastFromHandDoNotTriggerSymphonyOfPain() {
        val d = customScenario(KeeperOfSecrets, "Grizzly Bears")
        val p = d.player1
        val keeper = d.putCardInHand(p, "Keeper of Secrets")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 5)
        assertNull(d.castSpell(p, keeper).error)
        d.resolveAll()
        val permanent = d.findPermanent(p, "Keeper of Secrets")!!
        assertTrue(d.projected.hasKeyword(permanent, Keyword.FIRST_STRIKE))
        assertTrue(d.projected.hasKeyword(permanent, Keyword.HASTE))

        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun spellCastFromExileByCascadeDealsItsManaValueToAnOpponent() {
        val d = customScenario(KeeperOfSecrets, "Grizzly Bears", "Plains")
        val p = d.player1
        val keeper = d.putCardInHand(p, "Keeper of Secrets")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 5)
        assertNull(d.castSpell(p, keeper).error)
        d.resolveAll()

        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Plains")
        val heralds = d.putCardInHand(p, "Heralds of Tzeentch")
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, heralds).error)
        d.resolveAll(Choices(targets = listOf(d.player2)))

        assertEquals(18, d.getLifeTotal(d.player2))
        assertEquals(20, d.getLifeTotal(p))
        assertEquals(1, d.permanentsNamed(p, "Grizzly Bears").size)
        assertTrue(d.permanentsNamed(p, "Heralds of Tzeentch").isNotEmpty())
    }

    @Test
    fun otherPlayersNonHandSpellDoesNotTriggerYourKeeper() {
        val d = customScenario(KeeperOfSecrets, "Grizzly Bears")
        val p = d.player1
        val keeper = d.putCardInHand(p, "Keeper of Secrets")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 5)
        assertNull(d.castSpell(p, keeper).error)
        d.resolveAll()

        d.putCardOnTopOfLibrary(d.player2, "Grizzly Bears")
        val heralds = d.putCardInHand(d.player2, "Heralds of Tzeentch")
        d.giveMana(d.player2, Color.BLUE)
        d.giveColorlessMana(d.player2, 4)
        assertNull(d.castSpell(d.player2, heralds).error)
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(p))
        assertEquals(20, d.getLifeTotal(d.player2))
        assertEquals(1, d.permanentsNamed(d.player2, "Grizzly Bears").size)
    }
}
