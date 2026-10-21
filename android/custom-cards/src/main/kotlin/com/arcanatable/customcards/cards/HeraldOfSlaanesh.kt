package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
val HeraldOfSlaanesh = card("Herald of Slaanesh") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Demon"
    power = 2
    toughness = 2
    oracleText = "Locus of Slaanesh — Demon spells you cast cost {2} less to cast.\nOther Demons you control have haste."
    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.YouCast(GameObjectFilter.Creature.withSubtype("Demon")),
            modification = CostModification.ReduceGeneric(2)
        )
    }
    staticAbility {
        ability = GrantKeyword(
            Keyword.HASTE,
            GroupFilter(GameObjectFilter.Creature.withSubtype("Demon").youControl(), excludeSelf = true)
        )
    }
    metadata { rarity = Rarity.UNCOMMON; collectorNumber = "77"; artist = "Alexander Mokhov" }
}
