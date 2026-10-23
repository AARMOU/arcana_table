package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** A continuous grant: it also affects this creature and creatures entering after it. */
val DeathleaperTerrorWeapon = card("Deathleaper, Terror Weapon") {
    manaCost = "{2}{R}{G}"
    colorIdentity = "RG"
    typeLine = "Legendary Creature — Tyranid"
    power = 3
    toughness = 3
    oracleText = "Flash\nHaste\nFlesh Hooks — Creatures you control that entered this turn have double strike."
    keywords(Keyword.FLASH, Keyword.HASTE)

    staticAbility {
        ability = GrantKeyword(Keyword.DOUBLE_STRIKE, GroupFilter(GameObjectFilter.Creature.youControl().enteredThisTurn()))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "115"
        artist = "Mirko Failoni"
    }
}
