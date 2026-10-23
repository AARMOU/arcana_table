package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.Chooser
import com.wingedsheep.sdk.scripting.references.Player

val BaneOfBalaGed = card("Bane of Bala Ged") {
    manaCost = "{7}"
    colorIdentity = ""
    typeLine = "Creature — Eldrazi"
    power = 7
    toughness = 5
    oracleText = "Whenever this creature attacks, defending player exiles two permanents they control."
    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.Pipeline {
            val permanents = gather(CardSource.BattlefieldMatching(GameObjectFilter.Permanent, Player.DefendingPlayer))
            val chosen = chooseExactly(2, from = permanents, chooser = Chooser.DefendingPlayer,
                prompt = "Exile two permanents you control")
            exile(chosen, owner = Player.DefendingPlayer)
        }
    }
    metadata { rarity = Rarity.UNCOMMON; collectorNumber = "802"; artist = "Chase Stone" }
}
