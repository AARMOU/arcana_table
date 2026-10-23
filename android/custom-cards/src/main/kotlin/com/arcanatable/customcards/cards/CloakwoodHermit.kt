package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val CloakwoodHermit = card("Cloakwood Hermit") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Legendary Enchantment — Background"
    oracleText = "Commander creatures you own have \"At the beginning of your end step, if a creature card was put into your graveyard from anywhere this turn, create two tapped 1/1 green Squirrel creature tokens.\""

    staticAbility {
        ability = GrantTriggeredAbility(
            ability = TriggeredAbility.create(
                trigger = Triggers.you.beginningOf(Step.END),
                interveningIf = Conditions.CreatureCardPutIntoYourGraveyardThisTurn(),
                effect = Effects.CreateToken(1, 1, setOf(Color.GREEN), setOf("Squirrel"), count = 2, tapped = true),
            ),
            filter = GroupFilter(GameObjectFilter.Creature.ownedByYou().commander()),
        )
    }
    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "221"
        imageUri = "https://cards.scryfall.io/normal/front/4/b/4bd8689d-a0c1-4041-bead-7b512fe25663.jpg?1783922718"
    }
}
