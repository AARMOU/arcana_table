package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player
val GuildlessCommons = card("Guildless Commons") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "This land enters tapped.\nWhen this land enters, return a land you control to its owner's hand.\n{T}: Add {C}{C}."
    replacementEffect(EntersTapped())
    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.Pipeline {
            val lands = gather(CardSource.BattlefieldMatching(filter = GameObjectFilter.Land, player = Player.You))
            val returned = chooseExactly(
                1,
                from = lands,
                prompt = "Return a land you control to its owner's hand",
                useTargetingUI = true
            )
            toHand(returned)
        }
    }
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(2)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    metadata { rarity = Rarity.UNCOMMON; collectorNumber = "351"; artist = "Cliff Childs" }
}
