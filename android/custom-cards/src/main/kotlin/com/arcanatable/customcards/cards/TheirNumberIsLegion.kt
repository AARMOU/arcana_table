package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.MayCastSelfFromZones
import com.wingedsheep.sdk.scripting.references.Player

/** Intrinsic permission pays the normal cost and never changes sorcery timing.
 * The life total is counted after token creation, including the new artifact creatures.
 * Exile is a resolution instruction, not flashback: a countered spell goes to the graveyard.
 */
val TheirNumberIsLegion = card("Their Number Is Legion") {
    manaCost = "{X}{B}{B}{B}{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "Create X tapped 2/2 black Necron Warrior artifact creature tokens, then you gain life equal to the number of artifacts you control. Exile Their Number Is Legion.\nYou may cast this card from your graveyard."
    staticAbility { ability = MayCastSelfFromZones(zones = listOf(Zone.GRAVEYARD)) }
    spell {
        effect = Effects.CreateToken(
            count = DynamicAmounts.xValue(), power = 2, toughness = 2,
            colors = setOf(Color.BLACK), creatureTypes = setOf("Necron", "Warrior"),
            name = "Necron Warrior", tapped = true, artifactToken = true,
        ) then Effects.GainLife(DynamicAmounts.battlefield(Player.You, GameObjectFilter.Artifact).count())
        selfExile()
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "63"
        artist = "Games Workshop"
        ruling("2022-10-07", "You must still pay its normal cost and follow all normal timing rules when casting Their Number is Legion from your graveyard using the permission granted by its last ability.")
        imageUri = "https://cards.scryfall.io/normal/front/f/0/f03c1eb4-1f47-4b65-8d8d-4057fb6997e1.jpg?1783920902"
    }
}
