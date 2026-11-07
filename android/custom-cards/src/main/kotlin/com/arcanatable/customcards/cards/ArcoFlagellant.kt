package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.dsl.mechanics.squad
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CantBlock
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val ArcoFlagellant = card("Arco-Flagellant") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Human"
    power = 3
    toughness = 1
    oracleText = "Squad {2}\nThis creature can't block.\n" +
        "Endurant — Pay 3 life: This creature gains indestructible until end of turn."

    squad("{2}")
    staticAbility { ability = CantBlock() }

    activatedAbility {
        cost = Costs.PayLife(3)
        effect = Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "29"
        artist = "Games Workshop"
    }
}
