package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AllIsDust
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

class AllIsDustScenarioTest {
    @Test
    fun bothPlayersSacrificeColoredPermanentsButKeepColorlessArtifactsAndLands() {
        val d = customScenario(AllIsDust, "Grizzly Bears", "Craw Wurm", "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(opponent, "Craw Wurm")
        d.putPermanentOnBattlefield(p, "Sol Ring")
        d.putPermanentOnBattlefield(opponent, "Sol Ring")
        val spell = d.putCardInHand(p, "All Is Dust")
        d.giveColorlessMana(p, 7)

        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.permanentsNamed(opponent, "Craw Wurm").isEmpty())
        assertEquals(1, d.permanentsNamed(p, "Sol Ring").size)
        assertEquals(1, d.permanentsNamed(opponent, "Sol Ring").size)
    }

    @Test fun sacrificeIgnoresIndestructibleHexproofAndPermanentTypeButPreservesDevoid() {
        val d = customScenario(AllIsDust, "Eldrazi Displacer", "Sol Ring")
        val protected = card("Dust Protected Colored Artifact") {
            manaCost = "{G}"; typeLine = "Artifact"
            keywords(Keyword.INDESTRUCTIBLE, Keyword.HEXPROOF)
        }
        val multicolor = card("Dust Multicolor Enchantment") {
            manaCost = "{W}{U}"; typeLine = "Enchantment"
        }
        d.registerCards(listOf(protected, multicolor).map { CardLoader.fromJsonPreservingIds(exportLine(it)) })
        val artifact = d.putPermanentOnBattlefield(d.player2, protected.name)
        val enchantment = d.putPermanentOnBattlefield(d.player1, multicolor.name)
        val devoid = d.putCreatureOnBattlefield(d.player2, "Eldrazi Displacer")
        val ring = d.putPermanentOnBattlefield(d.player1, "Sol Ring")
        val land = d.putLandOnBattlefield(d.player2, "Plains")
        d.giveMana(d.player1, Color.BLUE, 7) // Generic {7}, not seven required {C}.
        assertNull(d.castSpell(d.player1, d.putCardInHand(d.player1, "All Is Dust")).error)
        d.resolveAll()
        assertTrue(artifact in d.state.getZone(d.player2, Zone.GRAVEYARD))
        assertTrue(enchantment in d.state.getZone(d.player1, Zone.GRAVEYARD))
        listOf(devoid, ring, land).forEach { assertTrue(it in d.state.getBattlefield()) }
    }

    @Test fun allDeathsAreSimultaneousSoADyingObserverSeesOtherPlayersCreatures() {
        val d = customScenario(AllIsDust, "Grizzly Bears")
        val observer = card("Dust Simultaneous Observer") {
            manaCost = "{B}"; typeLine = "Creature — Human"; power = 2; toughness = 2
            triggeredAbility {
                trigger = Triggers.another(GameObjectFilter.Creature).dies()
                effect = Effects.GainLife(1)
            }
        }
        d.registerCard(CardLoader.fromJsonPreservingIds(exportLine(observer)))
        d.putCreatureOnBattlefield(d.player1, observer.name)
        d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 7)
        assertNull(d.castSpell(d.player1, d.putCardInHand(d.player1, "All Is Dust")).error)
        d.resolveAll()
        assertTrue(d.getCreatures(d.player1).isEmpty())
        assertTrue(d.getCreatures(d.player2).isEmpty())
        assertEquals(22, d.getLifeTotal(d.player1))
        assertEquals(20, d.getLifeTotal(d.player2))
    }

}
