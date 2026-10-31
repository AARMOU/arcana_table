package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val ThunderhawkGunship = card("Thunderhawk Gunship") {
    manaCost = "{6}"
    colorIdentity = ""
    typeLine = "Artifact — Vehicle"
    power = 6
    toughness = 6
    oracleText = "Flying\nWhen this Vehicle enters, create two 2/2 white Astartes Warrior creature tokens with vigilance.\nWhenever this Vehicle attacks, attacking creatures you control gain flying until end of turn.\nCrew 2"

    keywords(Keyword.FLYING)
    keywordAbility(KeywordAbility.crew(2))

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.CreateToken(
            count = 2,
            power = 2,
            toughness = 2,
            colors = setOf(Color.WHITE),
            creatureTypes = setOf("Astartes", "Warrior"),
            keywords = setOf(Keyword.VIGILANCE),
        )
    }

    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.attacking().youControl()),
            Effects.GrantKeyword(Keyword.FLYING, EffectTarget.IterationEntity),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "167"
        artist = "Games Workshop"
        imageUri = "https://cards.scryfall.io/normal/front/6/7/6790720a-e1f3-4533-acd8-b002c8e91929.jpg?1783920811"
    }
}
