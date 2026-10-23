package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.PartnerKind
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val LeonardoTheBalance = card("Leonardo, the Balance") {
    manaCost = "{3}{W}"
    colorIdentity = "WUBRG"
    typeLine = "Legendary Creature — Mutant Ninja Turtle"
    power = 3
    toughness = 3
    oracleText = "Whenever a token you control enters, you may put a +1/+1 counter on each creature you control. " +
        "Do this only once each turn.\n" +
        "{W}{U}{B}{R}{G}: Creatures you control gain menace, trample, and lifelink until end of turn.\n" +
        "Partner—Character select (You can have two commanders if both have this ability.)"

    keywordAbility(KeywordAbility.Partner(PartnerKind.Named("Character select")))

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Token.youControl()).enters()
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl()),
            Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, EffectTarget.IterationEntity),
        )
        optional = true
        effectOncePerTurn = true
        description = "Whenever a token you control enters, you may put a +1/+1 counter on each creature you control. Do this only once each turn."
    }

    activatedAbility {
        cost = Costs.Mana("{W}{U}{B}{R}{G}")
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl()),
            Effects.GrantKeyword(Keyword.MENACE, EffectTarget.IterationEntity, Duration.EndOfTurn) then
                Effects.GrantKeyword(Keyword.TRAMPLE, EffectTarget.IterationEntity, Duration.EndOfTurn) then
                Effects.GrantKeyword(Keyword.LIFELINK, EffectTarget.IterationEntity, Duration.EndOfTurn),
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "1"
        artist = "Inkognit"
        imageUri = "https://api.scryfall.com/cards/tmc/1?format=image&version=normal"
    }
}
