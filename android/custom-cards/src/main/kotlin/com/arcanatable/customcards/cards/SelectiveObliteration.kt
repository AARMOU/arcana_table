package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Selective Obliteration — M3C #35.
 *
 * All players choose in APNAP order before anything is exiled. Each iteration stores that
 * player's exactly-monocolored survivors; the outer pipeline then exiles the other colored
 * permanents as one collection. Colorless permanents never enter the exile collection.
 * Current-controller choices, multicolor exclusion, colorless/Devoid survival and APNAP ordering
 * are exercised against the committed runtime in SelectiveObliterationScenarioTest.
 */
val SelectiveObliteration = card("Selective Obliteration") {
    manaCost = "{3}{C}{C}"
    colorIdentity = ""
    typeLine = "Sorcery"
    oracleText = "Each player chooses a color. Then exile each permanent unless it's colorless or it's only the color its controller chose."

    spell {
        effect = Effects.Pipeline {
            val survivors = forEachPlayerCollecting(Player.ActivePlayerFirst) {
                // Capture the typed handle while constructing the branch. The nested pipeline
                // shares this builder's key namespace; do not redeclare a runStoringCollection key.
                var kept: CollectionSlot? = null
                run(Effects.ChooseColorThen(
                        Effects.Pipeline {
                            kept = gather(
                                filter = GameObjectFilter.Permanent.monocolored().withChosenColor(),
                                player = Player.You,
                            )
                        },
                    ))
                listOf(checkNotNull(kept))
            }.single()
            val colored = gather(GameObjectFilter.Permanent.notExactlyColors(0))
            exile(exclude(colored, survivors))
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "35"
        artist = "Yeong-Hao Han"
        imageUri = "https://cards.scryfall.io/normal/front/f/0/f0c21e73-8db1-4ede-bea5-1dcd4b3cef01.jpg?1783911428"
    }
}
