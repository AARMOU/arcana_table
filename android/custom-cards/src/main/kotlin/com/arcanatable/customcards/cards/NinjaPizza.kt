package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.*

val NinjaPizza = card("Ninja Pizza") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment"
    oracleText = "Foods you control have \"{T}, Sacrifice this artifact: Add one mana of any color.\"\n" +
        "At the beginning of your second main phase, create a Food token. (It's an artifact with " +
        "\"{2}, {T}, Sacrifice this token: You gain 3 life.\")"

    staticAbility {
        ability = GrantActivatedAbility(
            ability = ActivatedAbility(
                id = AbilityId.next(),
                cost = Costs.Composite(Costs.Tap, Costs.SacrificeSelf),
                effect = Effects.AddAnyColorMana(),
                isManaAbility = true,
                timing = TimingRule.ManaAbility,
            ),
            filter = GroupFilter(GameObjectFilter.Artifact.withSubtype("Food").youControl()),
        )
    }

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.POSTCOMBAT_MAIN)
        effect = Effects.CreateFood()
        description = "At the beginning of your second main phase, create a Food token."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "32"
        imageUri = "https://cards.scryfall.io/normal/front/7/4/74c24ff9-882d-4ffb-bf8b-dba803481a99.jpg?1783904164"
        artist = "Brian Yuen"
    }
}
