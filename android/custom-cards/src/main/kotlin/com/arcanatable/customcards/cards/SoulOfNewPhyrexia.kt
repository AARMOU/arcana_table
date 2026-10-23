package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val SoulOfNewPhyrexia = card("Soul of New Phyrexia") {
    manaCost = "{6}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Phyrexian Avatar"
    power = 6
    toughness = 6
    oracleText = "Trample\n{5}: Permanents you control gain indestructible until end of turn.\n{5}, Exile this card from your graveyard: Permanents you control gain indestructible until end of turn."
    keywords(Keyword.TRAMPLE)
    val protectPermanents = Effects.ForEachInGroup(
        GroupFilter(GameObjectFilter.Permanent.youControl()),
        Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, EffectTarget.IterationEntity),
    )
    activatedAbility {
        cost = Costs.Mana("{5}")
        effect = protectPermanents
    }
    activatedAbility {
        activateFromZone = Zone.GRAVEYARD
        cost = Costs.Composite(Costs.Mana("{5}"), Costs.ExileSelf)
        effect = protectPermanents
    }
    metadata { rarity = Rarity.MYTHIC; collectorNumber = "974"; artist = "Daarken" }
}
