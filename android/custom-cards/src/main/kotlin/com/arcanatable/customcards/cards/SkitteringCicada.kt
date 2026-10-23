package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantFlashToSpellType
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val SkitteringCicada = card("Skittering Cicada") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Creature — Insect"
    power = 2
    toughness = 2
    oracleText = "Flash\nYou may cast colorless spells as though they had flash.\nWhenever you cast a colorless spell, until end of turn, this creature gains trample and gets +X/+X, where X is that spell's mana value."
    keywords(Keyword.FLASH)
    val colorlessSpells = GameObjectFilter.Any.withCardPredicate(CardPredicate.IsColorless)
    staticAbility { ability = GrantFlashToSpellType(colorlessSpells, controllerOnly = true) }
    triggeredAbility {
        trigger = Triggers.you.casts(colorlessSpells)
        val x = DynamicAmounts.triggeringSpellManaValue()
        effect = Effects.GrantKeyword(Keyword.TRAMPLE, EffectTarget.Self) then
            Effects.ModifyStats(x, x, EffectTarget.Self)
    }
    metadata { rarity = Rarity.RARE; collectorNumber = "717"; artist = "Denis Zhbankov" }
}
