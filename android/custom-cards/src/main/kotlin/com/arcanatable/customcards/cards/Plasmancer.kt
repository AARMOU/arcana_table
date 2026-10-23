package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.SearchDestination
val Plasmancer = card("Plasmancer") {
    manaCost = "{2}{B}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron Wizard"
    power = 3
    toughness = 3
    oracleText = "Flying\nDynastic Advisor — When this creature enters, search your library for a basic Swamp card, reveal it, put it into your hand, then shuffle."
    keywords(Keyword.FLYING)
    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.BasicLand.withSubtype(Subtype.SWAMP),
            destination = SearchDestination.HAND,
            reveal = true
        )
    }
    metadata { rarity = Rarity.UNCOMMON; collectorNumber = "48"; artist = "Bartek Fedyczak" }
}
