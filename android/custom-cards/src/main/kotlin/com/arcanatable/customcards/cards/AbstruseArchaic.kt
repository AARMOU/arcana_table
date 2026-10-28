package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate

/** Abstruse Archaic — CMM #712. */
val AbstruseArchaic = card("Abstruse Archaic") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Creature — Avatar"
    power = 3
    toughness = 4
    oracleText = "Vigilance\n{1}, {T}: Copy target activated or triggered ability you control from a colorless source. You may choose new targets for the copy. (Mana abilities can't be targeted.)"

    keywords(Keyword.VIGILANCE)

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
        val ability = target(
            TargetFilter.ActivatedOrTriggeredAbilityOnStack.youControl()
                .abilitySourceMatches(GameObjectFilter.Any.withCardPredicate(CardPredicate.IsColorless)),
        )
        effect = Effects.CopyTargetSpellOrAbility(ability)
        holdPriority = true
        description = "{1}, {T}: Copy target activated or triggered ability you control from a " +
            "colorless source. You may choose new targets for the copy."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "712"
        artist = "Hristo D. Chukov"
        imageUri = "https://cards.scryfall.io/normal/front/1/f/1fc2871b-c685-4951-b8e2-7370b9668f7e.jpg?1783915488"
    }
}
