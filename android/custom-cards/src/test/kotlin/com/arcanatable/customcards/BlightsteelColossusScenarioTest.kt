package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BlightsteelColossus
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import org.junit.Assert.*
import org.junit.Test

private val SacrificeCreatureForReplacementTest = card("Sacrifice Creature Test") {
    manaCost = "{0}"
    typeLine = "Sorcery"
    oracleText = "Sacrifice a creature."
    spell { effect = Effects.SacrificeOwn(GameObjectFilter.Creature) }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "1"
    }
}

class BlightsteelColossusScenarioTest {
    @Test
    fun graveyardReplacementShufflesColossusIntoOwnersLibrary() {
        // Infect is not supported by the pinned SDK; this scenario checks its implemented
        // graveyard replacement instead of implying infect damage is modeled.
        val d = customScenario(SacrificeCreatureForReplacementTest).apply {
            registerCard(BlightsteelColossus)
        }
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Forest")
        val colossus = d.putCreatureOnBattlefield(p, "Blightsteel Colossus")

        val sacrifice = d.putCardInHand(p, "Sacrifice Creature Test")
        assertNull(d.castSpell(p, sacrifice).error)
        d.resolveAll()

        assertTrue("the Colossus is redirected into its owner's library", d.zoneNames(p, Zone.LIBRARY).contains("Blightsteel Colossus"))
        assertFalse("the Colossus never reaches its owner's graveyard", d.zoneNames(p, Zone.GRAVEYARD).contains("Blightsteel Colossus"))
    }
}
