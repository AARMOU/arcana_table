package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player
val NoiseMarine = card("Noise Marine") {
    manaCost = "{4}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Astartes Warrior"
    power = 3
    toughness = 2
    oracleText = "Cascade\nSonic Blaster — When this creature enters, it deals damage equal to the number of spells you've cast this turn to any target."
    keywords(Keyword.CASCADE)
    triggeredAbility {
        trigger = Triggers.self.isCast()
        effect = Effects.Cascade
        description = "Cascade"
    }
    triggeredAbility {
        trigger = Triggers.self.enters()
        val damageTarget = target(Targets.Any)
        effect = Effects.DealDamage(DynamicAmounts.spellsCastThisTurn(Player.You), damageTarget)
    }
    metadata { rarity = Rarity.UNCOMMON; collectorNumber = "82"; artist = "Johan Grenier" }
}
