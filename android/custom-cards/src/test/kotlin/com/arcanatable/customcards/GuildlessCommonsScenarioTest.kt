package com.arcanatable.customcards
import com.arcanatable.customcards.cards.GuildlessCommons
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test
class GuildlessCommonsScenarioTest {
    @Test fun enteringTappedReturnsChosenLandWithoutTargeting() {
        val d = customScenario(GuildlessCommons)
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Plains")
        val land = d.putCardInHand(p, "Guildless Commons")
        assertNull(d.playLand(p, land).error)
        d.resolveAll(Choices(pick = listOf("Plains")))
        assertTrue(d.permanentsNamed(p, "Plains").isEmpty())
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Plains"))
        val commons = d.findPermanent(p, "Guildless Commons")!!
        assertTrue(d.isTapped(commons))
        assertTrue(d.cardRegistry.getCard("Guildless Commons")!!.script.activatedAbilities.any { it.manaAbility })
    }
}
