package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.CostReductionSource
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget

val AncientStoneIdol = card("Ancient Stone Idol") {
    manaCost = "{10}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Golem"
    power = 12
    toughness = 12
    oracleText = "Flash\nThis spell costs {1} less to cast for each attacking creature.\nTrample\nWhen this creature dies, create a 6/12 colorless Construct artifact creature token with trample."

    keywords(Keyword.FLASH, Keyword.TRAMPLE)

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.SelfCast,
            modification = CostModification.ReduceGenericBy(
                CostReductionSource.PermanentsOnBattlefieldMatching(
                    filter = GameObjectFilter.Creature.attacking(),
                ),
            ),
        )
    }

    triggeredAbility {
        trigger = Triggers.self.dies()
        effect = Effects.CreateToken(
            power = 6,
            toughness = 12,
            creatureTypes = setOf("Construct"),
            keywords = setOf(Keyword.TRAMPLE),
            artifactToken = true,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "222"
        artist = "Josh Hass"
        imageUri = "https://cards.scryfall.io/normal/front/3/3/333f6334-f027-4bec-95d7-7af80508c4ca.jpg?1783912975"
    }
}
