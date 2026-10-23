package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.values.*

val Werebear = card("Werebear") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Human Bear Druid"
    power = 1
    toughness = 1
    oracleText = "{T}: Add {G}.\nThreshold — Werebear gets +3/+3 as long as there are seven or more cards in your graveyard."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.GREEN)
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Add {G}."
    }

    staticAbility {
        condition = Conditions.CardsInGraveyardAtLeast(7)
        ability = ModifyStats(3, 3, GroupFilter.source())
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "282"
        artist = "Carl Critchlow"
        imageUri = "https://cards.scryfall.io/normal/front/9/6/964cf7e3-932d-432f-8ad4-9bd651aada96.jpg?1783945208"
    }
}
