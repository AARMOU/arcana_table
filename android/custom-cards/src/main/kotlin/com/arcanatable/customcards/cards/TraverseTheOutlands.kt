package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*
import com.wingedsheep.sdk.scripting.values.*

val TraverseTheOutlands = card("Traverse the Outlands") {
    manaCost = "{4}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "Search your library for up to X basic land cards, where X is the greatest power among creatures " +
        "you control. Put those cards onto the battlefield tapped, then shuffle."

    spell {
        effect = Effects.Pipeline {
            val basics = gather(
                CardSource.FromZone(Zone.LIBRARY, Player.You, GameObjectFilter.BasicLand),
                search = true,
            )
            val chosen = chooseUpTo(
                DynamicAmounts.battlefield(Player.You, GameObjectFilter.Creature.youControl()).maxPower(),
                from = basics,
                prompt = "Choose up to X basic land cards to put onto the battlefield tapped.",
            )
            move(chosen, CardDestination.ToZone(Zone.BATTLEFIELD, Player.You, ZonePlacement.Tapped))
            run(Effects.ShuffleLibrary())
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "34"
        artist = "Adam Paquette"
        imageUri = "https://cards.scryfall.io/normal/front/5/0/507590c4-fe55-404a-926b-24de387a2098.jpg?1783935938"
    }
}
