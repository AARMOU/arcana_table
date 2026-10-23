package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
val UlamogsCrusher = card("Ulamog's Crusher") {
    manaCost = "{8}"
    colorIdentity = ""
    typeLine = "Creature — Eldrazi"
    power = 8
    toughness = 8
    oracleText = "Annihilator 2\nThis creature attacks each combat if able."
    keywordAbility(KeywordAbility.annihilator(2))
    staticAbility { ability = MustAttack() }
    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.Sacrifice(
            GameObjectFilter.Permanent,
            2,
            EffectTarget.PlayerRef(Player.DefendingPlayer)
        )
        description = "Annihilator 2"
    }
    metadata { rarity = Rarity.COMMON; collectorNumber = "13"; artist = "Todd Lockwood" }
}
