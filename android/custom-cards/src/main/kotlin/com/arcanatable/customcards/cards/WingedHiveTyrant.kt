package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
private val counterBearingAllies = GroupFilter(
    GameObjectFilter.Creature.youControl().withAnyCounter(),
    excludeSelf = true
)
val WingedHiveTyrant = card("Winged Hive Tyrant") {
    manaCost = "{3}{U}{R}"
    colorIdentity = "UR"
    typeLine = "Creature — Tyranid"
    power = 4
    toughness = 4
    oracleText = "Flying, haste\nThe Will of the Hive Mind — Other creatures you control with counters on them have flying and haste."
    keywords(Keyword.FLYING, Keyword.HASTE)
    staticAbility { ability = GrantKeyword(Keyword.FLYING, counterBearingAllies) }
    staticAbility { ability = GrantKeyword(Keyword.HASTE, counterBearingAllies) }
    metadata { rarity = Rarity.RARE; collectorNumber = "148"; artist = "Antonio Jose Manzanedo" }
}
